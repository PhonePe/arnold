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

const detailsPattern = /<details(?:\s[^>]*)?>\s*<summary>([\s\S]*?)<\/summary>([\s\S]*?)<\/details>/gi;
const suggestionPattern = /^[ \t]*```suggestion[^\n]*\n[\s\S]*?^[ \t]*```[ \t]*$/gm;

export const withoutSuggestionBlocks = (source) => source.replace(suggestionPattern, '').replace(/\n{3,}/g, '\n\n').trim();

export function normalizeGitLabInlineHtml(source) {
  return source
    .replace(/<a\s+[^>]*href=(["'])(.*?)\1[^>]*>([\s\S]*?)<\/a>/gi, (_, quote, href, label) => `[${normalizeGitLabInlineHtml(label)}](${href})`)
    .replace(/<(strong|b)>([\s\S]*?)<\/\1>/gi, '**$2**')
    .replace(/<(em|i)>([\s\S]*?)<\/\1>/gi, '*$2*')
    .replace(/<code>([\s\S]*?)<\/code>/gi, '`$1`')
    .replace(/<br\s*\/?>/gi, '  \n')
    .replace(/<[^>]+>/g, '');
}

export function splitMarkdownDetails(source) {
  const parts = [];
  let cursor = 0;
  for (const match of source.matchAll(detailsPattern)) {
    if (match.index > cursor) parts.push({ type: 'markdown', value: source.slice(cursor, match.index) });
    parts.push({ type: 'details', summary: normalizeGitLabInlineHtml(match[1].trim()), value: match[2].trim() });
    cursor = match.index + match[0].length;
  }
  if (cursor < source.length) parts.push({ type: 'markdown', value: source.slice(cursor) });
  return parts.length ? parts : [{ type: 'markdown', value: source }];
}
