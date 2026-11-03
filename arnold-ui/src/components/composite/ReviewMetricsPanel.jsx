'use client';

import React, { useEffect, useState } from 'react';
import { BookOpen } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { ErrorState } from '@/components/ui/ErrorState';
import { LoadingState } from '@/components/ui/LoadingState';
import { ProgressBar } from '@/components/ui/ProgressBar';
import { Tabs } from '@/components/ui/Tabs';
import { fetchReviewFeedbackMetrics } from '@/lib/api';
import { barHeight } from '@/lib/bar-height';

const DAY_MS = 24 * 60 * 60 * 1000;
const PRESETS = [
  { key: '7', label: '7d' },
  { key: '30', label: '30d' },
  { key: '90', label: '90d' },
  { key: '180', label: '180d' },
];
const ORIGINS = [
  { key: 'arnold', label: 'Arnold', bar: 'accent', swatch: 'bg-accent' },
  { key: 'workflow', label: 'Previous workflow', bar: 'tertiary', swatch: 'bg-tertiary' },
];
const PERSONA_SPLIT = [
  { key: 'persona', label: 'Review personas', bar: 'accent', swatch: 'bg-accent' },
  { key: 'general', label: 'General Arnold', bar: 'tertiary', swatch: 'bg-tertiary' },
];
const FOCUS = [
  { key: 'useful', label: 'Useful', swatch: 'bg-ok' },
  { key: 'notUseful', label: 'Not useful', swatch: 'bg-bad' },
];
const AUTHORS = [
  { key: 'ai', label: 'AI (cibot)', bar: 'info', swatch: 'bg-[var(--sev-maint)]' },
  { key: 'human', label: 'Human', bar: 'success', swatch: 'bg-ok' },
];

const ARNOLD_FILTER = 'origin.keyword:arnold or _exists_:analysisRunId';

// Every metric the admin view exposes, where it lives in the CORE_REVIEWS index and how to chart it.
const METRIC_CATALOG = [
  {
    key: 'aiShare', name: 'AI vs human comments', field: 'authorUsername (arnold.comments)',
    meaning: 'Resolvable published MR comments by cibot (AI) versus everyone else (human), with acceptance rate, outcomes and categories per type.',
    chart: 'Weekly columns split by author type; 100% stacked for share',
    kibana: 'Index arnold.comments · filter state:published and not deleted:true · split: filters authorUsername.keyword:cibot / not',
  },
  {
    key: 'resolution', name: 'Thread resolution rate', field: 'resolvable, resolved (arnold.comments)',
    meaning: 'Resolved ÷ resolvable thread comments, per author type.',
    chart: 'Grouped bar or KPI per author type',
    kibana: 'Lens formula · count(kql=\'resolved:true\') / count(kql=\'resolvable:true\')',
  },

  {
    key: 'volume', name: 'Comment volume', field: 'count, ingestedAt',
    meaning: 'Review comments with recorded feedback in the period.',
    chart: 'Weekly columns split by origin',
    kibana: 'Vertical bar · X: date histogram on ingestedAt (1w) · split series: filters by origin',
  },
  {
    key: 'acceptance', name: 'Acceptance rate', field: 'acceptanceStatus',
    meaning: '(applied + partially_applied) ÷ (total − not_applicable). How often authors act on a comment.',
    chart: 'Weekly line or columns per origin',
    kibana: 'Lens formula · count(kql=\'acceptanceStatus:(applied or partially_applied)\') / count(kql=\'not acceptanceStatus:not_applicable\')',
  },
  {
    key: 'outcomes', name: 'Outcome mix', field: 'acceptanceStatus',
    meaning: 'Share of applied, partially applied, ignored and not applicable comments.',
    chart: '100% stacked bar per origin',
    kibana: 'Bar (percentage) · breakdown: terms acceptanceStatus.keyword',
  },
  {
    key: 'score', name: 'Feedback score', field: 'score',
    meaning: 'Assessor confidence (0–1) that the comment was valid. Average, min and max.',
    chart: 'KPI + weekly average line',
    kibana: 'Metric / line · average of score',
  },
  {
    key: 'priority', name: 'Priority mix', field: 'priority',
    meaning: 'Critical / high / medium / low split of posted comments.',
    chart: 'Stacked or grouped bar',
    kibana: 'Bar · terms priority.keyword · split by origin',
  },
  {
    key: 'commentType', name: 'Comment type', field: 'commentType',
    meaning: 'Inline (on a diff line) versus general MR comments.',
    chart: 'Grouped bar or donut',
    kibana: 'Donut · terms commentType.keyword',
  },
  {
    key: 'suggestion', name: 'Suggestion usage', field: 'hasSuggestion',
    meaning: 'Comments that carried an applicable code suggestion.',
    chart: 'Grouped bar; pair with acceptance rate',
    kibana: 'Bar · terms hasSuggestion · split by origin',
  },
  {
    key: 'category', name: 'Category', field: 'commentCategory',
    meaning: 'What the comment was about (style, bug, security…). Top 10.',
    chart: 'Horizontal bar',
    kibana: 'Horizontal bar · terms commentCategory.keyword (size 10)',
  },
  {
    key: 'model', name: 'Model', field: 'sourceModel',
    meaning: 'Which LLM produced the comment. Top 10.',
    chart: 'Horizontal bar; compare acceptance per model',
    kibana: 'Table · rows: terms sourceModel.keyword · metrics: count + acceptance formula',
  },
  {
    key: 'persona', name: 'Persona', field: 'personaId',
    meaning: 'Arnold persona that produced the comment. Top 10.',
    chart: 'Horizontal bar',
    kibana: 'Horizontal bar · terms personaId.keyword',
  },
  {
    key: 'project', name: 'Project', field: 'project.projectName',
    meaning: 'Repositories receiving the most review comments. Top 10.',
    chart: 'Horizontal bar',
    kibana: 'Horizontal bar · terms project.projectName.keyword',
  },
];

