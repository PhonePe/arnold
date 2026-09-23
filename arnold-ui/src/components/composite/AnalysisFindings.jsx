'use client';

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
