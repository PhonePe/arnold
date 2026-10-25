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

"""Centralized logger factory for Clio.

Usage::

    from utils.logger import get_logger
    logger = get_logger(__name__)
"""

import logging

_FORMATTER = logging.Formatter("%(asctime)s - %(levelname)s - %(message)s")


def get_logger(name: str, level: int = logging.INFO) -> logging.Logger:
    """Return a named logger with a StreamHandler, guarded against duplicate handlers.

    Sets *propagate* to ``False`` so log records are not forwarded to the root
    logger, which prevents double-printing when the root logger also has handlers.
    """
    logger = logging.getLogger(name)
    if not logger.handlers:
        logger.setLevel(level)
        logger.propagate = False
        _ch = logging.StreamHandler()
        _ch.setFormatter(_FORMATTER)
        logger.addHandler(_ch)
    else:
        # Ensure requested level is respected on subsequent calls
        logger.setLevel(level)
    return logger
