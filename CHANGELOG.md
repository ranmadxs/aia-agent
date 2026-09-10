# Changelog

Todos los cambios notables en este proyecto se documentarán aquí.

El formato sigue [Conventional Commits](https://conventionalcommits.org/).

## [0.8.1] - 2026-09-10

### Fixed
- `Dockerfile`: Cambiado `python` por `python3` para compatibilidad con la imagen base `ghcr.io/nimbleflux/opencode-docker:latest`
- `Dockerfile`: Agregada instalación de Python 3.12 (`apt-get`) y `POETRY_PYTHON=python3.12` para compatibilidad con `^3.12` en `pyproject.toml`
- `Dockerfile`: Agregado `--break-system-packages` a `pip install` para compatibilidad con PEP 668
- `.github/workflows/pr.yml`: Corregido `python` por `python3` en el paso de instalación de Poetry
- `.github/workflows/pr.yml`: Agregado `--break-system-packages` a `pip install` para compatibilidad con PEP 668
- `.github/workflows/docker-image.yml`: Reemplazados secrets inexistentes (`MONGODB_URI`, `MQTT_*`, `OPENROUTER_MODEL`) por los secrets reales disponibles (`OLLAMA_HOST`, `OPENROUTER_API_KEY`)
- `tests/test_ci.py`: Versión de pyproject.toml verificada de forma genérica (regex semver) en lugar de hardcodeada
- `tests/test_docker.py`: Corregido `python` por `python3` en la verificación de estructura del Dockerfile

## [0.8.0] - 2026-09-10

### Added
- `Dockerfile`: Instalación de Poetry vía `python -m pip install poetry`
- `tests/test_docker.py`: `TestDockerfileStructure` con tests de integridad del Dockerfile
- `.github/workflows/release.yml`: Auto-tag de versión desde `pyproject.toml` con build y push de imagen Docker

### Changed
- Bumps de versión a `0.8.0`

## [0.7.0] - 2026-09-09

### Added
- `docker-image.yml`: Workflow completo de build, push y deploy con dependencia de PR Checks
- `release.yml`: Auto-tag anotado `vX.Y.Z` desde `pyproject.toml` en pushes a `main`
- PR Checks con Semver Check y Unit Tests

## [0.5.0] - 2026-09-08

### Added
- `pr.yml`: Workflow de PR Checks (Semver Check + Unit Tests)
- Soporte para Python 3.12
- `tests/test_ci.py`: Tests CI mínimos
- `tests/test_docker.py`: Tests de entorno Docker

### Changed
- Bumps de versión a `0.5.0`

## [0.1.0] - 2026-09-07

### Added
- `feat: add docker support for all workers`
- Estructura base del proyecto con Poetry
- Workers: `mqtt-worker`, `whatsapp-reader`, `whatsapp-sender`
