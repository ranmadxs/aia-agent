FROM ghcr.io/nimbleflux/opencode-docker:latest

WORKDIR /app

COPY pyproject.toml poetry.lock ./

RUN poetry install --no-interaction --no-root

ENV PYTHONPATH=/app
ENV PATH="/root/.local/bin:${PATH}"
# OpenRouter config (both passed at runtime via environment)
ENV OPENROUTER_API_KEY=""
ENV OPENROUTER_MODEL=""

CMD ["poetry", "run", "pytest"]