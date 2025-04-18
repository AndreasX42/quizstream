import os
import uuid
import asyncio
import logging
import itertools
from typing import Union
from unittest.mock import patch

from fastapi import HTTPException, status
from langchain.text_splitter import RecursiveCharacterTextSplitter
from langchain.docstore.document import Document
from langchain.chains.qa_generation.base import QAGenerationChain
from langchain_core.prompts import PromptTemplate
from langchain_core.output_parsers import StrOutputParser
from langchain_openai import ChatOpenAI
from langchain_community.document_loaders import YoutubeLoader
from openai import APIError as OpenAIError


from quiz_generator.commons.prompts import get_qa_generation_prompt
from quiz_generator.commons.prompts import (
    RELEVANCY_FILTER_PROMPT,
    get_qa_relevancy_filter_prompt_input_dict,
)
from quiz_generator.commons.db import create_collection
from quiz_generator.api.models import QuizDifficulty, QuizLanguage
from quiz_generator.tests.mock_youtube_loader import MOCK_TRANSCRIPT
from quiz_generator.quiz_generation.utils import LANGUAGE_CODES, redact_api_key

LLM_MODELS = {"OPENAI": "gpt-4o-mini"}

logger = logging.getLogger(__name__)


async def agenerate_quiz(
    quiz_name: str,
    youtube_url: str,
    language: QuizLanguage,
    difficulty: QuizDifficulty,
    api_keys: dict[str, str],
) -> Union[str, list[str]]:
    """Entry method to generate a quiz.

    Steps:
        1. Fetch video transcript in the right language.
        2. Chunk transcript, depending on given difficulty, i.e. the LLM is given more context to generate hard quiz questions than for easy ones.
        3. Generate a short summary of the transcript.
        4. Generate quiz questions of each transcript chunk.
        5. Filter out the best questions in another LLM call and only return the 10 most relevant questions.
        6. Create a new collection and upsert the quiz questions as documents into the pgvector table.

    Args:
        quiz_name (str): Quiz name
        youtube_url (str): YouTube video url
        language (QuizLanguage): Language of quiz
        difficulty (QuizDifficulty): Difficuly of quiz
        api_keys (dict[str, str]): API keys for LLM APIs

    Returns:
        Union[str, list[str]]: id of created collection and ids of upserted quiz questions
    """

    # get video transcript
    transcript = await aget_video_transcript(youtube_url, language)

    # split transcript into chunks
    chunks, video_metadata = await achunk_transcript(transcript, difficulty)

    # validate api keys
    await validate_api_keys(api_keys)

    # create summary of video
    await asummarize_video(video_metadata, api_keys)

    # generate question-answer set
    qa_pairs = await agenerate_quiz_from_transcript(
        chunks, difficulty, language, api_keys, quiz_name
    )

    # filter most relevant questions
    qa_pairs = await afilter_most_relevant_questions(
        qa_pairs=qa_pairs,
        summary=video_metadata["description"],
        difficulty=difficulty,
        language=language,
        api_keys=api_keys,
    )

    # upsert into vector database
    collection_id, qa_ids = create_collection(quiz_name, qa_pairs, video_metadata)

    return collection_id, qa_ids


async def validate_api_keys(api_keys: dict[str, str]):
    """Validates the API keys

    Args:
        api_keys (dict[str, str]): API keys for LLM APIs
    """

    # TODO: Only OPENAI_API_KEY works for now
    if "OPENAI_API_KEY" not in api_keys or "default key" in api_keys["OPENAI_API_KEY"]:
        api_keys["OPENAI_API_KEY"] = os.environ.get("DEFAULT_OPENAI_API_KEY")


