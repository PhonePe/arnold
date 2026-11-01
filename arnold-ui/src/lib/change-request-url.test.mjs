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
import { matchChangeRequestUrl, providerForUrl } from './change-request-url.mjs';

test('matches GitHub pull request URLs on any host', () => {
  assert.deepEqual(matchChangeRequestUrl(' https://github.com/org/repo/pull/12 '), { provider: 'GITHUB', url: 'https://github.com/org/repo/pull/12' });
  assert.equal(matchChangeRequestUrl('https://ghe.corp/org/repo/pull/7/files')?.provider, 'GITHUB');
});

test('matches GitLab merge request URLs with nested groups', () => {
  assert.equal(matchChangeRequestUrl('https://gitlab.example/team/repo/-/merge_requests/123')?.provider, 'GITLAB');
  assert.equal(matchChangeRequestUrl('https://gitlab.example/a/b/c/-/merge_requests/9/diffs?x=1')?.provider, 'GITLAB');
});

test('rejects partial or unrelated URLs', () => {
  for (const value of ['', 'github.com/org/repo/pull/1', 'https://github.com/org/repo/pull/', 'https://github.com/org/repo/pulls', 'https://gitlab.example/team/repo/merge_requests/1', 'https://github.com/org/repo/pull/1 extra']) {
    assert.equal(matchChangeRequestUrl(value), null, value);
  }
});

test('derives the provider', () => {
  assert.equal(providerForUrl('https://github.com/org/repo/pull/1'), 'GITHUB');
  assert.equal(providerForUrl('nonsense'), 'GITLAB');
});
