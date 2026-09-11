FROM ghcr.io/nimbleflux/opencode-docker:latest

WORKDIR /app

RUN python3 -m pip install --upgrade pip --break-system-packages && python3 -m pip install poetry --break-system-packages

COPY pyproject.toml poetry.lock ./

RUN poetry install --no-interaction --no-root

ENV PYTHONPATH=/app
ENV PATH="/root/.local/bin:${PATH}"
# OpenRouter config (both passed at runtime via environment)
ENV OPENROUTER_API_KEY=""
ENV OPENROUTER_MODEL="inclusionai/ling-3.0-flash-fin:free"
ENV BROWSER=none

# Prevent opencode from crashing when trying to open browser via xdg-open
RUN echo '#!/bin/sh' > /usr/local/bin/xdg-open && chmod +x /usr/local/bin/xdg-open