async def afilter_most_relevant_questions(
    qa_pairs: list[Document],
    summary: str,
    difficulty: QuizDifficulty,
    language: QuizLanguage,
    api_keys: dict[str, str],
) -> list[Document]:
    """Returns the top 10 most relevant questions

    Args:
        qa_pairs (list[Document]): List of all generated questions
        summary (str): YouTube video transcript summary
        difficulty (QuizDifficulty): Quiz difficulty
        language (QuizLanguage): Quiz language
        api_keys (dict[str, str]): API keys for LLM APIs

    Raises:
        HTTPException: In case of invalid API key

    Returns:
        list[Document]: list of most relevant questions
    """

    logger.debug("Running relevancy filter on quiz questions.")

    # prepare formatted prompt per quiz question
    relevancy_filter_prompts = [
        get_qa_relevancy_filter_prompt_input_dict(
            quiz_question=qa_pairs[i],
            summary=summary,
            difficulty=difficulty.name,
            language=language.name,
        )
        for i in range(len(qa_pairs))
    ]

    try:

        llm = ChatOpenAI(
            temperature=0,
            model=LLM_MODELS["OPENAI"],
            api_key=api_keys["OPENAI_API_KEY"],
        )

        grading_chain = RELEVANCY_FILTER_PROMPT | llm | StrOutputParser()

        # run llm chain on list of formatted prompts
        grades_list = await grading_chain.abatch(relevancy_filter_prompts)

    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={
                "error_external": f"{type(e).__name__}: Error filtering for best quiz questions.",
                "error_internal": f"{type(e)}: {str(e)}",
            },
        ) from e

    # format grades and remove all with grade 0
    grades_list = [
        (i, float(grade)) for i, grade in enumerate(grades_list) if float(grade) > 0
    ]

    # sort by grade and remove all questions with grade 0
    sorted_grades = sorted(grades_list, key=lambda grade: grade[1], reverse=True)

    # select 10 most relevant questions
    qa_list_final = [qa_pairs[i] for i, _ in sorted_grades[:10]]

    return qa_list_final


async def aget_video_transcript(youtube_url: str, language: QuizLanguage) -> Document:
    """Retrieves transcript of given youtube video in given language

    Args:
        youtube_url (str): youtube video url
        language (str): target language of transcript

    Raises:
        HTTPException: In case of error during processing

    Returns:
        str: transcript
    """
    logger.debug(
        "Retrieving transcript for %s in language %s.",
        youtube_url,
        language.name,
    )

    # check if running in test mode
    if os.getenv("EXECUTION_CONTEXT") == "test":
        # mock YoutubeLoader.load() if we are in test mode
        logger.debug("Mocking YoutubeLoader.load() in test context.")
        with patch(
            "langchain_community.document_loaders.YoutubeLoader.load"
        ) as mock_load:
            # create a mock document with a mock transcript
            mock_load.return_value = [MOCK_TRANSCRIPT]
            return MOCK_TRANSCRIPT

    loader = YoutubeLoader.from_youtube_url(
        youtube_url=youtube_url,
        language=LANGUAGE_CODES,
        translation=language.name.lower(),
        add_video_info=True,
    )

    try:
        return loader.load()[0]
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={
                "error_external": f"{type(e).__name__}: Error fetching video transcript.",
                "error_internal": f"{type(e)}: {str(e)}",
            },
        ) from e


async def asummarize_video(video_metadata: dict[str, str], api_keys: dict[str, str]):
    """Creates a summary of the youtube video

    Args:
        video_metadata (dict[str, str]): metadata of transcript
        api_keys (dict[str, str]): API keys of LLM APIs

    Raises:
        HTTPException: Exception in case of invalid API keys
    """

    prompt_template = """Write a very concise summary of about 3 to 4 sentences of the following text and in the same language:
        "{text}"
    CONCISE SUMMARY:"""

    prompt = PromptTemplate.from_template(prompt_template)

    doc_to_summarize = Document(page_content=video_metadata["transcript"])

    try:

        llm = ChatOpenAI(
            temperature=0,
            model=LLM_MODELS["OPENAI"],
            api_key=api_keys["OPENAI_API_KEY"],
        )

        summarizing_chain = prompt | llm | StrOutputParser()
        video_metadata["description"] = summarizing_chain.invoke(doc_to_summarize)

    except KeyError as e:

        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={
                "error_external": f"{type(e).__name__}: No OpenAI API key provided.",
                "error_internal": f"{type(e)}: No OpenAI API key provided. " + str(e),
            },
        ) from e

    except OpenAIError as e:

        if "invalid_api_key" in e.message:
            msg = "Invalid OpenAI API key provided."
        else:
            msg = "Error during OpenAI API call."

        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={
                "error_external": f"{type(e).__name__}: {msg}",
                "error_internal": f"{type(e)}: {redact_api_key(str(e))}",
            },
        ) from e

    except Exception as e:

        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={
                "error_external": f"{type(e).__name__}: Error summarizing video transcript.",
                "error_internal": f"{type(e)}: {str(e)}",
            },
        ) from e


