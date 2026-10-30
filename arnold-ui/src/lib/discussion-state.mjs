export function replaceDraftDiscussions(discussions, drafts) {
  return [...(discussions || []).filter((item) => item.state !== 'draft'), ...drafts];
}
