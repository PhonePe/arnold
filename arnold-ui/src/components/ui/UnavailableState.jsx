'use client';

export function UnavailableState({ title, description, className = '', ...props }) {
  return (
    <div className={`border border-border border-l-[3px] border-l-tertiary bg-surface px-5 py-6 ${className}`} {...props}>
      <div className="font-mono text-[11px] uppercase tracking-[0.12em] text-tertiary-ink">Unavailable</div>
      <h2 className="mt-1 font-display text-lg font-bold text-fg">{title}</h2>
      {description ? <p className="mt-2 max-w-[70ch] text-sm text-muted">{description}</p> : null}
    </div>
  );
}
