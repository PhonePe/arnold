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

const fs = require('node:fs/promises');
const path = require('node:path');
const yaml = require('js-yaml');

// Validates the mounted production configuration before the server starts so misconfiguration fails fast.
async function loadConfig() {
  if (process.env.NODE_ENV !== 'production' || (process.env.NODE_CONFIG_ENV && process.env.NODE_CONFIG_ENV !== 'production')) {
    throw new Error('Container startup requires NODE_ENV=production and NODE_CONFIG_ENV unset or production');
  }
  const directory = process.env.NODE_CONFIG_DIR || path.resolve('config');
  const content = await fs.readFile(path.join(directory, 'production.yaml'), 'utf8');

  let configuration;
  try {
    configuration = yaml.load(content);
  } catch {
    throw new Error('Runtime configuration must be valid YAML');
  }
  if (!configuration || configuration.application?.devMode !== false) {
    throw new Error('Runtime configuration must set application.devMode to false');
  }
  const mode = process.env.ARNOLD_AUTH_MODE || configuration.auth?.mode;
  if (mode === 'dev' && process.env.ARNOLD_ALLOW_DEV_AUTH !== 'true') {
    throw new Error('auth.mode=dev is not allowed in production (set ARNOLD_ALLOW_DEV_AUTH=true for local quick starts)');
  }
}

loadConfig().catch((error) => {
  console.error(`Cannot load production configuration: ${error.message}`);
  process.exitCode = 1;
});
