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

export const SCM_RECONNECT_REQUIRED_EVENT = 'arnold:scm-reconnect-required';
export const SCM_RECONNECT_REASON = 'SCM_RECONNECT_REQUIRED';
export const RECONNECT_REQUIRED_STATUS = 'reconnect_required';
export const SCM_PROVIDERS = ['GITHUB', 'GITLAB'];

const SESSION_EXPIRED_ERROR_CODES = ['SESSION_EXPIRED'];
const LEGACY_AUTH_ERROR_CODE = 2000;

/**
 * Classifies a failed API response.
 * - `session`: the Arnold sign-in expired; go to login.
 * - `reconnect`: the user's GitHub/GitLab credential was rejected (`details.reconnect`), or — for requests flagged
 *   `gitLabAuthorization` against older backends — a bare GitLab 401 with errorCode 2000.
 */
export function classifyAuthFailure({ status, body, gitLabAuthorization = false } = {}) {
  if (status !== 401) return null;
  if (typeof body === 'string' || SESSION_EXPIRED_ERROR_CODES.includes(body?.errorCode)) return { kind: 'session' };
  const details = body?.details;
  const provider = String(details?.provider || '').toUpperCase();
  if (details?.reconnect === true && SCM_PROVIDERS.includes(provider)) return { kind: 'reconnect', provider };
  if (gitLabAuthorization && Number(body?.errorCode) === LEGACY_AUTH_ERROR_CODE) return { kind: 'reconnect', provider: 'GITLAB' };
  return null;
}

/** Providers whose stored credential the backend reports as no longer usable. */
export function reconnectRequiredProviders(setup) {
  return (setup?.forges || [])
    .filter((forge) => forge.configured && forge.status === RECONNECT_REQUIRED_STATUS)
    .map((forge) => forge.provider);
}
