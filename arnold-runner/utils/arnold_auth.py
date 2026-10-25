"""Credentials used by the runner when calling back into Arnold."""
import os


class ArnoldAuth:
    """Builds the Authorization header for Arnold callbacks from ``ARNOLD_RUNNER_API_KEY``."""

    @staticmethod
    def get_auth_token() -> str | None:
        key = os.environ.get("ARNOLD_RUNNER_API_KEY", "").strip()
        return f"Bearer {key}" if key else None
