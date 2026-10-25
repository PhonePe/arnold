function parsePersonas(value) {
  if (!value) return [];
  try {
    const parsed = JSON.parse(value);
    if (!Array.isArray(parsed)) return [];
    return parsed
      .filter((persona) => persona && typeof persona.id === 'string' && persona.id && typeof persona.name === 'string' && persona.name)
      .map(({ id, name }) => ({ id, name }));
  } catch {
    return [];
  }
}

export const configuredPersonas = parsePersonas(process.env.NEXT_PUBLIC_ARNOLD_PERSONAS_JSON);

export const providerCapabilities = {
  GITLAB: {
    provider: 'GITLAB',
    label: 'GitLab',
    changeLabel: 'MR',
    canResolve: true,
    canReadDiscussions: true,
    canCreateDiscussions: true,
    canStartAnalysis: process.env.NEXT_PUBLIC_ANALYSIS_EXECUTION_ENABLED === 'true' && configuredPersonas.length > 0,
    canReadDiff: false,
    canMerge: false,
  },
  GITHUB: {
    provider: 'GITHUB',
    label: 'GitHub',
    changeLabel: 'PR',
    canResolve: true,
    canReadDiscussions: true,
    canCreateDiscussions: true,
    canStartAnalysis: process.env.NEXT_PUBLIC_ANALYSIS_EXECUTION_ENABLED === 'true' && configuredPersonas.length > 0,
    canReadDiff: false,
    canMerge: false,
  },
};
