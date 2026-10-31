/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
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
import { analyzeUrlHref, availableProviders, changeNumberLabel, normalizeScope, providerName, repositorySyncState, syncAge, syncStatusLine } from './repository-sync.mjs';

const now = 1_000_000_000;

test('labels change numbers per provider', () => {
  assert.equal(changeNumberLabel({ provider: 'GITHUB', changeNumber: 12 }), '#12');
  assert.equal(changeNumberLabel({ provider: 'GITLAB', changeNumber: 7 }), '!7');
  assert.equal(providerName('github'), 'GitHub');
});

test('normalizes scope', () => {
  assert.equal(normalizeScope('all'), 'all');
  assert.equal(normalizeScope('bogus'), 'mine');
  assert.equal(normalizeScope(null), 'mine');
});

test('lists available providers', () => {
  assert.deepEqual(availableProviders({ providers: [{ provider: 'GITHUB', available: true }, { provider: 'GITLAB', available: false }] }), ['GITHUB']);
  assert.deepEqual(availableProviders(null), []);
});

test('formats sync status line', () => {
  assert.equal(syncAge(now - 120_000, now), '2 min ago');
  assert.equal(syncStatusLine({ provider: 'GITHUB', status: 'IDLE', repositories: 12, changeRequests: 40, lastSucceededAt: now - 120_000 }, now), 'GitHub · 12 repos · 40 open PRs · synced 2 min ago');
  assert.equal(syncStatusLine({ provider: 'GITLAB', status: 'RUNNING' }, now), 'GitLab · Sync running…');
  assert.equal(syncStatusLine({ provider: 'GITLAB', status: 'FAILED', failure: 'boom' }, now), 'GitLab · Sync failed: boom');
  assert.equal(syncStatusLine({ provider: 'GITLAB', status: 'NEVER_SYNCED' }, now), 'GitLab · Not synced yet');
});

test('derives overall sync state', () => {
  assert.equal(repositorySyncState(null), 'loading');
  assert.equal(repositorySyncState({ enabled: true, running: false, providers: [] }), 'unconfigured');
  assert.equal(repositorySyncState({ enabled: true, running: true, providers: [] }), 'running');
  assert.equal(repositorySyncState({ enabled: true, providers: [{ available: true, status: 'IDLE', lastSucceededAt: 1 }] }), 'ready');
});

test('builds analyze href', () => {
  assert.equal(analyzeUrlHref({ id: 'GITHUB:1:2', webUrl: 'https://github.com/o/r/pull/2' }), '/analyze?url=https%3A%2F%2Fgithub.com%2Fo%2Fr%2Fpull%2F2');
  assert.equal(analyzeUrlHref({ id: 'GITLAB:1:2' }), '/analyze?mergeRequestId=GITLAB%3A1%3A2');
});
