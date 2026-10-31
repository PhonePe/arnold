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

import { parsePatchFiles } from '@pierre/diffs';
import { annotationLocation } from './diff-lines.mjs';

export const FOCUS_CONTEXT_LINES = 4;

function hunkRows(fileDiff, hunk) {
  const rows = [];
  let oldLine = hunk.deletionStart || 1;
  let newLine = hunk.additionStart || 1;
  for (const block of hunk.hunkContent || []) {
    if (block.type === 'context') {
      for (let index = 0; index < block.lines; index += 1) {
        rows.push({ type: ' ', text: fileDiff.additionLines[block.additionLineIndex + index], oldLine, newLine });
        oldLine += 1;
        newLine += 1;
      }
      continue;
    }
    for (let index = 0; index < block.deletions; index += 1) {
      rows.push({ type: '-', text: fileDiff.deletionLines[block.deletionLineIndex + index], oldLine, newLine });
      oldLine += 1;
    }
    for (let index = 0; index < block.additions; index += 1) {
      rows.push({ type: '+', text: fileDiff.additionLines[block.additionLineIndex + index], oldLine, newLine });
      newLine += 1;
    }
  }
  return rows;
}

function threadSpan(thread) {
  const range = thread.position?.lineRange;
  const location = annotationLocation(thread.position);
  if (!location) return null;
  const old = location.side === 'deletions';
  const start = range ? (old ? range.startOldLine : range.startNewLine) : null;
  const end = range ? (old ? range.endOldLine : range.endNewLine) : null;
  return { old, start: Math.min(start ?? location.lineNumber, location.lineNumber), end: Math.max(end ?? location.lineNumber, location.lineNumber) };
}

const touches = (row, span) => {
  const line = span.old ? (row.type !== '+' ? row.oldLine : null) : (row.type !== '-' ? row.newLine : null);
  return line != null && line >= span.start && line <= span.end;
};

function hunkText(rows) {
  const oldCount = rows.filter((row) => row.type !== '+').length;
  const newCount = rows.filter((row) => row.type !== '-').length;
  const oldStart = oldCount ? rows.find((row) => row.type !== '+').oldLine : rows[0].oldLine - 1;
  const newStart = newCount ? rows.find((row) => row.type !== '-').newLine : rows[0].newLine - 1;
  const body = rows.map((row) => `${row.type}${row.text.endsWith('\n') ? row.text : `${row.text}\n`}`).join('');
  return `@@ -${oldStart},${oldCount} +${newStart},${newCount} @@\n${body}`;
}

/**
 * Narrows a parsed file diff to the lines around the given threads, keeping real line numbers so annotations still
 * land on the right rows. Returns the original diff when no thread is anchored to a line inside it.
 */
export function focusFileDiff(fileDiff, threads, context = FOCUS_CONTEXT_LINES) {
  const spans = (threads || []).map(threadSpan).filter(Boolean);
  if (!spans.length || !fileDiff?.hunks?.length) return fileDiff;
  const hunks = [];
  for (const hunk of fileDiff.hunks) {
    const rows = hunkRows(fileDiff, hunk);
    const ranges = [];
    rows.forEach((row, index) => {
      if (!spans.some((span) => touches(row, span))) return;
      const start = Math.max(0, index - context);
      const end = Math.min(rows.length - 1, index + context);
      const last = ranges.at(-1);
      if (last && start <= last.end + 1) last.end = Math.max(last.end, end);
      else ranges.push({ start, end });
    });
    for (const range of ranges) hunks.push(hunkText(rows.slice(range.start, range.end + 1)));
  }
  if (!hunks.length) return fileDiff;
  const oldName = fileDiff.prevName || fileDiff.name;
  const header = `diff --git a/${oldName} b/${fileDiff.name}\n--- ${fileDiff.type === 'new' ? '/dev/null' : `a/${oldName}`}\n+++ ${fileDiff.type === 'deleted' ? '/dev/null' : `b/${fileDiff.name}`}\n`;
  const focused = parsePatchFiles(`${header}${hunks.join('')}`, undefined, true)[0]?.files[0];
  if (!focused) return fileDiff;
  return { ...fileDiff, ...focused, name: fileDiff.name, prevName: fileDiff.prevName, type: fileDiff.type, cacheKey: `${fileDiff.cacheKey || fileDiff.name}:focus:${hunks.length}:${spans.map((span) => span.start).join(',')}` };
}
