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
  await fs.writeFile(keyFile, 'keys:\n  devx: test-secret\n');

  const childEnvironment = Object.fromEntries(Object.entries(process.env)
    .filter(([key]) => !/^(https?|all|no)_proxy$/i.test(key)));
  const run = (extra = {}) => new Promise((resolve, reject) => {
    const child = spawn(process.execPath, [path.join(__dirname, 'load-config.cjs')], {
      env: { ...childEnvironment, NODE_ENV: 'production', NODE_CONFIG_ENV: 'production', NODE_CONFIG_DIR: directory, CONFIG_SERVICE_HOST: '', ROSEY_KEY_FILE: keyFile, ROSEY_CONFIG_PATH: '', ...extra },
    });
    let stderr = '';
    child.stderr.on('data', (chunk) => { stderr += chunk; });
    child.on('error', reject);
    child.on('close', (code) => resolve({ code, stderr }));
  });

  const mounted = await run();
  assert.equal(mounted.code, 0, mounted.stderr);
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

  const fetched = await run(environment);
  assert.equal(fetched.code, 0, fetched.stderr);
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
  await fs.writeFile(keyFile, 'keys:\n  devx: [test-secret');
  const invalidKey = await run(environment);
  assert.notEqual(invalidKey.code, 0);
  assert.ok(!invalidKey.stderr.includes('test-secret'));
});

test('server starts without GitLab config and proxies authenticated API calls only to Arnold', async () => {
  const vm = require('node:vm');
  const configuration = {
    application: { devMode: false },
    server: { host: 'arnold.example.com', port: 3000 },
    api: { host: 'https://arnold-api.example.com', domain: 'arnold-api.example.com', port: 443 },
    olympusIMClientConfig: { nodeServerUrl: 'https://arnold.example.com' },
  };
  const routes = [];
  const targets = [];
  const errors = [];
  let listeningPort;
  const authenticatedCalls = [];
  const authenticated = (req) => authenticatedCalls.push(req);
  const server = {
    get() {},
    use: (...args) => routes.push(args),
    all() {},
    listen: (port, callback) => { listeningPort = port; callback(); },
  };
  const express = Object.assign(() => server, { static: () => () => {} });
  const modules = {
    next: () => ({ prepare: async () => {}, getRequestHandler: () => () => {} }),
    config: { get: (key) => {
      const value = key.split('.').reduce((object, part) => object?.[part], configuration);
      assert.notEqual(value, undefined, `Unexpected configuration lookup: ${key}`);
      return value;
    } },
    express,
    'express-http-proxy': (target, options) => { targets.push(target); return options; },
    path,
    '@phonepe/olympus-node-client': {
      initializeClient() {},
      middlewares: { withHumanUserToken: authenticated, isAuthenticated: authenticated },
    },
  };
  const source = await fs.readFile(path.join(__dirname, '../server.js'), 'utf8');
  vm.runInNewContext(source, {
    require: (name) => { assert.ok(Object.hasOwn(modules, name)); return modules[name]; },
    __dirname: path.join(__dirname, '..'),
    process: { env: { NODE_ENV: 'production' }, exit: (code) => errors.push(code) },
    console: { log() {}, error: (...args) => errors.push(args) },
    URL,
  });
  await new Promise(setImmediate);
  assert.deepEqual(errors, []);
  assert.equal(listeningPort, 3000);
  assert.deepEqual(targets, [configuration.api.host]);
  assert.ok(!routes.some(([route]) => route === '/api/gitlab'));
  const [, middleware, proxyOptions] = routes.find(([route]) => route === '/api');
  let forwarded = 0;
  const headerRequest = { headers: { authorization: 'Bearer token' } };
  middleware(headerRequest, {}, () => { forwarded += 1; });
  assert.equal(forwarded, 1);
  assert.deepEqual(authenticatedCalls, []);
  const sessionRequest = { headers: {} };
  middleware(sessionRequest, {}, () => { forwarded += 1; });
  assert.deepEqual(authenticatedCalls, [sessionRequest]);
  const request = proxyOptions.proxyReqOptDecorator({ headers: {} }, { headers: { authorization: 'Bearer user-token' } });
  assert.equal(request.headers.authorization, 'Bearer user-token');
  assert.equal(request.headers['PRIVATE-TOKEN'], undefined);
});
