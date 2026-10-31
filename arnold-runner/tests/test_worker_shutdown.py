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

import os
import signal
import threading
import time
from unittest.mock import patch

from runner import worker as runner_worker


class BlockingClient:
    def claim(self, worker_id):
        time.sleep(60)


def test_sigterm_stops_idle_worker_promptly():
    threading.Timer(0.5, os.kill, args=(os.getpid(), signal.SIGTERM)).start()
    previous = signal.getsignal(signal.SIGTERM)
    started = time.monotonic()
    try:
        with patch.object(runner_worker, "ArnoldRunnerClient", BlockingClient):
            runner_worker.worker(concurrency=2, name="test")
    finally:
        signal.signal(signal.SIGTERM, previous)
    assert time.monotonic() - started < 5
