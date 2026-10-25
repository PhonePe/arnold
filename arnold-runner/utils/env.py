"""Environment helpers."""
import os


def get_env_optional(name: str, default: str | None = None) -> str | None:
    """Return the environment variable, or ``default`` when it is unset or blank."""
    value = os.environ.get(name)
    return value if value is not None and value.strip() else default
