"""Abstract base class for agentic harnesses."""

from __future__ import annotations

from abc import ABC, abstractmethod


class AgenticHarness(ABC):
    """Interface every agentic harness must implement."""

    @abstractmethod
    def run_session(
        self,
        prompt: str,
        tools: list,
        system_message: str,
        model: str,
        working_directory: str | None = None,
        custom_agents: list[dict] | None = None,
        enable_graph_tools: bool = True,
    ) -> str:
        """Execute an agentic session and return the final assistant message.

        Parameters
        ----------
        prompt:
            The user-role message content.
        tools:
            A list of tool objects (Copilot SDK tools or ``_ToolWrapper``).
            Each exposes ``.metadata`` (:class:`ToolMetadata`).
        system_message:
            System-role instructions.
        model:
            Model identifier for the LLM.
        working_directory:
            Filesystem root for agent file operations.
        custom_agents:
            Optional list of sub-agent config dicts (``name``, ``prompt``, …).
        enable_graph_tools:
            Whether graph MCP tools may be registered for this session.
        """
        ...
