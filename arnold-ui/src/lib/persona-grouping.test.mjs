import assert from 'node:assert/strict';
import test from 'node:test';
import { GENERAL_PERSONA_KEY, groupByPersona, threadPersona } from './persona-grouping.mjs';

const thread = (id, root) => ({ id, notes: [{ reviewSource: 'human', ...root }, { reviewSource: 'human' }] });
const annotation = (value) => ({ side: 'additions', lineNumber: 1, metadata: { kind: 'thread', thread: value } });
const item = (name, threads, stats = { additions: 2, deletions: 1 }) => ({ id: name, fileDiff: { name }, threads, annotations: threads.map(annotation), stats });

test('classifies threads by their root note', () => {
  assert.deepEqual(threadPersona(thread('t1', { reviewSource: 'persona', personaId: 'netra', personaName: 'Netra' })), { key: 'persona:netra', personaId: 'netra', name: 'Netra' });
  assert.equal(threadPersona(thread('t2', { reviewSource: 'ai' })).key, GENERAL_PERSONA_KEY);
  assert.equal(threadPersona(thread('t3', {})), null);
  assert.equal(threadPersona(thread('t4', { reviewSource: 'persona' })), null);
});

test('groups files per persona, keeps only that persona threads and puts general Arnold last', () => {
  const netraA = thread('a1', { reviewSource: 'persona', personaId: 'netra', personaName: 'Netra' });
  const general = thread('a2', { reviewSource: 'ai' });
  const human = thread('a3', {});
  const netraB = thread('b1', { reviewSource: 'persona', personaId: 'netra', personaName: 'Netra' });
  const sai = thread('b2', { reviewSource: 'persona', personaId: 'sai' });
  const groups = groupByPersona([item('A.java', [netraA, general, human]), item('B.java', [netraB, sai]), item('C.java', [human])]);

  assert.deepEqual(groups.map((group) => [group.key, group.name, group.threadCount, group.fileCount]), [
    ['persona:netra', 'Netra', 2, 2],
    ['persona:sai', 'sai', 1, 1],
    [GENERAL_PERSONA_KEY, 'Arnold', 1, 1],
  ]);
  const [first] = groups[0].items;
  assert.equal(first.id, 'persona:persona:netra:A.java');
  assert.equal(first.sourceId, 'A.java');
  assert.deepEqual(first.threads.map((value) => value.id), ['a1']);
  assert.deepEqual(first.annotations.map((value) => value.metadata.thread.id), ['a1']);
  assert.deepEqual(groups[0].stats, { additions: 4, deletions: 2 });
});

test('keeps a line composer only on the persona copy where it was opened', () => {
  const persona = (id, personaId) => thread(id, { reviewSource: 'persona', personaId });
  const source = item('A.java', [persona('a1', 'netra'), persona('a2', 'sai')]);
  source.annotations.push({ side: 'additions', lineNumber: 3, metadata: { kind: 'composer', viewItemId: 'persona:persona:sai:A.java' } });
  const groups = groupByPersona([source]);
  const composers = (key) => groups.find((group) => group.key === key).items[0].annotations.filter((value) => value.metadata.kind === 'composer').length;
  assert.equal(composers('persona:netra'), 0);
  assert.equal(composers('persona:sai'), 1);
});

test('collects outdated persona and Arnold threads, including personas with no thread on the current diff', () => {
  const current = thread('c1', { reviewSource: 'persona', personaId: 'netra' });
  const oldNetra = thread('o1', { reviewSource: 'persona', personaId: 'netra' });
  const oldKozo = thread('o2', { reviewSource: 'persona', personaId: 'kozo', personaName: 'Kōzō' });
  const oldGeneral = thread('o3', { reviewSource: 'ai' });
  const groups = groupByPersona([item('A.java', [current])], [oldNetra, oldKozo, oldGeneral, thread('o4', {})]);

  assert.deepEqual(groups.map((group) => [group.key, group.threadCount, group.outdatedThreads.map((value) => value.id), group.fileCount]), [
    ['persona:netra', 1, ['o1'], 1],
    ['persona:kozo', 0, ['o2'], 0],
    [GENERAL_PERSONA_KEY, 0, ['o3'], 0],
  ]);
});
