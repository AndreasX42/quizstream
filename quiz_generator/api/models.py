from sqlalchemy import (
    Column,
    String,
    Integer,
    BigInteger,
    DateTime,
    Boolean,
    JSON,
    ForeignKey,
    Enum,
)

import uuid
import datetime as dt

from sqlalchemy.dialects.postgresql import UUID
from pgvector.sqlalchemy import Vector
from sqlalchemy.schema import Index, UniqueConstraint
from sqlalchemy.dialects.postgresql import JSONB

from quiz_generator.commons.db import Base

from enum import Enum as PythonEnum


class QuizType(str, PythonEnum):
    MULTIPLE_CHOICE = "MULTIPLE_CHOICE"


class QuizDifficulty(str, PythonEnum):
    EASY = "EASY"
    MEDIUM = "MEDIUM"
    HARD = "HARD"


class QuizLanguage(str, PythonEnum):
    EN = "EN"
    ES = "ES"
    DE = "DE"


class Role(str, PythonEnum):
    USER = "USER"
    ADMIN = "ADMIN"


class LangchainPGCollection(Base):
    __tablename__ = "langchain_pg_collection"

    uuid = Column(
        UUID(as_uuid=True), primary_key=True, nullable=False, default=uuid.uuid4
    )
    name = Column(String, nullable=False, unique=True)
    cmetadata = Column(JSON, nullable=True)

    __table_args__ = (
        UniqueConstraint("name", name="langchain_pg_collection_name_key"),
    )


class LangchainPGEmbedding(Base):
    __tablename__ = "langchain_pg_embedding"

    id = Column(String, primary_key=True, index=True, unique=True)
    collection_id = Column(
        UUID(as_uuid=True),
        ForeignKey("langchain_pg_collection.uuid", ondelete="CASCADE"),
    )
    embedding = Column(Vector, nullable=True)
    document = Column(String, nullable=True)
    cmetadata = Column(JSONB, nullable=True)

    __table_args__ = (
        Index(
            "ix_cmetadata_gin",
            "cmetadata",
            postgresql_using="gin",
            postgresql_ops={"cmetadata": "jsonb_path_ops"},
        ),
    )


class User(Base):
    __tablename__ = "users"
    id = Column(
        UUID(as_uuid=True),
        primary_key=True,
        nullable=False,
        default=uuid.uuid4,
        index=True,
    )
    username = Column(String, index=False, unique=True, nullable=False)
    email = Column(String, index=False, unique=True, nullable=False)


class UserToQuiz(Base):
    __tablename__ = "user_quiz"
    user_id = Column(
        UUID(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), primary_key=True
    )
    quiz_id = Column(UUID, ForeignKey("langchain_pg_collection.uuid"), primary_key=True)
    num_tries = Column(Integer, default=0)
    num_correct = Column(Integer, default=0)
    num_questions = Column(Integer, default=0)
    language = Column(Enum(QuizLanguage), default=QuizLanguage.EN)
    type = Column(Enum(QuizType), default=QuizType.MULTIPLE_CHOICE)
    difficulty = Column(Enum(QuizDifficulty), default=QuizDifficulty.EASY)
    date_created = Column(DateTime, default=dt.datetime.now(dt.UTC))
