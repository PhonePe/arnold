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
import { classifyAuthFailure, reconnectRequiredProviders } from './scm-auth.mjs';

const reconnectBody = (provider) => ({
  success: false, errorCode: 2000, errorMessage: 'Error [2000]: Authorization Error',
  details: { reason: 'SCM_RECONNECT_REQUIRED', provider, reconnect: true },
});

test('reconnect signal is recognised for either provider regardless of the request flag', () => {
  assert.deepEqual(classifyAuthFailure({ status: 401, body: reconnectBody('GITHUB') }), { kind: 'reconnect', provider: 'GITHUB' });
  assert.deepEqual(classifyAuthFailure({ status: 401, body: reconnectBody('gitlab') }), { kind: 'reconnect', provider: 'GITLAB' });
  assert.equal(classifyAuthFailure({ status: 403, body: reconnectBody('GITHUB') }), null);
  assert.equal(classifyAuthFailure({ status: 401, body: reconnectBody('BITBUCKET') }), null);
});

test('session expiry wins and legacy GitLab 401s still reconnect GitLab only when flagged', () => {
  assert.deepEqual(classifyAuthFailure({ status: 401, body: { errorCode: 'SESSION_EXPIRED' } }), { kind: 'session' });
  assert.deepEqual(classifyAuthFailure({ status: 401, body: 'Unauthorized' }), { kind: 'session' });
  assert.deepEqual(classifyAuthFailure({ status: 401, body: { errorCode: 2000 }, gitLabAuthorization: true }), { kind: 'reconnect', provider: 'GITLAB' });
  assert.equal(classifyAuthFailure({ status: 401, body: { errorCode: 2000 } }), null);
});

test('setup forges flagged reconnect_required are listed', () => {
  const setup = { forges: [
    { provider: 'GITHUB', configured: true, connected: false, status: 'reconnect_required' },
    { provider: 'GITLAB', configured: true, connected: true, status: 'connected' },
    { provider: 'OTHER', configured: false, connected: false, status: 'reconnect_required' },
  ] };
  assert.deepEqual(reconnectRequiredProviders(setup), ['GITHUB']);
  assert.deepEqual(reconnectRequiredProviders(null), []);
});
