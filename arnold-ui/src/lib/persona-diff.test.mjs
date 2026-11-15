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

import test from 'node:test';
import assert from 'node:assert/strict';
import { parsePatchFiles } from '@pierre/diffs';
import { focusFileDiff } from './persona-diff.mjs';

const parse = (patch) => parsePatchFiles(patch, undefined, true)[0].files[0];
const thread = (position) => ({ id: 't', position });
const lineNumbers = (fileDiff, side) => fileDiff.hunks.map((hunk) => side === 'old' ? [hunk.deletionStart, hunk.deletionCount] : [hunk.additionStart, hunk.additionCount]);

const newFile = parse(`diff --git a/a.py b/a.py
new file mode 100644
--- /dev/null
+++ b/a.py
@@ -0,0 +1,30 @@
${Array.from({ length: 30 }, (_, index) => `+line ${index + 1}`).join('\n')}
`);

test('keeps only a window around the commented line of a new file', () => {
  const focused = focusFileDiff(newFile, [thread({ newLine: 15 })], 2);
  assert.deepEqual(lineNumbers(focused, 'new'), [[13, 5]]);
  assert.deepEqual(focused.additionLines, ['line 13\n', 'line 14\n', 'line 15\n', 'line 16\n', 'line 17\n']);
  assert.equal(focused.type, 'new');
  assert.equal(focused.name, 'a.py');
});

test('merges overlapping windows and keeps separate ones apart', () => {
  const focused = focusFileDiff(newFile, [thread({ newLine: 5 }), thread({ newLine: 7 }), thread({ newLine: 25 })], 1);
  assert.deepEqual(lineNumbers(focused, 'new'), [[4, 5], [24, 3]]);
});

test('covers multi-line ranges and deleted-side comments with real line numbers', () => {
  const changed = parse(`diff --git a/b.py b/b.py
--- a/b.py
+++ b/b.py
@@ -10,8 +10,8 @@
 c10
 c11
-old12
+new12
 c13
 c14
 c15
 c16
-old17
+new17
`);
  const deleted = focusFileDiff(changed, [thread({ oldLine: 17, newLine: null })], 0);
  assert.deepEqual(lineNumbers(deleted, 'old'), [[17, 1]]);
  assert.deepEqual(deleted.deletionLines, ['old17\n']);
  const ranged = focusFileDiff(changed, [thread({ newLine: 14, lineRange: { side: 'new', startNewLine: 12, endNewLine: 14 } })], 0);
  assert.deepEqual(lineNumbers(ranged, 'new'), [[12, 3]]);
});

test('returns the original diff for file-level threads', () => {
  assert.equal(focusFileDiff(newFile, [thread({ newLine: null, oldLine: null })]), newFile);
  assert.equal(focusFileDiff(newFile, [thread({ newLine: 99 })]), newFile);
});
