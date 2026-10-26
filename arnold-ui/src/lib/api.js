import { ArnoldApiError } from './errors';
import {
  mapAnalysisResponse,
  mapAnalysisListResponse,
  mapDashboardResponse,
  mapDiscussionResponse,
  mapEnrichedQueueResponse,
  mapMergeRequestGroupCollectionResponse,
  mapMergeRequestResponse,
  mapMergeRequestReviewResponse,
  mapNotificationPageResponse,
  mapNotificationResponse,
  mapPersonaListResponse,
  mapPersonaResponse,
  mapQueueResponse,
  mapReviewFeedbackMetricsResponse,
  mapReviewerCandidatesResponse,
  mapStatsResponse,
  mapSyncResponse,
  mapUserSyncControlResponse,
  mapUserMergeRequestPageResponse,
  mapSmartDiffResponse,
  mapUserListResponse,
  mapUserResponse,
} from './contracts';

export { ArnoldApiError } from './errors';

let authRedirectStarted = false;
export const GITLAB_AUTH_REQUIRED_EVENT = 'arnold:gitlab-auth-required';

const SESSION_EXPIRED_ERROR_CODES = ['PR004', 'OIM004', 'OIM011'];

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
    else if (response.status === 401 && SESSION_EXPIRED_ERROR_CODES.includes(body?.errorCode)) redirectToLogin();
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

export async function fetchUsers() {
  return mapUserListResponse(await request('/users/v1'));
}

