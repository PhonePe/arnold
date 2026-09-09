import assert from 'node:assert/strict';
import test from 'node:test';
import { explainReviewEffort } from './review-brief.mjs';

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
