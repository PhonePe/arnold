const badges = {
  applied: ['Applied', 'success'],
  partially_applied: ['Partially applied', 'warning'],
  ignored: ['Ignored', 'error'],
  not_applicable: ['Not applicable', 'default'],
};

export function commentAssessment(note, headSha) {
  const badge = badges[note.acceptanceStatus];
  if (!badge) return null;
  return {
    label: `AI · ${badge[0]}`,
    variant: badge[1],
    stale: Boolean(headSha && note.assessedSha && headSha.toLowerCase() !== note.assessedSha.toLowerCase()),
    unverified: Boolean(headSha && !note.assessedSha),
  };
}
