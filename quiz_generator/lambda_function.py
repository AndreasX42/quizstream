import os
from typing import Dict, Any
import asyncio
from asyncio import TimeoutError

from aws_lambda_powertools import Logger, Metrics
from aws_lambda_powertools.metrics import MetricUnit
from aws_lambda_powertools.utilities.parser import parse
from aws_lambda_powertools.utilities.batch import (
    process_partial_response,
    BatchProcessor,
    EventType,
)
from aws_lambda_powertools.utilities.typing import LambdaContext
from quiz_generator.commons.db.database import SessionLocal
from quiz_generator.api.services import quiz_service
from quiz_generator.api.schemas import QuizCreateRequestDto

logger = Logger(service="quiz-service")
metrics = Metrics(namespace="QuizService")

# Instantiate BatchProcessor globally for SQS events
processor = BatchProcessor(event_type=EventType.SQS)


@logger.inject_lambda_context()
def lambda_handler(event: Dict[str, Any], context: LambdaContext) -> Dict[str, Any]:
    return process_partial_response(
        event=event, record_handler=process_record, processor=processor, context=context
    )


def process_record(record: Dict[str, Any]):
    try:
        quiz_data: QuizCreateRequestDto = parse(
            event=record["body"], model=QuizCreateRequestDto
        )

        logger.info(f"Quiz data: {quiz_data}")

        logger.append_keys(user_id=quiz_data.user_id, quiz_name=quiz_data.quiz_name)
        logger.info("Processing quiz creation from SQS")

        with SessionLocal() as db:
            timeout_seconds = 120
            try:

                async def run_create_with_timeout():
                    await asyncio.wait_for(
                        quiz_service.create_quiz_lambda(quiz_data, db),
                        timeout=timeout_seconds,
                    )

                asyncio.run(run_create_with_timeout())
                metrics.add_metric(name="QuizCreated", unit=MetricUnit.Count, value=1)
            except TimeoutError:
                logger.error(f"Quiz creation timed out after {timeout_seconds} seconds")
                raise

    except Exception as e:
        logger.exception(f"Failed to process record: {e}")
        metrics.add_metric(name="QuizFailed", unit=MetricUnit.Count, value=1)
        raise
