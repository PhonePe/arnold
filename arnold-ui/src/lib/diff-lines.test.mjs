import test from 'node:test';
import assert from 'node:assert/strict';
import { positionForLine, positionForRange } from './diff-lines.mjs';

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
