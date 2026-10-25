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

import assert from 'node:assert/strict';
import { queueSections, queueMatches, blockerLabel, relativeAge, reviewLabel, priorityMembers, priorityMatches, recentMergedMembers, movePriorityMember, inboxSections } from './queue-inbox.mjs';
const member = { roles: ['reviewer'], reviewState: 'ANALYSED', blocked: false, mergeRequest: { id: 'mr-42', state: 'opened', title: 'Fix retry', changeNumber: 42, repositoryPath: 'payments/api', labels: ['bug'], reviewerNames: ['Ana'], hasConflicts: false, blockingDiscussionsResolved: true } };
const item = { targetType: 'MERGE_REQUEST', mergeRequest: member };
assert.deepEqual(queueSections(item), ['Needs your review']);
assert.equal(reviewLabel('ANALYSED'), 'Analysis finished · view result');
assert.equal(queueMatches(item, '!42', 'payments/api', 'bug'), true);
assert.equal(queueMatches(item, 'retry', 'other/repo', ''), false);
member.mergeRequest.hasConflicts = true;
member.reviewState = 'REVIEW_READY';
assert.deepEqual(queueSections(item), ['Needs your review', 'Blocked']);
assert.equal(blockerLabel(member), 'Merge conflicts');
member.mergeRequest.draft = true;
assert.deepEqual(queueSections(item), ['Needs your review', 'Blocked', 'Drafts']);
const group = { targetType: 'GROUP', group: { name: 'Rollout', members: [member, { ...member, mergeRequest: { ...member.mergeRequest, repositoryPath: 'other/repo', labels: ['feature'] } }] } };
assert.deepEqual(queueSections(group), ['Review groups']);
assert.equal(queueMatches(group, 'rollout', 'payments/api', 'feature'), false);
assert.equal(queueMatches(group, 'rollout', 'payments/api', 'bug'), true);
assert.equal(relativeAge(60_000, 120_000), '1m ago');
assert.equal(relativeAge(0), 'Unknown');
console.log('Queue inbox checks passed');

const open = { ...member, roles: ['author', 'reviewer'], mergeRequest: { ...member.mergeRequest, draft: false, hasConflicts: false } };
const assigned = { ...open, roles: ['assignee'], mergeRequest: { ...open.mergeRequest, id: 'assigned' } };
const merged = { ...open, mergeRequest: { ...open.mergeRequest, id: 'merged', state: 'merged', mergedAt: 100 } };
const grouped = { targetType: 'GROUP', group: { members: [open, assigned, merged] } };
const watched = { targetType: 'MERGE_REQUEST', role: 'watcher', mergeRequest: open };
const members = priorityMembers([grouped, watched, grouped]);
assert.equal(members.length, 2);
assert.equal(priorityMatches(members[0], 'author'), true);
assert.equal(priorityMatches(members[0], 'reviewer'), true);
assert.equal(priorityMatches(members[0], 'watcher'), true);
assert.equal(priorityMatches(members[1], 'watcher'), true);
assert.equal(priorityMatches(members[1], 'reviewer'), false);
assert.deepEqual(queueSections({ targetType: 'MERGE_REQUEST', mergeRequest: merged }), []);
assert.deepEqual(queueSections({ targetType: 'MERGE_REQUEST', mergeRequest: { ...open, reviewState: 'REVIEW_READY' } }), ['Needs your review', 'Waiting for review']);
assert.deepEqual(queueSections({ targetType: 'MERGE_REQUEST', mergeRequest: { ...open, roles: ['author'], reviewState: 'CREATED' } }), ['Waiting for review']);
assert.deepEqual(queueSections({ targetType: 'MERGE_REQUEST', mergeRequest: { ...assigned, reviewState: 'CREATED' } }), ['Watching']);
assert.equal(recentMergedMembers([merged], 100 + 30 * 24 * 60 * 60_000).length, 1);
assert.equal(recentMergedMembers([merged], 101 + 30 * 24 * 60 * 60_000).length, 0);
assert.equal(recentMergedMembers([open], 100).length, 0);
console.log('Priority tile checks passed');

