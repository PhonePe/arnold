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
import { analysisEntry, analysisHref, pastRunsFor, reviewHref, reviewHrefFromChangeRequestId, runPersonaIds } from './analysis-navigation.mjs';

test('builds canonical review links', () => {
  assert.equal(reviewHref({ projectId: '42', iid: 7 }), '/review/GITLAB/42/7');
  assert.equal(reviewHref({ provider: 'gitlab', projectId: '42', iid: 7, runId: 'run-1' }), '/review/GITLAB/42/7/runs/run-1');
  assert.equal(reviewHrefFromChangeRequestId('gitlab:42:7'), '/review/GITLAB/42/7');
  assert.equal(reviewHrefFromChangeRequestId('gitlab:42:7', 'run-1'), '/review/GITLAB/42/7/runs/run-1');
});

test('builds prefilled analysis links', () => {
  assert.equal(analysisHref('gitlab:12:34'), '/analyze?mergeRequestId=gitlab%3A12%3A34');
  assert.equal(
    analysisHref('gitlab:12:34', ['security', 'reliability']),
    '/analyze?mergeRequestId=gitlab%3A12%3A34&personas=security%2Creliability',
  );
});

test('selects the action for each review state', () => {
  assert.deepEqual(analysisEntry('CREATED', 'gitlab:12:34'), {
    href: '/analyze?mergeRequestId=gitlab%3A12%3A34',
    label: 'Run analysis',
  });
  assert.deepEqual(analysisEntry('ANALYSING', 'gitlab:12:34'), {
    href: '/history?changeRequestId=gitlab%3A12%3A34',
    label: 'View analysis',
  });
  assert.equal(analysisEntry('ANALYSED', 'gitlab:12:34').label, 'Run again');
  assert.equal(analysisEntry('MERGED', 'gitlab:12:34'), null);
});

test('retry link preserves the harness and selected model', () => {
  const href = analysisHref('gitlab:12:34', ['security'], { harness: 'copilot', model: 'available-model' });
  const params = new URL(href, 'https://arnold.example').searchParams;
  assert.equal(params.get('harness'), 'copilot');
  assert.equal(params.get('model'), 'available-model');
});

test('extracts reusable persona ids without the improve baseline', () => {
  assert.deepEqual(runPersonaIds({ personaIds: ['improve'] }), []);
  assert.deepEqual(runPersonaIds({ personaIds: ['improve', 'security', 'a', 'b', 'c'] }), ['security', 'a', 'b']);
  assert.deepEqual(runPersonaIds({ reviewPersonas: [{ id: 'reliability', name: 'R', version: 1 }] }), ['reliability']);
});

test('finds earlier runs for the selected target', () => {
  const singleRuns = [
    { runId: 'old', changeRequestId: 'gitlab:42:7', createdAt: 1 },
    { runId: 'new', changeRequestId: 'gitlab:42:7', createdAt: 3 },
    { runId: 'other', changeRequestId: 'gitlab:42:8', createdAt: 2 },
  ];
  const groupRuns = [{ runId: 'g1', groupId: 'grp-1', createdAt: 5 }];
  assert.deepEqual(pastRunsFor('single', { id: 'gitlab:42:7' }, singleRuns, groupRuns).map((run) => run.runId), ['new', 'old']);
  assert.deepEqual(pastRunsFor('group', { id: 'grp-1' }, singleRuns, groupRuns).map((run) => run.runId), ['g1']);
  assert.deepEqual(pastRunsFor('single', null, singleRuns, groupRuns), []);
});
