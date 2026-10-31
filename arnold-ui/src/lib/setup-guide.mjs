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

export const SETUP_GUIDE_DISMISSED_KEY = 'arnold.setupGuide.dismissed.v1';

export const PROVIDER_LABELS = { GITHUB: 'GitHub', GITLAB: 'GitLab' };

export function providerLabel(provider) {
  return PROVIDER_LABELS[String(provider || '').toUpperCase()] || String(provider || 'Source');
}

export function configuredForges(setup) {
  return (setup?.forges || []).filter((forge) => forge.configured);
}

export function connectedForges(setup) {
  return configuredForges(setup).filter((forge) => forge.connected);
}

export function isForgeConfigured(setup, provider) {
  return configuredForges(setup).some((forge) => forge.provider === provider);
}

/** `#123` for GitHub pull requests, `!123` for GitLab merge requests. */
export function changeReference(mergeRequest) {
  const prefix = String(mergeRequest?.provider || '').toUpperCase() === 'GITHUB' ? '#' : '!';
  return `${prefix}${mergeRequest?.changeNumber ?? ''}`;
}

/** Human noun for the change requests of the configured forges. */
export function changeRequestNoun(setup) {
  const providers = new Set(configuredForges(setup).map((forge) => forge.provider));
  if (providers.has('GITHUB') && providers.has('GITLAB')) return 'merge requests and pull requests';
  if (providers.has('GITHUB')) return 'pull requests';
  return 'merge requests';
}

export function formatAgo(timestamp, now = Date.now()) {
  if (!timestamp) return null;
  const seconds = Math.max(0, Math.round((now - Number(timestamp)) / 1000));
  if (seconds < 60) return 'just now';
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  return `${Math.round(hours / 24)}d ago`;
}

/**
 * Derives the first-run checklist from `/app/v1/setup` and the analysis harness options.
 * `gitLabReauthRequired` reflects a rejected GitLab credential and turns the GitLab row into a blocking reconnect.
 */
export function computeSetupGuide({ setup, harnesses = [], repositorySync = null, gitLabReauthRequired = false } = {}) {
  if (!setup) return { steps: [], blocking: false, pending: false, reconnect: false };
  const configured = configuredForges(setup);
  const reconnect = gitLabReauthRequired && configured.some((forge) => forge.provider === 'GITLAB');
  const forges = configured.map((forge) => ({
    provider: forge.provider,
    label: providerLabel(forge.provider),
    connected: Boolean(forge.connected) && !(reconnect && forge.provider === 'GITLAB'),
    reconnect: reconnect && forge.provider === 'GITLAB',
    username: forge.username || null,
  }));
  const steps = [];

  steps.push({
    id: 'source-control',
    title: 'Connect source control',
    required: configured.length > 0,
    admin: configured.length === 0,
    done: !reconnect && forges.some((forge) => forge.connected),
    forges,
  });

  const gitHub = (setup.forges || []).find((forge) => forge.provider === 'GITHUB');
  if (gitHub?.configured && gitHub.connected && gitHub.installUrl) {
    const synced = (repositorySync?.providers || []).find((provider) => provider.provider === 'GITHUB');
    steps.push({
      id: 'install',
      title: 'Install Arnold on repositories',
      required: false,
      admin: false,
      done: Number(synced?.repositories) > 0,
      installUrl: gitHub.installUrl,
    });
  }

  const options = Array.isArray(harnesses) ? harnesses : [];
  const copilot = setup.copilot || {};
  if (copilot.enabled) {
    const alternative = options.some((option) => option.id !== 'copilot' && option.available);
    steps.push({
      id: 'copilot',
      title: 'Connect GitHub Copilot',
      required: !alternative,
      admin: false,
      done: Boolean(copilot.connected),
      username: copilot.username || null,
    });
  }

  const copilotPending = Boolean(copilot.enabled && !copilot.connected);
  if (options.length > 0 && !options.some((option) => option.available) && !copilotPending) {
    steps.push({
      id: 'engine',
      title: 'Review engine',
      required: false,
      admin: true,
      done: false,
      messages: options.map((option) => option.message).filter(Boolean),
    });
  }

  const sourceControl = steps[0];
  return {
    steps,
    blocking: sourceControl.required && !sourceControl.done,
    pending: steps.some((step) => !step.done),
    reconnect,
  };
}

/** Footer items: one per configured source, from repository sync + the user's connection. */
export function statusBarItems({ setup, repositorySync, starting = false, now = Date.now() } = {}) {
  if (starting) return [{ status: 'warning', label: 'Arnold starting' }];
  if (!setup) return [];
  return configuredForges(setup).map((forge) => {
    const name = providerLabel(forge.provider);
    const sync = repositorySync?.enabled
      ? (repositorySync.providers || []).find((provider) => provider.provider === forge.provider && provider.available)
      : null;
    if (!sync) {
      return forge.connected
        ? { status: 'success', label: `${name} connected` }
        : { status: 'warning', label: `${name} not connected` };
    }
    const noun = forge.provider === 'GITHUB' ? 'PRs' : 'MRs';
    if (sync.status === 'FAILED') return { status: 'warning', label: `${name} sync failed` };
    if (sync.status === 'RUNNING') return { status: 'warning', label: `${name} syncing…` };
    if (sync.status === 'SUCCEEDED') {
      const ago = formatAgo(sync.lastSucceededAt, now);
      return {
        status: 'success',
        label: `${name} · ${sync.repositories ?? 0} repo${sync.repositories === 1 ? '' : 's'} · ${sync.changeRequests ?? 0} ${sync.changeRequests === 1 ? noun.slice(0, -1) : noun}${ago ? ` · synced ${ago}` : ''}`,
      };
    }
    return { status: 'neutral', label: `${name} · not synced yet` };
  });
}

/** Aggregate deployment-wide repository sync state for the top bar. */
export function repositorySyncSummary(repositorySync, now = Date.now()) {
  if (!repositorySync?.enabled) return null;
  const providers = (repositorySync.providers || []).filter((provider) => provider.available);
  if (providers.length === 0) return null;
  const running = repositorySync.running || providers.some((provider) => provider.status === 'RUNNING');
  const failed = providers.find((provider) => provider.status === 'FAILED');
  const lastSucceededAt = Math.max(0, ...providers.map((provider) => Number(provider.lastSucceededAt) || 0));
  if (running) return { tone: 'busy', label: 'Syncing repositories…', running: true };
  if (failed) return { tone: 'bad', label: 'Repository sync failed', detail: failed.failure || null };
  if (lastSucceededAt) return { tone: 'ok', label: `Synced ${formatAgo(lastSucceededAt, now)}` };
  return { tone: 'neutral', label: 'Not synced yet' };
}

export function repositorySyncChangeRequests(repositorySync) {
  return (repositorySync?.providers || []).reduce((total, provider) => total + (Number(provider.changeRequests) || 0), 0);
}

/** Sidebar connection line: connected forges, or null when setup is still required. */
export function forgeSummary(setup) {
  const connected = connectedForges(setup);
  if (connected.length === 0) return null;
  const providers = connected.map((forge) => forge.provider);
  const label = connected.length === 1
    ? `${providerLabel(connected[0].provider)}${connected[0].username ? ` · ${connected[0].username}` : ''}`
    : connected.map((forge) => providerLabel(forge.provider)).join(' + ');
  return { providers, label };
}
