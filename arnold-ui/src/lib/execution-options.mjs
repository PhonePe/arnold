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

function defaultModelId(harness) {
  const models = harness?.models || [];
  return (models.find((model) => model.isDefault) || models[0])?.id || '';
}

// Keeps the previous harness/model when still valid, otherwise falls back to the first
// available harness and its default (or first) model.
export function pickDefaultExecution(options, previous = {}) {
  const list = options || [];
  const previousHarness = list.find((option) => option.id === previous.harness);
  const harness = previousHarness?.available
    ? previousHarness
    : list.find((option) => option.available) || previousHarness || list[0];
  if (!harness) return { harness: previous.harness || '', model: previous.model || '' };
  if (!harness.available) return { harness: harness.id, model: '' };
  const keepModel = harness.id === previous.harness && harness.models?.some((model) => model.id === previous.model);
  return { harness: harness.id, model: keepModel ? previous.model : defaultModelId(harness) };
}

// Returns a short reason why the selection cannot run, or null when it is ready.
export function executionBlocker(options, { harness, model } = {}) {
  if (!options) return 'Loading harnesses…';
  if (!options.length) return 'No harnesses available';
  const selected = options.find((option) => option.id === harness);
  if (!selected) return 'Select a harness';
  if (!selected.available) return `${selected.name} is unavailable`;
  if (selected.models?.length && !selected.models.some((option) => option.id === model)) return 'Select a model';
  return null;
}
