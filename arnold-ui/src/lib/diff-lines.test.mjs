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
import { annotationLocation, positionForLine, positionForRange, positionForSelection } from './diff-lines.mjs';

const fileDiff = {
  name: 'src/example.js',
  prevName: 'src/example.js',
  hunks: [{
    deletionStart: 10,
    additionStart: 10,
    hunkContent: [
      { type: 'context', lines: 2 },
      { type: 'change', deletions: 1, additions: 2 },
      { type: 'context', lines: 2 },
    ],
  }],
};

test('maps context, deleted, and added lines to GitLab positions', () => {
  assert.deepEqual(positionForLine(fileDiff, 'additions', 10), {
    oldPath: 'src/example.js', newPath: 'src/example.js', oldLine: 10, newLine: 10,
  });
  assert.deepEqual(positionForLine(fileDiff, 'deletions', 12), {
    oldPath: 'src/example.js', newPath: 'src/example.js', oldLine: 12, newLine: null,
  });
  assert.deepEqual(positionForLine(fileDiff, 'additions', 13), {
    oldPath: 'src/example.js', newPath: 'src/example.js', oldLine: null, newLine: 13,
  });
  assert.deepEqual(positionForLine(fileDiff, 'additions', 14), {
    oldPath: 'src/example.js', newPath: 'src/example.js', oldLine: 13, newLine: 14,
  });
});

test('maps a selected range to a GitLab multiline position', () => {
  assert.deepEqual(positionForRange(fileDiff, 'additions', 13, 14), {
    oldPath: 'src/example.js', newPath: 'src/example.js', oldLine: 13, newLine: 14,
    lineRange: {
      side: 'new', startOldLine: null, startNewLine: 13, endOldLine: 13, endNewLine: 14,
    },
  });
});

test('preserves gutter ranges in both directions and rejects cross-side selections', () => {
  for (const side of ['additions', 'deletions']) {
    assert.deepEqual(positionForSelection(fileDiff, { side, start: 14, end: 12 }), positionForRange(fileDiff, side, 12, 14));
    assert.deepEqual(positionForSelection(fileDiff, { side, start: 12, end: 12 }), positionForLine(fileDiff, side, 12));
  }
  assert.equal(positionForSelection(fileDiff, { side: 'deletions', endSide: 'additions', start: 12, end: 14 }), null);
  assert.equal(positionForSelection(fileDiff, { start: 0, end: 14 }), null);
  assert.deepEqual(annotationLocation(positionForSelection(fileDiff, { side: 'deletions', start: 12, end: 14 })), { side: 'deletions', lineNumber: 14 });
});
