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
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import vm from 'node:vm';

const source = (await readFile(new URL('./api.js', import.meta.url), 'utf8'))
  .replace(/^import[\s\S]*?;\n/gm, '')
  .replace(/^export \{[^}]*\} from [^;]*;\n/gm, '')
  .replace(/^export /gm, '');
const errorSource = (await readFile(new URL('./errors.js', import.meta.url), 'utf8'))
  .replace(/^export /gm, '');
const scmAuthSource = (await readFile(new URL('./scm-auth.mjs', import.meta.url), 'utf8'))
  .replace(/^export /gm, '');
const prelude = `${errorSource}\n${scmAuthSource}`;

test('only an expired UI session redirects to sign-in', async () => {
  for (const [errorCode, gitLabAuthorization, expectedRedirects, expectedEvents] of [
    ['SESSION_EXPIRED', false, 1, 0],
    [2000, true, 0, 2],
    [2000, false, 0, 0],
    [undefined, false, 0, 0],
  ]) {
    const redirects = [];
    const events = [];
    const context = vm.createContext({
      Headers, URL, Event, CustomEvent,
      fetch: async () => Response.json({ errorCode, message: 'Unauthorized' }, { status: 401 }),
      window: {
        location: {
          pathname: '/review', search: '?projectId=1&iid=2', origin: 'https://arnold.example',
          replace: (url) => redirects.push(url),
        },
        dispatchEvent: (event) => events.push(event.type),
      },
    });
    vm.runInContext(`${prelude}\n${source}\nglobalThis.request = request;`, context);
    await assert.rejects(context.request('/app/v1/bootstrap', { gitLabAuthorization }),
      (error) => error.status === 401 && error.errorCode === errorCode);
    assert.equal(redirects.length, expectedRedirects, String(errorCode));
    assert.equal(events.length, expectedEvents, String(errorCode));
    if (expectedRedirects) {
      assert.equal(new URL(redirects[0], 'https://arnold.example').searchParams.get('successRedirect'),
        'https://arnold.example/review?projectId=1&iid=2');
    }
  }
});


test('merged history follows every cursor and deduplicates overlapping pages', async () => {
  const requests = [];
  const pages = [
    { items: [{ mergeRequest: { id: 'a' } }], nextCursor: 'next-page' },
    { items: [{ mergeRequest: { id: 'a' } }, { mergeRequest: { id: 'b' } }], nextCursor: null },
  ];
  const controller = new AbortController();
  const context = vm.createContext({
    Headers, URLSearchParams,
    mapUserMergeRequestPageResponse: (page) => page,
    fetch: async (url, options) => {
      requests.push(url);
      assert.equal(options.signal, controller.signal);
      return Response.json(pages.shift());
    },
  });
  vm.runInContext(`${prelude}\n${source}\nglobalThis.load = fetchMergedMergeRequests;`, context);
  const items = await context.load({ signal: controller.signal });
  assert.equal(items.map((item) => item.mergeRequest.id).join(','), 'a,b');
  assert.equal(requests.length, 2);
  assert.equal(new URL(requests[1], 'https://arnold.example').searchParams.get('cursor'), 'next-page');
  assert.equal(new URL(requests[0], 'https://arnold.example').searchParams.get('state'), 'merged');
});


test('priority ordering sends MR ids and queue version to the personal endpoint', async () => {
  const reorder = { orderedItemIds: ['gitlab:10:2', 'gitlab:10:1'], version: 4 };
  const context = vm.createContext({
    Headers,
    mapEnrichedQueueResponse: (queue) => queue,
    fetch: async (url, options) => {
      assert.equal(url, '/api/merge-queues/v2/mine/priority');
      assert.equal(options.method, 'PUT');
      assert.deepEqual(JSON.parse(options.body), reorder);
      return Response.json({ priorityOrder: reorder.orderedItemIds, version: 5 });
    },
  });
  vm.runInContext(`${prelude}\n${source}\nglobalThis.reorder = reorderPriorityQueue;`, context);
  const result = await context.reorder(reorder);
  assert.deepEqual(result.priorityOrder, reorder.orderedItemIds);
  assert.equal(result.version, 5);
});

test('provider-scoped review calls carry the provider and only GitLab triggers the GitLab connection gate', async () => {
  const urls = [];
  const events = [];
  const context = vm.createContext({
    Headers, URL, Event, CustomEvent,
    fetch: async (url) => { urls.push(url); return Response.json({ errorCode: 2000 }, { status: 401 }); },
    window: { location: { pathname: '/', search: '', origin: 'https://arnold.example', replace() {} },
      dispatchEvent: (event) => events.push(event.type) },
  });
  vm.runInContext(`${prelude}\n${source}\nglobalThis.request = request;`, context);
  await assert.rejects(context.request('/merge-requests/v1/42/7/diff', { provider: 'GITHUB', gitLabAuthorization: true }));
  await assert.rejects(context.request('/discussions/v1?project_id=42&mr_iid=7', { provider: 'GITHUB' }));
  await assert.rejects(context.request('/merge-requests/v1/42/7?provider=GITHUB', { provider: 'GITHUB' }));
  await assert.rejects(context.request('/merge-requests/v1/1/2/diff', { provider: 'GITLAB', gitLabAuthorization: true }));
  assert.deepEqual(urls, [
    '/api/merge-requests/v1/42/7/diff?provider=GITHUB',
    '/api/discussions/v1?project_id=42&mr_iid=7&provider=GITHUB',
    '/api/merge-requests/v1/42/7?provider=GITHUB',
    '/api/merge-requests/v1/1/2/diff?provider=GITLAB',
  ]);
  assert.deepEqual(events, ['arnold:scm-reconnect-required', 'arnold:gitlab-auth-required']);
});

test('any 401 carrying the reconnect signal opens the reconnect flow for that provider', async () => {
  for (const [body, flagged, expected, redirects] of [
    [{ errorCode: 2000, details: { reason: 'SCM_RECONNECT_REQUIRED', provider: 'GITHUB', reconnect: true } }, false, [['arnold:scm-reconnect-required', 'GITHUB']], 0],
    [{ errorCode: 2000, details: { reason: 'SCM_RECONNECT_REQUIRED', provider: 'GITLAB', reconnect: true } }, false, [['arnold:scm-reconnect-required', 'GITLAB'], ['arnold:gitlab-auth-required', undefined]], 0],
    [{ errorCode: 2000, details: { provider: 'GITHUB', reconnect: false } }, false, [], 0],
    [{ errorCode: 'SESSION_EXPIRED', details: { provider: 'GITHUB', reconnect: true } }, false, [], 1],
  ]) {
    const events = [];
    const replaced = [];
    const context = vm.createContext({
      Headers, URL, Event, CustomEvent,
      fetch: async () => Response.json(body, { status: 401 }),
      window: {
        location: { pathname: '/queue', search: '', origin: 'https://arnold.example', replace: (url) => replaced.push(url) },
        dispatchEvent: (event) => events.push([event.type, event.detail?.provider]),
      },
    });
    vm.runInContext(`${prelude}\n${source}\nglobalThis.request = request;`, context);
    await assert.rejects(context.request('/merge-requests/v1/1/2/diff', { provider: 'GITHUB', gitLabAuthorization: flagged }),
      (error) => error.status === 401 && error.errorCode === body.errorCode);
    assert.deepEqual(events, expected, JSON.stringify(body));
    assert.equal(replaced.length, redirects, JSON.stringify(body));
  }
});
