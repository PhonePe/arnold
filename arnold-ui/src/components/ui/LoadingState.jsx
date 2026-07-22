'use client';

export function LoadingState({ label = 'Loading', className = '', ...props }) {
  return (
    <div className={`border border-border bg-surface px-5 py-8 text-center text-sm text-muted ${className}`} aria-live="polite" {...props}>
      <span className="mr-2 inline-block h-2 w-2 animate-pulse rounded-full bg-accent" />
      {label}
    </div>
  );
}
