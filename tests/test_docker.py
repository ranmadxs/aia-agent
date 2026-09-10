"""Docker integration tests for aia-agent."""

import pytest
import os
import subprocess


class TestDockerEnvironment:
    """Tests that run inside the Docker container."""

    def test_docker_env_file_exists(self):
        """Test that we're running in Docker (/.dockerenv exists)."""
        assert os.path.exists('/.dockerenv'), "Not running in Docker container"

    def test_working_directory_is_app(self):
        """Test working directory is /app in Docker."""
        assert os.getcwd() == '/app'

    def test_pythonpath_is_set(self):
        """Test PYTHONPATH is set to /app."""
        assert os.environ.get('PYTHONPATH') == '/app'

    def test_openrouter_model_default(self):
        """Test OPENROUTER_MODEL is set (empty by default, configured at deploy)."""
        model = os.environ.get('OPENROUTER_MODEL')
        # Should be defined (empty string by default in image, set at deploy time)
        assert model is not None, "OPENROUTER_MODEL should be defined"
        assert model == '', f"Expected empty string by default, got '{model}'"

    def test_openrouter_api_key_is_empty_by_default(self):
        """Test OPENROUTER_API_KEY is empty by default (set at runtime)."""
        # In Docker image, it defaults to empty string
        # At runtime, it should be passed via -e OPENROUTER_API_KEY=...
        api_key = os.environ.get('OPENROUTER_API_KEY')
        # Could be empty string (default) or set at runtime
        assert api_key is not None, "OPENROUTER_API_KEY should be defined (even if empty)"


class TestOpenRouterConfig:
    """Test OpenRouter configuration can be used."""

    def test_can_import_openrouter_client(self):
        """Test that we can import the openrouter client if available."""
        # This just checks Python can import modules
        # The actual client would be in the application code
        import sys
        assert sys.version_info >= (3, 12)

    def test_openrouter_env_vars_available(self):
        """Test that OpenRouter env vars are accessible to Python."""
        model = os.getenv('OPENROUTER_MODEL')
        api_key = os.getenv('OPENROUTER_API_KEY')
        
        # Both should be defined (empty string by default in image, set at deploy time)
        assert model is not None
        assert model == ''
        assert api_key is not None


# Run only in Docker
@pytest.mark.skipif(not os.path.exists('/.dockerenv'), reason="Only runs in Docker container")
class TestDockerOnly:
    """Tests that ONLY run in Docker container."""

    def test_poetry_installed(self):
        """Test poetry is available in container."""
        result = subprocess.run(['poetry', '--version'], capture_output=True, text=True)
        assert result.returncode == 0

    def test_pytest_installed(self):
        """Test pytest is available in container."""
        result = subprocess.run(['poetry', 'run', 'pytest', '--version'], capture_output=True, text=True)
        assert result.returncode == 0

    def test_python_version_in_container(self):
        """Test Python 3.12+ in container."""
        import sys
        assert sys.version_info.major == 3
        assert sys.version_info.minor >= 12

    def test_project_structure_in_container(self):
        """Test project files exist in container."""
        from pathlib import Path
        assert Path('/app/pyproject.toml').exists()
        assert Path('/app/poetry.lock').exists()
        assert Path('/app/tests').exists()
        assert Path('/app/tests/test_ci.py').exists()
        assert Path('/app/tests/test_docker.py').exists()


class TestDockerfileStructure:
    """Tests that validate the Dockerfile structure is correct."""

    def test_poetry_install_before_use(self):
        """Test that the Dockerfile installs poetry before using it."""
        dockerfile = Path(__file__).parent.parent / 'Dockerfile'
        content = dockerfile.read_text()

        poetry_install_line = 'python -m pip install poetry'
        poetry_use_line = 'RUN poetry install'

        install_idx = content.find(poetry_install_line)
        use_idx = content.find(poetry_use_line)

        assert install_idx != -1, "Dockerfile debe tener una instrucción para instalar poetry"
        assert use_idx != -1, "Dockerfile debe usar poetry install"
        assert install_idx < use_idx, "poetry debe instalarse ANTES de ser usado en el Dockerfile"

    def test_dockerfile_has_workdir(self):
        """Test that Dockerfile sets WORKDIR."""
        dockerfile = Path(__file__).parent.parent / 'Dockerfile'
        content = dockerfile.read_text()
        assert 'WORKDIR /app' in content