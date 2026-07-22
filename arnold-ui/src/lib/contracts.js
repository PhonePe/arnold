import { ArnoldApiError } from './errors';

export const STALE_THRESHOLD_MS = 5 * 60 * 1000;
const CHANGE_REQUEST_PATTERN = /^gitlab:([^:]+):(\d+)$/i;

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
  return { provider: 'GITLAB', repositoryId: match[1], number: Number(match[2]) };
}

export function formatChangeRequestId({ provider, repositoryId, number }) {
  if (String(provider).toUpperCase() !== 'GITLAB' || !repositoryId || !Number.isInteger(Number(number)) || Number(number) < 1) {
    throw new ArnoldApiError('Invalid change request ID');
  }
  return `gitlab:${repositoryId}:${Number(number)}`;
}

export function mapUserResponse(response) {
  if (!response || typeof response !== 'object') invalid();
  const gitlabIdentity = response.gitlabUserId == null && response.gitlabUsername == null
    ? null
    : { userId: requiredString(response.gitlabUserId), username: requiredString(response.gitlabUsername) };
  return {
    id: requiredString(response.id),
    email: requiredString(response.email),
    username: nullableString(response.username) || '',
    displayName: nullableString(response.displayName) || '',
    avatarUrl: nullableString(response.avatarUrl),
    providerIdentities: { GITLAB: gitlabIdentity },
    roles: stringArray(response.roles),
  };
}

export function mapMergeRequestResponse(response) {
  if (!response || typeof response !== 'object') invalid();
  const parsed = parseChangeRequestId(requiredString(response.id));
  if (parsed.repositoryId !== String(response.projectId) || parsed.number !== Number(response.iid)) invalid();
  return {
    id: response.id,
    provider: parsed.provider,
    repositoryId: requiredString(response.projectId),
    providerId: requiredString(response.gitlabId),
    changeNumber: requiredNumber(response.iid),
    repositoryPath: requiredString(response.projectPath),
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
    providerUpdatedAt: requiredNumber(response.gitlabUpdatedAt),
    lastSyncedAt: requiredNumber(response.lastSyncedAt),
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
