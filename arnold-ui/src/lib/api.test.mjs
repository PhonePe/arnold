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
    ['OIM004', false, 0, 0],
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
