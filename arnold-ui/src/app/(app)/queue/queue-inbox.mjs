/*
 * Copyright (c) 2026 Original Author(s), PhonePe Limited
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

export function queueMembers(item) {
  return item.targetType === 'GROUP' ? item.group.members : [item.mergeRequest];
}

export function blockerLabel(item) {
  const mr = item.mergeRequest;
  return [mr.hasConflicts && 'Merge conflicts', !mr.blockingDiscussionsResolved && 'Unresolved discussions'].filter(Boolean).join(' · ') || (mr.mergeStatus?.toLowerCase() === 'not_approved' ? 'Awaiting approval' : item.blocked ? 'Merge blocked' : '');
}

export function reviewLabel(state) {
  return { CREATED: 'Not analysed', ANALYSING: 'Arnold analysing…', ANALYSED: 'Analysis finished · view result', REVIEW_READY: 'Review ready', CLOSED: 'Closed', MERGED: 'Merged' }[state] || 'Review status unavailable';
}

export function queueSections(item) {
  if (item.targetType === 'GROUP') return ['Review groups'];
  const member = item.mergeRequest;
  if (!isOpenMember(member)) return [];
  const { draft, mergeStatus } = member.mergeRequest;
  const status = mergeStatus?.toLowerCase();
  const blocked = Boolean(item.blocked || blockerLabel(member));
  return [
    ['Needs your review', priorityMatches(member, 'reviewer')],
    ['Ready to merge', !draft && !blocked && status === 'mergeable'],
    ['Blocked', blocked],
    ['Drafts', draft],
    ['Waiting for review', priorityMatches(member, 'author') && !draft
      && (status === 'not_approved' || (!blocked && status !== 'mergeable'))],
    ['Watching', priorityMatches(member, 'watcher')],
  ].filter(([, matches]) => matches).map(([name]) => name);
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

export function movePriorityMember(members, fromId, toId, position = 'at') {
  const ids = members.map((member) => member.mergeRequest.id);
  const from = ids.indexOf(fromId);
  const to = ids.indexOf(toId);
  if (from < 0 || to < 0 || from === to) return null;
  ids.splice(from, 1);
  const destination = position === 'at' ? to : ids.indexOf(toId) + (position === 'after' ? 1 : 0);
  ids.splice(destination, 0, fromId);
  return ids.every((id, index) => id === members[index].mergeRequest.id) ? null : ids;
}

export function inboxSections(items, mergedMembers, { search = '', repository = '', label = '', sort = 'manual' } = {}) {
  const names = ['Needs your review', 'Ready to merge', 'Blocked', 'Drafts', 'Waiting for review', 'Watching'];
  const rows = priorityMembers(items).map((member) => ({
    id: member.mergeRequest.id, targetType: 'MERGE_REQUEST', blocked: member.blocked, mergeRequest: member,
  })).filter((item) => queueMatches(item, search, repository, label));
  if (sort !== 'manual') rows.sort((a, b) => {
    const difference = a.mergeRequest.mergeRequest.providerUpdatedAt - b.mergeRequest.mergeRequest.providerUpdatedAt;
    return sort === 'oldest' ? difference : -difference;
  });
  return [
    { name: 'All open', items },
    ...names.map((name) => ({ name, items: rows.filter((item) => queueSections(item).includes(name)) })),
    { name: 'Review groups', items: items.filter((item) => item.targetType === 'GROUP') },
    { name: 'Recently merged', items: mergedMembers.map((member) => ({
      id: member.mergeRequest.id, targetType: 'MERGE_REQUEST', mergeRequest: member,
    })) },
  ];
}