async def agenerate_quiz_from_transcript(
    chunks: list[Document],
    difficulty: QuizDifficulty,
    language: QuizLanguage,
    api_keys: dict[str, str],
    quiz_name: str,
) -> list[Document]:
    """Generates a quiz from transcript and user input parameters

    Args:
        chunks (list[Document]): Transcript chunks
        difficulty (QuizDifficulty): Quiz difficulty
        language (QuizLanguage): Quiz language
        api_keys (dict[str, str]): API keys of LLM APIs
        quiz_name (str): Name of quiz

    Raises:
        HTTPException: In case of error

    Returns:
        list[dict[str, str]]: _description_
    """

    logger.debug("Creating quiz from transcript chunks.")

    try:
        llm = ChatOpenAI(
            temperature=0,
            model=LLM_MODELS["OPENAI"],
            api_key=api_keys["OPENAI_API_KEY"],
        )

        qa_generator_chain = QAGenerationChain.from_llm(
            llm=llm,
            prompt=get_qa_generation_prompt(
                difficulty=difficulty.name, language=language.name, num_attempt=1
            ),
        )

        tasks = [
            get_quiz_question_from_chunk(chunk, qa_generator_chain, i)
            for i, chunk in enumerate(chunks)
        ]

        qa_pairs = await asyncio.gather(*tasks)
        qa_pairs = list(itertools.chain.from_iterable(qa_pairs))

    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={
                "error_external": f"{type(e).__name__}: Error generating quiz questions.",
                "error_internal": f"{type(e)}: {str(e)}",
            },
        ) from e

    if len(qa_pairs) < len(chunks):
        logger.warning(
            "Only generated %s quiz questions in quiz '%s', expected %s.",
            str(len(qa_pairs)),
            quiz_name,
            str(len(chunks)),
        )

    if len(qa_pairs) == 0:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={
                "error_external": f"GeneratorError: Try other video or settings.",
                "error_internal": f"Found {len(chunks)} chunks, but generated {len(qa_pairs)} quiz questions with {LLM_MODELS['OPENAI']}. Length of chunks: {str.join(', ', [str(len(chunk.page_content)) for chunk in chunks])}",
            },
        )

    return qa_pairs


async def get_quiz_question_from_chunk(
    chunk: Document,
    qa_generator_chain: QAGenerationChain,
    index: int,
) -> list[Document]:
    """Generates a quiz question from a given chunk

    Args:
        chunk (Document): The chunk
        qa_generator_chain (QAGenerationChain): Chain for QA generation
        index (int): The index of chunk in list of chunks

    Returns:
        list[Document]: List of quiz questions
    """

    logger.debug("Generating quiz question from given chunk.")

    max_retries = 3
    num_attempt = 1
    processed_pairs = []

    while num_attempt <= max_retries and len(processed_pairs) < 1:

        try:
            # attempt to generate qa pairs
            questions_answers_dict = qa_generator_chain.invoke(chunk.page_content)

            if not isinstance(questions_answers_dict, dict):
                raise TypeError(
                    f"Unexpected return type: {type(questions_answers_dict)}. Expected a dict."
                )

            for question_answer in questions_answers_dict["questions"]:
                question = question_answer["question"]
                answers = question_answer["answer"]
                metadata = {**chunk.metadata}
                metadata.update(
                    {
                        "answers": answers,
                        "id": str(uuid.uuid4()),
                        "context": chunk.page_content,
                    }
                )
                qa_document = Document(page_content=question, metadata=metadata)
                processed_pairs.append(qa_document)
            break

        except Exception:
            # increment and retry with updated prompt
            num_attempt += 1
            qa_generator_chain.llm_chain.prompt.messages[
                1
            ].prompt.partial_variables.update({"num_attempt": num_attempt})

    return processed_pairs


async def achunk_transcript(
    transcript: Document, difficulty: QuizDifficulty
) -> list[Document]:
    """Creates chunks from the given video transcript

    Args:
        transcript (Document): video transcript
        difficulty (QuizDifficulty): difficulty of quiz

    Returns:
        list[Document]: list of documents which represent the chunks
    """

    logger.debug("Creating chunks from transcript")

    # number of chunks should depend on difficulty set by user,
    # such that the LLM has more context to generate harder quiz questions
    # and less context to generate easier ones.
    if difficulty == QuizDifficulty.EASY:
        chunk_size = 500
    elif difficulty == QuizDifficulty.MEDIUM:
        chunk_size = 750
    else:
        chunk_size = 1000

    text_splitter = RecursiveCharacterTextSplitter(
        chunk_size=chunk_size,
        chunk_overlap=20,
        length_function=len,
        add_start_index=True,
    )

    doc = Document(page_content=transcript.page_content, metadata={})

    video_metadata = dict(**transcript.metadata)
    video_metadata.update({"transcript": transcript.page_content})

    chunks = text_splitter.split_documents([doc])

    # add end_index
    for chunk in chunks:
        chunk.metadata["end_index"] = chunk.metadata["start_index"] + len(
            chunk.page_content
        )

    return chunks, video_metadata
