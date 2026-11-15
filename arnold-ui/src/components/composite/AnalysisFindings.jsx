'use client';

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

import { Badge } from '@/components/ui/Badge';
import { Markdown } from '@/components/ui/Markdown';

function groupFindings(findings, personas) {
  const names = new Map(personas.map((persona) => [persona.id, persona.name]));
  const groups = new Map();
  for (const finding of findings) {
    const key = finding.personaId || '';
    if (!groups.has(key)) groups.set(key, { key, name: key ? names.get(key) || key : 'General (Arnold)', findings: [] });
    groups.get(key).findings.push(finding);
  }
  return [...groups.values()].sort((left, right) => (!left.key) - (!right.key) || left.name.localeCompare(right.name));
}

export function AnalysisFindings({
  findings,
  personas = [],
  contextForFinding,
  className = '',
  ...props
}) {
  const publishedFindings = findings.filter((finding) => finding.publicationStatus === 'posted').length;
  const groups = groupFindings(findings, personas);
  const grouped = groups.some((group) => group.key);

  return (
    <div className={`mt-5 ${className}`} {...props}>
      <p className="font-mono text-[10px] uppercase tracking-[0.08em] text-faint">{publishedFindings}/{findings.length} findings published</p>
      {grouped ? <div className="mt-2 flex flex-wrap gap-1.5">{groups.map((group) => <Badge key={group.key || 'general'} variant={group.key ? 'learnedRule' : 'default'}>{group.name} · {group.findings.length}</Badge>)}</div> : null}
      {groups.map((group) => (
        <section key={group.key || 'general'} className="mt-3" aria-label={grouped ? `${group.name} findings` : undefined}>
          {grouped ? <h4 className="font-mono text-[11px] font-semibold uppercase tracking-[0.06em] text-muted">{group.name} · {group.findings.length}</h4> : null}
          <div className="mt-2 grid gap-3">{group.findings.map((finding) => (
            <section key={finding.id} className="min-w-0 border border-border bg-surface-2 p-3">
              <p className="mb-2 break-all font-mono text-xs text-muted">{contextForFinding(finding)} · {finding.filePath || 'General discussion'}{finding.newLine ? `:${finding.newLine}` : ''}</p>
              <Markdown>{finding.body}</Markdown>
              <div className="mt-2 flex items-center gap-3">
                <Badge variant={finding.publicationStatus === 'posted' ? 'success' : 'warning'}>{finding.publicationStatus === 'unknown' ? 'Unconfirmed' : finding.publicationStatus}</Badge>
                {finding.commentUrl ? <a href={finding.commentUrl} className="text-sm text-accent-ink" target="_blank" rel="noreferrer">View comment</a> : null}
              </div>
            </section>
          ))}</div>
        </section>
      ))}
    </div>
  );
}
