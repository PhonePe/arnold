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
const nullableNumber = (value) => value === null || value === undefined ? null : requiredNumber(value);
const stringArray = (value) => Array.isArray(value) ? value.map(String) : [];
const userName = (value) => nullableString(value)?.replace(/\s+\(@[^)]+\)$/, '') || null;
const userNames = (value) => stringArray(value).map(userName).filter(Boolean);

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

export function mapUserListResponse(response) {
  if (!Array.isArray(response)) invalid();
  return response.map(mapUserResponse);
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
    authorName: userName(response.authorName),
    assigneeIds: stringArray(response.assigneeIds),
    assigneeNames: userNames(response.assigneeNames),
    reviewerIds: stringArray(response.reviewerIds),
    reviewerNames: userNames(response.reviewerNames),
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

export function mapReviewerCandidatesResponse(response) {
  if (!Array.isArray(response)) invalid();
  return response.map((candidate) => ({
    id: requiredString(candidate.id),
    name: requiredString(candidate.name),
    username: requiredString(candidate.username),
    avatarUrl: nullableString(candidate.avatarUrl),
  }));
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
    priorityOrder: stringArray(response.priorityOrder),
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
    body: nullableString(response.body),
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

const countMap = (value) => Object.fromEntries(Object.entries(value && typeof value === 'object' ? value : {})
  .map(([key, count]) => [key, requiredNumber(count || 0)]));

function mapFeedbackOrigin(origin) {
  const value = origin && typeof origin === 'object' ? origin : {};
  const score = value.score || {};
  return {
    total: requiredNumber(value.total || 0),
    accepted: requiredNumber(value.accepted || 0),
    actionable: requiredNumber(value.actionable || 0),
    acceptanceRate: nullableNumber(value.acceptanceRate),
    score: {
      count: requiredNumber(score.count || 0),
      average: nullableNumber(score.average),
      min: nullableNumber(score.min),
      max: nullableNumber(score.max),
    },
    acceptance: countMap(value.acceptance),
    priority: countMap(value.priority),
    commentType: countMap(value.commentType),
    suggestion: countMap(value.suggestion),
    category: countMap(value.category),
    model: countMap(value.model),
    persona: countMap(value.persona),
    project: countMap(value.project),
    weekly: (Array.isArray(value.weekly) ? value.weekly : []).map((week) => ({
      weekStart: requiredString(week.weekStart),
      total: requiredNumber(week.total || 0),
      accepted: requiredNumber(week.accepted || 0),
      acceptanceRate: nullableNumber(week.acceptanceRate),
    })),
  };
}

const mapWeeklyTotals = (weekly) => (Array.isArray(weekly) ? weekly : []).map((week) => ({
  weekStart: requiredString(week.weekStart),
  total: requiredNumber(week.total || 0),
}));

function mapRunMetrics(runs) {
  const value = runs && typeof runs === 'object' ? runs : {};
  return {
    total: requiredNumber(value.total || 0),
    outcome: countMap(value.outcome),
    status: countMap(value.status),
    averageDurationMs: nullableNumber(value.averageDurationMs),
    findings: requiredNumber(value.findings || 0),
    postedFindings: requiredNumber(value.postedFindings || 0),
    totalTokens: requiredNumber(value.totalTokens || 0),
    weekly: mapWeeklyTotals(value.weekly),
  };
}

function mapSmartDiffMetrics(smartDiffs) {
  const value = smartDiffs && typeof smartDiffs === 'object' ? smartDiffs : {};
  return {
    total: requiredNumber(value.total || 0),
    status: countMap(value.status),
    assessment: countMap(value.assessment),
    averageDurationMs: nullableNumber(value.averageDurationMs),
    totalTokens: requiredNumber(value.totalTokens || 0),
    weekly: mapWeeklyTotals(value.weekly),
  };
}

function mapEngagementMetrics(engagement) {
  const value = engagement && typeof engagement === 'object' ? engagement : {};
  return {
    reviewers: requiredNumber(value.reviewers || 0),
    mergeRequests: requiredNumber(value.mergeRequests || 0),
    focusAreasRated: requiredNumber(value.focusAreasRated || 0),
    focusAreasUseful: requiredNumber(value.focusAreasUseful || 0),
    focusAreasNotUseful: requiredNumber(value.focusAreasNotUseful || 0),
    focusUsefulRate: nullableNumber(value.focusUsefulRate),
    weekly: (Array.isArray(value.weekly) ? value.weekly : []).map((week) => ({
      weekStart: requiredString(week.weekStart),
      useful: requiredNumber(week.useful || 0),
      notUseful: requiredNumber(week.notUseful || 0),
    })),
  };
}

export function mapReviewFeedbackMetricsResponse(response) {
  if (!response || typeof response !== 'object') invalid();
  return {
    from: requiredNumber(response.from),
    to: requiredNumber(response.to),
    arnold: mapFeedbackOrigin(response.arnold),
    workflow: mapFeedbackOrigin(response.workflow),
    authorship: mapCommentAuthorship(response.authorship),
    personaSplit: {
      persona: mapFeedbackOrigin(response.personaSplit?.persona),
      general: mapFeedbackOrigin(response.personaSplit?.general),
    },
    runs: mapRunMetrics(response.runs),
    smartDiffs: mapSmartDiffMetrics(response.smartDiffs),
    engagement: mapEngagementMetrics(response.engagement),
  };
}

export function mapTokenUsage(usage) {
  if (!usage) return null;
  if (typeof usage !== 'object') invalid();
  const jobUrl = nullableString(usage.jobUrl);
  return {
    inputTokens: requiredNumber(usage.inputTokens),
    outputTokens: requiredNumber(usage.outputTokens),
    cacheReadTokens: nullableNumber(usage.cacheReadTokens),
    models: (Array.isArray(usage.models) ? usage.models : []).map((model) => ({
      model: requiredString(model?.model),
      inputTokens: requiredNumber(model.inputTokens),
      outputTokens: requiredNumber(model.outputTokens),
      cacheReadTokens: nullableNumber(model.cacheReadTokens),
    })),
    jobUrl: jobUrl?.startsWith('https://') ? jobUrl : null,
  };
}

export function mapTokenUsageMetricsResponse(response) {
  if (!response || typeof response !== 'object') invalid();
  const totals = (item, key) => ({
    [key]: requiredString(item?.[key]),
    jobs: requiredNumber(item.jobs),
    inputTokens: requiredNumber(item.inputTokens),
    outputTokens: requiredNumber(item.outputTokens),
    cacheReadTokens: nullableNumber(item.cacheReadTokens) ?? 0,
  });
  return {
    from: requiredNumber(response.from),
    to: requiredNumber(response.to),
    jobs: requiredNumber(response.jobs),
    inputTokens: requiredNumber(response.inputTokens),
    outputTokens: requiredNumber(response.outputTokens),
    cacheReadTokens: nullableNumber(response.cacheReadTokens) ?? 0,
    flows: (Array.isArray(response.flows) ? response.flows : []).map((flow) => totals(flow, 'flow')),
    models: (Array.isArray(response.models) ? response.models : []).map((model) => totals(model, 'model')),
    recentJobs: (Array.isArray(response.recentJobs) ? response.recentJobs : []).map((job) => ({
      id: requiredString(job?.id),
      flow: requiredString(job.flow),
      runId: requiredString(job.runId),
      target: nullableString(job.target),
      invokedBy: nullableString(job.invokedBy),
      harness: nullableString(job.harness),
      status: nullableString(job.status),
      usage: mapTokenUsage(job.usage) || invalid(),
      createdAt: requiredNumber(job.createdAt),
    })),
  };
}

function mapCommentAuthors(authors) {
  const value = authors && typeof authors === 'object' ? authors : {};
  return {
    total: requiredNumber(value.total || 0),
    resolvable: requiredNumber(value.resolvable || 0),
    resolved: requiredNumber(value.resolved || 0),
    resolutionRate: nullableNumber(value.resolutionRate),
    mergeRequests: requiredNumber(value.mergeRequests || 0),
    byMergeRequestAuthor: requiredNumber(value.byMergeRequestAuthor || 0),
    feedback: mapFeedbackOrigin(value.feedback),
    weekly: (Array.isArray(value.weekly) ? value.weekly : []).map((week) => ({
      weekStart: requiredString(week.weekStart),
      total: requiredNumber(week.total || 0),
    })),
  };
}

function mapCommentAuthorship(authorship) {
  const value = authorship && typeof authorship === 'object' ? authorship : {};
  return {
    aiAuthor: typeof value.aiAuthor === 'string' ? value.aiAuthor : 'cibot',
    ai: mapCommentAuthors(value.ai),
    human: mapCommentAuthors(value.human),
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
    copilot: response.copilot || null,
    sync: mapSyncResponse(response.sync),
    queue: mapEnrichedQueueResponse(response.queue),
    openMergeRequests: Array.isArray(response.openMergeRequests)
      ? response.openMergeRequests.map(mapUserMergeRequestResponse)
      : [],
    productivity: mapStatsResponse(response.productivity),
    notifications: mapNotificationPageResponse(response.notifications),
  };
}

export function mapMergeRequestReviewResponse(response) {
  if (!response || typeof response !== 'object') invalid();
  return {
    detailedMergeStatus: nullableString(response.detailedMergeStatus),
    hasConflicts: Boolean(response.hasConflicts),
    discussionsResolved: Boolean(response.discussionsResolved),
    pipelineStatus: nullableString(response.pipelineStatus),
    pipelineUrl: nullableString(response.pipelineUrl),
    approvalsRequired: requiredNumber(response.approvalsRequired),
    approvalsLeft: requiredNumber(response.approvalsLeft),
    approved: Boolean(response.approved),
    reviewers: Array.isArray(response.reviewers) ? response.reviewers.map((reviewer) => ({
      id: requiredString(reviewer.id),
      name: requiredString(reviewer.name),
      username: requiredString(reviewer.username),
      avatarUrl: nullableString(reviewer.avatarUrl),
      status: requiredString(reviewer.status),
    })) : [],
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
    repositoryPath: nullableString(response.repositoryPath),
    title: nullableString(response.title),
    invokedBy: nullableString(response.invokedBy),
    reviewState: requiredString(response.reviewState),
    runId: requiredString(response.runId),
    personaIds: stringArray(response.personaIds),
    runStatus: requiredString(response.runStatus),
    execution: response.execution || null,
    completedPersonas: requiredNumber(response.completedPersonas),
    pipelineId: nullableNumber(response.pipelineId),
    pipelineUrl: nullableString(response.pipelineUrl),
    analysisOutcome: nullableString(response.analysisOutcome),
    outcomeDetail: nullableString(response.outcomeDetail),
    reviewPersonas: Array.isArray(response.reviewPersonas) ? response.reviewPersonas.map((persona) => ({
      id: requiredString(persona.id),
      name: requiredString(persona.name),
      version: requiredNumber(persona.version),
    })) : [],
    findings: Array.isArray(response.findings) ? response.findings.map((finding) => ({
      id: requiredString(finding.id),
      headSha: requiredString(finding.headSha),
      filePath: nullableString(finding.filePath),
      oldPath: nullableString(finding.oldPath),
      newLine: nullableNumber(finding.newLine),
      body: requiredString(finding.body),
      publicationStatus: requiredString(finding.publicationStatus),
      commentUrl: nullableString(finding.commentUrl),
      personaId: nullableString(finding.personaId),
      personaVersion: nullableNumber(finding.personaVersion),
    })) : [],
    createdAt: requiredNumber(response.createdAt),
    updatedAt: requiredNumber(response.updatedAt),
    usage: mapTokenUsage(response.usage),
  };
}

export function mapPersonaResponse(response) {
  if (!response || typeof response !== 'object') invalid();
  const status = requiredString(response.status).toUpperCase();
  if (!['DRAFT', 'PUBLISHED'].includes(status)) invalid();
  const policy = response.policy;
  if (!policy || requiredNumber(policy.version) !== 1 || !Array.isArray(policy.rules)
    || !Array.isArray(policy.prerequisites)) invalid();
  return {
    id: requiredString(response.id),
    name: requiredString(response.name),
    summary: requiredString(response.summary),
    instructions: requiredString(response.instructions),
    policy: {
      version: 1,
      guidance: nullableString(policy.guidance) || '',
      rules: policy.rules.map((rule) => ({
        id: requiredString(rule.id),
        requirement: requiredString(rule.requirement),
      })),
      prerequisites: policy.prerequisites.map((prerequisite) => ({
        id: requiredString(prerequisite.id),
        appliesWhen: requiredString(prerequisite.appliesWhen),
        requirement: requiredString(prerequisite.requirement),
      })),
      ignore: stringArray(policy.ignore),
    },
    tags: stringArray(response.tags),
    ownerId: requiredString(response.ownerId),
    contributorIds: stringArray(response.contributorIds),
    status,
    systemOwned: Boolean(response.systemOwned),
    version: requiredNumber(response.version),
    createdAt: requiredNumber(response.createdAt || 0),
    updatedAt: requiredNumber(response.updatedAt || 0),
  };
}

const rateOrNull = (value) => nullableNumber(value);

export function mapPersonaMetricsResponse(response) {
  if (!response || typeof response !== 'object') invalid();
  const findings = response.findings || {};
  const acceptance = response.acceptance || {};
  const resolution = response.resolution || {};
  return {
    personaId: requiredString(response.personaId),
    from: requiredNumber(response.from),
    to: requiredNumber(response.to),
    runs: requiredNumber(response.runs || 0),
    findings: {
      total: requiredNumber(findings.total || 0),
      posted: requiredNumber(findings.posted || 0),
      failed: requiredNumber(findings.failed || 0),
    },
    acceptance: {
      feedback: requiredNumber(acceptance.feedback || 0),
      accepted: requiredNumber(acceptance.accepted || 0),
      actionable: requiredNumber(acceptance.actionable || 0),
      acceptanceRate: rateOrNull(acceptance.acceptanceRate),
    },
    resolution: {
      comments: requiredNumber(resolution.comments || 0),
      resolvable: requiredNumber(resolution.resolvable || 0),
      resolved: requiredNumber(resolution.resolved || 0),
      resolutionRate: rateOrNull(resolution.resolutionRate),
    },
    weekly: (Array.isArray(response.weekly) ? response.weekly : []).map((week) => ({
      weekStart: requiredString(week.weekStart),
      findings: requiredNumber(week.findings || 0),
      feedback: requiredNumber(week.feedback || 0),
      accepted: requiredNumber(week.accepted || 0),
      acceptanceRate: rateOrNull(week.acceptanceRate),
    })),
    versions: (Array.isArray(response.versions) ? response.versions : []).map((version) => ({
      version: requiredNumber(version.version),
      runs: requiredNumber(version.runs || 0),
      findings: requiredNumber(version.findings || 0),
      feedback: requiredNumber(version.feedback || 0),
      accepted: requiredNumber(version.accepted || 0),
      acceptanceRate: rateOrNull(version.acceptanceRate),
    })),
  };
}

function mapPersonaMetricsEntry(entry) {
  const value = entry && typeof entry === 'object' ? entry : {};
  return {
    personaId: nullableString(value.personaId),
    runs: requiredNumber(value.runs || 0),
    findings: requiredNumber(value.findings || 0),
    posted: requiredNumber(value.posted || 0),
    feedback: requiredNumber(value.feedback || 0),
    accepted: requiredNumber(value.accepted || 0),
    acceptanceRate: rateOrNull(value.acceptanceRate),
  };
}

export function mapPersonaMetricsSummaryResponse(response) {
  if (!response || typeof response !== 'object') invalid();
  return {
    from: requiredNumber(response.from),
    to: requiredNumber(response.to),
    personas: (Array.isArray(response.personas) ? response.personas : []).map(mapPersonaMetricsEntry),
    general: mapPersonaMetricsEntry(response.general),
  };
}

export function mapPersonaListResponse(response) {
  if (!Array.isArray(response)) invalid();
  return response.map(mapPersonaResponse);
}

export function mapAnalysisListResponse(response) {
  if (!Array.isArray(response)) invalid();
  return response.map(mapAnalysisResponse);
}

export function mapSmartDiffResponse(response) {
  if (!response || typeof response !== 'object') invalid();
  const status = requiredString(response.status);
  if (!['not_requested', 'queued', 'running', 'completed', 'failed'].includes(status)) invalid();
  const commentAssessmentStatus = nullableString(response.commentAssessmentStatus) || 'not_requested';
  if (!['not_requested', 'queued', 'running', 'completed', 'failed'].includes(commentAssessmentStatus)) invalid();
  if (!Array.isArray(response.groups)) invalid();
  return {
    headSha: requiredString(response.headSha),
    status,
    summary: nullableString(response.summary),
    groups: response.groups.map((group) => {
      if (!group || typeof group !== 'object' || !Array.isArray(group.steps)) invalid();
      return {
        id: requiredString(group.id),
        title: requiredString(group.title),
        rationale: requiredString(group.rationale),
        steps: group.steps.map((step) => {
          if (!step || typeof step !== 'object' || !Array.isArray(step.changes)) invalid();
          return {
            id: requiredString(step.id),
            title: requiredString(step.title),
            explanation: requiredString(step.explanation),
            changes: step.changes.map((change) => {
              if (!change || typeof change !== 'object'
                || !Object.hasOwn(change, 'oldStartLine') || !Object.hasOwn(change, 'oldEndLine')
                || !Object.hasOwn(change, 'newStartLine') || !Object.hasOwn(change, 'newEndLine')) invalid();
              const oldStartLine = nullableNumber(change.oldStartLine);
              const oldEndLine = nullableNumber(change.oldEndLine);
              const newStartLine = nullableNumber(change.newStartLine);
              const newEndLine = nullableNumber(change.newEndLine);
              if ((oldStartLine === null) !== (oldEndLine === null) || (newStartLine === null) !== (newEndLine === null)
                || oldStartLine === null && newStartLine === null
                || [oldStartLine, oldEndLine, newStartLine, newEndLine].some((line) => line !== null && (!Number.isInteger(line) || line < 0))
                || oldStartLine !== null && oldEndLine < oldStartLine || newStartLine !== null && newEndLine < newStartLine) invalid();
              return { filePath: requiredString(change.filePath), oldStartLine, oldEndLine, newStartLine, newEndLine };
            }),
          };
        }),
      };
    }),
    updatedAt: requiredNumber(response.updatedAt),
    errorMessage: nullableString(response.errorMessage),
    commentAssessmentStatus,
    assessedCommentCount: requiredNumber(response.assessedCommentCount || 0),
    usage: mapTokenUsage(response.usage),
    pipelineId: nullableNumber(response.pipelineId),
    pipelineUrl: nullableString(response.pipelineUrl),
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
    draftId: nullableNumber(response.draftId),
    authorId: nullableString(response.authorId),
    authorUsername: nullableString(response.authorUsername),
    body: requiredString(response.body),
    state: requiredString(response.state),
    resolvable: Boolean(response.resolvable),
    resolved: Boolean(response.resolved),
    individualNote: Boolean(response.individualNote),
    source: requiredString(response.source),
    createdAt: requiredNumber(response.createdAt),
    providerUpdatedAt: requiredNumber(response.gitlabUpdatedAt),
    lastSyncedAt: requiredNumber(response.lastSyncedAt),
    acceptanceStatus: nullableString(response.acceptanceStatus),
    assessedSha: nullableString(response.assessedSha),
    assessmentReasoning: nullableString(response.assessmentReasoning),
    analysisRunId: nullableString(response.analysisRunId),
    personaId: nullableString(response.personaId),
    personaVersion: nullableNumber(response.personaVersion),
    personaName: nullableString(response.personaName),
    reviewSource: nullableString(response.reviewSource) || 'human',
    position: response.position ? {
      baseSha: nullableString(response.position.baseSha),
      startSha: nullableString(response.position.startSha),
      headSha: nullableString(response.position.headSha),
      oldPath: nullableString(response.position.oldPath),
      newPath: nullableString(response.position.newPath),
      oldLine: nullableNumber(response.position.oldLine),
      newLine: nullableNumber(response.position.newLine),
      lineRange: response.position.lineRange ? {
        side: requiredString(response.position.lineRange.side),
        startOldLine: nullableNumber(response.position.lineRange.startOldLine),
        startNewLine: nullableNumber(response.position.lineRange.startNewLine),
        endOldLine: nullableNumber(response.position.lineRange.endOldLine),
        endNewLine: nullableNumber(response.position.lineRange.endNewLine),
      } : null,
    } : null,
    suggestions: Array.isArray(response.suggestions) ? response.suggestions.map((suggestion) => ({
      id: requiredNumber(suggestion.id),
      applicable: Boolean(suggestion.applicable),
      applied: Boolean(suggestion.applied),
      fromContent: nullableString(suggestion.fromContent) || '',
      toContent: nullableString(suggestion.toContent) || '',
      fromLine: requiredNumber(suggestion.fromLine),
      toLine: requiredNumber(suggestion.toLine),
    })) : [],
  };
}

export function isStale(lastSyncedAt, thresholdMs = STALE_THRESHOLD_MS) {
  return Number.isFinite(lastSyncedAt) && Date.now() - lastSyncedAt > thresholdMs;
}
