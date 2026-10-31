# Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
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

"""
Harness-agnostic tool adapter for Clio.

Provides a ``define_tool`` decorator that mirrors the Copilot SDK's API but
stores enough metadata for *any* harness (Copilot SDK, OpenCode/MCP, etc.)
to register and invoke the tool.

Usage in flow code::

    from utils.tool_adapter import define_tool

    @define_tool(description="Write the results …")
    def my_tool(params: MyPydanticModel) -> str:
        ...

The returned object behaves as a Copilot SDK tool when that harness is
active, and carries ``.metadata`` for other harnesses to introspect.
"""

from __future__ import annotations

import inspect
import logging
import os
from dataclasses import dataclass, field
from typing import Any, Callable, Type, get_type_hints

from pydantic import BaseModel

logger = logging.getLogger("tool_adapter")

# ---------------------------------------------------------------------------
# Public metadata container
# ---------------------------------------------------------------------------


@dataclass
class ToolMetadata:
    """Harness-neutral description of a tool."""

    name: str
    description: str
    params_model: Type[BaseModel]
    handler: Callable[..., str]
    # A successful call ends the agent turn instead of feeding the result back for another round.
    ends_session: bool = False
    # Populated only when the Copilot SDK is the active harness:
    copilot_tool: Any | None = field(default=None, repr=False)


# ---------------------------------------------------------------------------
from utils.config import get_harness_label

# ---------------------------------------------------------------------------
# Harness detection
# ---------------------------------------------------------------------------

_HARNESS = get_harness_label()


def active_harness() -> str:
    """Return the currently selected harness name."""
    return _HARNESS


# ---------------------------------------------------------------------------
# define_tool decorator
# ---------------------------------------------------------------------------


def define_tool(description: str, ends_session: bool = False):
    """Decorator factory matching the Copilot SDK's ``@define_tool(description=…)`` API.

    * When the active harness is ``copilot``, it delegates to the real
      ``copilot.define_tool`` so the returned object is a native SDK tool.
    * For *all* harnesses the returned object carries a ``.metadata``
      attribute (:class:`ToolMetadata`) that other harnesses can inspect.
    * ``ends_session=True`` marks a final-output tool: on Copilot a successful
      call ends the turn, saving a full-context round trip just to say "done".
      A failed (raising) call still lets the model read the error and retry.
    """

    def decorator(fn: Callable) -> Any:
        # Derive Pydantic model from the function's first parameter
        hints = get_type_hints(fn)
        params_model: Type[BaseModel] | None = None
        for param_name, param_type in hints.items():
            if param_name == "return":
                continue
            if isinstance(param_type, type) and issubclass(param_type, BaseModel):
                params_model = param_type
                break

        tool_name = fn.__name__

        metadata = ToolMetadata(
            name=tool_name,
            description=description,
            params_model=params_model,
            handler=fn,
            ends_session=ends_session,
        )

        if _HARNESS == "copilot":
            # Delegate to the real Copilot SDK decorator
            from copilot import define_tool as _copilot_define_tool

            copilot_tool = _copilot_define_tool(description=description, is_terminal=ends_session)(fn)
            metadata.copilot_tool = copilot_tool
            # SDK 1.x serializes Tool.metadata into JSON-RPC, so keep Clio's
            # dataclass in a private attribute instead of that wire field.
            copilot_tool._clio_metadata = metadata
            return copilot_tool
        else:
            # For non-Copilot harnesses, wrap the function in a lightweight
            # object that carries metadata and remains callable.
            wrapper = _ToolWrapper(fn, metadata)
            return wrapper

    return decorator


class _ToolWrapper:
    """Lightweight wrapper returned for non-Copilot harnesses.

    Acts as a callable that delegates to the underlying handler while
    exposing ``.metadata`` for harness-specific registration.
    """

    def __init__(self, fn: Callable, metadata: ToolMetadata):
        self._fn = fn
        self.metadata = metadata
        self.name = metadata.name
        self.__name__ = metadata.name
        self.__doc__ = metadata.description

    def __call__(self, *args, **kwargs):
        return self._fn(*args, **kwargs)

    def __repr__(self):
        return f"<ToolWrapper {self.name!r}>"
