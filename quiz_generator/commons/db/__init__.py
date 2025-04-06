TABLE_COLLECTION = "langchain_pg_collection"
TABLE_DOCS = "langchain_pg_embedding"


from quiz_generator.commons.db.database import (
    SessionLocal as SessionLocal,
    DATABASE_URL as CONNECTION_STRING,
    get_db,
)

from quiz_generator.commons.db.pgvector import (
    create_collection,
    delete_collection,
    get_collection_metadata,
    get_all_by_collection_id,
    get_by_ids,
    list_collections,
)

from quiz_generator.commons.db.database import Base, engine
