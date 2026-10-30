export function groupDiscussions(discussions) {
  const groups = new Map();
  discussions.forEach((discussion) => {
    const key = discussion.individualNote ? `note:${discussion.id}` : discussion.threadId || `note:${discussion.id}`;
    const thread = groups.get(key) || { id: key, discussionId: discussion.threadId, individualNote: discussion.individualNote, notes: [], position: null };
    thread.notes.push(discussion);
    thread.position ||= discussion.position;
    groups.set(key, thread);
  });
  return [...groups.values()].map((thread) => ({
    ...thread,
    notes: thread.notes.sort((left, right) => left.createdAt - right.createdAt),
    resolvable: thread.notes.some((note) => note.resolvable),
    resolved: thread.notes.some((note) => note.resolved),
  })).sort((left, right) => left.notes[0].createdAt - right.notes[0].createdAt);
}
