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

// Splits on non-alphanumerics, lower→upper camel humps and letter/digit boundaries.
// Each token keeps its offset in the source so matches can be highlighted.
const TOKEN = /[A-Z]+(?![a-z])|[A-Z]?[a-z]+|\d+/g;

export function tokenizeWithOffsets(text) {
  const source = String(text ?? '');
  return [...source.matchAll(TOKEN)].map((match) => ({ token: match[0].toLowerCase(), start: match.index }));
}

export function tokenize(text) {
  return tokenizeWithOffsets(text).map((item) => item.token);
}

function edgeNgrams(tokens) {
  const grams = new Set();
  for (const token of tokens) {
    for (let length = 1; length <= token.length; length += 1) grams.add(token.slice(0, length));
  }
  return grams;
}

function leafId(id) {
  const index = id.lastIndexOf('/');
  return index === -1 ? id : id.slice(index + 1);
}

export function buildModelIndex(models) {
  return (models || []).map((model, position) => {
    const id = String(model.id ?? '');
    const name = String(model.name ?? '');
    const idTokens = tokenize(id);
    const nameTokens = tokenize(name);
    const tokens = [...idTokens, ...nameTokens, id.toLowerCase()];
    return {
      model,
      position,
      grams: edgeNgrams(tokens),
      sequences: [idTokens, nameTokens, tokenize(leafId(id))],
      tokenCount: new Set([...idTokens, ...nameTokens]).size,
    };
  });
}

function inOrderFromStart(queryTokens, sequence) {
  return queryTokens.length <= sequence.length
    && queryTokens.every((token, index) => sequence[index].startsWith(token));
}

// A model matches when every query token is a prefix (edge n-gram) of one of its tokens.
// Results are ranked: exact id > in-order from start > default model > fewer tokens > original order.
export function searchModels(models, query, index = buildModelIndex(models)) {
  const raw = String(query ?? '').trim().toLowerCase();
  const queryTokens = tokenize(raw);
  if (!queryTokens.length) return index.map((entry) => entry.model);
  return index
    .filter((entry) => queryTokens.every((token) => entry.grams.has(token)))
    .map((entry) => ({
      entry,
      exact: String(entry.model.id).toLowerCase() === raw ? 0 : 1,
      ordered: entry.sequences.some((sequence) => inOrderFromStart(queryTokens, sequence)) ? 0 : 1,
      fallback: entry.model.isDefault ? 0 : 1,
    }))
    .sort((a, b) => a.exact - b.exact
      || a.ordered - b.ordered
      || a.fallback - b.fallback
      || a.entry.tokenCount - b.entry.tokenCount
      || a.entry.position - b.entry.position)
    .map((item) => item.entry.model);
}

// Splits text into segments, flagging the token prefixes matched by the query.
export function highlightSegments(text, query) {
  const source = String(text ?? '');
  const queryTokens = tokenize(query);
  if (!queryTokens.length || !source) return [{ text: source, match: false }];
  const ranges = [];
  for (const { token, start } of tokenizeWithOffsets(source)) {
    const length = Math.max(0, ...queryTokens.filter((candidate) => token.startsWith(candidate)).map((candidate) => candidate.length));
    if (length) ranges.push([start, start + length]);
  }
  const segments = [];
  let cursor = 0;
  for (const [start, end] of ranges) {
    if (start > cursor) segments.push({ text: source.slice(cursor, start), match: false });
    segments.push({ text: source.slice(start, end), match: true });
    cursor = end;
  }
  if (cursor < source.length) segments.push({ text: source.slice(cursor), match: false });
  return segments;
}
