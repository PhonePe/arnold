export const GENERAL_PERSONA_KEY = 'arnold';

/** Classifies a thread by its root note: a review persona, general Arnold AI, or null for human threads. */
export function threadPersona(thread) {
  const root = thread?.notes?.[0];
  if (!root) return null;
  if (root.reviewSource === 'persona' && root.personaId) {
    return { key: `persona:${root.personaId}`, personaId: root.personaId, name: root.personaName || root.personaId };
  }
  if (root.reviewSource === 'ai') return { key: GENERAL_PERSONA_KEY, personaId: null, name: 'Arnold' };
  return null;
}

/**
 * Splits decorated diff items into one section per persona. Each section holds clones of the files that persona
 * commented on, keeping only that persona's threads. General Arnold comments come last; human threads are dropped.
 */
export function groupByPersona(items) {
  const groups = new Map();
  for (const item of items) {
    const byPersona = new Map();
    for (const thread of item.threads || []) {
      const persona = threadPersona(thread);
      if (!persona) continue;
      if (!groups.has(persona.key)) groups.set(persona.key, { ...persona, items: [], threadCount: 0 });
      const personaThreads = byPersona.get(persona.key) || new Set();
      personaThreads.add(thread.id);
      byPersona.set(persona.key, personaThreads);
    }
    for (const [key, threadIds] of byPersona) {
      const group = groups.get(key);
      group.threadCount += threadIds.size;
      const id = `persona:${key}:${item.id}`;
      group.items.push({
        ...item,
        id,
        sourceId: item.sourceId || item.id,
        persona: { key, name: group.name },
        threads: item.threads.filter((thread) => threadIds.has(thread.id)),
        annotations: (item.annotations || []).filter(({ metadata }) => metadata?.kind === 'thread'
          ? threadIds.has(metadata.thread?.id)
          : metadata?.kind !== 'composer' || metadata.viewItemId === id),
      });
    }
  }
  return [...groups.values()].map((group) => ({
    ...group,
    fileCount: group.items.length,
    stats: group.items.reduce((sum, item) => ({
      additions: sum.additions + (item.stats?.additions || 0),
      deletions: sum.deletions + (item.stats?.deletions || 0),
    }), { additions: 0, deletions: 0 }),
  })).sort((left, right) => (left.key === GENERAL_PERSONA_KEY) - (right.key === GENERAL_PERSONA_KEY)
    || right.threadCount - left.threadCount || left.name.localeCompare(right.name));
}

export const personaInitials = (name) => (name || '?').split(/[\s_-]+/).filter(Boolean).slice(0, 2).map((part) => part[0]).join('').toUpperCase();
