import { ArnoldApiError } from './errors';

export const STALE_THRESHOLD_MS = 5 * 60 * 1000;
const CHANGE_REQUEST_PATTERN = /^([a-z][a-z0-9_-]*):([^:]+):(\d+)$/i;
const VERSION_CONTROL_PROVIDERS = new Set(['GITLAB', 'GITHUB']);

function invalid() {
  throw new ArnoldApiError('Invalid backend response');
}

function requiredString(value) {
  if (value === null || value === undefined || String(value).length === 0) invalid();
  return String(value);
}

function requiredNumber(value) {
  const number = Number(value);
  if (!Number.isFinite(number)) invalid();
  return number;
}

const nullableString = (value) => value === null || value === undefined || value === '' ? null : String(value);
const stringArray = (value) => Array.isArray(value) ? value.map(String) : [];

export function parseChangeRequestId(id) {
  const match = typeof id === 'string' ? id.match(CHANGE_REQUEST_PATTERN) : null;
  if (!match) throw new ArnoldApiError('Invalid change request ID');
  const provider = match[1].toUpperCase();
  if (!VERSION_CONTROL_PROVIDERS.has(provider)) throw new ArnoldApiError('Invalid change request ID');
  return { provider, repositoryId: match[2], number: Number(match[3]) };
}

export function formatChangeRequestId({ provider, repositoryId, number }) {
  const normalizedProvider = String(provider).toUpperCase();
  if (!VERSION_CONTROL_PROVIDERS.has(normalizedProvider) || !repositoryId || !Number.isInteger(Number(number)) || Number(number) < 1) {
    throw new ArnoldApiError('Invalid change request ID');
  }
  return `${normalizedProvider.toLowerCase()}:${repositoryId}:${Number(number)}`;
}

export function mapUserResponse(response) {
  if (!response || typeof response !== 'object') invalid();
  const providerIdentities = Object.fromEntries(Object.entries(response.providerIdentities || {}).map(([provider, identity]) => [
    requiredString(provider).toUpperCase(),
    {
      userId: requiredString(identity.providerUserId),
      username: nullableString(identity.username) || '',
      avatarUrl: nullableString(identity.avatarUrl),
    },
  ]));
  return {
    id: requiredString(response.id),
    email: requiredString(response.email),
    username: nullableString(response.username) || '',
    displayName: nullableString(response.displayName) || '',
    avatarUrl: nullableString(response.avatarUrl),
    providerIdentities,
    roles: stringArray(response.roles),
  };
}

export function mapMergeRequestResponse(response) {
  if (!response || typeof response !== 'object') invalid();
  const parsed = parseChangeRequestId(requiredString(response.id));
  if (parsed.provider !== requiredString(response.provider).toUpperCase()
    || parsed.repositoryId !== String(response.repositoryId)
    || parsed.number !== Number(response.number)) invalid();
  return {
    id: response.id,
    provider: parsed.provider,
    repositoryId: requiredString(response.repositoryId),
    providerId: requiredString(response.providerId),
    changeNumber: requiredNumber(response.number),
    repositoryPath: requiredString(response.repositoryPath),
    title: requiredString(response.title),
    description: nullableString(response.description),
    webUrl: requiredString(response.webUrl),
    sourceBranch: requiredString(response.sourceBranch),
    targetBranch: requiredString(response.targetBranch),
    headSha: nullableString(response.headSha),
    state: requiredString(response.state),
    mergeStatus: nullableString(response.mergeStatus),
    draft: Boolean(response.draft),
    authorId: nullableString(response.authorId),
    assigneeIds: stringArray(response.assigneeIds),
    reviewerIds: stringArray(response.reviewerIds),
    labels: stringArray(response.labels),
    createdAt: requiredNumber(response.createdAt || response.providerUpdatedAt),
    mergedAt: requiredNumber(response.mergedAt || 0),
    closedAt: requiredNumber(response.closedAt || 0),
    hasConflicts: Boolean(response.hasConflicts),
    blockingDiscussionsResolved: response.blockingDiscussionsResolved !== false,
    providerUpdatedAt: requiredNumber(response.providerUpdatedAt),
    lastSyncedAt: requiredNumber(response.lastSyncedAt),
  };
}

