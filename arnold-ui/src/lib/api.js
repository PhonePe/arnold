import { ArnoldApiError } from './errors';
import {
  mapAnalysisResponse,
  mapDashboardResponse,
  mapDiscussionResponse,
  mapEnrichedQueueResponse,
  mapMergeRequestGroupCollectionResponse,
  mapMergeRequestResponse,
  mapNotificationPageResponse,
  mapNotificationResponse,
  mapQueueResponse,
  mapStatsResponse,
  mapSyncResponse,
  mapUserSyncControlResponse,
  mapUserMergeRequestPageResponse,
  mapUserResponse,
} from './contracts';

export { ArnoldApiError } from './errors';

let authRedirectStarted = false;
export const GITLAB_AUTH_REQUIRED_EVENT = 'arnold:gitlab-auth-required';

export function isGitLabAuthorizationError(error) {
  return error instanceof ArnoldApiError && error.status === 401 && Number(error.errorCode) === 2000;
}

function requestGitLabAuthorization() {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new Event(GITLAB_AUTH_REQUIRED_EVENT));
  }
}

function redirectToLogin() {
  if (typeof window === 'undefined' || window.location.pathname.startsWith('/auth') || authRedirectStarted) return;
  authRedirectStarted = true;
  const returnUrl = new URL(`${window.location.pathname}${window.location.search}`, window.location.origin).toString();
  window.location.replace(`/auth/login?successRedirect=${encodeURIComponent(returnUrl)}`);
}

