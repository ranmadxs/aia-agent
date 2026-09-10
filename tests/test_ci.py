"""Minimal tests for aia-agent CI - no external dependencies."""

import pytest
import sys
import os

# Use tomllib (stdlib in Python 3.11+) or fallback to tomli
if sys.version_info >= (3, 11):
    import tomllib as tomli
else:
    import tomli


class TestBasicPython:
    """Basic Python functionality tests."""

    def test_python_version(self):
        """Test Python version is 3.12+."""
        assert sys.version_info.major == 3
        assert sys.version_info.minor >= 12

    def test_poetry_available(self):
        """Test poetry is available."""
        import subprocess
        result = subprocess.run(['poetry', '--version'], capture_output=True, text=True)
        assert result.returncode == 0
        assert 'Poetry' in result.stdout

    def test_pytest_available(self):
        """Test pytest is available."""
        import subprocess
        result = subprocess.run(['poetry', 'run', 'pytest', '--version'], capture_output=True, text=True)
        assert result.returncode == 0


class TestProjectConfig:
    """Test project configuration."""

    def test_pyproject_exists(self):
        """Test pyproject.toml exists and is valid."""
        from pathlib import Path
        
        pyproject = Path(__file__).parent.parent / 'pyproject.toml'
        assert pyproject.exists()
        
        with open(pyproject, 'rb') as f:
            data = tomli.load(f)
        
        assert data['tool']['poetry']['name'] == 'aia-agent'
        assert data['tool']['poetry']['version'] == '0.8.0'
        assert data['tool']['poetry']['description'] == 'Agentes inteligentes para automatización'
        assert data['tool']['poetry']['authors'] == ['Edgar']
        assert data['tool']['poetry']['packages'] == []

    def test_dev_dependencies(self):
        """Test dev dependencies are declared."""
        from pathlib import Path
        
        pyproject = Path(__file__).parent.parent / 'pyproject.toml'
        with open(pyproject, 'rb') as f:
            data = tomli.load(f)
        
        dev_deps = data['tool']['poetry']['group']['dev']['dependencies']
        assert 'pytest' in dev_deps
        assert 'pytest-cov' in dev_deps
        assert 'pytest-mock' in dev_deps


class TestEnvironment:
    """Test environment setup (only in Docker)."""

    def test_working_directory(self):
        """Test working directory is /app in Docker."""
        # Only check in Docker environment (not GitHub Actions)
        if os.path.exists('/.dockerenv'):
            assert os.getcwd() == '/app'
        else:
            pytest.skip("Not in Docker environment")

    def test_pythonpath(self):
        """Test PYTHONPATH is set in Docker."""
        if os.path.exists('/.dockerenv'):
            assert os.environ.get('PYTHONPATH') == '/app'
        else:
            pytest.skip("Not in Docker environment")


# Parametrized test example
@pytest.mark.parametrize("input_val,expected", [
    (1, 1),
    (2, 4),
    (3, 9),
    (10, 100),
])
def test_square(input_val, expected):
    """Test square function."""
    assert input_val ** 2 == expected