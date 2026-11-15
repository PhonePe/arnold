# Copyright (c) 2026 Original Author(s), PhonePe Limited
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

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
