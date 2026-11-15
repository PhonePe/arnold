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
