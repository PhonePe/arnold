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
  </div>;
}
