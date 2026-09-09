import { Markdown } from '@/components/ui/Markdown';
import { Button } from '@/components/ui/Button';
import { explainReviewEffort } from '@/lib/review-brief.mjs';

export function PreReview({ summary, onStart, className = '', ...props }) {
  return <article className={`h-full overflow-y-auto p-4 sm:p-6 lg:p-10 ${className}`} {...props}>
    <section className="mx-auto max-w-3xl border border-border border-t-[3px] border-t-accent bg-surface px-6 py-7 sm:px-10 sm:py-9" aria-label="Review Brief">
      {summary ? <Markdown remarkPlugins={[explainReviewEffort]} className="text-sm leading-6 [&_h2]:mb-5 [&_h2]:mt-0 [&_h2]:border-b [&_h2]:border-border [&_h2]:pb-4 [&_h2]:text-2xl [&_li]:my-2 [&_p]:text-muted">{summary}</Markdown> : <><h1 className="font-display text-2xl font-bold text-fg">Review Brief</h1><p className="mt-4 text-sm text-muted">No AI overview is available.</p></>}
      {onStart ? <div className="mt-8 border-t border-border pt-5"><Button type="button" onClick={onStart}>Start review →</Button></div> : null}
    </section>
  </article>;
}
