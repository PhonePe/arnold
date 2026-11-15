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


import assert from 'node:assert/strict';
import test from 'node:test';
import { executionBlocker, pickDefaultExecution } from './execution-options.mjs';

const copilot = { id: 'copilot', name: 'GitHub Copilot', available: false, message: 'Not connected', models: [] };
const opencode = { id: 'opencode', name: 'OpenCode', available: true, models: [
  { id: 'openai/gpt-5', name: 'GPT-5' },
  { id: 'openai/gpt-5-mini', name: 'GPT-5 mini', isDefault: true },
] };

test('selects the first available harness and its default model', () => {
  assert.deepEqual(pickDefaultExecution([copilot, opencode]), { harness: 'opencode', model: 'openai/gpt-5-mini' });
  assert.deepEqual(pickDefaultExecution([copilot, opencode], { harness: 'copilot', model: '' }), { harness: 'opencode', model: 'openai/gpt-5-mini' });
});

test('keeps a still-valid previous selection', () => {
  assert.deepEqual(pickDefaultExecution([copilot, opencode], { harness: 'opencode', model: 'openai/gpt-5' }), { harness: 'opencode', model: 'openai/gpt-5' });
  assert.deepEqual(pickDefaultExecution([copilot, opencode], { harness: 'opencode', model: 'gone' }), { harness: 'opencode', model: 'openai/gpt-5-mini' });
});

test('falls back to the first model without a default', () => {
  const harness = { ...opencode, models: opencode.models.map(({ isDefault, ...model }) => model) };
  assert.equal(pickDefaultExecution([harness]).model, 'openai/gpt-5');
});

test('keeps an unavailable harness only when nothing is available', () => {
  assert.deepEqual(pickDefaultExecution([copilot], { harness: 'copilot', model: 'x' }), { harness: 'copilot', model: '' });
  assert.deepEqual(pickDefaultExecution([]), { harness: '', model: '' });
});

test('explains why the selection is not runnable', () => {
  assert.equal(executionBlocker(null, {}), 'Loading harnesses…');
  assert.equal(executionBlocker([], {}), 'No harnesses available');
  assert.equal(executionBlocker([copilot, opencode], { harness: 'nope' }), 'Select a harness');
  assert.equal(executionBlocker([copilot, opencode], { harness: 'copilot' }), 'GitHub Copilot is unavailable');
  assert.equal(executionBlocker([copilot, opencode], { harness: 'opencode', model: '' }), 'Select a model');
  assert.equal(executionBlocker([copilot, opencode], { harness: 'opencode', model: 'openai/gpt-5' }), null);
  assert.equal(executionBlocker([{ ...opencode, models: [] }], { harness: 'opencode', model: '' }), null);
});
