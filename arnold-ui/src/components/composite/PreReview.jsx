import { Markdown } from '@/components/ui/Markdown';
import { Button } from '@/components/ui/Button';
import { TokenUsageDetails } from '@/components/composite/TokenUsageDetails';
import { explainReviewEffort, formatReviewBrief } from '@/lib/review-brief.mjs';

export function PreReview({ summary, usage = null, onStart, className = '', ...props }) {
  return <article className={`h-full overflow-y-auto p-4 sm:p-6 lg:p-10 ${className}`} {...props}>
    <section className="mx-auto max-w-3xl border border-border border-t-[3px] border-t-accent bg-surface px-6 py-7 sm:px-10 sm:py-9" aria-label="Review Brief">
      {summary ? <Markdown remarkPlugins={[formatReviewBrief, explainReviewEffort]} className="text-sm leading-6 [&_h2]:mb-5 [&_h2]:mt-0 [&_h2]:border-b [&_h2]:border-border [&_h2]:pb-4 [&_h2]:text-2xl [&_h3]:mb-2 [&_h3]:mt-6 [&_h3]:border-t [&_h3]:border-border [&_h3]:pt-5 [&_h3]:font-display [&_h3]:text-lg [&_h3]:tracking-tight [&_h3]:text-fg [&_li]:my-2 [&_p]:text-muted">{summary}</Markdown> : <><h1 className="font-display text-2xl font-bold text-fg">Review Brief</h1><p className="mt-4 text-sm text-muted">No AI overview is available.</p></>}
      <TokenUsageDetails className="mt-8" usage={usage} />
      {onStart ? <div className="mt-8 border-t border-border pt-5"><Button type="button" onClick={onStart}>Start review →</Button></div> : null}
    </section>
  </article>;
}
