const assert = require('node:assert/strict');
const { spawn } = require('node:child_process');
const fs = require('node:fs/promises');
const http = require('node:http');
const os = require('node:os');
const path = require('node:path');
const { test } = require('node:test');

test('container loads mounted or Rosey configuration and fails closed on invalid input', async (t) => {
  const directory = await fs.mkdtemp(path.join(os.tmpdir(), 'arnold-config-'));
  t.after(() => fs.rm(directory, { recursive: true, force: true }));
  const filename = path.join(directory, 'production.yaml');
  const keyFile = path.join(directory, 'key.yml');
  const content = 'application:\n  devMode: false\nserver:\n  port: 3000\n';
  await fs.writeFile(filename, content);
  await fs.writeFile(keyFile, 'devx: test-secret\n');

  const run = (extra = {}) => new Promise((resolve, reject) => {
    const child = spawn(process.execPath, [path.join(__dirname, 'load-config.cjs')], {
      env: { ...process.env, NODE_ENV: 'production', NODE_CONFIG_ENV: 'production', NODE_CONFIG_DIR: directory, CONFIG_SERVICE_HOST: '', ROSEY_KEY_FILE: keyFile, ROSEY_CONFIG_PATH: '', ...extra },
    });
    let stderr = '';
    child.stderr.on('data', (chunk) => { stderr += chunk; });
    child.on('error', reject);
    child.on('close', (code) => resolve({ code, stderr }));
  });

  assert.equal((await run()).code, 0);
  assert.notEqual((await run({ NODE_ENV: 'development' })).code, 0);
  await fs.writeFile(filename, 'application:\n  devMode: true\n');
  assert.notEqual((await run()).code, 0);
  await fs.unlink(filename);
  assert.notEqual((await run()).code, 0);

  let status = 200;
  let body = content;
  let request;
  const server = http.createServer((req, res) => {
    request = { url: req.url, authorization: req.headers.authorization };
    res.writeHead(status);
    res.end(body);
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  t.after(() => new Promise((resolve) => server.close(resolve)));
  const environment = { CONFIG_SERVICE_HOST: `http://127.0.0.1:${server.address().port}/`, ROSEY_CONFIG_PATH: 'devx/arnold-ui/test/raw' };

  assert.equal((await run(environment)).code, 0);
  assert.deepEqual(request, { url: '/configs/devx/arnold-ui/test/raw', authorization: 'Bearer test-secret' });
  assert.equal(await fs.readFile(filename, 'utf8'), content);
  assert.equal((await fs.stat(filename)).mode & 0o777, 0o600);
  assert.notEqual((await run({ ...environment, ROSEY_CONFIG_PATH: '' })).code, 0);
  status = 401;
  assert.notEqual((await run(environment)).code, 0);
  status = 200;
  body = 'application: [invalid-secret';
  const invalid = await run(environment);
  assert.notEqual(invalid.code, 0);
  assert.ok(!invalid.stderr.includes('invalid-secret'));
  assert.equal(await fs.readFile(filename, 'utf8'), content);
  await fs.writeFile(keyFile, 'devx: [test-secret');
  const invalidKey = await run(environment);
  assert.notEqual(invalidKey.code, 0);
  assert.ok(!invalidKey.stderr.includes('test-secret'));
});
