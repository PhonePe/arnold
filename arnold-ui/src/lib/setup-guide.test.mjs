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

import assert from 'node:assert/strict';
import test from 'node:test';
import {
  changeReference, computeSetupGuide, forgeSummary, repositorySyncSummary, statusBarItems,
} from './setup-guide.mjs';

const gitHub = (overrides = {}) => ({ provider: 'GITHUB', configured: true, connected: false, username: null, installUrl: 'https://github.com/apps/arnold/installations/new', ...overrides });
const gitLab = (overrides = {}) => ({ provider: 'GITLAB', configured: false, connected: false, username: null, installUrl: null, ...overrides });
const setupWith = (forges, copilot = { enabled: false }) => ({ forges, copilot, runnerMode: 'LOCAL' });
const opencode = { id: 'opencode', available: true, message: null };
const step = (guide, id) => guide.steps.find((item) => item.id === id);

test('GitHub-only connected: non-blocking, install step recommended', () => {
  const guide = computeSetupGuide({ setup: setupWith([gitHub({ connected: true, username: 'octocat' }), gitLab()]), harnesses: [opencode] });
  assert.equal(guide.blocking, false);
  assert.equal(step(guide, 'source-control').done, true);
  assert.deepEqual(step(guide, 'source-control').forges.map((forge) => forge.provider), ['GITHUB']);
  assert.equal(step(guide, 'install').required, false);
  assert.equal(guide.pending, true);
  assert.deepEqual(forgeSummary(setupWith([gitHub({ connected: true, username: 'octocat' })])), { providers: ['GITHUB'], label: 'GitHub · octocat' });
});

test('GitHub-only with installed repositories has nothing pending', () => {
  const guide = computeSetupGuide({
    setup: setupWith([gitHub({ connected: true }), gitLab()]),
    harnesses: [opencode],
    repositorySync: { enabled: true, providers: [{ provider: 'GITHUB', available: true, repositories: 3 }] },
  });
  assert.equal(guide.pending, false);
});

test('GitHub-only not connected blocks', () => {
  const guide = computeSetupGuide({ setup: setupWith([gitHub(), gitLab()]), harnesses: [opencode] });
  assert.equal(guide.blocking, true);
  assert.equal(step(guide, 'source-control').required, true);
  assert.equal(step(guide, 'install'), undefined);
  assert.equal(forgeSummary(setupWith([gitHub()])), null);
});

test('nothing configured shows an admin message without blocking', () => {
  const guide = computeSetupGuide({ setup: setupWith([gitHub({ configured: false }), gitLab()]), harnesses: [opencode] });
  const sourceControl = step(guide, 'source-control');
  assert.equal(guide.blocking, false);
  assert.equal(sourceControl.admin, true);
  assert.equal(sourceControl.forges.length, 0);
  assert.equal(guide.pending, true);
  assert.deepEqual(statusBarItems({ setup: setupWith([gitHub({ configured: false })]) }), []);
});

test('copilot is required only without another available harness', () => {
  const setup = setupWith([gitHub({ connected: true })], { enabled: true, connected: false });
  const required = computeSetupGuide({ setup, harnesses: [{ id: 'copilot', available: false }, { ...opencode, available: false, message: 'off' }] });
  assert.equal(step(required, 'copilot').required, true);
  assert.equal(step(required, 'engine'), undefined);
  const optional = computeSetupGuide({ setup, harnesses: [{ id: 'copilot', available: false }, opencode] });
  assert.equal(step(optional, 'copilot').required, false);
  assert.equal(optional.blocking, false);
});

test('no available harness surfaces admin messages', () => {
  const guide = computeSetupGuide({
    setup: setupWith([gitHub({ connected: true })]),
    harnesses: [{ ...opencode, available: false, message: 'OpenCode execution is not enabled by your administrator.' }],
  });
  assert.deepEqual(step(guide, 'engine').messages, ['OpenCode execution is not enabled by your administrator.']);
});

test('GitLab reconnect blocks only when GitLab is configured', () => {
  const setup = setupWith([gitHub({ configured: false }), gitLab({ configured: true, connected: true })]);
  const guide = computeSetupGuide({ setup, harnesses: [opencode], gitLabReauthRequired: true });
  assert.equal(guide.reconnect, true);
  assert.equal(guide.blocking, true);
  assert.equal(step(guide, 'source-control').forges[0].reconnect, true);
  const unconfigured = computeSetupGuide({ setup: setupWith([gitHub({ connected: true }), gitLab()]), harnesses: [opencode], gitLabReauthRequired: true });
  assert.equal(unconfigured.reconnect, false);
  assert.equal(unconfigured.blocking, false);
});

test('status bar and top bar labels follow repository sync', () => {
  const now = 1_000_000_000;
  const setup = setupWith([gitHub({ connected: true }), gitLab()]);
  const sync = { enabled: true, running: false, providers: [{ provider: 'GITHUB', available: true, status: 'SUCCEEDED', lastSucceededAt: now - 120_000, repositories: 12, changeRequests: 40 }] };
  assert.deepEqual(statusBarItems({ setup, repositorySync: sync, now }), [{ status: 'success', label: 'GitHub · 12 repos · 40 PRs · synced 2m ago' }]);
  assert.deepEqual(statusBarItems({ setup, starting: true }), [{ status: 'warning', label: 'Arnold starting' }]);
  const failed = { ...sync, providers: [{ ...sync.providers[0], status: 'FAILED', failure: 'boom' }] };
  assert.equal(statusBarItems({ setup, repositorySync: failed, now })[0].label, 'GitHub sync failed');
  assert.equal(repositorySyncSummary(failed, now).tone, 'bad');
  assert.equal(repositorySyncSummary(sync, now).label, 'Synced 2m ago');
  assert.equal(changeReference({ provider: 'GITHUB', changeNumber: 7 }), '#7');
  assert.equal(changeReference({ provider: 'GITLAB', changeNumber: 7 }), '!7');
});
