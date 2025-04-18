from pydantic import BaseModel, Field, HttpUrl, field_validator
from datetime import datetime, timezone
from uuid import uuid4, UUID
from typing import Any
import re

from quiz_generator.api.models import QuizType, QuizDifficulty, QuizLanguage


class QuizCreateRequestDto(BaseModel):
    user_id: UUID = Field(description="User id")
    quiz_name: str = Field(min_length=3, description="Name of quiz")
    api_keys: dict[str, str] = Field(description="Dictionary of API keys.")
    youtube_url: HttpUrl
    language: QuizLanguage = Field(
        description="Language in which the quiz should be generated."
    )
    type: QuizType = Field(description="Type of quiz")
    difficulty: QuizDifficulty = Field(description="Difficulty of quiz")

    @field_validator("quiz_name")
    @classmethod
    def lowercase_quiz_name(cls, v: str) -> str:
        if isinstance(v, str):
            return v.lower()
        raise ValueError("Unexpected type for 'quiz name'.")

    @field_validator("youtube_url")
    @classmethod
    def validate_youtube_url(cls, value: HttpUrl) -> HttpUrl:

        youtube_regex = (
            r"(https?://)?(www\.)?"
            r"(youtube|youtu|youtube-nocookie)\.(com|be)/"
            r"(?:watch\?v=|embed/|v/|.+/|v=|\/)"
            r"([0-9A-Za-z_-]{11})"
        )

        youtube_pattern = re.compile(youtube_regex)

        if not youtube_pattern.match(str(value)):
            raise ValueError("Invalid YouTube video URL")

        return value


class QuizCreateResultDto(BaseModel):
    user_id: UUID = Field(description="User id")
    quiz_id: UUID = Field(default_factory=uuid4, description="Quiz id")
    quiz_name: str = Field(min_length=1, description="Name of quiz")


class QuizOutboundDto(BaseModel):
    user_id: UUID = Field(description="User id")
    quiz_id: UUID = Field(default_factory=uuid4, description="Quiz id")
    quiz_name: str = Field(min_length=1, description="Name of quiz")
    language: str = Field(
        min_length=2, description="Language in which the quiz should be generated."
    )
    date_created: str = Field(
        default_factory=lambda: datetime.now(timezone.utc).strftime(
            "%Y-%m-%dT%H:%M:%SZ"
        ),
        description="Date when quiz was created",
    )
    type: QuizType = Field(description="Type of quiz")
    difficulty: QuizDifficulty = Field(description="Difficulty of quiz")
    video_metadata: dict[str, Any] = Field(description="Additional video information.")
