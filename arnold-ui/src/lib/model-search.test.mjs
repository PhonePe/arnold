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
import { highlightSegments, searchModels, tokenize } from './model-search.mjs';

const models = [
  { id: 'openai/gpt-5', name: 'GPT-5' },
  { id: 'openai/gpt-5-mini', name: 'GPT-5 mini', isDefault: true },
  { id: 'openai/gpt-5.6-luna', name: 'gpt-5.6-luna' },
  { id: 'openai/omni-moderation-latest', name: 'omni-moderation-latest' },
  { id: 'anthropic/claude-sonnet-4.5', name: 'Claude Sonnet 4.5' },
  { id: 'openai/gpt-4o', name: 'GPT4o' },
];
const ids = (query) => searchModels(models, query).map((model) => model.id);

test('tokenizes on separators, camel humps and digit boundaries', () => {
  assert.deepEqual(tokenize('openai/gpt-5.6-luna'), ['openai', 'gpt', '5', '6', 'luna']);
  assert.deepEqual(tokenize('GPT4o'), ['gpt', '4', 'o']);
  assert.deepEqual(tokenize('claudeSonnet'), ['claude', 'sonnet']);
});

test('requires every query token to prefix a model token', () => {
  assert.deepEqual(ids('gpt 5 mi'), ['openai/gpt-5-mini']);
  assert.deepEqual(ids('luna'), ['openai/gpt-5.6-luna']);
  assert.deepEqual(ids('omni'), ['openai/omni-moderation-latest']);
  assert.deepEqual(ids('mod lat'), ['openai/omni-moderation-latest']);
  assert.deepEqual(ids('zzz'), []);
});

test('returns everything in original order for an empty query', () => {
  assert.deepEqual(ids('  '), models.map((model) => model.id));
});

test('ranks exact id, then in-order, then default, then fewer tokens', () => {
  assert.equal(ids('openai/gpt-5')[0], 'openai/gpt-5');
  assert.deepEqual(ids('gpt 5'), ['openai/gpt-5-mini', 'openai/gpt-5', 'openai/gpt-5.6-luna']);
  assert.deepEqual(ids('5'), ['openai/gpt-5-mini', 'openai/gpt-5', 'openai/gpt-5.6-luna', 'anthropic/claude-sonnet-4.5']);
});

test('highlights matched prefixes', () => {
  assert.deepEqual(highlightSegments('openai/gpt-5-mini', 'gpt mi'), [
    { text: 'openai/', match: false },
    { text: 'gpt', match: true },
    { text: '-5-', match: false },
    { text: 'mi', match: true },
    { text: 'ni', match: false },
  ]);
  assert.deepEqual(highlightSegments('GPT-5', ''), [{ text: 'GPT-5', match: false }]);
});
