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
  if (member.mergeRequest.draft) return 'Drafts';
  if (item.blocked || blockerLabel(member)) return 'Blocked';
  if (member.reviewState === 'READY_FOR_MERGE') return 'Ready to merge';
  if (member.roles.includes('reviewer')) return 'Reviewing';
  if (member.roles.includes('author')) return 'Authored';
  return 'Other work';
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
