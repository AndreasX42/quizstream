from quiz_generator.api.schemas import (
    QuizCreateRequestDto,
    QuizCreateResultDto,
)
from sqlalchemy.orm import Session
from sqlalchemy import select
from quiz_generator.quiz_generation import agenerate_quiz
from quiz_generator.api.models import (
    UserToQuiz,
    LangchainPGCollection,
    QuizRequest,
    QuizRequestStatus,
)
from fastapi import HTTPException, status

import datetime as dt
from uuid import UUID
import logging

logger = logging.getLogger(__name__)


async def create_quiz_http(
    quiz_data: QuizCreateRequestDto, session: Session
) -> QuizCreateResultDto:
    """Entry point for quiz generation for FastAPI HTTP requests"""

    # check quiz name is unique
    assert_quiz_name_not_exists(quiz_data.user_id, quiz_data.quiz_name, session)

    collection_id, qa_ids = await agenerate_quiz(
        quiz_name=quiz_data.quiz_name,
        youtube_url=str(quiz_data.youtube_url),
        language=quiz_data.language,
        difficulty=quiz_data.difficulty,
        api_keys=quiz_data.api_keys,
    )

    # insert the mapping into the users_quizzes table
    user_to_quiz = UserToQuiz(
        user_id=quiz_data.user_id,
        quiz_id=collection_id,
        num_questions=len(qa_ids),
        language=quiz_data.language.name,
        type=quiz_data.type,
        difficulty=quiz_data.difficulty,
        date_created=dt.datetime.now(dt.UTC).strftime("%Y-%m-%dT%H:%M:%SZ"),
    )

    session.add(user_to_quiz)
    session.commit()

    # prepare result dto
    quizResultDto = QuizCreateResultDto(
        user_id=quiz_data.user_id, quiz_id=collection_id, quiz_name=quiz_data.quiz_name
    )

    return quizResultDto


async def create_quiz_lambda(quiz_data: QuizCreateRequestDto, session: Session) -> None:
    """Entry point for quiz generation for AWS Lambda"""

    # Fetch the QuizRequest record first
    quiz_request = (
        session.query(QuizRequest)
        .filter_by(user_id=quiz_data.user_id, quiz_name=quiz_data.quiz_name)
        .with_for_update()
        .first()
    )

    if not quiz_request:
        logger.error(
            f"QuizRequest not found for user {quiz_data.user_id}, quiz_name: {quiz_data.quiz_name}"
        )
        return

    # Check if already processing or completed
    if quiz_request.status != QuizRequestStatus.QUEUED:
        logger.warning(
            f"Quiz generation already processed or in progress for {quiz_data.user_id}/{quiz_data.quiz_name}. Status: {quiz_request.status}"
        )
        return

    # Initial update to PROCESSING
    quiz_request.status = QuizRequestStatus.PROCESSING
    quiz_request.message_int = "Starting quiz generation process"
    quiz_request.message_ext = "Processing quiz request"

    try:
        session.commit()
        logger.info(
            f"QuizRequest status updated to PROCESSING for {quiz_data.user_id}/{quiz_data.quiz_name}"
        )
    except Exception as commit_err:
        logger.error(
            f"Failed to commit PROCESSING status for {quiz_data.user_id}/{quiz_data.quiz_name}: {commit_err}",
            exc_info=True,
        )
        session.rollback()
        raise commit_err

    collection_id = None
    qa_ids = []
    try:
        collection_id, qa_ids = await agenerate_quiz(
            quiz_name=quiz_data.quiz_name,
            youtube_url=str(quiz_data.youtube_url),
            language=quiz_data.language,
            difficulty=quiz_data.difficulty,
            api_keys=quiz_data.api_keys,
        )
        logger.info(
            f"Quiz generation successful for {quiz_data.user_id}/{quiz_data.quiz_name}. Collection ID: {collection_id}, QA IDs: {len(qa_ids)}"
        )

        quiz_request.status = QuizRequestStatus.FINISHED
        quiz_request.quiz_id = collection_id
        quiz_request.message_int = f"Quiz generated with {len(qa_ids)} questions"
        quiz_request.message_ext = "Quiz generated successfully"

        user_to_quiz = UserToQuiz(
            user_id=quiz_data.user_id,
            quiz_id=collection_id,
            num_questions=len(qa_ids),
            language=quiz_data.language,
            type=quiz_data.type,
            difficulty=quiz_data.difficulty,
        )
        session.add(user_to_quiz)
        logger.info(
            f"UserToQuiz mapping created for user {quiz_data.user_id}, quiz_id {collection_id}"
        )

    except HTTPException as http_exc:
        logger.error(
            f"HTTPException during quiz generation for {quiz_data.user_id}/{quiz_data.quiz_name}: Status={http_exc.status_code}, Detail={http_exc.detail}",
            exc_info=True,
        )
        quiz_request.status = QuizRequestStatus.FAILED

        detail = http_exc.detail
        if isinstance(detail, dict):
            quiz_request.message_int = detail.get("error_internal")
            quiz_request.message_ext = detail.get("error_external")
        else:
            quiz_request.message_int = (
                f"HTTPException {http_exc.status_code}: {str(detail)}"
            )
            quiz_request.message_ext = "Quiz generation failed"

        quiz_request.quiz_id = None
        raise http_exc

    except Exception as e:
        logger.error(
            f"Unexpected error during quiz generation for {quiz_data.user_id}/{quiz_data.quiz_name}: {e}",
            exc_info=True,
        )
        quiz_request.status = QuizRequestStatus.FAILED
        quiz_request.message_int = f"Unexpected Error: {type(e).__name__}: {str(e)}"
        quiz_request.message_ext = "Quiz generation failed due to an unexpected error"
        quiz_request.quiz_id = None
        raise e

    finally:
        try:
            session.commit()
            logger.info(
                f"Final QuizRequest status committed for {quiz_data.user_id}/{quiz_data.quiz_name}: {quiz_request.status}"
            )
        except Exception as final_commit_err:
            logger.error(
                f"Failed to commit final status for {quiz_data.user_id}/{quiz_data.quiz_name}: {final_commit_err}",
                exc_info=True,
            )
            session.rollback()
            raise final_commit_err


def assert_quiz_name_not_exists(user_id: UUID, quiz_name: str, session: Session):
    """Check on uniqueness of quiz name"""

    quizzes_with_same_name = (
        session.execute(
            select(LangchainPGCollection.uuid).where(
                LangchainPGCollection.name == quiz_name
            )
        )
        .scalars()
        .all()
    )

    if not quizzes_with_same_name:
        return

    matchWithUserQuizzes = (
        session.execute(
            select(UserToQuiz).where(
                UserToQuiz.user_id == user_id,
                UserToQuiz.quiz_id.in_(quizzes_with_same_name),
            )
        )
        .scalars()
        .first()
    )

    if matchWithUserQuizzes:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={
                "error_external": f"Quiz with name '{quiz_name}' already exists.",
                "error_internal": f"Quiz name '{quiz_name}' already exists for user {user_id}.",
            },
        )
