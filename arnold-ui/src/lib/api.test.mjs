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

test('only an expired UI session redirects to Olympus login', async () => {
  for (const [errorCode, gitLabAuthorization, expectedRedirects, expectedEvents] of [
    ['PR004', false, 1, 0],
    ['OIM004', false, 1, 0],
    ['OIM011', false, 1, 0],
    [2000, true, 0, 1],
    [2000, false, 0, 0],
    [undefined, false, 0, 0],
  ]) {
    const redirects = [];
    const events = [];
    const context = vm.createContext({
      Headers, URL, Event,
      fetch: async () => Response.json({ errorCode, message: 'Unauthorized' }, { status: 401 }),
      window: {
        location: {
          pathname: '/review', search: '?projectId=1&iid=2', origin: 'https://arnold.example',
          replace: (url) => redirects.push(url),
        },
        dispatchEvent: (event) => events.push(event.type),
      },
    });
    vm.runInContext(`${errorSource}\n${source}\nglobalThis.request = request;`, context);
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
  vm.runInContext(`${errorSource}\n${source}\nglobalThis.load = fetchMergedMergeRequests;`, context);
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
  vm.runInContext(`${errorSource}\n${source}\nglobalThis.reorder = reorderPriorityQueue;`, context);
  const result = await context.reorder(reorder);
  assert.deepEqual(result.priorityOrder, reorder.orderedItemIds);
  assert.equal(result.version, 5);
});
