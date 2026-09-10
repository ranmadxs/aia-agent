FROM ghcr.io/nimbleflux/opencode-docker:latest

WORKDIR /app

RUN apt-get update && apt-get install -y python3.12 python3.12-venv python3.12-dev && rm -rf /var/lib/apt/lists/* && python3.12 -m pip install --upgrade pip --break-system-packages && python3.12 -m pip install poetry --break-system-packages

COPY pyproject.toml poetry.lock ./

RUN POETRY_PYTHON=python3.12 poetry install --no-interaction --no-root

ENV PYTHONPATH=/app
ENV PATH="/root/.local/bin:${PATH}"
# OpenRouter config (both passed at runtime via environment)
ENV OPENROUTER_API_KEY=""
ENV OPENROUTER_MODEL=""

CMD ["poetry", "run", "pytest"]