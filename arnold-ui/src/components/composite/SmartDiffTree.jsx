'use client';

import { useEffect, useRef, useState } from 'react';
import { Check, ChevronDown, ChevronRight } from 'lucide-react';
import { smartFileItems } from '@/lib/smart-diff.mjs';

export function SmartDiffTree({ groups, storyViewed, activeItemId, onNavigate, onPreReview, onToggleSteps, className = '', ...props }) {
  const [manualExpansion, setManualExpansion] = useState(null);
  const activeRef = useRef(null);
  const activeGroupId = groups.find((group) => group.items.some((item) => item.id === activeItemId))?.id;
  const manuallyExpandedGroupId = manualExpansion && manualExpansion.activeGroupId === activeGroupId
    ? manualExpansion.groupId : null;

  useEffect(() => {
    activeRef.current?.scrollIntoView({ block: 'nearest' });
  }, [activeItemId]);

  function toggleGroup(id) {
    if (id === activeGroupId) return;
    setManualExpansion((current) => current && current.activeGroupId === activeGroupId && current.groupId === id
      ? null : { activeGroupId, groupId: id });
  }

  return <nav className={`max-h-52 overflow-y-auto border-b border-border bg-surface [contain:layout_paint] lg:max-h-[72vh] lg:border-b-0 lg:border-r ${className}`} aria-label="Review index" {...props}>
    <button className={`w-full border-b border-border px-4 py-3 text-left hover:bg-[var(--ink-wash)] ${activeItemId === 'pre-review' ? 'bg-[var(--accent-soft)]' : 'bg-surface-2'}`} aria-current={activeItemId === 'pre-review' ? 'step' : undefined} onClick={onPreReview}><span className="block font-mono text-[10px] uppercase tracking-[0.12em] text-accent">Pre-review</span><span className="mt-1 block font-display text-sm font-bold text-fg">AI overview</span></button>
    {groups.map((group, groupIndex) => {
      const closed = group.id !== activeGroupId && group.id !== manuallyExpandedGroupId;
      const files = smartFileItems(group.items);
      const stepIds = group.steps.map((step) => step.id);
      const chapterViewed = stepIds.every((stepId) => storyViewed.has(stepId));
      const reviewedSteps = stepIds.filter((stepId) => storyViewed.has(stepId)).length;
      return <section key={group.id}>
        <div className="grid grid-cols-[44px_1fr] border-b border-border bg-surface-2 hover:bg-[var(--ink-wash)]">
          <label className="grid min-h-16 place-items-center border-r border-border"><input type="checkbox" checked={chapterViewed} onChange={() => onToggleSteps(stepIds)} aria-label={`${chapterViewed ? 'Unmark' : 'Mark'} ${group.title} reviewed`} /></label>
          <button className="flex min-w-0 items-start gap-2 px-3 py-3 text-left" aria-expanded={!closed} onClick={() => toggleGroup(group.id)}>
            <span className="grid h-6 w-6 shrink-0 place-items-center rounded-full bg-accent font-mono text-[10px] font-bold text-white">{groupIndex + 1}</span>
            <span className="min-w-0 flex-1"><span className="flex items-center gap-2"><span className="truncate font-display text-sm font-bold text-fg" title={group.title}>{group.title}</span><span className="ml-auto shrink-0 font-mono text-[10px] text-accent-ink">{reviewedSteps}/{group.steps.length}</span>{closed ? <ChevronRight className="h-4 w-4 shrink-0 text-muted" /> : <ChevronDown className="h-4 w-4 shrink-0 text-muted" />}</span><span className="mt-1 block text-xs leading-4 text-muted">{group.rationale}</span></span>
          </button>
        </div>
        {!closed ? <div className="ml-[21px] border-l border-border">{files.map((file) => {
          const path = file.fileDiff.name;
          const fileSteps = group.steps.flatMap((step) => {
            const items = step.items.filter((item) => item.fileDiff.name === path);
            return items.length ? [{ ...step, items }] : [];
          });
          const fileViewed = fileSteps.every((step) => storyViewed.has(step.id));
          const fileActive = fileSteps.some((step) => step.items.some((item) => item.id === activeItemId));
          return <section key={path} className="border-b border-border">
            <div className={`grid grid-cols-[44px_1fr] ${fileActive ? 'bg-[var(--accent-soft)]' : 'bg-surface'}`}>
              <label className="grid min-h-11 place-items-center"><input className="sr-only" type="checkbox" checked={fileViewed} onChange={() => onToggleSteps(fileSteps.map((step) => step.id))} aria-label={`${fileViewed ? 'Unmark' : 'Mark'} ${path} steps reviewed`} /><span className={`grid h-5 w-5 place-items-center rounded-full border ${fileViewed ? 'border-ok bg-ok text-white' : 'border-border-2 bg-surface text-transparent'}`} aria-hidden="true">{fileViewed ? <Check className="h-3 w-3" /> : <span className="h-1.5 w-1.5 rounded-full bg-current" />}</span></label>
              <button className={`flex min-w-0 items-center gap-2 py-2 pr-3 text-left font-mono text-[10px] hover:text-fg ${fileViewed ? 'opacity-60' : ''}`} title={path} onClick={() => onNavigate(fileSteps[0].items[0])}><span className="min-w-0 flex-1 truncate font-semibold text-fg">{path.split('/').pop()}</span><span className="shrink-0 text-ok">+{file.stats.additions}</span><span className="shrink-0 text-bad">−{file.stats.deletions}</span></button>
            </div>
            <div className="ml-[43px] border-l border-border">{fileSteps.map((step) => {
              const active = step.items.some((item) => item.id === activeItemId);
              const stepViewed = storyViewed.has(step.id);
              const focused = step.items.some((item) => item.annotations.some((annotation) => annotation.metadata.kind === 'focus'));
              return <div key={step.id} ref={active ? activeRef : null} className={`grid grid-cols-[28px_1fr] border-t border-border ${active ? 'bg-[var(--accent-soft)]' : 'hover:bg-[var(--ink-wash)]'} ${stepViewed ? 'opacity-60' : ''}`}><label className="grid min-h-9 place-items-center"><input className="sr-only" type="checkbox" checked={stepViewed} onChange={() => onToggleSteps([step.id])} aria-label={`${stepViewed ? 'Unmark' : 'Mark'} ${step.title} reviewed`} /><span className={`grid h-3.5 w-3.5 place-items-center rounded-full border ${stepViewed ? 'border-ok bg-ok text-white' : active ? 'border-accent bg-surface text-accent' : 'border-border-2 bg-surface text-transparent'}`} aria-hidden="true">{stepViewed ? <Check className="h-2.5 w-2.5" /> : <span className="h-1 w-1 rounded-full bg-current" />}</span></label><button className="flex min-h-9 min-w-0 items-center gap-2 py-2 pr-3 text-left" title={step.title} onClick={() => onNavigate(step.items[0])}><span className="min-w-0 flex-1 truncate text-xs font-medium text-fg">{step.title}</span>{focused ? <span className="shrink-0 font-mono text-[9px] uppercase text-accent-ink">Focus</span> : null}<span className="sr-only">{active ? 'Current' : stepViewed ? 'Reviewed' : 'To review'}</span></button></div>;
            })}</div>
          </section>;
        })}</div> : null}
      </section>;
    })}
    {!groups.length ? <p className="p-4 text-sm text-muted">No files match these filters.</p> : null}
  </nav>;
}