export function mapSyncResponse(response) {
  if (!response || typeof response !== 'object') invalid();
  return {
    provider: requiredString(response.provider),
    status: requiredString(response.status).toUpperCase(),
    lastAttemptAt: requiredNumber(response.lastAttemptAt || 0),
    lastSucceededAt: requiredNumber(response.lastSucceededAt || 0),
    nextDueAt: requiredNumber(response.nextDueAt || 0),
    failure: nullableString(response.failure),
    retryable: Boolean(response.retryable),
  };
}

export function mapUserSyncControlResponse(response) {
  if (!response || typeof response !== 'object') invalid();
  const configuredMode = requiredString(response.configuredMode).toUpperCase();
  const effectiveMode = requiredString(response.effectiveMode).toUpperCase();
  const modes = new Set(['AUTO', 'POLLING', 'CALLBACK_ONLY']);
  if (!modes.has(configuredMode) || !modes.has(effectiveMode)) invalid();
  return {
    configuredMode,
    effectiveMode,
    callbackObserved: Boolean(response.callbackObserved),
    lastCallbackAt: requiredNumber(response.lastCallbackAt || 0),
    pollingEnabled: Boolean(response.pollingEnabled),
    leaderId: nullableString(response.leaderId),
    leaderLeaseUntil: requiredNumber(response.leaderLeaseUntil || 0),
    currentNodeLeader: Boolean(response.currentNodeLeader),
    currentNodeId: requiredString(response.currentNodeId),
  };
}

export function mapUserMergeRequestResponse(response) {
  if (!response || typeof response !== 'object') invalid();
  return {
    mergeRequest: mapMergeRequestResponse(response.mergeRequest),
    roles: stringArray(response.roles).map((role) => role.toLowerCase()),
    reviewState: requiredString(response.reviewState),
    blocked: Boolean(response.blocked),
  };
}

export function mapUserMergeRequestPageResponse(response) {
  if (!response || !Array.isArray(response.items)) invalid();
  const syncs = Object.fromEntries(Object.entries(response.syncs || {}).map(([provider, sync]) => [
    requiredString(provider).toUpperCase(), mapSyncResponse(sync),
  ]));
  return {
    items: response.items.map(mapUserMergeRequestResponse),
    nextCursor: nullableString(response.nextCursor),
    syncs,
    sync: syncs.GITLAB || Object.values(syncs)[0] || null,
  };
}

function mapMergeRequestGroupResponse(response) {
  if (!response || !Array.isArray(response.members)) invalid();
  return {
    id: requiredString(response.id),
    name: requiredString(response.name),
    members: response.members.map(mapUserMergeRequestResponse),
    createdAt: requiredNumber(response.createdAt),
    updatedAt: requiredNumber(response.updatedAt),
  };
}

export function mapEnrichedQueueResponse(response) {
  if (!response || !Array.isArray(response.items)) invalid();
  return {
    items: response.items.map((item) => {
      const targetType = requiredString(item.targetType).toUpperCase();
      const mergeRequest = item.mergeRequest ? mapUserMergeRequestResponse(item.mergeRequest) : null;
      const group = item.group ? mapMergeRequestGroupResponse(item.group) : null;
      if ((targetType === 'MERGE_REQUEST' && !mergeRequest) || (targetType === 'GROUP' && !group)) invalid();
      return {
        id: requiredString(item.id),
        targetType,
        targetId: requiredString(item.targetId),
        role: requiredString(item.role).toLowerCase(),
        source: requiredString(item.source).toUpperCase(),
        blocked: Boolean(item.blocked),
        mergeRequest,
        group,
      };
    }),
    version: requiredNumber(response.version),
    groupVersion: requiredNumber(response.groupVersion || 0),
    updatedAt: requiredNumber(response.updatedAt || 0),
  };
}

export function mapMergeRequestGroupCollectionResponse(response) {
  if (!response || !Array.isArray(response.groups)) invalid();
  return {
    groups: response.groups.map(mapMergeRequestGroupResponse),
    version: requiredNumber(response.version),
    updatedAt: requiredNumber(response.updatedAt || 0),
  };
}