async function request(path, options = {}) {
  const { gitLabAuthorization = false, ...fetchOptions } = options;
  const headers = new Headers(fetchOptions.headers);
  if (fetchOptions.body != null && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  let response;
  try {
    response = await fetch(`/api${path}`, { ...fetchOptions, headers, credentials: 'include' });
  } catch (cause) {
    throw new ArnoldApiError('Could not reach Arnold', { details: cause?.message, cause });
  }

  if (response.status === 204) return null;

  const contentType = response.headers.get('content-type') || '';
  const body = contentType.includes('application/json')
    ? await response.json().catch(() => null)
    : await response.text().catch(() => '');
  const failedEnvelope = body && typeof body === 'object' && body.success === false;

  if (!response.ok || failedEnvelope) {
    const isGitLabAuthRequired = gitLabAuthorization
      && response.status === 401
      && Number(body?.errorCode) === 2000;
    if (isGitLabAuthRequired) requestGitLabAuthorization();
    else if (response.status === 401) redirectToLogin();
    const message = body?.errorMessage || body?.message || body?.error || response.statusText || 'Arnold request failed';
    throw new ArnoldApiError(message, {
      status: response.status,
      errorCode: body?.errorCode,
      details: body,
    });
  }

  if (body && typeof body === 'object' && Object.hasOwn(body, 'success')) {
    return body.response;
  }
  return body;
}

const jsonBody = (value) => JSON.stringify(value);

export async function fetchCurrentUser() {
  return mapUserResponse(await request('/users/v1/me'));
}

export async function fetchAppBootstrap(options = {}) {
  return mapDashboardResponse(await request('/app/v1/bootstrap', options));
}

export function fetchGitLabIntegrationStatus() {
  return request('/gitlab-integrations/v1/status');
}

export function fetchGitLabOAuthAuthorization() {
  return request('/gitlab-integrations/v1/oauth/authorization');
}

export function completeGitLabOAuth(code, state) {
  const query = new URLSearchParams({ code, state });
  return request(`/gitlab-integrations/v1/oauth/callback?${query}`);
}

export function connectGitLab(connection) {
  return request('/gitlab-integrations/v1/connect', { method: 'POST', body: jsonBody(connection) });
}

export function disconnectGitLab() {
  return request('/gitlab-integrations/v1/connection', { method: 'DELETE' });
}

export async function resolveMergeRequest(url, provider = 'GITLAB') {
  return mapMergeRequestResponse(await request('/merge-requests/v1/resolve', {
    method: 'POST',
    body: jsonBody({ provider, url }),
    gitLabAuthorization: provider === 'GITLAB',
  }));
}

export async function fetchMergeRequest(repositoryId, number, { provider = 'GITLAB', ...options } = {}) {
  const query = new URLSearchParams({ provider });
  return mapMergeRequestResponse(await request(
    `/merge-requests/v1/${encodeURIComponent(repositoryId)}/${encodeURIComponent(number)}?${query}`,
    options,
  ));
}

export async function fetchMergeQueue(options = {}) {
  return mapQueueResponse(await request('/merge-queues/v1/mine', options));
}

export async function fetchEnrichedMergeQueue(options = {}) {
  return mapEnrichedQueueResponse(await request('/merge-queues/v2/mine', options));
}

export async function fetchOpenMergeRequests({ role = 'all', limit = 50, cursor, ...options } = {}) {
  const query = new URLSearchParams({ role, limit: String(limit) });
  if (cursor) query.set('cursor', cursor);
  return mapUserMergeRequestPageResponse(await request(`/merge-requests/v1/mine?${query}`, options));
}

export async function fetchSyncStatus(options = {}) {
  return mapSyncResponse(await request('/gitlab-syncs/v1/mine', options));
}

export async function retrySync() {
  return mapSyncResponse(await request('/gitlab-syncs/v1/mine', { method: 'POST' }));
}

export async function fetchUserSyncControl(options = {}) {
  return mapUserSyncControlResponse(await request('/admin/v1/user-sync', options));
}

export async function updateUserSyncMode(mode) {
  return mapUserSyncControlResponse(await request('/admin/v1/user-sync', {
    method: 'PUT',
    body: jsonBody({ mode }),
  }));
}

export async function resetUserSyncCallbackObservation() {
  return mapUserSyncControlResponse(await request(
    '/admin/v1/user-sync/callback-observation/reset',
    { method: 'POST' },
  ));
}

export async function addMergeQueueItem(item) {
  return mapQueueResponse(await request('/merge-queues/v1/mine/items', {
    method: 'POST', body: jsonBody(item),
  }));
}

export async function removeMergeQueueItem(itemId) {
  return mapQueueResponse(await request(`/merge-queues/v1/mine/items/${encodeURIComponent(itemId)}`, {
    method: 'DELETE',
  }));
}

export async function reorderMergeQueue(reorder) {
  return mapQueueResponse(await request('/merge-queues/v1/mine/reorder', {
    method: 'POST', body: jsonBody(reorder),
  }));
}

export async function reorderEnrichedMergeQueue(reorder) {
  await request('/merge-queues/v1/mine/reorder', {
    method: 'POST', body: jsonBody(reorder),
  });
  return fetchEnrichedMergeQueue();
}

export async function fetchMergeRequestGroups(options = {}) {
  return mapMergeRequestGroupCollectionResponse(
    await request('/merge-request-groups/v1/mine', options),
  );
}

export async function createMergeRequestGroup(group) {
  return mapMergeRequestGroupCollectionResponse(await request('/merge-request-groups/v1/mine', {
    method: 'POST', body: jsonBody(group),
  }));
}

export async function updateMergeRequestGroup(groupId, group) {
  return mapMergeRequestGroupCollectionResponse(await request(
    `/merge-request-groups/v1/mine/${encodeURIComponent(groupId)}`,
    { method: 'PUT', body: jsonBody(group) },
  ));
}

export async function deleteMergeRequestGroup(groupId, version) {
  const query = new URLSearchParams({ version: String(version) });
  return mapMergeRequestGroupCollectionResponse(await request(
    `/merge-request-groups/v1/mine/${encodeURIComponent(groupId)}?${query}`,
    { method: 'DELETE' },
  ));
}

export async function fetchNotifications({ limit = 20, before, ...options } = {}) {
  const query = new URLSearchParams({ limit: String(limit) });
  if (before) query.set('before', before);
  return mapNotificationPageResponse(await request(`/notifications/v1/mine?${query}`, options));
}

export async function markNotificationRead(notificationId) {
  return mapNotificationResponse(await request(
    `/notifications/v1/mine/${encodeURIComponent(notificationId)}/read`,
    { method: 'PATCH' },
  ));
}

export async function markAllNotificationsRead() {
  return mapNotificationPageResponse(await request('/notifications/v1/mine/read-all', {
    method: 'POST',
  }));
}

export async function fetchStats(scope = 'personal', options = {}) {
  const path = scope === 'organization' ? '/stats/v1/organization' : '/stats/v1/mine';
  return mapStatsResponse(await request(path, options));
}

export async function startAnalysis(analysis) {
  return mapAnalysisResponse(await request('/analyses/v1', {
    method: 'POST', body: jsonBody(analysis),
  }));
}

export async function fetchAnalysis(runId, options = {}) {
  return mapAnalysisResponse(await request(`/analyses/v1/${encodeURIComponent(runId)}`, options));
}

export async function fetchDiscussions(projectId, mrIid, options = {}) {
  const query = new URLSearchParams({ project_id: String(projectId), mr_iid: String(mrIid) });
  const response = await request(`/discussions/v1?${query}`, options);
  if (!Array.isArray(response)) throw new ArnoldApiError('Invalid backend response');
  return response.filter((item) => !item.deleted).map(mapDiscussionResponse);
}

export async function createDiscussion(discussion) {
  return mapDiscussionResponse(await request('/discussions/v1', {
    method: 'POST', body: jsonBody(discussion), gitLabAuthorization: true,
  }));
}
