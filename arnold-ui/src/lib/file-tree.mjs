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

export function buildFileTree(items) {
  const root = { children: new Map() };
  for (const item of items) {
    const parts = item.fileDiff.name.split('/');
    let parent = root;
    parts.forEach((name, index) => {
      const isFile = index === parts.length - 1;
      if (!parent.children.has(name)) parent.children.set(name, isFile
        ? { type: 'file', name, path: item.fileDiff.name, item }
        : { type: 'directory', name, path: parts.slice(0, index + 1).join('/'), children: new Map() });
      parent = parent.children.get(name);
    });
  }
  return finalize(root.children);
}

export function orderFileTreeItems(items) {
  const ordered = [];
  const visit = (nodes) => nodes.forEach((node) => node.type === 'directory' ? visit(node.children) : ordered.push(node.item));
  visit(buildFileTree(items));
  return ordered;
}

export function toggleViewedPaths(viewed, paths) {
  const next = new Set(viewed);
  const allViewed = paths.every((path) => next.has(path));
  paths.forEach((path) => allViewed ? next.delete(path) : next.add(path));
  return next;
}

function finalize(children) {
  return [...children.values()]
    .map((node) => node.type === 'directory' ? compact({ ...node, children: finalize(node.children) }) : node)
    .sort((left, right) => left.type === right.type ? left.name.localeCompare(right.name) : left.type === 'directory' ? -1 : 1);
}

function compact(node) {
  if (node.children.length !== 1 || node.children[0].type !== 'directory') return node;
  const child = node.children[0];
  return compact({ ...child, name: `${node.name}/${child.name}` });
}
