"""FastAPI backend that sends frontend messages to a local Ollama model."""

import logging

import ollama
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field


MODEL_NAME = "exaone3.5:2.4b"

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(
    title="Local LLM API",
    description="Backend API powered by a local Ollama model.",
    version="1.0.0",
)

# Local development setting. Restrict this to the frontend origin in production.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)


class AIRequest(BaseModel):
    """JSON request body received from the frontend."""

    message: str = Field(min_length=1, description="Message sent to the local LLM")


class AIResponse(BaseModel):
    """JSON response body returned to the frontend."""

    result: str


@app.get("/")
def health_check() -> dict[str, str]:
    """Return server health and the configured model name."""

    return {"status": "ok", "model": MODEL_NAME}


@app.post("/api/ai", response_model=AIResponse)
def generate_ai_response(request: AIRequest) -> AIResponse:
    """Send a message to Ollama and return the generated text."""

    try:
        response = ollama.chat(
            model=MODEL_NAME,
            messages=[{"role": "user", "content": request.message}],
        )
        result = response["message"]["content"].strip()
        return AIResponse(result=result)
    except Exception as exc:
        logger.exception("Failed to generate an Ollama response")
        raise HTTPException(
            status_code=503,
            detail=(
                "Local AI response generation failed. "
                "Check that Ollama is running and exaone3.5:2.4b is installed."
            ),
        ) from exc

