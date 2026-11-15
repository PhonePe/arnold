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
import { explainReviewEffort, formatReviewBrief } from './review-brief.mjs';

const paragraph = (value) => ({ type: 'paragraph', children: [{ type: 'text', value }] });

test('hides unexplained effort numbers and preserves MR-specific explanations', () => {
  for (const label of ['Review Efforts (3/5)', 'Review effort: 3 / 5', 'Review effort: 3/5 — moderate']) {
    const tree = { children: [paragraph(label), paragraph('**not** addressed in this MR.')] };
    explainReviewEffort()(tree);
    assert.equal(tree.children[0].children[0].value, 'Review effort');
    assert.match(tree.children[1].children[0].value, /rating is hidden/);
    assert.equal(tree.children[2].children[0].value, '**not** addressed in this MR.');
  }
  for (const children of [
    [paragraph('Review effort: 3/5 — Changes span the API and persistence layer.')],
    [paragraph('Review effort: 3/5'), paragraph('Basis: Changes span the API and persistence layer.')],
    [{ ...paragraph('Review effort: 3/5'), type: 'heading', depth: 3 }, paragraph('Changes span the API and persistence layer.')],
  ]) {
    const tree = { children };
    const original = structuredClone(tree);
    explainReviewEffort()(tree);
    assert.deepEqual(tree, original);
  }
});

test('restores overview sections without splitting inline emphasis and removes the old scale', () => {
  const strong = (value) => ({ type: 'strong', children: [{ type: 'text', value }] });
  const inline = { type: 'paragraph', children: [strong('not'), { type: 'text', value: ' addressed in this MR.' }] };
  const tree = { children: [
    { type: 'paragraph', children: [strong('Tests'), { type: 'break' }, { type: 'text', value: 'Pagination tests added.' }] },
    inline,
    { type: 'paragraph', children: [strong('Review effort: 3/5')] },
    paragraph('Basis: Pagination boundaries need inspection.'),
    paragraph('Scale: 1 trivial · 2 small · 3 medium · 4 large · 5 very large.'),
  ] };
  formatReviewBrief()(tree);
  explainReviewEffort()(tree);
  assert.equal(tree.children[0].type, 'heading');
  assert.equal(tree.children[1].children[0].value, 'Pagination tests added.');
  assert.deepEqual(tree.children[2], inline);
  assert.equal(tree.children[3].type, 'heading');
  assert.equal(tree.children[3].children[0].value, 'Review effort: 3/5');
  assert.equal(tree.children.length, 5);
});
