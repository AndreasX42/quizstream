import os
from sqlalchemy import create_engine
from sqlalchemy.orm import declarative_base, sessionmaker

try:
    from aws_lambda_powertools.utilities.parameters import SecretsProvider

    # Use the AWS Secrets Manager secrets provider
    secrets_provider = SecretsProvider()

    # Fetch the secret
    secret_arn: str = os.environ.get("DB_SECRET_ARN", "DbSecret")

    secret = secrets_provider.get(secret_arn, transform="json")
    USER = secret["username"]
    PWD = secret["password"]
    HOST = secret["host"]
    PORT = secret.get("port", 5432)
    DB = secret["dbname"]
    DRIVER = "psycopg"
except Exception as e:
    USER = os.environ.get("POSTGRES_USER")
    PWD = os.environ.get("POSTGRES_PASSWORD")
    HOST = os.environ.get("POSTGRES_HOST")
    PORT = os.environ.get("POSTGRES_PORT")
    DB = os.environ.get("POSTGRES_DATABASE")
    DRIVER = os.environ.get("POSTGRES_DRIVER", "psycopg")

DATABASE_URL = f"postgresql+{DRIVER}://{USER}:{PWD}@{HOST}:{PORT}/{DB}"

# SQLAlchemy setup
engine = create_engine(DATABASE_URL, pool_pre_ping=True)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()


def get_db():
    db_session = SessionLocal()
    try:
        yield db_session
    finally:
        db_session.close()
