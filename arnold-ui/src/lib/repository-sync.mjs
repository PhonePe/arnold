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

const PROVIDER_NAMES = { GITHUB: 'GitHub', GITLAB: 'GitLab' };

export const QUEUE_SCOPES = ['mine', 'all'];
export const CHANGE_STATES = ['open', 'merged', 'closed', 'all'];

export function providerName(provider) {
  const key = String(provider || '').toUpperCase();
  return PROVIDER_NAMES[key] || key || 'Unknown';
}

export function changeNumberLabel(mergeRequest) {
  const prefix = String(mergeRequest?.provider || '').toUpperCase() === 'GITHUB' ? '#' : '!';
  return `${prefix}${mergeRequest?.changeNumber ?? ''}`;
}

export function normalizeScope(value) {
  return QUEUE_SCOPES.includes(value) ? value : 'mine';
}

export function availableProviders(status) {
  return (status?.providers || []).filter((provider) => provider.available).map((provider) => String(provider.provider).toUpperCase());
}

export function syncAge(timestamp, now = Date.now()) {
  if (!timestamp) return 'never';
  const minutes = Math.max(0, Math.floor((now - timestamp) / 60_000));
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes} min ago`;
  if (minutes < 1440) return `${Math.floor(minutes / 60)} h ago`;
  return `${Math.floor(minutes / 1440)} d ago`;
}

export function syncStatusLine(provider, now = Date.now()) {
  const name = providerName(provider.provider);
  const status = String(provider.status || '').toUpperCase();
  const unit = String(provider.provider).toUpperCase() === 'GITHUB' ? 'PRs' : 'MRs';
  if (status === 'RUNNING' || status === 'QUEUED') return `${name} · Sync running…`;
  const counts = `${provider.repositories || 0} repo${provider.repositories === 1 ? '' : 's'} · ${provider.changeRequests || 0} open ${unit}`;
  if (status === 'FAILED' || provider.failure) {
    return `${name} · Sync failed${provider.failure ? `: ${provider.failure}` : ''}${provider.lastSucceededAt ? ` · last synced ${syncAge(provider.lastSucceededAt, now)}` : ''}`;
  }
  if (!provider.lastSucceededAt) return `${name} · Not synced yet`;
  return `${name} · ${counts} · synced ${syncAge(provider.lastSucceededAt, now)}`;
}

export function repositorySyncState(status) {
  if (!status) return 'loading';
  const providers = (status.providers || []).filter((provider) => provider.available);
  if (status.running || providers.some((provider) => ['RUNNING', 'QUEUED'].includes(String(provider.status).toUpperCase()))) return 'running';
  if (!status.enabled || providers.length === 0 || providers.every((provider) => !provider.lastSucceededAt)) return 'unconfigured';
  return 'ready';
}

export function analyzeUrlHref(mergeRequest) {
  if (mergeRequest?.webUrl) return `/analyze?${new URLSearchParams({ url: mergeRequest.webUrl })}`;
  return `/analyze?${new URLSearchParams({ mergeRequestId: mergeRequest.id })}`;
}
