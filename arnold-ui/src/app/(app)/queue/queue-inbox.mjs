export function queueMembers(item) {
  return item.targetType === 'GROUP' ? item.group.members : [item.mergeRequest];
}

export function blockerLabel(item) {
  const mr = item.mergeRequest;
  return [mr.hasConflicts && 'Merge conflicts', !mr.blockingDiscussionsResolved && 'Unresolved discussions'].filter(Boolean).join(' · ') || (item.blocked ? 'Merge blocked' : '');
}

export function reviewLabel(state) {
  return { CREATED: 'Not analysed', ANALYSING: 'Arnold analysing…', ANALYSED: 'Analysis finished · view result', REVIEW_READY: 'Review ready', READY_FOR_MERGE: 'Ready to merge', CLOSED: 'Closed', MERGED: 'Merged' }[state] || 'Review status unavailable';
}

export function queueSection(item) {
  if (item.targetType === 'GROUP') return 'Review groups';
  const member = item.mergeRequest;
  if (!isOpenMember(member)) return null;
  if (member.mergeRequest.draft) return 'Drafts';
  if (item.blocked || blockerLabel(member)) return 'Blocked';
  if (member.reviewState === 'READY_FOR_MERGE') return 'Ready to merge';
  if (member.roles.includes('reviewer')) return 'Needs your review';
  if (member.roles.includes('author')) return 'Waiting for review';
  return 'Watching';
}

export function queueMatches(item, search, repository, label) {
  const query = search.trim().toLowerCase();
  return queueMembers(item).some(({ mergeRequest: mr }) => (!repository || mr.repositoryPath === repository)
    && (!label || mr.labels.includes(label))
    && [item.group?.name, mr.title, `!${mr.changeNumber}`, mr.repositoryPath, mr.authorName, ...mr.reviewerNames, mr.sourceBranch, mr.targetBranch].join(' ').toLowerCase().includes(query));
}

export function relativeAge(timestamp, now = Date.now()) {
  if (!timestamp) return 'Unknown';
  const minutes = Math.max(0, Math.floor((now - timestamp) / 60_000));
  return minutes < 1 ? 'Just now' : minutes < 60 ? `${minutes}m ago` : minutes < 1440 ? `${Math.floor(minutes / 60)}h ago` : `${Math.floor(minutes / 1440)}d ago`;
}

export function isOpenMember(member) {
  return ['open', 'opened'].includes(member.mergeRequest.state?.toLowerCase());
}

export function priorityMembers(items, priorityOrder = []) {
  const members = new Map();
  for (const item of items) {
    for (const member of queueMembers(item)) {
      if (!isOpenMember(member)) continue;
      const previous = members.get(member.mergeRequest.id);
      const roles = new Set([...(previous?.roles || []), ...member.roles]);
      if (item.targetType !== 'GROUP' && item.role === 'watcher') roles.add('watcher');
      members.set(member.mergeRequest.id, { ...member, roles: [...roles] });
    }
  }
  const ranks = new Map(priorityOrder.map((id, index) => [id, index]));
  return [...members.values()].sort((a, b) => (ranks.get(a.mergeRequest.id) ?? Infinity)
    - (ranks.get(b.mergeRequest.id) ?? Infinity));
}

export function priorityMatches(member, tile) {
  return member.roles.includes(tile) || (tile === 'watcher' && member.roles.includes('assignee'));
}

export function recentMergedMembers(members, now = Date.now()) {
  return members.filter(({ mergeRequest: mr }) => mr.state?.toLowerCase() === 'merged'
    && mr.mergedAt >= now - 30 * 24 * 60 * 60_000)
    .sort((a, b) => b.mergeRequest.mergedAt - a.mergeRequest.mergedAt);
}

export function movePriorityMember(members, fromId, toId) {
  const ids = members.map((member) => member.mergeRequest.id);
  const from = ids.indexOf(fromId);
  const to = ids.indexOf(toId);
  if (from < 0 || to < 0 || from === to) return null;
  [ids[from], ids[to]] = [ids[to], ids[from]];
  return ids;
}