const savedMembers = priorityMembers([grouped, watched], ['assigned', 'mr-42']);
assert.deepEqual(savedMembers.map((member) => member.mergeRequest.id), ['assigned', 'mr-42']);
assert.deepEqual(movePriorityMember(savedMembers, 'mr-42', 'assigned'), ['mr-42', 'assigned']);
assert.equal(movePriorityMember(savedMembers, 'mr-42', 'missing'), null);
assert.equal(movePriorityMember(savedMembers, 'mr-42', 'mr-42'), null);
const extra = { ...open, mergeRequest: { ...open.mergeRequest, id: 'new' } };
const withNew = priorityMembers([grouped, { targetType: 'MERGE_REQUEST', mergeRequest: extra }], ['assigned', 'gone', 'mr-42']);
assert.deepEqual(withNew.map((member) => member.mergeRequest.id), ['assigned', 'mr-42', 'new']);
assert.deepEqual(movePriorityMember(withNew, 'new', 'assigned'), ['new', 'assigned', 'mr-42']);
console.log('Saved priority order checks passed');

const dragMembers = ['a', 'b', 'c', 'd'].map((id) => ({ mergeRequest: { id } }));
assert.deepEqual(movePriorityMember(dragMembers, 'a', 'd', 'after'), ['b', 'c', 'd', 'a']);
assert.deepEqual(movePriorityMember(dragMembers, 'd', 'a', 'before'), ['d', 'a', 'b', 'c']);
assert.deepEqual(movePriorityMember(dragMembers, 'a', 'c', 'before'), ['b', 'a', 'c', 'd']);
assert.deepEqual(movePriorityMember(dragMembers, 'd', 'b', 'after'), ['a', 'b', 'd', 'c']);
assert.equal(movePriorityMember(dragMembers, 'b', 'c', 'before'), null);
assert.equal(movePriorityMember(dragMembers, 'b', 'a', 'after'), null);
assert.deepEqual(dragMembers.map((member) => member.mergeRequest.id), ['a', 'b', 'c', 'd']);
console.log('Priority drag-and-drop checks passed');

const reviewItem = { targetType: 'MERGE_REQUEST', mergeRequest: { ...open, reviewState: 'REVIEW_READY' } };
const sidebar = inboxSections([reviewItem, grouped], [merged]);
assert.equal(sidebar.length, 9);
assert.deepEqual(sidebar.find((section) => section.name === 'All open').items, [reviewItem, grouped]);
assert.deepEqual(sidebar.find((section) => section.name === 'Needs your review').items.map((item) => item.id), ['mr-42']);
assert.deepEqual(sidebar.find((section) => section.name === 'Review groups').items, [grouped]);
assert.equal(sidebar.find((section) => section.name === 'Drafts').items.length, 0);
assert.equal(sidebar.find((section) => section.name === 'Recently merged').items[0].mergeRequest, merged);
assert.equal(inboxSections([], []).every((section) => section.items.length === 0), true);
console.log('Inbox sidebar checks passed');

const blockedReviewer = { ...open, blocked: true, mergeRequest: { ...open.mergeRequest, id: 'blocked-review', providerUpdatedAt: 20 } };
const draftReviewer = { ...open, mergeRequest: { ...open.mergeRequest, id: 'draft-review', draft: true, providerUpdatedAt: 10 } };
const readyReviewer = { ...open, reviewState: 'CREATED', mergeRequest: { ...open.mergeRequest, id: 'ready-review', mergeStatus: 'mergeable', providerUpdatedAt: 30 } };
const reviewGroup = { targetType: 'GROUP', group: { members: [blockedReviewer, draftReviewer, readyReviewer, assigned, merged] } };
const reviewQueue = [reviewGroup, { targetType: 'MERGE_REQUEST', mergeRequest: blockedReviewer }];
const reviewSections = inboxSections(reviewQueue, [], { sort: 'updated' });
const sectionIds = (sections, name) => sections.find((section) => section.name === name).items.map((item) => item.id);
assert.deepEqual(sectionIds(reviewSections, 'Needs your review'), ['ready-review', 'blocked-review', 'draft-review']);
assert.deepEqual(new Set(sectionIds(reviewSections, 'Needs your review')), new Set(priorityMembers(reviewQueue)
  .filter((member) => priorityMatches(member, 'reviewer')).map((member) => member.mergeRequest.id)));
