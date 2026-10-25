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
