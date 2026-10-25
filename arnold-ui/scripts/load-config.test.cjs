const assert = require('node:assert/strict');
const { spawn } = require('node:child_process');
const fs = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const { test } = require('node:test');

test('container validates the mounted configuration and fails closed on invalid input', async (t) => {
  const directory = await fs.mkdtemp(path.join(os.tmpdir(), 'arnold-config-'));
  t.after(() => fs.rm(directory, { recursive: true, force: true }));
  const filename = path.join(directory, 'production.yaml');
  await fs.writeFile(filename, 'application:\n  devMode: false\nserver:\n  port: 3000\nauth:\n  mode: oauth\n');

  const run = (extra = {}) => new Promise((resolve, reject) => {
    const child = spawn(process.execPath, [path.join(__dirname, 'load-config.cjs')], {
      env: { ...process.env, NODE_ENV: 'production', NODE_CONFIG_ENV: 'production', NODE_CONFIG_DIR: directory, ARNOLD_AUTH_MODE: '', ARNOLD_ALLOW_DEV_AUTH: '', ...extra },
    });
    let stderr = '';
    child.stderr.on('data', (chunk) => { stderr += chunk; });
    child.on('error', reject);
    child.on('close', (code) => resolve({ code, stderr }));
  });

  const mounted = await run();
  assert.equal(mounted.code, 0, mounted.stderr);
  assert.notEqual((await run({ NODE_ENV: 'development' })).code, 0);
  assert.notEqual((await run({ ARNOLD_AUTH_MODE: 'dev' })).code, 0);
  assert.equal((await run({ ARNOLD_AUTH_MODE: 'dev', ARNOLD_ALLOW_DEV_AUTH: 'true' })).code, 0);
  await fs.writeFile(filename, 'application:\n  devMode: true\n');
  assert.notEqual((await run()).code, 0);
  await fs.writeFile(filename, 'application: [invalid');
  assert.notEqual((await run()).code, 0);
  await fs.unlink(filename);
  assert.notEqual((await run()).code, 0);
});