assert.deepEqual(sectionIds(reviewSections, 'Blocked'), ['blocked-review']);
assert.deepEqual(sectionIds(reviewSections, 'Drafts'), ['draft-review']);
assert.ok(sectionIds(reviewSections, 'Ready to merge').includes('ready-review'));
assert.deepEqual(sectionIds(inboxSections(reviewQueue, [], { sort: 'oldest' }), 'Needs your review'), ['draft-review', 'blocked-review', 'ready-review']);
const filteredGroup = { targetType: 'GROUP', group: { members: [blockedReviewer, { ...draftReviewer,
  mergeRequest: { ...draftReviewer.mergeRequest, title: 'Other task', repositoryPath: 'other/repo', labels: ['feature'] },
}] } };
for (const filters of [{ search: 'retry' }, { repository: 'payments/api' }, { label: 'bug' }]) {
  assert.deepEqual(sectionIds(inboxSections([filteredGroup], [], filters), 'Needs your review'), ['blocked-review']);
}
console.log('Reviewer inbox membership checks passed');

for (const reviewState of ['CREATED', 'ANALYSING', 'ANALYSED', 'REVIEW_READY']) {
  for (const mergeStatus of ['mergeable', 'MERGEABLE']) {
    assert.deepEqual(queueSections({ targetType: 'MERGE_REQUEST', mergeRequest: { ...readyReviewer, reviewState,
      mergeRequest: { ...readyReviewer.mergeRequest, mergeStatus },
    } }), ['Needs your review', 'Ready to merge']);
  }
}
for (const fields of [
  { draft: true }, { hasConflicts: true }, { blockingDiscussionsResolved: false },
  { state: 'closed' }, { state: 'merged' }, { mergeStatus: null }, { mergeStatus: 'unchecked' }, { mergeStatus: 'can_be_merged' },
  { mergeStatus: 'not_approved' }, { mergeStatus: 'ci_still_running' }, { mergeStatus: 'ci_must_pass' },
]) {
  const entry = { targetType: 'MERGE_REQUEST', mergeRequest: { ...readyReviewer, reviewState: 'REVIEW_READY',
    mergeRequest: { ...readyReviewer.mergeRequest, ...fields },
  } };
  assert.deepEqual(sectionIds(inboxSections([entry], []), 'Ready to merge'), []);
}
console.log('GitLab merge readiness checks passed');


const awaitingApproval = [84, 85, 86].map((number) => ({
  ...open, roles: ['author'], blocked: true,
  mergeRequest: { ...open.mergeRequest, id: `gitlab:17352:${number}`, repositoryId: '17352',
    repositoryPath: 'acme/runner', changeNumber: number, reviewerIds: ['61'], mergeStatus: 'not_approved' },
}));
const waitingQueue = [
  { targetType: 'GROUP', group: { members: awaitingApproval } },
  { targetType: 'MERGE_REQUEST', mergeRequest: awaitingApproval[0] },
];
const waitingSections = inboxSections(waitingQueue, []);
assert.deepEqual(sectionIds(waitingSections, 'Waiting for review'), awaitingApproval.map((member) => member.mergeRequest.id));
assert.deepEqual(sectionIds(waitingSections, 'Blocked'), awaitingApproval.map((member) => member.mergeRequest.id));
assert.deepEqual(sectionIds(waitingSections, 'Ready to merge'), []);
assert.equal(blockerLabel(awaitingApproval[0]), 'Awaiting approval');
for (const fields of [{ draft: true }, { state: 'merged' }, { state: 'closed' }, { mergeStatus: 'ci_must_pass' }, { mergeStatus: 'mergeable' }]) {
  const entry = { targetType: 'MERGE_REQUEST', mergeRequest: { ...awaitingApproval[0],
    mergeRequest: { ...awaitingApproval[0].mergeRequest, ...fields } } };
  assert.deepEqual(sectionIds(inboxSections([entry], []), 'Waiting for review'), []);
}
const someoneElsesMr = { targetType: 'MERGE_REQUEST', mergeRequest: { ...awaitingApproval[0], roles: ['reviewer'] } };
assert.deepEqual(sectionIds(inboxSections([someoneElsesMr], []), 'Waiting for review'), []);
assert.deepEqual(sectionIds(inboxSections([someoneElsesMr], []), 'Needs your review'), ['gitlab:17352:84']);
assert.deepEqual(sectionIds(inboxSections(waitingQueue, [], { search: '!85' }), 'Waiting for review'), ['gitlab:17352:85']);
assert.deepEqual(sectionIds(inboxSections(waitingQueue, [], { repository: 'other/repo' }), 'Waiting for review'), []);
console.log('Authored MRs awaiting approval remain in Waiting for review');

