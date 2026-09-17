'use client';

import { Eye } from 'lucide-react';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { commentAssessment } from '@/lib/comment-assessment.mjs';

export function HiddenDiscussionThread({ thread, headSha = null, onShow, className = '', ...props }) {
  const assessments = thread.notes
    .map((note) => ({ note, assessment: commentAssessment(note, headSha) }))
    .filter(({ assessment }) => assessment);

  return <div className={`border-y border-border bg-surface ${className}`} {...props}>
    <div className="flex flex-wrap items-center justify-between gap-3 px-4 py-1.5">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-mono text-xs uppercase tracking-[0.08em] text-muted">Comment hidden · {thread.notes.length} {thread.notes.length === 1 ? 'comment' : 'comments'} · {thread.resolved ? 'resolved' : 'open'}</span>
        {assessments.map(({ note, assessment }) => <Badge key={note.id} variant={assessment.variant}>{assessment.label}</Badge>)}
      </div>
      <Button className="!h-6 !px-2 !text-xs" size="xs" variant="ghost" onClick={onShow}><Eye className="h-3.5 w-3.5" /> Show comment</Button>
    </div>
    {assessments.map(({ note, assessment }) => <div key={note.id} className="border-t border-border px-4 py-2 text-sm text-muted"><span className="font-semibold text-fg">{assessment.label}:</span> {note.assessmentReasoning || 'No explanation was provided.'}{assessment.stale ? <span className="ml-2 font-mono text-[10px] uppercase text-tertiary-ink">Stale assessment</span> : null}{assessment.unverified ? <span className="ml-2 font-mono text-[10px] uppercase text-tertiary-ink">SHA unknown</span> : null}</div>)}
  </div>;
}