export function mapNotificationResponse(response) {
  if (!response || typeof response !== 'object') invalid();
  return {
    id: requiredString(response.id),
    type: requiredString(response.type),
    title: requiredString(response.title),
    body: requiredString(response.body),
    aggregateType: requiredString(response.aggregateType),
    aggregateId: requiredString(response.aggregateId),
    deepLink: requiredString(response.deepLink),
    read: Boolean(response.read),
    readAt: requiredNumber(response.readAt || 0),
    createdAt: requiredNumber(response.createdAt),
  };
}

export function mapNotificationPageResponse(response) {
  if (!response || !Array.isArray(response.items)) invalid();
  return {
    items: response.items.map(mapNotificationResponse),
    unreadCount: requiredNumber(response.unreadCount || 0),
    nextCursor: nullableString(response.nextCursor),
  };
}

export function mapStatsResponse(response) {
  if (!response || !Array.isArray(response.weekly)) invalid();
  return {
    scope: requiredString(response.scope),
    openAuthored: requiredNumber(response.openAuthored || 0),
    openReviewWork: requiredNumber(response.openReviewWork || 0),
    merged: requiredNumber(response.merged || 0),
    medianTimeToMergeMs: requiredNumber(response.medianTimeToMergeMs || 0),
    throughputDeltaPct: response.throughputDeltaPct == null ? null : requiredNumber(response.throughputDeltaPct),
    weekly: response.weekly.map((week) => ({
      weekStart: requiredString(week.weekStart),
      authoredMerged: requiredNumber(week.authoredMerged || 0),
      reviewWorkCompleted: requiredNumber(week.reviewWorkCompleted || 0),
    })),
    generatedAt: requiredNumber(response.generatedAt),
    dataThrough: requiredNumber(response.dataThrough || 0),
  };
}

export function mapDashboardResponse(response) {
  if (!response || typeof response !== 'object') invalid();
  return {
    user: mapUserResponse(response.user),
    integration: response.integration,
    sync: mapSyncResponse(response.sync),
    queue: mapEnrichedQueueResponse(response.queue),
    openMergeRequests: Array.isArray(response.openMergeRequests)
      ? response.openMergeRequests.map(mapUserMergeRequestResponse)
      : [],
    productivity: mapStatsResponse(response.productivity),
    notifications: mapNotificationPageResponse(response.notifications),
  };
}

export function mapQueueResponse(response) {
  if (!response || !Array.isArray(response.items)) invalid();
  return {
    items: response.items.map((item) => {
      const changeRequestId = requiredString(item.mergeRequestId);
      parseChangeRequestId(changeRequestId);
      return {
        id: requiredString(item.id), changeRequestId, role: requiredString(item.role), blocked: Boolean(item.blocked),
      };
    }),
    version: requiredNumber(response.version),
    updatedAt: requiredNumber(response.updatedAt),
  };
}

export function mapAnalysisResponse(response) {
  if (!response || typeof response !== 'object') invalid();
  const changeRequestId = requiredString(response.mergeRequestId);
  parseChangeRequestId(changeRequestId);
  return {
    changeRequestId,
    reviewState: requiredString(response.reviewState),
    runId: requiredString(response.runId),
    personaIds: stringArray(response.personaIds),
    runStatus: requiredString(response.runStatus),
    completedPersonas: requiredNumber(response.completedPersonas),
    createdAt: requiredNumber(response.createdAt),
    updatedAt: requiredNumber(response.updatedAt),
  };
}

export function mapDiscussionResponse(response) {
  if (!response || typeof response !== 'object') invalid();
  const changeRequestId = requiredString(response.mergeRequestId);
  const { provider } = parseChangeRequestId(changeRequestId);
  return {
    id: requiredString(response.id),
    provider,
    changeRequestId,
    threadId: nullableString(response.discussionId),
    providerCommentId: requiredString(response.noteId),
    authorId: nullableString(response.authorId),
    body: requiredString(response.body),
    state: requiredString(response.state),
    resolved: Boolean(response.resolved),
    source: requiredString(response.source),
    providerUpdatedAt: requiredNumber(response.gitlabUpdatedAt),
    lastSyncedAt: requiredNumber(response.lastSyncedAt),
  };
}

export function isStale(lastSyncedAt, thresholdMs = STALE_THRESHOLD_MS) {
  return Number.isFinite(lastSyncedAt) && Date.now() - lastSyncedAt > thresholdMs;
}