// `option` names the response breakdown whose keys populate the dropdown; `values` are fixed choices.
const FILTER_FIELDS = [
  { key: 'project', option: 'project', label: 'All projects' },
  { key: 'model', option: 'model', label: 'All models' },
  { key: 'personaId', option: 'persona', label: 'All personas' },
  { key: 'status', option: 'acceptance', label: 'All outcomes' },
  { key: 'priority', option: 'priority', label: 'All priorities' },
  { key: 'commentType', option: 'commentType', label: 'All comment types' },
  { key: 'category', option: 'category', label: 'All categories' },
  {
    key: 'hasSuggestion',
    label: 'With or without suggestion',
    values: [{ value: 'true', label: 'with suggestion' }, { value: 'false', label: 'without suggestion' }],
  },
];
const EMPTY_FILTERS = Object.fromEntries(FILTER_FIELDS.map((field) => [field.key, '']));
const EMPTY_OPTIONS = Object.fromEntries(FILTER_FIELDS.filter((field) => field.option).map((field) => [field.option, []]));

const selectClass = 'h-9 min-w-0 border border-border bg-surface px-3 text-[12px] text-fg focus-visible:border-accent focus-visible:outline-none';
const percent = (value) => value == null ? '—' : `${value.toFixed(1)}%`;
const toDateInput = (millis) => new Date(millis).toISOString().slice(0, 10);

function share(count, total) {
  return total ? (count / total) * 100 : 0;
}

function rangeFor(days) {
  const today = Date.parse(toDateInput(Date.now()));
  return { from: toDateInput(today - (Number(days) - 1) * DAY_MS), to: toDateInput(today) };
}

function Legend({ series }) {
  return (
    <div className="flex flex-wrap gap-4 font-mono text-[10px] uppercase tracking-[0.1em] text-muted">
      {series.map((origin) => (
        <span key={origin.key} className="flex items-center gap-1.5">
          <span className={`h-2.5 w-2.5 rounded-full ${origin.swatch}`} aria-hidden="true" />
          {origin.label}
        </span>
      ))}
    </div>
  );
}

function Panel({ title, detail, children, className = '' }) {
  return (
    <section className={`border border-border bg-surface ${className}`}>
      <div className="border-b border-border px-4 py-3 sm:px-5">
        <h3 className="font-display text-[16px] font-bold text-fg">{title}</h3>
        {detail ? <p className="mt-0.5 text-[12px] text-muted">{detail}</p> : null}
      </div>
      <div className="p-4 sm:p-5">{children}</div>
    </section>
  );
}

