const fs = require('node:fs/promises');
const path = require('node:path');
const yaml = require('js-yaml');

async function loadConfig() {
  if (process.env.NODE_ENV !== 'production' || (process.env.NODE_CONFIG_ENV && process.env.NODE_CONFIG_ENV !== 'production')) {
    throw new Error('Container startup requires NODE_ENV=production and NODE_CONFIG_ENV unset or production');
  }
  const directory = process.env.NODE_CONFIG_DIR || path.resolve('config');
  const filename = path.join(directory, 'production.yaml');
  let content;

  if (process.env.CONFIG_SERVICE_HOST) {
    const configPath = process.env.ROSEY_CONFIG_PATH;
    if (!configPath) throw new Error('Set ROSEY_CONFIG_PATH to the path after /configs/');
    const keyFile = process.env.ROSEY_KEY_FILE || '/rosey/config.yml';
    const keyContent = await fs.readFile(keyFile, 'utf8');
    let credentials;
    try {
      credentials = yaml.load(keyContent);
    } catch {
      throw new Error('Rosey key file must be valid YAML');
    }
    const key = credentials?.keys?.devx;
    if (typeof key !== 'string' || !key.trim()) throw new Error('Rosey key file must contain a non-empty devx key');

    const response = await fetch(`${process.env.CONFIG_SERVICE_HOST.replace(/\/$/, '')}/configs/${configPath.replace(/^\//, '')}`, {
      headers: { Authorization: `Bearer ${key}` },
      signal: AbortSignal.timeout(30000),
    });
    if (!response.ok) throw new Error(`Rosey configuration request failed (${response.status})`);
    content = await response.text();
  } else {
    content = await fs.readFile(filename, 'utf8');
  }

  let configuration;
  try {
    configuration = yaml.load(content);
  } catch {
    throw new Error('Runtime configuration must be valid YAML');
  }
  if (!configuration || configuration.application?.devMode !== false) {
    throw new Error('Runtime configuration must set application.devMode to false');
  }

  if (process.env.CONFIG_SERVICE_HOST) {
    await fs.mkdir(directory, { recursive: true });
    await fs.writeFile(filename, content, { mode: 0o600 });
    await fs.chmod(filename, 0o600);
  }
}

loadConfig().catch((error) => {
  console.error(`Cannot load production configuration: ${error.message}`);
  process.exitCode = 1;
});
