'use client';

export function EmptyState({ title, description, action, className = '', ...props }) {
  return (
    <div className={`flex flex-col items-center border border-border bg-surface px-5 py-8 text-center ${className}`} {...props}>
      <h2 className="font-display text-lg font-bold text-fg">{title}</h2>
      {description ? <p className="mt-2 w-full max-w-[60ch] text-center text-sm text-muted">{description}</p> : null}
      {action ? <div className="mt-4">{action}</div> : null}
    </div>
  );
}