function MetricCatalog() {
  return (
    <details className="group border border-border bg-surface">
      <summary className="flex cursor-pointer items-center gap-2 px-4 py-3 font-display text-[16px] font-bold text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring sm:px-5">
        <BookOpen className="h-4 w-4 text-accent" strokeWidth={1.8} aria-hidden="true" />
        Available metrics &amp; how to plot them
      </summary>
      <div className="overflow-x-auto border-t border-border">
        <table className="w-full min-w-[860px] text-left text-[12px]">
          <thead className="bg-[var(--ink-wash)] font-mono text-[10px] uppercase tracking-[0.1em] text-faint">
            <tr>
              <th className="px-4 py-2 font-semibold">Metric</th>
              <th className="px-4 py-2 font-semibold">What it measures</th>
              <th className="px-4 py-2 font-semibold">ES field</th>
              <th className="px-4 py-2 font-semibold">Recommended chart</th>
              <th className="px-4 py-2 font-semibold">Kibana recipe</th>
            </tr>
          </thead>
          <tbody>
            {METRIC_CATALOG.map((metric) => (
              <tr key={metric.key} className="border-t border-border align-top">
                <td className="px-4 py-2.5 font-semibold text-fg">{metric.name}</td>
                <td className="px-4 py-2.5 text-muted">{metric.meaning}</td>
                <td className="px-4 py-2.5 font-mono text-[11px] text-fg">{metric.field}</td>
                <td className="px-4 py-2.5 text-muted">{metric.chart}</td>
                <td className="px-4 py-2.5 font-mono text-[11px] text-muted">{metric.kibana}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <p className="border-t border-border px-4 py-3 text-[12px] text-muted sm:px-5">
          Index <span className="font-mono text-fg">arnold.code.reviews</span>. Split origins in Kibana with the filter{' '}
          <span className="font-mono text-fg">{ARNOLD_FILTER}</span> (Arnold) and its negation (previous workflow).
          Comments posted through Arnold carry a hidden <span className="font-mono text-fg">{'<!-- arnold -->'}</span> marker.
        </p>
      </div>
    </details>
  );
}

function SectionHeading({ title, detail, series }) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-3 border-b border-border pb-2 pt-2">
      <div>
        <h2 className="font-display text-[20px] font-bold text-fg">{title}</h2>
        <p className="mt-0.5 text-[12px] text-muted">{detail}</p>
      </div>
      <Legend series={series} />
    </div>
  );
}

function Breakdown({ title, counts }) {
  const entries = Object.entries(counts || {}).sort((a, b) => b[1] - a[1]);
  const total = entries.reduce((sum, [, count]) => sum + count, 0);
  if (!total) return null;
  return (
    <div className="mt-4 border-t border-border pt-3">
      <div className="font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-faint">{title}</div>
      <ul className="mt-2 space-y-1.5">
        {entries.map(([name, count]) => (
          <li key={name} className="grid grid-cols-[120px_1fr_90px] items-center gap-2 text-[12px]">
            <span className="truncate text-muted">{name.replaceAll('_', ' ')}</span>
            <ProgressBar value={count} max={total} color="accent" aria-label={name} />
            <span className="text-right font-mono text-fg">{count} · {percent(share(count, total))}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

function AuthorshipComparison({ authorship }) {
  const rows = [
    {
      label: 'Avg comments per MR',
      value: (data) => (data.mergeRequests ? (data.total / data.mergeRequests).toFixed(2) : '—'),
    },
    { label: 'Accepted / actionable', value: (data) => `${data.feedback.accepted} / ${data.feedback.actionable}` },
    { label: 'Resolvable comments', value: (data) => data.total.toLocaleString() },
    { label: 'Resolution rate', value: (data) => percent(data.resolutionRate) },
    { label: 'Resolved / resolvable', value: (data) => `${data.resolved} / ${data.resolvable}` },
    { label: 'Merge requests', value: (data) => data.mergeRequests.toLocaleString() },
  ];
  return (
    <section className="grid gap-px border border-border bg-border sm:grid-cols-2">
      {AUTHORS.map((author) => {
        const data = authorship[author.key];
        return (
          <div key={author.key} className="bg-surface p-4 sm:p-5">
            <div className="flex items-center gap-2 font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-faint">
              <span className={`h-2.5 w-2.5 rounded-full ${author.swatch}`} aria-hidden="true" />
              {author.label}
            </div>
            <div className="mt-2 font-display text-[40px] font-bold leading-none tracking-[-0.03em] text-fg">
              {percent(data.feedback.acceptanceRate)}
            </div>
            <div className="mt-1 text-[13px] text-muted">acceptance rate</div>
            <dl className="mt-4 grid grid-cols-2 gap-x-4 gap-y-2 text-[12px]">
              {rows.map((row) => (
                <React.Fragment key={row.label}>
                  <dt className="text-muted">{row.label}</dt>
                  <dd className="text-right font-mono text-fg">{row.value(data)}</dd>
                </React.Fragment>
              ))}
            </dl>
            <Breakdown title="Acceptance outcomes" counts={data.feedback.acceptance} />
          </div>
        );
      })}
    </section>
  );
}

function Stat({ label, value }) {
  return (
    <div className="bg-surface p-4">
      <div className="font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-faint">{label}</div>
      <div className="mt-1 font-display text-[28px] font-bold leading-none text-fg">{value}</div>
    </div>
  );
}

function ActivityMetrics({ runs, smartDiffs, engagement }) {
  const minutes = runs.averageDurationMs == null ? '—' : `${(runs.averageDurationMs / 60000).toFixed(1)} min`;
  return (
    <>
      <SectionHeading
        title="Analysis runs"
        detail="Arnold analysis runs created in the range on merged merge requests."
        series={[]}
      />
      <section className="grid gap-px border border-border bg-border sm:grid-cols-2 lg:grid-cols-4">
        <Stat label="Runs" value={runs.total.toLocaleString()} />
        <Stat label="Avg duration" value={minutes} />
        <Stat label="Findings posted" value={`${runs.postedFindings} / ${runs.findings}`} />
        <Stat label="Tokens" value={runs.totalTokens.toLocaleString()} />
      </section>
      <div className="grid gap-5 lg:grid-cols-2">
        <Panel title="Run outcome"><Breakdown title="Outcome" counts={runs.outcome} /></Panel>
        <Panel title="Run status"><Breakdown title="Status" counts={runs.status} /></Panel>
      </div>
      <WeeklyChart
        series={[{ key: 'runs', label: 'Runs', swatch: 'bg-accent' }]}
        data={{ runs }}
        title="Weekly runs"
        detail="Analysis runs, per week (UTC)"
        value={(week) => week.total}
        format={(value) => (value || 0).toLocaleString()}
      />
      <SectionHeading
        title="Smart diffs"
        detail="Smart diffs requested in the range on merged merge requests."
        series={[]}
      />
      <section className="grid gap-px border border-border bg-border sm:grid-cols-2 lg:grid-cols-4">
        <Stat label="Smart diffs" value={smartDiffs.total.toLocaleString()} />
        <Stat label="Succeeded" value={(smartDiffs.status.completed || 0).toLocaleString()} />
        <Stat label="Failed" value={(smartDiffs.status.failed || 0).toLocaleString()} />
        <Stat
          label="Avg duration"
          value={smartDiffs.averageDurationMs == null ? '—' : `${(smartDiffs.averageDurationMs / 60000).toFixed(1)} min`}
        />
      </section>
      <div className="grid gap-5 lg:grid-cols-2">
        <Panel title="Smart diff status"><Breakdown title="Status" counts={smartDiffs.status} /></Panel>
        <Panel title="Comment assessment"><Breakdown title="Assessment" counts={smartDiffs.assessment} /></Panel>
      </div>
      <WeeklyChart
        series={[{ key: 'smartDiffs', label: 'Smart diffs', swatch: 'bg-tertiary' }]}
        data={{ smartDiffs }}
        title="Weekly smart diffs"
        detail="Smart diffs requested, per week (UTC)"
        value={(week) => week.total}
        format={(value) => (value || 0).toLocaleString()}
      />
      <SectionHeading
        title="Reviewer focus"
        detail="Reviewer ratings of Arnold focus areas on merged merge requests."
        series={FOCUS}
      />
      <section className="grid gap-px border border-border bg-border sm:grid-cols-2 lg:grid-cols-3">
        <Stat label="Reviewers" value={engagement.reviewers.toLocaleString()} />
        <Stat label="Merge requests" value={engagement.mergeRequests.toLocaleString()} />
        <Stat label="Focus areas rated" value={engagement.focusAreasRated.toLocaleString()} />
        <Stat label="Useful" value={engagement.focusAreasUseful.toLocaleString()} />
        <Stat label="Not useful" value={engagement.focusAreasNotUseful.toLocaleString()} />
        <Stat label="Useful rate" value={percent(engagement.focusUsefulRate)} />
      </section>
      <WeeklyChart
        series={FOCUS}
        data={{
          useful: { weekly: engagement.weekly.map((week) => ({ total: week.useful })) },
          notUseful: { weekly: engagement.weekly.map((week) => ({ total: week.notUseful })) },
        }}
        title="Weekly focus-area ratings"
        detail="Useful vs not useful ratings, per week (UTC)"
        value={(week) => week.total}
        format={(value) => (value || 0).toLocaleString()}
      />
    </>
  );
}

function Comparison({ metrics, series = ORIGINS }) {
  const rows = [
    { label: 'Comments', value: (origin) => origin.total.toLocaleString() },
    { label: 'Accepted / actionable', value: (origin) => `${origin.accepted} / ${origin.actionable}` },
    { label: 'Average score', value: (origin) => origin.score.average == null ? '—' : origin.score.average.toFixed(2) },
    {
      label: 'With suggestion',
      value: (origin) => percent(origin.total ? share(origin.suggestion.with_suggestion || 0, origin.total) : null),
    },
  ];
  return (
    <section className="grid gap-px border border-border bg-border sm:grid-cols-2">
      {series.map((origin) => {
        const data = metrics[origin.key];
        return (
          <div key={origin.key} className="bg-surface p-4 sm:p-5">
            <div className="flex items-center gap-2 font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-faint">
              <span className={`h-2.5 w-2.5 rounded-full ${origin.swatch}`} aria-hidden="true" />
              {origin.label}
            </div>
            <div className="mt-2 font-display text-[40px] font-bold leading-none tracking-[-0.03em] text-fg">
              {percent(data.acceptanceRate)}
            </div>
            <div className="mt-1 text-[13px] text-muted">acceptance rate</div>
            <dl className="mt-4 grid grid-cols-2 gap-x-4 gap-y-2 text-[12px]">
              {rows.map((row) => (
                <React.Fragment key={row.label}>
                  <dt className="text-muted">{row.label}</dt>
                  <dd className="text-right font-mono text-fg">{row.value(data)}</dd>
                </React.Fragment>
              ))}
            </dl>
          </div>
        );
      })}
    </section>
  );
}

function WeeklyChart({ series, data, title, detail, value, format }) {
  const weeks = data[series[0].key].weekly.map((week, index) => ({
    weekStart: week.weekStart,
    ...Object.fromEntries(series.map((item) => [item.key, value(data[item.key].weekly[index] || {})])),
  }));
  const maximum = Math.max(1, ...weeks.flatMap((week) => series.map((item) => week[item.key] || 0)));
  return (
    <Panel title={title} detail={detail}>
      {weeks.length === 0 ? (
        <p className="py-8 text-center text-[13px] text-muted">No weeks in range.</p>
      ) : (
        <div className="flex h-[180px] items-end gap-1.5 overflow-x-auto sm:gap-3">
          {weeks.map((week) => (
            <div key={week.weekStart} className="flex h-full min-w-8 flex-1 flex-col justify-end">
              <div className="flex h-full items-end justify-center gap-0.5">
                {series.map((origin) => (
                  <div
                    key={origin.key}
                    title={`${origin.label}: ${format(week[origin.key])}`}
                    className={`w-full max-w-4 ${week[origin.key] ? origin.swatch : 'bg-transparent'} ${barHeight(week[origin.key] || 0, maximum)}`}
                  />
                ))}
              </div>
              <span className="mt-2 truncate text-center font-mono text-[9px] uppercase text-faint">
                {new Date(`${week.weekStart}T00:00:00Z`).toLocaleDateString(undefined, { month: 'short', day: 'numeric', timeZone: 'UTC' })}
              </span>
            </div>
          ))}
        </div>
      )}
    </Panel>
  );
}

export function ReviewMetricsPanel({ className = '', ...props }) {
  const [preset, setPreset] = useState('30');
  const [range, setRange] = useState(() => rangeFor(30));
  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [options, setOptions] = useState(EMPTY_OPTIONS);
  const [metrics, setMetrics] = useState(null);
  const [status, setStatus] = useState('loading');
  const [error, setError] = useState(null);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    fetchReviewFeedbackMetrics({
      from: Date.parse(range.from),
      to: Date.parse(range.to) + DAY_MS,
      filters,
      signal: controller.signal,
    }).then((response) => {
      setMetrics(response);
      setError(null);
      // Keep previously seen values so narrowing one filter doesn't hide the alternatives.
      setOptions((current) => Object.fromEntries(Object.entries(current).map(([key, values]) => [key,
        [...new Set([...values, ...Object.keys(response.arnold[key]), ...Object.keys(response.workflow[key])])].sort()])));
      setStatus('ready');
    }, (nextError) => {
      if (controller.signal.aborted) return;
      setError(nextError);
      setStatus('error');
    });
    return () => controller.abort();
  }, [range, filters, attempt]);

  const setRangeLoading = (next) => {
    setStatus('loading');
    setRange(next);
  };

  return (
    <div className={`space-y-5 ${className}`} {...props}>
      <MetricCatalog />

      <section className="flex flex-wrap items-end gap-3 border border-border bg-surface p-4 sm:p-5" aria-label="Metric filters">
        <Tabs
          variant="segment"
          items={PRESETS}
          activeKey={preset}
          onTabChange={(key) => {
            setPreset(key);
            setRangeLoading(rangeFor(key));
          }}
        />
        <label className="flex flex-col gap-1 font-mono text-[10px] uppercase tracking-[0.1em] text-faint">
          From
          <input
            type="date"
            className={selectClass}
            value={range.from}
            max={range.to}
            onChange={(event) => {
              if (!event.target.value) return;
              setPreset('');
              setRangeLoading({ ...range, from: event.target.value });
            }}
          />
        </label>
        <label className="flex flex-col gap-1 font-mono text-[10px] uppercase tracking-[0.1em] text-faint">
          To
          <input
            type="date"
            className={selectClass}
            value={range.to}
            min={range.from}
            onChange={(event) => {
              if (!event.target.value) return;
              setPreset('');
              setRangeLoading({ ...range, to: event.target.value });
            }}
          />
        </label>
        {FILTER_FIELDS.map((field) => (
          <select
            key={field.key}
            aria-label={field.label}
            className={`${selectClass} max-w-[220px]`}
            value={filters[field.key]}
            onChange={(event) => {
              setStatus('loading');
              setFilters((current) => ({ ...current, [field.key]: event.target.value }));
            }}
          >
            <option value="">{field.label}</option>
            {(field.values || options[field.option].map((value) => ({ value, label: value.replaceAll('_', ' ') })))
              .map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
          </select>
        ))}
        {Object.values(filters).some(Boolean) ? (
          <Button
            variant="ghost"
            onClick={() => {
              setStatus('loading');
              setFilters(EMPTY_FILTERS);
            }}
          >
            Clear filters
          </Button>
        ) : null}
      </section>

      {status === 'loading' && !metrics ? <LoadingState label="Loading review metrics" /> : null}
      {status === 'error' ? (
        <ErrorState title="Could not load review metrics" error={error} onRetry={() => {
          setStatus('loading');
          setAttempt((current) => current + 1);
        }} />
      ) : null}

      {metrics && status !== 'error' ? (
        <div className={`space-y-5 transition-opacity ${status === 'loading' ? 'opacity-60' : ''}`} aria-busy={status === 'loading'}>
          <SectionHeading
            title="AI vs human comments"
            detail={`Resolvable published MR comments. ${metrics.authorship.aiAuthor} is AI, everyone else is human. Acceptance comes from review feedback by reviewer type. Only merged merge requests are included; the date range applies to their merge date.`}
            series={AUTHORS}
          />
          <AuthorshipComparison authorship={metrics.authorship} />
          <WeeklyChart
            series={AUTHORS}
            data={metrics.authorship}
            title="Weekly comments by author type"
            detail="Published comments, per week (UTC)"
            value={(week) => week.total}
            format={(value) => (value || 0).toLocaleString()}
          />

          <SectionHeading
            title="Review personas vs general Arnold"
            detail="Arnold comments with recorded feedback, split by whether a review persona raised them."
            series={PERSONA_SPLIT}
          />
          <Comparison metrics={metrics.personaSplit} series={PERSONA_SPLIT} />

          <SectionHeading
            title="Arnold vs previous workflow"
            detail="Resolvable (inline) AI review comments with recorded feedback, split by who produced them."
            series={ORIGINS}
          />
          <Comparison metrics={metrics} />
          <div className="grid gap-5 lg:grid-cols-2">
            <WeeklyChart
              series={ORIGINS}
              data={metrics}
              title="Weekly comment volume"
              detail="Comments with feedback, per week (UTC)"
              value={(week) => week.total}
              format={(value) => (value || 0).toLocaleString()}
            />
            <WeeklyChart
              series={ORIGINS}
              data={metrics}
              title="Weekly acceptance rate"
              detail="Applied + partially applied over actionable comments"
              value={(week) => week.acceptanceRate}
              format={percent}
            />
          </div>
          <ActivityMetrics runs={metrics.runs} smartDiffs={metrics.smartDiffs} engagement={metrics.engagement} />
        </div>
      ) : null}
    </div>
  );
}
