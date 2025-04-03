from fastapi import FastAPI
from fastapi.responses import JSONResponse
from fastapi import status
from backend.api.routers import quiz_router
from backend.commons.db import Base, engine

app = FastAPI(
    title="QuizManager Documentation",
    version="0.01",
    description="""API for the backend component managing the quizzes.""",
    # root path should only be "/api" in production
    # root_path="" if os.environ.get("EXECUTION_CONTEXT") in ["DEV", "TEST"] else "/api",
)


@app.get("/health", tags=["Health"])
async def health_check():
    return JSONResponse(
        status_code=status.HTTP_200_OK,
        content={"message": "Health check passed", "status": 200},
    )


app.include_router(quiz_router.router)

Base.metadata.create_all(bind=engine)
