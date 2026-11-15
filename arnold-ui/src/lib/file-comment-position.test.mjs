/*
 * Copyright (c) 2026 Original Author(s), PhonePe Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import assert from 'node:assert/strict';
import test from 'node:test';
import { fileCommentPosition } from './file-comment-position.mjs';

test('aligns with the diff pane, flips above a bottom icon, and stays within the viewport', () => {
  const popup = { width: 400, height: 250 };
  const viewport = { width: 1200, height: 800 };
  assert.deepEqual(fileCommentPosition({ top: 100, bottom: 128, right: 1000 }, popup, viewport, { left: 600 }), { left: 600, top: 136 });
  assert.deepEqual(fileCommentPosition({ top: 700, bottom: 728, right: 1000 }, popup, viewport, { left: 600 }), { left: 600, top: 442 });
  assert.deepEqual(fileCommentPosition({ top: 100, bottom: 128, right: 40 }, popup, viewport, { left: -10 }), { left: 8, top: 136 });
  assert.deepEqual(fileCommentPosition({ top: 700, bottom: 728, right: 1300 }, popup, viewport, { left: 1000 }), { left: 792, top: 442 });
  assert.deepEqual(fileCommentPosition({ top: -500, bottom: -472, right: 1000 }, popup, viewport, { left: 600 }), { left: 600, top: 8 });
});
