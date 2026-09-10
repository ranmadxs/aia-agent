FROM ghcr.io/nimbleflux/opencode-docker:latest

WORKDIR /app

COPY pyproject.toml poetry.lock ./

RUN poetry install --no-interaction --no-root

COPY workers/ ./workers/
COPY render.yaml ./

ENV PYTHONPATH=/app
ENV PATH="/root/.local/bin:${PATH}"

CMD ["poetry", "run", "mqtt-worker"]