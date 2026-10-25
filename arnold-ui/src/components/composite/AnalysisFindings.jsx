'use client';

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

import { Badge } from '@/components/ui/Badge';
import { Markdown } from '@/components/ui/Markdown';

export function AnalysisFindings({
  findings,
  contextForFinding,
  className = '',
  ...props
}) {
  const publishedFindings = findings.filter((finding) => finding.publicationStatus === 'posted').length;

  return (
    <div className={`mt-5 ${className}`} {...props}>
      <p className="font-mono text-[10px] uppercase tracking-[0.08em] text-faint">{publishedFindings}/{findings.length} findings published</p>
      <div className="mt-3 grid gap-3">{findings.map((finding) => (
        <section key={finding.id} className="min-w-0 border border-border bg-surface-2 p-3">
          <p className="mb-2 break-all font-mono text-xs text-muted">{contextForFinding(finding)} · {finding.filePath || 'General discussion'}{finding.newLine ? `:${finding.newLine}` : ''}</p>
          <Markdown>{finding.body}</Markdown>
          <div className="mt-2 flex items-center gap-3">
            <Badge variant={finding.publicationStatus === 'posted' ? 'success' : 'warning'}>{finding.publicationStatus === 'unknown' ? 'Unconfirmed' : finding.publicationStatus}</Badge>
            {finding.commentUrl ? <a href={finding.commentUrl} className="text-sm text-accent-ink" target="_blank" rel="noreferrer">View comment</a> : null}
          </div>
        </section>
      ))}</div>
    </div>
  );
}
