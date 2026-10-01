import assert from 'node:assert/strict';
import test from 'node:test';
import { analysisEntry, analysisHref } from './analysis-navigation.mjs';

test('builds prefilled analysis links', () => {
  assert.equal(analysisHref('gitlab:12:34'), '/analyze?mergeRequestId=gitlab%3A12%3A34');
  assert.equal(
    analysisHref('gitlab:12:34', ['security', 'reliability']),
    '/analyze?mergeRequestId=gitlab%3A12%3A34&personas=security%2Creliability',
  );
});

test('selects the action for each review state', () => {
  assert.deepEqual(analysisEntry('CREATED', 'gitlab:12:34'), {
    href: '/analyze?mergeRequestId=gitlab%3A12%3A34',
    label: 'Run analysis',
  });
  assert.deepEqual(analysisEntry('ANALYSING', 'gitlab:12:34'), {
    href: '/history?changeRequestId=gitlab%3A12%3A34',
    label: 'View analysis',
  });
  assert.equal(analysisEntry('ANALYSED', 'gitlab:12:34').label, 'Run again');
  assert.equal(analysisEntry('MERGED', 'gitlab:12:34'), null);
});

test('retry link preserves the harness and selected model', () => {
  const href = analysisHref('gitlab:12:34', ['security'], { harness: 'copilot', model: 'available-model' });
  const params = new URL(href, 'https://arnold.example').searchParams;
  assert.equal(params.get('harness'), 'copilot');
  assert.equal(params.get('model'), 'available-model');
});
