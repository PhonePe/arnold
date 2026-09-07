import assert from 'node:assert/strict';
import test from 'node:test';
import { applyFocusAreas, createSmartDiffFixture, reconcileSmartGroups, trimStoryFileContext } from './smart-diff.mjs';

const hunk = (oldStart, newStart, deletions = 3, additions = 4) => ({ deletionStart: oldStart, deletionCount: deletions, additionStart: newStart, additionCount: additions, additionLines: additions, deletionLines: deletions, deletionLineIndex: 0, additionLineIndex: 0, splitLineStart: 0, splitLineCount: 4, unifiedLineStart: 0, unifiedLineCount: 7, hunkContent: [] });
const item = (name, hunks) => ({ id: name, fileDiff: { name, hunks, additionLines: ['a', 'b', 'c', 'd'], deletionLines: ['x', 'y', 'z'] }, annotations: [], threads: [], stats: { additions: 4, deletions: 3 } });

test('anchors focus areas to their exact changed line range', () => {
  const items = [item('src/A.java', [hunk(10, 12)])];
  const smartDiff = {
    headSha: 'abc',
    status: 'completed',
    groups: [{ id: 'review-focus', title: 'Review focus areas', steps: [{ id: 'focus-1', title: 'Validate parsing', explanation: 'Check null handling.', changes: [{ filePath: 'src/A.java', oldStartLine: 11, oldEndLine: 12, newStartLine: 13, newEndLine: 15 }] }] }],
  };

  const result = applyFocusAreas(items, smartDiff, 'abc');

  assert.deepEqual(result[0].annotations[0], {
    side: 'additions',
    lineNumber: 13,
    metadata: { kind: 'focus', stepId: 'focus-1', title: 'Validate parsing', explanation: 'Check null handling.', groupTitle: 'Review focus areas', startLine: 13, endLine: 15 },
  });
});

test('uses the old side for deletion-only focus areas and skips stale lines', () => {
  const items = [item('src/A.java', [hunk(10, 12)])];
  const smartDiff = {
    headSha: 'abc',
    status: 'completed',
    groups: [{ id: 'review-focus', title: 'Review focus areas', steps: [{ id: 'focus-1', title: 'Removed guard', explanation: 'Verify callers.', changes: [
      { filePath: 'src/A.java', oldStartLine: 11, oldEndLine: 12, newStartLine: null, newEndLine: null },
      { filePath: 'src/A.java', oldStartLine: 99, oldEndLine: 100, newStartLine: null, newEndLine: null },
    ] }] }],
  };

  const result = applyFocusAreas(items, smartDiff, 'abc');

  assert.equal(result[0].annotations.length, 1);
  assert.equal(result[0].annotations[0].side, 'deletions');
  assert.equal(result[0].annotations[0].lineNumber, 11);
});

test('creates fixture ranges from displayed hunks', () => {
  const fixture = createSmartDiffFixture([item('src/A.java', [hunk(10, 12)])], 'abc');

  assert.deepEqual(fixture.groups[0].steps[0].changes[0], {
    filePath: 'src/A.java', oldStartLine: 10, oldEndLine: 12, newStartLine: 12, newEndLine: 15,
  });
});

test('keeps story chapters while carrying focus annotations into their hunks', () => {
  const items = [item('src/A.java', [hunk(10, 12)])];
  const change = { filePath: 'src/A.java', oldStartLine: 10, oldEndLine: 12, newStartLine: 12, newEndLine: 15 };
  const smartDiff = {
    headSha: 'abc', status: 'completed', groups: [
      { id: 'chapter-1', title: 'Core workflow', rationale: 'Follow the flow.', steps: [{ id: 'step-1', title: 'Review API', explanation: 'Understand the contract.', changes: [change] }] },
      { id: 'review-focus', title: 'Review focus areas', steps: [{ id: 'focus-1', title: 'Null handling', explanation: 'Check nullable values.', changes: [{ ...change, newStartLine: 13, newEndLine: 14 }] }] },
    ],
  };

  const result = reconcileSmartGroups(applyFocusAreas(items, smartDiff, 'abc'), smartDiff, 'abc');

  assert.deepEqual(result.groups.map((group) => group.title), ['Core workflow']);
  assert.deepEqual(result.items[0].annotations.map((annotation) => annotation.metadata.kind), ['narration', 'focus']);
});

test('gives each story hunk a distinct renderer cache key', () => {
  const items = [item('src/A.java', [hunk(10, 12), hunk(40, 42)])];
  const changes = items[0].fileDiff.hunks.map((entry) => ({
    filePath: 'src/A.java',
    oldStartLine: entry.deletionStart,
    oldEndLine: entry.deletionStart + entry.deletionCount - 1,
    newStartLine: entry.additionStart,
    newEndLine: entry.additionStart + entry.additionCount - 1,
  }));
  const smartDiff = {
    headSha: 'abc', status: 'completed', groups: [
      { id: 'chapter-1', title: 'Core workflow', steps: changes.map((change, index) => ({ id: `step-${index}`, title: 'Review API', explanation: 'Trace the change.', changes: [change] })) },
    ],
  };

  const result = reconcileSmartGroups(items, smartDiff, 'abc');

  assert.equal(new Set(result.items.map((entry) => entry.fileDiff.cacheKey)).size, 2);
});

test('keeps story hunk expansion within matching old and new context', () => {
  const fileDiff = { hunks: [hunk(3, 3, 1, 2)] };
  const files = {
    oldFile: { contents: 'o1\no2\no3\no4\no5\no6\no7\n' },
    newFile: { contents: 'n1\nn2\nn3\nn4\nn5\nn6\nn7\nn8\nn9\nn10\n' },
  };

  const result = trimStoryFileContext(fileDiff, files);

  assert.equal(result.oldFile.contents, files.oldFile.contents);
  assert.equal(result.newFile.contents, 'n1\nn2\nn3\nn4\nn5\nn6\nn7\nn8\n');
});