export function fetchPermissions(options = {}) {
  return request('/app/v1/permissions', options);
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

export async function addMergeRequestReviewer(repositoryId, number, username) {
  return mapMergeRequestResponse(await request(
    `/merge-requests/v1/${encodeURIComponent(repositoryId)}/${encodeURIComponent(number)}/reviewers`,
    { method: 'POST', body: jsonBody({ username }), gitLabAuthorization: true },
  ));
}

export async function removeMergeRequestReviewer(repositoryId, number, reviewerId) {
  return mapMergeRequestResponse(await request(
    `/merge-requests/v1/${encodeURIComponent(repositoryId)}/${encodeURIComponent(number)}/reviewers/${encodeURIComponent(reviewerId)}`,
    { method: 'DELETE', gitLabAuthorization: true },
  ));
}

export async function searchMergeRequestReviewers(repositoryId, query, options = {}) {
  const search = new URLSearchParams({ query });
  return mapReviewerCandidatesResponse(await request(
    `/merge-requests/v1/${encodeURIComponent(repositoryId)}/reviewer-candidates?${search}`,
    { ...options, gitLabAuthorization: true },
  ));
}

export async function fetchMergeRequestReview(projectId, iid, options = {}) {
  return mapMergeRequestReviewResponse(await request(
    `/merge-requests/v1/${encodeURIComponent(projectId)}/${encodeURIComponent(iid)}/review-status`,
    { ...options, gitLabAuthorization: true },
  ));
}

export async function fetchMergeRequestDiff(projectId, iid, options = {}) {
  const response = await request(
    `/merge-requests/v1/${encodeURIComponent(projectId)}/${encodeURIComponent(iid)}/diff`,
    { ...options, gitLabAuthorization: true },
  );
  if (typeof response !== 'string') throw new ArnoldApiError('Invalid backend response');
  return response;
}

export async function fetchMergeRequestDiffVersions(projectId, iid, options = {}) {
  const response = await request(
    `/merge-requests/v1/${encodeURIComponent(projectId)}/${encodeURIComponent(iid)}/diff-versions`,
    { ...options, gitLabAuthorization: true },
  );
  if (!Array.isArray(response)) throw new ArnoldApiError('Invalid backend response');
  return response.map((version) => ({
    id: Number(version.id),
    baseSha: String(version.baseSha),
    startSha: String(version.startSha),
    headSha: String(version.headSha),
    createdAt: Number(version.createdAt),
    commitTitle: version.commitTitle == null ? null : String(version.commitTitle),
  }));
}

export async function fetchMergeRequestDiffVersion(projectId, iid, versionId, options = {}) {
  const response = await request(
    `/merge-requests/v1/${encodeURIComponent(projectId)}/${encodeURIComponent(iid)}/diff-versions/${encodeURIComponent(versionId)}`,
    { ...options, gitLabAuthorization: true },
  );
  if (typeof response !== 'string') throw new ArnoldApiError('Invalid backend response');
  return response;
}

export async function fetchMergeRequestFiles(projectId, iid, oldPath, newPath, refs = {}, options = {}) {
  const query = new URLSearchParams({ oldPath, newPath });
  if (refs.baseSha) query.set('baseSha', refs.baseSha);
  if (refs.headSha) query.set('headSha', refs.headSha);
  const response = await request(
    `/merge-requests/v1/${encodeURIComponent(projectId)}/${encodeURIComponent(iid)}/files?${query}`,
    { ...options, gitLabAuthorization: true },
  );
  if (!response || typeof response.oldContent !== 'string' || typeof response.newContent !== 'string') {
    throw new ArnoldApiError('Invalid backend response');
  }
  return response;
}

export async function fetchViewedFiles(projectId, iid, headSha, options = {}) {
  const query = new URLSearchParams({ headSha });
  const response = await request(
    `/merge-requests/v1/${encodeURIComponent(projectId)}/${encodeURIComponent(iid)}/viewed-files?${query}`,
    options,
  );
  if (!response || !Array.isArray(response.filePaths)) throw new ArnoldApiError('Invalid backend response');
  return {
    filePaths: response.filePaths.map(String),
    storyStepIds: Array.isArray(response.storyStepIds) ? response.storyStepIds.map(String) : [],
    focusAreaFeedback: response.focusAreaFeedback && typeof response.focusAreaFeedback === 'object' ? response.focusAreaFeedback : {},
  };
}

export async function updateViewedFiles(projectId, iid, headSha, filePaths, storyStepIds, focusAreaFeedback) {
  const response = await request(
    `/merge-requests/v1/${encodeURIComponent(projectId)}/${encodeURIComponent(iid)}/viewed-files`,
    { method: 'PUT', body: jsonBody({ headSha, filePaths, storyStepIds, focusAreaFeedback }) },
  );
  if (!response || !Array.isArray(response.filePaths)) throw new ArnoldApiError('Invalid backend response');
  return {
    filePaths: response.filePaths.map(String),
    storyStepIds: Array.isArray(response.storyStepIds) ? response.storyStepIds.map(String) : [],
    focusAreaFeedback: response.focusAreaFeedback && typeof response.focusAreaFeedback === 'object' ? response.focusAreaFeedback : {},
  };
}

export async function fetchSmartDiff(projectId, iid, headSha, options = {}) {
  const query = new URLSearchParams({ headSha });
  return mapSmartDiffResponse(await request(
    `/merge-requests/v1/${encodeURIComponent(projectId)}/${encodeURIComponent(iid)}/smart-diff?${query}`,
    options,
  ));
}

export async function organizeSmartDiff(projectId, iid, headSha, { regenerate = false, execution = null, ...options } = {}) {
  return mapSmartDiffResponse(await request(
    `/merge-requests/v1/${encodeURIComponent(projectId)}/${encodeURIComponent(iid)}/smart-diff`,
    { ...options, method: 'POST', body: jsonBody({ headSha, ...(regenerate ? { regenerate: true } : {}), ...(execution ? { execution } : {}) }) },
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

export async function fetchMergedMergeRequests(options = {}) {
  const items = [];
  let cursor;
  do {
    const query = new URLSearchParams({ state: 'merged', limit: '100' });
    if (cursor) query.set('cursor', cursor);
    const page = mapUserMergeRequestPageResponse(await request(`/merge-requests/v1/mine?${query}`, options));
    items.push(...page.items);
    cursor = page.nextCursor;
  } while (cursor);
  return [...new Map(items.map((item) => [item.mergeRequest.id, item])).values()];
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

export async function fetchReviewFeedbackMetrics({ from, to, filters = {}, ...options } = {}) {
  const query = new URLSearchParams();
  if (from) query.set('from', String(from));
  if (to) query.set('to', String(to));
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== '' && value != null) query.set(key, String(value));
  });
  const suffix = query.size ? `?${query}` : '';
  return mapReviewFeedbackMetricsResponse(await request(`/admin/v1/metrics/review-feedback${suffix}`, options));
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

export async function reorderPriorityQueue(reorder) {
  return mapEnrichedQueueResponse(await request('/merge-queues/v2/mine/priority', {
    method: 'PUT', body: jsonBody(reorder),
  }));
}

export async function fetchMergeRequestGroups(options = {}) {
  return mapMergeRequestGroupCollectionResponse(
    await request('/merge-request-groups/v1', options),
  );
}

export async function fetchMergeRequestGroup(groupId, options = {}) {
  return mapMergeRequestGroupResponse(await request(
    `/merge-request-groups/v1/${encodeURIComponent(groupId)}`, options,
  ));
}

export async function createMergeRequestGroup(group) {
  return mapMergeRequestGroupCollectionResponse(await request('/merge-request-groups/v1', {
    method: 'POST', body: jsonBody(group),
  }));
}

export async function updateMergeRequestGroup(groupId, group) {
  return mapMergeRequestGroupResponse(await request(
    `/merge-request-groups/v1/${encodeURIComponent(groupId)}`,
    { method: 'PUT', body: jsonBody(group) },
  ));
}

export async function deleteMergeRequestGroup(groupId, version) {
  const query = new URLSearchParams({ version: String(version) });
  return request(
    `/merge-request-groups/v1/${encodeURIComponent(groupId)}?${query}`,
    { method: 'DELETE' },
  );
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

export function startGroupAnalysis(groupId, context) {
  return request(`/merge-request-groups/v1/${encodeURIComponent(groupId)}/analyses`, {
    method: 'POST', body: jsonBody(context), gitLabAuthorization: true,
  });
}

export function fetchGroupAnalyses({ groupId, before, limit, ...options } = {}) {
  const query = new URLSearchParams();
  if (groupId) query.set('groupId', groupId);
  if (before) query.set('before', String(before));
  if (limit) query.set('limit', String(limit));
  const suffix = query.size ? `?${query}` : '';
  return request(`/group-analyses/v1${suffix}`, options);
}

export function fetchGroupAnalysis(runId, options = {}) {
  return request(`/group-analyses/v1/${encodeURIComponent(runId)}`, options);
}

export function retryGroupAnalysis(runId) {
  return request(`/group-analyses/v1/${encodeURIComponent(runId)}/retry`, { method: 'POST', gitLabAuthorization: true });
}

export async function fetchAnalysis(runId, options = {}) {
  return mapAnalysisResponse(await request(`/analyses/v1/${encodeURIComponent(runId)}`, options));
}

export async function fetchAnalyses({ mergeRequestId, before, limit, ...options } = {}) {
  const query = new URLSearchParams();
  if (mergeRequestId) query.set('mergeRequestId', mergeRequestId);
  if (before) query.set('before', String(before));
  if (limit) query.set('limit', String(limit));
  const suffix = query.size ? `?${query}` : '';
  return mapAnalysisListResponse(await request(`/analyses/v1${suffix}`, options));
}

export async function fetchPersonas(options = {}) {
  return mapPersonaListResponse(await request('/personas/v1', options));
}

export async function createPersona(persona) {
  return mapPersonaResponse(await request('/personas/v1', { method: 'POST', body: jsonBody(persona) }));
}

export async function updatePersona(personaId, persona) {
  return mapPersonaResponse(await request(`/personas/v1/${encodeURIComponent(personaId)}`, {
    method: 'PUT', body: jsonBody(persona),
  }));
}

export async function publishPersona(personaId, version) {
  return mapPersonaResponse(await request(`/personas/v1/${encodeURIComponent(personaId)}/publish?version=${version}`, { method: 'POST' }));
}

export async function unpublishPersona(personaId, version) {
  return mapPersonaResponse(await request(`/personas/v1/${encodeURIComponent(personaId)}/unpublish?version=${version}`, { method: 'POST' }));
}

export async function deletePersona(personaId, version) {
  return request(`/personas/v1/${encodeURIComponent(personaId)}?version=${version}`, { method: 'DELETE' });
}

export async function fetchDiscussions(projectId, mrIid, options = {}) {
  const query = new URLSearchParams({ project_id: String(projectId), mr_iid: String(mrIid) });
  const response = await request(`/discussions/v1?${query}`, { ...options, gitLabAuthorization: true });
  if (!Array.isArray(response)) throw new ArnoldApiError('Invalid backend response');
  return response.filter((item) => !item.deleted).map(mapDiscussionResponse);
}

export async function fetchDraftDiscussions(projectId, mrIid, options = {}) {
  const query = new URLSearchParams({ project_id: String(projectId), mr_iid: String(mrIid) });
  const response = await request(`/discussions/v1/drafts?${query}`, { ...options, gitLabAuthorization: true });
  if (!Array.isArray(response)) throw new ArnoldApiError('Invalid backend response');
  return response.map(mapDiscussionResponse);
}

export async function createDiscussion(discussion) {
  return mapDiscussionResponse(await request('/discussions/v1', {
    method: 'POST', body: jsonBody(discussion), gitLabAuthorization: true,
  }));
}

export async function createDraftDiscussion(discussion) {
  return mapDiscussionResponse(await request('/discussions/v1/drafts', {
    method: 'POST', body: jsonBody(discussion), gitLabAuthorization: true,
  }));
}

export async function updateDraftDiscussion(draftId, update) {
  return mapDiscussionResponse(await request(`/discussions/v1/drafts/${encodeURIComponent(draftId)}`, {
    method: 'PUT', body: jsonBody(update), gitLabAuthorization: true,
  }));
}

export function deleteDraftDiscussion(draftId, projectId, mrIid) {
  const query = new URLSearchParams({ project_id: String(projectId), mr_iid: String(mrIid) });
  return request(`/discussions/v1/drafts/${encodeURIComponent(draftId)}?${query}`, {
    method: 'DELETE', gitLabAuthorization: true,
  });
}

export async function publishReview(review) {
  const response = await request('/discussions/v1/drafts/publish', {
    method: 'POST', body: jsonBody(review), gitLabAuthorization: true,
  });
  if (!Array.isArray(response)) throw new ArnoldApiError('Invalid backend response');
  return response.filter((item) => !item.deleted).map(mapDiscussionResponse);
}

export function setDiscussionResolution(discussionId, resolution) {
  return request(`/discussions/v1/${encodeURIComponent(discussionId)}/resolution`, {
    method: 'PUT', body: jsonBody(resolution), gitLabAuthorization: true,
  });
}

export function applySuggestion(suggestionId, commitMessage = null) {
  return request(`/discussions/v1/suggestions/${encodeURIComponent(suggestionId)}/apply`, {
    method: 'PUT', body: jsonBody({ commitMessage }), gitLabAuthorization: true,
  });
}

export function fetchCopilotStatus(options = {}) {
  return request('/copilot-integrations/v1/status', options);
}

export function initiateCopilotConnection() {
  return request('/copilot-integrations/v1/device/initiate', { method: 'POST' });
}

export function pollCopilotConnection(attemptId, options = {}) {
  return request('/copilot-integrations/v1/device/poll', {
    ...options, method: 'POST', body: jsonBody({ attemptId }),
  });
}

export function cancelCopilotConnection(attemptId) {
  return request('/copilot-integrations/v1/device/attempt', {
    method: 'DELETE', body: jsonBody({ attemptId }),
  });
}

export function verifyCopilotConnection() {
  return request('/copilot-integrations/v1/verify', { method: 'POST' });
}

export function disconnectCopilot() {
  return request('/copilot-integrations/v1/connection', { method: 'DELETE' });
}

export function fetchAnalysisExecutionOptions(options = {}) {
  return request('/analysis-execution/v1', options);
}
