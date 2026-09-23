'use client';

import { Badge } from '@/components/ui/Badge';
import { Overline } from '@/components/ui/Overline';
import { ProgressBar } from '@/components/ui/ProgressBar';

export function AnalysisRunDetail({
  title,
  state,
  progressLabel,
  progressValue,
  progressMax,
  personas = [],
  metadata = [],
  notice,
  children,
  actions,
  className = '',
  ...props
}) {
  return (
    <aside className={`border border-border border-l-[3px] bg-surface p-5 lg:sticky lg:top-20 ${state.border} ${className}`} {...props}>
      <div className="flex items-start justify-between gap-3">
        <div><Overline tone="faint">Selected run</Overline><h2 className="mt-1 font-display text-xl font-bold text-fg">{title}</h2></div>
        <Badge variant={state.variant}>{state.label}</Badge>
      </div>

      {notice}

      <div className="mt-5">
        <div className="mb-2 flex justify-between font-mono text-[11px] text-muted"><span>{progressLabel}</span><span>{progressValue}/{progressMax}</span></div>
        <ProgressBar value={progressValue} max={Math.max(progressMax, 1)} color={state.progress} />
      </div>

      {personas.length ? <div className="mt-4">
        <p className="mb-2 font-mono text-[10px] uppercase tracking-[0.08em] text-faint">Review personas</p>
        <div className="flex flex-wrap gap-2">{personas.map((persona) => <Badge key={`${persona.id}:${persona.version}`} variant="info">{persona.name}</Badge>)}</div>
      </div> : null}

      <dl className="mt-5 divide-y divide-border border-y border-border">
        {metadata.map(([label, value]) => (
          <div key={label} className="grid grid-cols-[90px_minmax(0,1fr)] gap-3 py-3">
            <dt className="font-mono text-[10px] uppercase tracking-[0.06em] text-faint">{label}</dt>
            <dd className="break-all font-mono text-xs text-fg">{value}</dd>
          </div>
        ))}
      </dl>

      {children}
      {actions ? <div className="mt-5 grid gap-2">{actions}</div> : null}
    </aside>
  );
}
