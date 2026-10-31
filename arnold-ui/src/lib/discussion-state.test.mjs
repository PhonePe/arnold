import assert from 'node:assert/strict';
import test from 'node:test';
import { replaceDraftDiscussions } from './discussion-state.mjs';

test('replaces drafts without reloading published discussions', () => {
  assert.deepEqual(replaceDraftDiscussions([
    { id: '1', state: 'published' }, { id: 'draft:1', state: 'draft' },
  ], [{ id: 'draft:2', state: 'draft' }]), [
    { id: '1', state: 'published' }, { id: 'draft:2', state: 'draft' },
  ]);
});
