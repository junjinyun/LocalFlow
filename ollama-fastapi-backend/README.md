# Ollama Local LLM Backend

This FastAPI backend receives a string from a frontend, sends it to the local
Ollama `exaone3.5:2.4b` model, and returns the generated text as JSON. It does
not require an OpenAI API key or a `.env` file.

## Prerequisites

- Python 3.10 or newer
- Ollama installed and running
- The `exaone3.5:2.4b` model available locally

If the model is not installed yet, run this once:

```powershell
ollama pull exaone3.5:2.4b
```

## Install and run

Run these commands inside this folder:

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
python -m uvicorn main:app --reload
```

The server starts at `http://127.0.0.1:8000`. Interactive API documentation is
available at `http://127.0.0.1:8000/docs`.

## API examples

Health check:

```http
GET /
```

Expected response:

```json
{"status":"ok","model":"exaone3.5:2.4b"}
```

Generate an AI response:

```http
POST /api/ai
Content-Type: application/json

{"message":"Explain three benefits of Python."}
```

Response format:

```json
{"result":"Generated AI response"}
```

Frontend example:

```javascript
const response = await fetch("http://127.0.0.1:8000/api/ai", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ message: "Hello" }),
});

const data = await response.json();
console.log(data.result);
```

CORS allows all origins for local development. Before deployment, restrict
`allow_origins` in `main.py` to the actual frontend origin.

