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