const overlapCases = [
  { id: 'approval', blocked: true, fields: { mergeStatus: 'not_approved' },
    sections: ['Needs your review', 'Blocked', 'Waiting for review', 'Watching'] },
  { id: 'draft', blocked: true, fields: { draft: true, hasConflicts: true, mergeStatus: 'not_approved' },
    sections: ['Needs your review', 'Blocked', 'Drafts', 'Watching'] },
  { id: 'ready', blocked: false, fields: { mergeStatus: 'mergeable' },
    sections: ['Needs your review', 'Ready to merge', 'Watching'] },
  { id: 'waiting', blocked: false, fields: { mergeStatus: 'checking' },
    sections: ['Needs your review', 'Waiting for review', 'Watching'] },
];
const overlapMembers = overlapCases.map(({ id, blocked, fields }, index) => ({
  ...open, roles: ['author', 'reviewer', 'assignee'], blocked,
  mergeRequest: { ...open.mergeRequest, id, providerUpdatedAt: index + 1, ...fields },
}));
const overlapGroup = { id: 'overlap-group', targetType: 'GROUP', group: { members: overlapMembers } };
const overlapQueue = [overlapGroup, ...overlapMembers.map((mergeRequest) => ({
  id: mergeRequest.mergeRequest.id, targetType: 'MERGE_REQUEST', mergeRequest,
}))];
for (const sort of ['manual', 'updated', 'oldest']) {
  const sections = inboxSections(overlapQueue, [merged], { sort });
  for (const { id, sections: expected } of overlapCases) {
    assert.deepEqual(sections.filter((section) => section.items.some((item) => item.id === id))
      .map((section) => section.name), ['All open', ...expected]);
    for (const section of sections) assert.ok(section.items.filter((item) => item.id === id).length <= 1);
  }
  assert.deepEqual(sectionIds(sections, 'Review groups'), ['overlap-group']);
  assert.deepEqual(sectionIds(sections, 'Recently merged'), ['merged']);
}
for (const filters of [
  { search: 'retry' }, { repository: 'payments/api' }, { label: 'bug' },
  { search: 'retry', repository: 'payments/api', label: 'bug' },
]) {
  const sections = inboxSections(overlapQueue, [], filters).slice(1, 7);
  for (const { id, sections: expected } of overlapCases) {
    assert.deepEqual(sections.filter((section) => section.items.some((item) => item.id === id))
      .map((section) => section.name), expected);
  }
}
for (const filters of [{ search: 'missing' }, { repository: 'other/repo' }, { label: 'feature' }]) {
  assert.ok(inboxSections(overlapQueue, [], filters).slice(1, 7).every((section) => section.items.length === 0));
}
const watchedReady = { targetType: 'MERGE_REQUEST', role: 'watcher', mergeRequest: {
  ...readyReviewer, roles: ['reviewer'],
} };
assert.deepEqual(inboxSections([watchedReady], []).filter((section) => section.items.length)
  .map((section) => section.name), ['All open', 'Needs your review', 'Ready to merge', 'Watching']);
console.log('All inbox sections preserve overlapping membership, filtering, sorting, and deduplication');
