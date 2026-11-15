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
import { buildFileTree, orderFileTreeItems, toggleViewedPaths } from './file-tree.mjs';

test('builds a folders-first alphabetical directory tree', () => {
  const item = (name) => ({ fileDiff: { name } });
  const tree = buildFileTree([item('README.md'), item('src/z.js'), item('src/lib/a.js')]);

  assert.deepEqual(tree.map((node) => node.name), ['src', 'README.md']);
  assert.deepEqual(tree[0].children.map((node) => node.name), ['lib', 'z.js']);
  assert.equal(tree[0].children[0].children[0].path, 'src/lib/a.js');
  assert.deepEqual(orderFileTreeItems([item('README.md'), item('src/z.js'), item('src/lib/a.js')])
    .map((entry) => entry.fileDiff.name), ['src/lib/a.js', 'src/z.js', 'README.md']);
});

test('compacts directories without direct file changes', () => {
  const tree = buildFileTree([{ fileDiff: { name: 'src/main/java/App.java' } }]);

  assert.equal(tree[0].name, 'src/main/java');
  assert.equal(tree[0].children[0].name, 'App.java');
});

test('marks a chapter viewed and clears it when every file is already viewed', () => {
  assert.deepEqual([...toggleViewedPaths(new Set(['a.js']), ['a.js', 'b.js'])], ['a.js', 'b.js']);
  assert.deepEqual([...toggleViewedPaths(new Set(['a.js', 'b.js', 'c.js']), ['a.js', 'b.js'])], ['c.js']);
});
