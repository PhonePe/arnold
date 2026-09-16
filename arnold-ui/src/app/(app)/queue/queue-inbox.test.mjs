import assert from 'node:assert/strict';
import { queueSection, queueMatches, blockerLabel, relativeAge, reviewLabel, priorityMembers, priorityMatches, recentMergedMembers, movePriorityMember, inboxSections } from './queue-inbox.mjs';
const member = { roles: ['reviewer'], reviewState: 'ANALYSED', blocked: false, mergeRequest: { id: 'mr-42', state: 'opened', title: 'Fix retry', changeNumber: 42, repositoryPath: 'payments/api', labels: ['bug'], reviewerNames: ['Ana'], hasConflicts: false, blockingDiscussionsResolved: true } };
const item = { targetType: 'MERGE_REQUEST', mergeRequest: member };
assert.equal(queueSection(item), 'Needs your review');
assert.equal(reviewLabel('ANALYSED'), 'Analysis finished · view result');
assert.equal(queueMatches(item, '!42', 'payments/api', 'bug'), true);
assert.equal(queueMatches(item, 'retry', 'other/repo', ''), false);
member.mergeRequest.hasConflicts = true;
member.reviewState = 'READY_FOR_MERGE';
assert.equal(queueSection(item), 'Blocked');
assert.equal(blockerLabel(member), 'Merge conflicts');
member.mergeRequest.draft = true;
assert.equal(queueSection(item), 'Drafts');
const group = { targetType: 'GROUP', group: { name: 'Rollout', members: [member, { ...member, mergeRequest: { ...member.mergeRequest, repositoryPath: 'other/repo', labels: ['feature'] } }] } };
assert.equal(queueSection(group), 'Review groups');
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
assert.equal(queueSection({ targetType: 'MERGE_REQUEST', mergeRequest: merged }), null);
assert.equal(queueSection({ targetType: 'MERGE_REQUEST', mergeRequest: { ...open, reviewState: 'READY_FOR_MERGE' } }), 'Ready to merge');
assert.equal(queueSection({ targetType: 'MERGE_REQUEST', mergeRequest: { ...open, roles: ['author'], reviewState: 'CREATED' } }), 'Waiting for review');
assert.equal(queueSection({ targetType: 'MERGE_REQUEST', mergeRequest: { ...assigned, reviewState: 'CREATED' } }), 'Watching');
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
const readyReviewer = { ...open, mergeRequest: { ...open.mergeRequest, id: 'ready-review', providerUpdatedAt: 30 } };
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
