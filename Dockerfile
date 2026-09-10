FROM ghcr.io/nimbleflux/opencode-docker:latest

WORKDIR /app

COPY pyproject.toml poetry.lock ./

RUN poetry install --no-interaction --no-root

ENV PYTHONPATH=/app
ENV PATH="/root/.local/bin:${PATH}"
# OpenRouter config (API key passed at runtime, model has default)
ENV OPENROUTER_API_KEY=""
ENV OPENROUTER_MODEL="cohere/north-mini-code:free"

CMD ["poetry", "run", "pytest"]