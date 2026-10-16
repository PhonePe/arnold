'use client';

import React, { Suspense, useEffect, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { ArrowLeft, ArrowRight, Cpu, FileText, GitPullRequest, LockKeyhole, RotateCcw, Search, Users, X } from 'lucide-react';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { ErrorState } from '@/components/ui/ErrorState';
import { Input } from '@/components/ui/Input';
import { Label } from '@/components/ui/Label';
import { LoadingState } from '@/components/ui/LoadingState';
import { Markdown } from '@/components/ui/Markdown';
import { Overline } from '@/components/ui/Overline';
import { ProgressBar } from '@/components/ui/ProgressBar';
import { UnavailableState } from '@/components/ui/UnavailableState';
import { addMergeRequestReviewer, fetchAnalyses, fetchAnalysis, fetchAnalysisExecutionOptions, fetchGroupAnalyses, fetchMergeRequest, fetchMergeRequestGroup, fetchMergeRequestGroups, fetchPersonas, removeMergeRequestReviewer, resolveMergeRequest, searchMergeRequestReviewers, startAnalysis, startGroupAnalysis } from '@/lib/api';
import { parseChangeRequestId } from '@/lib/contracts';
import { pastRunsFor, runPersonaIds } from '@/lib/analysis-navigation.mjs';

const IMPROVE_PERSONA = 'improve';
const delayForAttempt = (attempt) => attempt <= 5 ? 2000 : attempt <= 20 ? 5000 : 10000;

function Step({ number, icon: Icon, title, description, children }) {
  return (
    <section className="min-w-0 border border-border bg-surface p-5 sm:p-6">
      <div className="mb-5 flex gap-4">
        <span className="grid h-8 w-8 shrink-0 place-items-center bg-fg font-mono text-xs font-bold text-surface">{number}</span>
        <div className="min-w-0">
          <h2 className="flex items-center gap-2 font-display text-lg font-bold text-fg">
            <Icon className="h-4 w-4 text-accent" aria-hidden="true" />
            {title}
          </h2>
          <p className="mt-1 text-[13px] leading-5 text-muted">{description}</p>
        </div>
      </div>
      {children}
    </section>
  );
}

function AnalyzeContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const initialRunId = searchParams.get('runId');
  const initialMergeRequestId = searchParams.get('mergeRequestId');
  const initialGroupId = searchParams.get('groupId');
  const [step, setStep] = useState(1);
  const [history, setHistory] = useState(null);
  const [reusedRunId, setReusedRunId] = useState(null);
  const [scope, setScope] = useState(initialGroupId ? 'group' : 'single');
  const [groups, setGroups] = useState(null);
  const [groupId, setGroupId] = useState(initialGroupId || '');
  const [groupError, setGroupError] = useState(null);
  const group = groups?.find((item) => item.id === groupId);
  const [url, setUrl] = useState('');
  const [mergeRequest, setMergeRequest] = useState(null);
  const selectedTarget = scope === 'group' ? group : mergeRequest;
  const [runId, setRunId] = useState(initialRunId);
  const [analysis, setAnalysis] = useState(null);
  const [status, setStatus] = useState(initialRunId ? 'polling' : initialMergeRequestId ? 'resolving' : 'idle');
  const [error, setError] = useState(null);
  const [pollToken, setPollToken] = useState(0);
  const [reviewerInput, setReviewerInput] = useState('');
  const [reviewerMutation, setReviewerMutation] = useState(null);
  const [reviewerError, setReviewerError] = useState(null);
  const [reviewerCandidates, setReviewerCandidates] = useState([]);
  const [reviewerSearching, setReviewerSearching] = useState(false);
  const [rfcInput, setRfcInput] = useState('');
  const [jiraInput, setJiraInput] = useState('');
  const [contextLinks, setContextLinks] = useState([]);
  const [contextError, setContextError] = useState(null);
  const [reviewContext, setReviewContext] = useState('');
  const harness = 'copilot';
  const [model, setModel] = useState(searchParams.get('model') || '');
  const [executionOptions, setExecutionOptions] = useState(null);
  const [executionError, setExecutionError] = useState(null);
  const [catalogVersion, setCatalogVersion] = useState(0);
  const selectedHarness = executionOptions?.find((option) => option.id === harness);
  const executionReady = selectedHarness?.available && selectedHarness.models.some((option) => option.id === model);
  const defaultModelId = selectedHarness?.models.find((option) => option.isDefault)?.id || selectedHarness?.models[0]?.id;

  useEffect(() => {
    if (executionReady || !selectedHarness?.available || !defaultModelId) return;
    setModel(defaultModelId);
  }, [executionReady, selectedHarness?.available, defaultModelId]);

  useEffect(() => {
    const controller = new AbortController();
    fetchAnalysisExecutionOptions({ signal: controller.signal }).then((options) => {
      if (!controller.signal.aborted) setExecutionOptions(options);
    }).catch((error) => {
      if (!controller.signal.aborted) setExecutionError(error);
    });
    return () => controller.abort();
  }, [catalogVersion]);

  function refreshModels() {
    setExecutionOptions(null);
    setExecutionError(null);
    setCatalogVersion((version) => version + 1);
  }

  const [personas, setPersonas] = useState(null);
  const [personaError, setPersonaError] = useState(null);
  const [personaQuery, setPersonaQuery] = useState('');
  const [selectedPersonaIds, setSelectedPersonaIds] = useState(() => new Set(
    (searchParams.get('personas') || '').split(',').filter(Boolean).slice(0, 3),
  ));

  useEffect(() => {
    if (scope !== 'group') return undefined;
    const controller = new AbortController();
    if (initialGroupId && groups?.every((item) => item.id !== initialGroupId)) {
      fetchMergeRequestGroup(initialGroupId, { signal: controller.signal }).then((group) => {
        setGroups((current) => {
          const next = current ? [...current] : [];
          if (!next.some((item) => item.id === group.id)) next.push(group);
          return next;
        });
        setGroupError(null);
      }).catch((nextError) => !controller.signal.aborted && setGroupError(nextError));
      return () => controller.abort();
    }
    fetchMergeRequestGroups({ signal: controller.signal }).then((result) => {
      setGroups(result.groups);
      setGroupError(null);
    }).catch((nextError) => !controller.signal.aborted && setGroupError(nextError));
    return () => controller.abort();
  }, [scope, initialGroupId, groups]);

  useEffect(() => {
    if (runId) return undefined;
    const controller = new AbortController();
    fetchPersonas({ signal: controller.signal }).then((items) => {
      setPersonas(items);
      setSelectedPersonaIds((current) => new Set([...current].filter((id) => items.some((persona) => persona.id === id))));
    }).catch((nextError) => !controller.signal.aborted && setPersonaError(nextError));
    return () => controller.abort();
  }, [runId]);

  useEffect(() => {
    if (runId) return undefined;
    const controller = new AbortController();
    Promise.all([
      fetchAnalyses({ limit: 100, signal: controller.signal }),
      fetchGroupAnalyses({ limit: 100, signal: controller.signal }),
    ]).then(([singleRuns, groupRuns]) => {
      if (!controller.signal.aborted) setHistory({ singleRuns, groupRuns });
    }).catch(() => !controller.signal.aborted && setHistory({ singleRuns: [], groupRuns: [] }));
    return () => controller.abort();
  }, [runId]);

  useEffect(() => {
    if (runId || !initialMergeRequestId) return undefined;
    const controller = new AbortController();
    try {
      const reference = parseChangeRequestId(initialMergeRequestId);
      fetchMergeRequest(reference.repositoryId, reference.number, {
        provider: reference.provider,
        signal: controller.signal,
      }).then((nextMergeRequest) => {
        setMergeRequest(nextMergeRequest);
        setUrl(nextMergeRequest.webUrl);
        setStatus('resolved');
      }).catch((nextError) => {
        if (controller.signal.aborted) return;
        setError(nextError);
        setStatus(nextError.status === 404 ? 'unsynchronized' : 'resolve-error');
      });
    } catch (nextError) {
      setError(nextError);
      setStatus('resolve-error');
    }
    return () => controller.abort();
  }, [initialMergeRequestId, runId]);

  useEffect(() => {
    if (!runId || status !== 'polling') return undefined;
    const controller = new AbortController();
    let attempt = 0;
    let failures = 0;
    let timer;

    async function poll() {
      if (controller.signal.aborted) return;
      if (document.hidden) {
        timer = window.setTimeout(poll, 2000);
        return;
      }
      try {
        const nextAnalysis = await fetchAnalysis(runId, { signal: controller.signal });
        setAnalysis(nextAnalysis);
        failures = 0;
        attempt += 1;
        if (['completed', 'failed'].includes(nextAnalysis.runStatus)) {
          setStatus('completed');
        } else if (nextAnalysis.runStatus === 'running') {
          timer = window.setTimeout(poll, delayForAttempt(attempt));
        } else {
          setStatus('unknown');
        }
      } catch (nextError) {
        if (controller.signal.aborted) return;
        if (nextError.status === 404) {
          setError(nextError);
          setStatus('not-found');
          return;
        }
        failures += 1;
        if (failures >= 5) {
          setError(nextError);
          setStatus('interrupted');
        } else {
          timer = window.setTimeout(poll, delayForAttempt(attempt + 1));
        }
      }
    }
    poll();
    return () => { controller.abort(); window.clearTimeout(timer); };
  }, [runId, status, pollToken]);

  useEffect(() => {
    if (!analysis || mergeRequest) return undefined;
    const controller = new AbortController();
    const reference = parseChangeRequestId(analysis.changeRequestId);
    fetchMergeRequest(reference.repositoryId, reference.number, {
      provider: reference.provider,
      signal: controller.signal,
    }).then(setMergeRequest).catch(() => {});
    return () => controller.abort();
  }, [analysis, mergeRequest]);

  useEffect(() => {
    const query = reviewerInput.trim().replace(/^@/, '');
    if (!mergeRequest || query.length < 2 || reviewerMutation !== null) return undefined;
    const controller = new AbortController();
    const timer = window.setTimeout(async () => {
      setReviewerSearching(true);
      try {
        setReviewerCandidates(await searchMergeRequestReviewers(mergeRequest.repositoryId, query, {
          signal: controller.signal,
        }));
      } catch (nextError) {
        if (!controller.signal.aborted) setReviewerCandidates([]);
      } finally {
        if (!controller.signal.aborted) setReviewerSearching(false);
      }
    }, 250);
    return () => {
      controller.abort();
      window.clearTimeout(timer);
    };
  }, [mergeRequest, reviewerInput, reviewerMutation]);

  async function resolve(event) {
    event.preventDefault();
    if (!url.trim()) return;
    setStatus('resolving');
    setError(null);
    try {
      setMergeRequest(await resolveMergeRequest(url.trim()));
      setReviewerInput('');
      setReviewerError(null);
      setContextLinks([]);
      setRfcInput('');
      setJiraInput('');
      setContextError(null);
      setReviewContext('');
      setStatus('resolved');
    } catch (nextError) {
      setError(nextError);
      setStatus(nextError.status === 404 ? 'unsynchronized' : 'resolve-error');
    }
  }

  async function start() {
    if (!executionReady) return;
    setStatus('starting');
    setError(null);
    try {
      if (scope === 'group') {
        const run = await startGroupAnalysis(groupId, {
          rfcUrl: contextLinks.find((item) => item.type === 'rfc')?.value || null,
          jiraTickets: contextLinks.filter((item) => item.type === 'jira').map((item) => item.value),
          reviewContext: reviewContext.trim() || null,
          reviewPersonaIds: [...selectedPersonaIds],
          execution: { harness, model },
        });
        router.replace(`/history?runId=${encodeURIComponent(run.runId)}`);
        return;
      }
      const nextAnalysis = await startAnalysis({
        mergeRequestId: mergeRequest.id,
        personaIds: [IMPROVE_PERSONA],
        rfcUrl: contextLinks.find((item) => item.type === 'rfc')?.value || null,
        jiraTickets: contextLinks.filter((item) => item.type === 'jira').map((item) => item.value),
        reviewContext: reviewContext.trim() || null,
        reviewPersonaIds: [...selectedPersonaIds],
          execution: { harness, model },
      });
      setAnalysis(nextAnalysis);
      setRunId(nextAnalysis.runId);
      setStatus(['completed', 'failed'].includes(nextAnalysis.runStatus) ? 'completed' : 'polling');
      router.replace(`/history?runId=${encodeURIComponent(nextAnalysis.runId)}`);
    } catch (nextError) {
      setError(nextError);
      setStatus('start-error');
    }
  }

  async function saveReviewer(username) {
    if (!mergeRequest || !username) return;
    setReviewerMutation('adding');
    setReviewerError(null);
    setReviewerCandidates([]);
    try {
      setMergeRequest(await addMergeRequestReviewer(mergeRequest.repositoryId, mergeRequest.changeNumber, username));
      setReviewerInput('');
    } catch (nextError) {
      setReviewerError(nextError);
    } finally {
      setReviewerMutation(null);
    }
  }

  function addReviewer(event) {
    event.preventDefault();
    saveReviewer(reviewerInput.trim().replace(/^@/, ''));
  }

  function addContext(event, type) {
    event.preventDefault();
    const raw = (type === 'rfc' ? rfcInput : jiraInput).trim();
    const normalized = type === 'jira'
      ? raw.toUpperCase().match(/[A-Z]{2,10}-\d+/)?.[0]
      : raw;
    if (!normalized) {
      setContextError(type === 'jira' ? 'Enter a Jira ticket key or URL containing a ticket key.' : 'Enter the RFC URL.');
      return;
    }
    setContextLinks((items) => {
      if (type === 'jira' && items.some((item) => item.type === 'jira' && item.value === normalized)) return items;
      if (items.some((item) => item.type === type && item.value === normalized)) return items;
      return type === 'rfc'
        ? [...items.filter((item) => item.type !== 'rfc'), { type, value: normalized }]
        : [...items, { type, value: normalized }];
    });
    if (type === 'rfc') setRfcInput('');
    else setJiraInput('');
    setContextError(null);
  }

  function removeContext(item) {
    setContextLinks((items) => items.filter((candidate) => candidate !== item));
  }

  async function removeReviewer(reviewerId) {
    setReviewerMutation(reviewerId);
    setReviewerError(null);
    try {
      setMergeRequest(await removeMergeRequestReviewer(mergeRequest.repositoryId, mergeRequest.changeNumber, reviewerId));
    } catch (nextError) {
      setReviewerError(nextError);
    } finally {
      setReviewerMutation(null);
    }
  }

  function togglePersona(personaId) {
    setSelectedPersonaIds((current) => {
      const next = new Set(current);
      if (next.has(personaId)) next.delete(personaId);
      else if (next.size < 3) next.add(personaId);
      return next;
    });
  }

  function reuseRun(run) {
    setSelectedPersonaIds(new Set(runPersonaIds(run)));
    if (run.execution?.model) setModel(run.execution.model);
    setReusedRunId(run.runId);
    setStep(3);
  }

  function startFresh() {
    setSelectedPersonaIds(new Set());
    setReusedRunId(null);
    setStep(2);
  }

  if (runId) {
    const failed = analysis?.runStatus === 'failed' || analysis?.analysisOutcome === 'FAILED';
    const finished = analysis?.runStatus === 'completed' || failed;
    return (
      <div className="mx-auto max-w-[1000px]">
        <div className="mb-6">
          <Overline>Clio Improve</Overline>
          <h1 className="mt-1 font-display text-[clamp(30px,4vw,42px)] font-bold leading-[1.05] tracking-[-0.02em] text-fg">Analysis run</h1>
          <p className="mt-2 text-[15px] text-muted">Inline code suggestions are generated and posted on GitLab.</p>
        </div>

        {status === 'polling' && !analysis ? <LoadingState label="Loading analysis run" /> : null}
        {status === 'not-found' ? <ErrorState title="Analysis run not found" error={error} /> : null}
        {status === 'interrupted' ? (
          <ErrorState title="Analysis polling interrupted" error={error} onRetry={() => { setError(null); setStatus('polling'); setPollToken((value) => value + 1); }} />
        ) : null}
        {status === 'unknown' ? <UnavailableState title="Unknown analysis status" description={`Arnold returned ${analysis?.runStatus || 'an unknown status'}.`} /> : null}

        {analysis ? (
          <section className={`border border-border border-l-[3px] bg-surface p-5 sm:p-6 ${failed ? 'border-l-bad' : finished ? 'border-l-ok' : 'border-l-accent'}`}>
            <div className="flex flex-wrap items-start justify-between gap-4">
              <div>
                <Overline tone="faint">{mergeRequest?.repositoryPath || analysis.changeRequestId}</Overline>
                <h2 className="mt-1 font-display text-2xl font-bold text-fg">{mergeRequest?.title || 'Improve analysis'}</h2>
                {mergeRequest ? <p className="mt-2 font-mono text-xs text-muted">!{mergeRequest.changeNumber} · {mergeRequest.sourceBranch} → {mergeRequest.targetBranch}</p> : null}
              </div>
              <Badge variant={failed ? 'error' : finished ? 'success' : 'warning'}>{failed ? 'Failed' : finished ? 'Completed' : 'Running'}</Badge>
            </div>

            <div className="mt-7 grid gap-px border border-border bg-border sm:grid-cols-3">
              <div className="bg-surface-2 p-4"><span className="font-mono text-[10px] uppercase tracking-[0.08em] text-faint">Arnold run</span><p className="mt-1 break-all font-mono text-xs text-fg">{analysis.runId}</p></div>
              <div className="bg-surface-2 p-4"><span className="font-mono text-[10px] uppercase tracking-[0.08em] text-faint">Clio pipeline</span><p className="mt-1 font-mono text-xs text-fg">{analysis.pipelineUrl ? <a href={analysis.pipelineUrl} className="text-accent-ink hover:underline" target="_blank" rel="noreferrer">{analysis.pipelineId} ↗</a> : (analysis.pipelineId || 'Starting…')}</p></div>
              <div className="bg-surface-2 p-4"><span className="font-mono text-[10px] uppercase tracking-[0.08em] text-faint">Review state</span><p className="mt-1 font-mono text-xs text-fg">{analysis.reviewState.replaceAll('_', ' ')}</p></div>
            </div>

            <div className="mt-6">
              <div className="mb-2 flex justify-between font-mono text-[11px] text-muted"><span>{finished ? 'Workflow finished' : 'Clio is reviewing the merge request'}</span><span>{finished ? '100%' : 'In progress'}</span></div>
              <ProgressBar value={finished ? 1 : 0} max={1} color={failed ? 'error' : finished ? 'success' : 'primary'} />
            </div>
            {analysis.outcomeDetail ? <Markdown className={`mt-5 border border-border bg-surface-2 p-3 ${failed ? 'text-bad' : 'text-muted'}`}>{analysis.outcomeDetail}</Markdown> : null}
            <p className="mt-5 font-mono text-[10px] text-faint">Created {new Date(analysis.createdAt).toLocaleString()} · Updated {new Date(analysis.updatedAt).toLocaleString()}</p>
          </section>
        ) : null}
      </div>
    );
  }

  const reviewers = (mergeRequest?.reviewerIds || []).map((id, index) => ({
    id,
    name: mergeRequest.reviewerNames[index] || `GitLab user ${id}`,
  }));
  const availableReviewerCandidates = reviewerCandidates.filter((candidate) =>
    !mergeRequest?.reviewerIds.includes(candidate.id));
  const targetRuns = pastRunsFor(scope, selectedTarget, history?.singleRuns, history?.groupRuns);
  const stepOrder = ['target', 'context', 'configure', 'summary'];
  const maxStep = stepOrder.length;
  const currentStep = Math.min(step, maxStep);
  const stepKey = stepOrder[currentStep - 1];
  const visiblePersonas = (personas || []).filter((persona) => {
    const query = personaQuery.trim().toLowerCase();
    return !query || `${persona.name} ${persona.summary}`.toLowerCase().includes(query);
  });
  return (
    <div className="mx-auto max-w-[1280px]">
      <div className="mb-6">
        <Overline>New analysis</Overline>
        <h1 className="mt-1 font-display text-[clamp(30px,4vw,42px)] font-bold leading-[1.05] tracking-[-0.02em] text-fg">Run analysis on your code</h1>
        <p className="mt-2 max-w-[76ch] text-[15px] text-muted">Review a merge request or a connected group. Findings are posted to the relevant GitLab merge requests.</p>
      </div>

      <ol className="mb-5 flex flex-wrap items-center gap-x-2 gap-y-2 font-mono text-[11px]">
        {stepOrder.map((key, index) => (
          <li key={key} className="flex items-center gap-2">
            <button type="button" disabled={index + 1 > currentStep && !selectedTarget} onClick={() => setStep(index + 1)} aria-current={currentStep === index + 1 ? 'step' : undefined} className={`grid h-6 w-6 place-items-center border transition-colors focus-visible:outline focus-visible:outline-accent disabled:cursor-not-allowed ${currentStep === index + 1 ? 'border-fg bg-fg text-surface' : currentStep > index + 1 ? 'border-ok text-ok' : 'border-border text-faint'}`}>{index + 1}</button>
            {index + 1 < maxStep ? <span className="h-px w-5 bg-border" aria-hidden="true" /> : null}
          </li>
        ))}
      </ol>

      <div className="grid min-w-0 gap-4">
        {stepKey === 'target' ? <Step number="1" icon={GitPullRequest} title="What are you analysing?" description="Select a synchronized MR or an existing review group.">
            <div className="mb-5 grid grid-cols-2 border border-border-2 bg-surface-2 p-1 font-mono text-xs">
              {['single', 'group'].map((value) => <button key={value} type="button" aria-pressed={scope === value} onClick={() => { setScope(value); setReusedRunId(null); }} className={`px-3 py-2 focus-visible:outline focus-visible:outline-accent ${scope === value ? 'bg-fg font-semibold text-surface' : 'text-muted'}`}>{value === 'single' ? 'Single MR' : 'MR group'}</button>)}
            </div>
            {scope === 'group' ? <div className="grid gap-3">
              {!groups && !groupError ? <LoadingState label="Loading review groups" /> : null}
              {groupError ? <ErrorState title="Could not load review groups" error={groupError} /> : null}
              {groups ? <select aria-label="Review group" className="h-10 border border-border-2 bg-surface-2 px-3 text-sm text-fg" value={groupId} onChange={(event) => { setGroupId(event.target.value); setReusedRunId(null); }}><option value="">Select a group</option>{groups.map((item) => <option key={item.id} value={item.id}>{item.name} · {item.members.length} MRs</option>)}</select> : null}
              {groups?.length === 0 ? <p className="text-sm text-muted">Create a review group in the <a className="text-accent-ink" href="/queue">merge queue</a> first.</p> : null}
              {group?.members.map((member) => <div key={member.mergeRequest.id} className="border border-border bg-surface-2 p-3"><p className="font-semibold text-fg">{member.mergeRequest.title}</p><p className="font-mono text-xs text-muted">{member.mergeRequest.repositoryPath} · !{member.mergeRequest.changeNumber}</p></div>)}
              <p className="text-xs text-muted">Reviews all member changes and their interactions at the revisions captured when analysis starts.</p>
            </div> : <>
            <form onSubmit={resolve} className="grid min-w-0 gap-2 sm:grid-cols-[minmax(0,1fr)_auto]">
              <Input className="min-w-0 font-mono" value={url} onChange={(event) => setUrl(event.target.value)} placeholder="https://gitlab.example/team/repo/-/merge_requests/123" aria-label="GitLab merge request URL" disabled={status === 'resolving'} />
              <Button type="submit" variant="secondary" disabled={!url.trim() || status === 'resolving'}><Search className="h-4 w-4" aria-hidden="true" />{status === 'resolving' ? 'Fetching…' : 'Fetch details'}</Button>
            </form>
            {status === 'unsynchronized' ? <ErrorState className="mt-4" title="Merge request not synchronized" error={{ message: 'Sync this merge request into Arnold before running analysis.' }} /> : null}
            {status === 'resolve-error' ? <ErrorState className="mt-4" title="Could not resolve merge request" error={error} /> : null}
            {mergeRequest ? (
              <div className="mt-4 min-w-0 border border-ok bg-surface-2 p-4">
                <div className="flex min-w-0 flex-wrap items-start justify-between gap-3"><div className="min-w-0 flex-1"><h3 className="break-words font-display text-lg font-bold text-fg">{mergeRequest.title}</h3><p className="mt-1 break-words font-mono text-[11px] text-muted">{mergeRequest.repositoryPath} · !{mergeRequest.changeNumber} · {mergeRequest.sourceBranch} → {mergeRequest.targetBranch}</p></div><Badge variant="success">Synchronized</Badge></div>
                <Label className="mt-4 block">Merge request description</Label>
                <div className="mt-2 max-h-64 overflow-y-auto border border-border bg-surface p-3">
                  {mergeRequest.description ? <Markdown>{mergeRequest.description}</Markdown> : <p className="text-sm text-faint">No description provided.</p>}
                </div>
              </div>
            ) : null}
            </>}
            {selectedTarget && targetRuns.length ? (
              <div className="mt-5 border border-border bg-surface-2 p-4">
                <Label className="block">{scope === 'group' ? 'Earlier runs for this group' : 'Earlier runs for this merge request'}</Label>
                <p className="mt-1 text-xs text-muted">Open an existing run, reuse its settings, or start fresh.</p>
                <div className="mt-3 divide-y divide-border border-y border-border">
                  {targetRuns.slice(0, 5).map((run) => (
                    <div key={run.runId} className="flex flex-wrap items-center justify-between gap-3 py-2.5">
                      <span className="min-w-0"><span className="block truncate text-sm font-semibold capitalize text-fg">{run.runStatus}</span><span className="block truncate font-mono text-[10px] text-muted">{new Date(run.createdAt).toLocaleString()} · {run.runId}</span></span>
                      <span className="flex items-center gap-2">
                        <Button size="sm" variant="secondary" onClick={() => router.push(`/history?runId=${encodeURIComponent(run.runId)}`)}>Open</Button>
                        <Button size="sm" variant="secondary" onClick={() => reuseRun(run)}><RotateCcw className="h-4 w-4" aria-hidden="true" />Reuse settings</Button>
                      </span>
                    </div>
                  ))}
                </div>
                <Button className="mt-3" size="sm" variant="ghost" onClick={startFresh}>Start fresh</Button>
              </div>
            ) : null}
          </Step> : null}

          {stepKey === 'context' ? <Step number="2" icon={FileText} title="Context for the review" description="Attach an RFC or Jira tickets so Improve can validate the implementation against its requirements.">
            <div className="grid gap-6">
              <div>
                <Label htmlFor="rfc-input" className="block">RFC document</Label>
                <form onSubmit={(event) => addContext(event, 'rfc')} className="mt-2 grid gap-2 sm:grid-cols-[minmax(0,1fr)_auto]">
                  <Input id="rfc-input" type="url" value={rfcInput} onChange={(event) => { setRfcInput(event.target.value); setContextError(null); }} placeholder="https://docs.example/rfc" aria-label="RFC URL" disabled={!selectedTarget} required />
                  <Button type="submit" variant="secondary" disabled={!selectedTarget || !rfcInput.trim()}>Attach</Button>
                </form>
                {contextLinks.some((item) => item.type === 'rfc') ? <div className="mt-2 grid gap-2">{contextLinks.filter((item) => item.type === 'rfc').map((item) => (
                  <div key={item.value} className="flex min-w-0 items-center gap-3 border border-border bg-surface-2 px-3 py-2">
                    <Badge variant="info">RFC</Badge>
                    <a className="min-w-0 flex-1 truncate font-mono text-xs text-accent-ink underline" href={item.value} target="_blank" rel="noreferrer">{item.value}</a>
                    <button type="button" className="grid h-7 w-7 place-items-center text-faint hover:text-bad focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" onClick={() => removeContext(item)} aria-label={`Remove ${item.value}`}><X className="h-4 w-4" aria-hidden="true" /></button>
                  </div>
                ))}</div> : <p className="mt-2 text-xs text-muted">Optional. The design doc the implementation should be validated against.</p>}
              </div>
              <div>
                <Label htmlFor="jira-input" className="block">Jira tickets</Label>
                <form onSubmit={(event) => addContext(event, 'jira')} className="mt-2 grid gap-2 sm:grid-cols-[minmax(0,1fr)_auto]">
                  <Input id="jira-input" type="text" value={jiraInput} onChange={(event) => { setJiraInput(event.target.value); setContextError(null); }} placeholder="ACME-0 or Jira URL" aria-label="Jira ticket" disabled={!selectedTarget} required />
                  <Button type="submit" variant="secondary" disabled={!selectedTarget || !jiraInput.trim()}>Attach</Button>
                </form>
                {contextLinks.some((item) => item.type === 'jira') ? <div className="mt-2 grid gap-2">{contextLinks.filter((item) => item.type === 'jira').map((item) => (
                  <div key={item.value} className="flex min-w-0 items-center gap-3 border border-border bg-surface-2 px-3 py-2">
                    <Badge variant="critical">JIRA</Badge>
                    <span className="min-w-0 flex-1 truncate font-mono text-xs font-bold uppercase text-fg">{item.value}</span>
                    <button type="button" className="grid h-7 w-7 place-items-center text-faint hover:text-bad focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" onClick={() => removeContext(item)} aria-label={`Remove ${item.value}`}><X className="h-4 w-4" aria-hidden="true" /></button>
                  </div>
                ))}</div> : <p className="mt-2 text-xs text-muted">Optional, up to 10. Tickets the change is meant to resolve.</p>}
              </div>
            </div>
            {contextError ? <p className="mt-2 text-xs text-bad" role="alert">{contextError}</p> : null}
            <p className="mt-4 text-xs text-muted">RFC and Jira references in the MR title, description, or branch may also be detected automatically.</p>
            <Label htmlFor="review-context" className="mt-5 block">Extra context for the review</Label>
            <textarea id="review-context" className="mt-2 min-h-24 w-full resize-y border border-border-2 bg-surface-2 p-3 text-sm text-fg focus:border-accent focus:outline-none focus:ring-[3px] focus:ring-[var(--accent-soft)] disabled:opacity-50" value={reviewContext} onChange={(event) => setReviewContext(event.target.value)} placeholder="Describe requirements, risk areas, or anything Improve should pay special attention to." disabled={!selectedTarget} maxLength={4000} />
          </Step> : null}

          {stepKey === 'configure' ? <Step number="3" icon={LockKeyhole} title="Configure the review" description="Pick personas, the AI model, and reviewers. All optional except the model.">
            <div className="grid gap-4">
              <div className="border border-border bg-surface-2 p-4">
                <h3 className="mb-3 flex items-center gap-2 font-display text-base font-bold text-fg"><LockKeyhole className="h-4 w-4 text-accent" aria-hidden="true" />Review personas</h3>
            {reusedRunId ? <p className="mb-3 border border-accent bg-[var(--accent-soft)] p-2.5 font-mono text-[10px] text-accent-ink">Prefilled from run {reusedRunId}</p> : null}
            {!personas && !personaError ? <LoadingState label="Loading review personas" /> : null}
            {personaError ? <ErrorState title="Could not load review personas" error={personaError} /> : null}
            {personas ? <>
              <label className="relative mb-3 block">
                <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-faint" aria-hidden="true" />
                <Input className="pl-9" value={personaQuery} onChange={(event) => setPersonaQuery(event.target.value)} placeholder="Search personas by name or summary" aria-label="Search review personas" />
              </label>
              {selectedPersonaIds.size ? <div className="mb-4 flex flex-wrap items-center gap-2">
                {[...selectedPersonaIds].map((id) => (
                  <button key={id} type="button" className="inline-flex items-center gap-2 border border-accent bg-[var(--accent-soft)] px-3 py-1 text-sm font-semibold text-fg" onClick={() => togglePersona(id)} aria-label={`Remove ${id}`}>
                    {personas.find((persona) => persona.id === id)?.name || id}<X className="h-3.5 w-3.5" aria-hidden="true" />
                  </button>
                ))}
              </div> : null}
              {visiblePersonas.length ? <div className="grid gap-2 sm:grid-cols-2">{visiblePersonas.map((persona) => {
                const selected = selectedPersonaIds.has(persona.id);
                const disabled = !selected && selectedPersonaIds.size >= 3;
                return <button key={persona.id} type="button" disabled={disabled} onClick={() => togglePersona(persona.id)} className={`flex items-start gap-3 border p-3 text-left transition-colors disabled:opacity-40 ${selected ? 'border-accent bg-[var(--accent-soft)]' : 'border-border bg-surface-2 hover:border-accent'}`} aria-pressed={selected}><span className="grid h-9 w-9 shrink-0 place-items-center bg-fg font-display font-bold text-surface">{persona.name[0]}</span><span className="min-w-0 flex-1"><span className="flex items-center gap-2 text-sm font-semibold text-fg">{persona.name}{persona.status === 'DRAFT' ? <Badge variant="warning">Draft</Badge> : null}</span><span className="mt-1 block text-xs leading-5 text-muted">{persona.summary}</span></span>{selected ? <Badge variant="success">Selected</Badge> : null}</button>;
              })}</div> : <p className="text-sm text-muted">No personas match “{personaQuery.trim()}”.</p>}
              <p className="mt-3 font-mono text-[10px] text-muted">{selectedPersonaIds.size}/3 selected · no selection runs standard Improve</p>
            </> : null}
              </div>

              <div className="border border-border bg-surface-2 p-4">
                <h3 className="mb-3 flex items-center gap-2 font-display text-base font-bold text-fg"><Cpu className="h-4 w-4 text-accent" aria-hidden="true" />AI execution</h3>
            <div className="flex items-center justify-end">
              <Button variant="ghost" size="sm" onClick={refreshModels} disabled={!executionOptions && !executionError}>Refresh models</Button>
            </div>
            {executionError ? <ErrorState className="mt-3" title="Could not load harnesses and models" error={executionError} onRetry={refreshModels} /> : !executionOptions ? <LoadingState label="Loading available models" /> : <>
              <div className="mt-3 grid gap-4 sm:grid-cols-2">
                <div>
                  <Label htmlFor="analysis-harness">Harness</Label>
                  <select id="analysis-harness" value={harness} disabled className="mt-2 h-10 w-full rounded-none border border-border-2 bg-surface-2 px-3 text-sm text-fg focus:border-accent focus:outline-none focus:ring-2 focus:ring-ring">
                    {executionOptions.map((option) => <option key={option.id} value={option.id}>{option.name}{option.available ? '' : ' (unavailable)'}</option>)}
                  </select>
                </div>
                <div>
                  <Label htmlFor="analysis-model">Model</Label>
                  <select id="analysis-model" value={executionReady ? model : ''} onChange={(event) => setModel(event.target.value)} disabled={!selectedHarness?.available || status === 'starting'} aria-describedby="analysis-model-help" className="mt-2 h-10 w-full rounded-none border border-border-2 bg-surface-2 px-3 text-sm text-fg focus:border-accent focus:outline-none focus:ring-2 focus:ring-ring disabled:opacity-50">
                    <option value="">Select a model</option>
                    {selectedHarness?.models.map((option) => <option key={option.id} value={option.id}>{option.name} · {option.id}{option.isDefault ? ' (default)' : ''}</option>)}
                  </select>
                </div>
              </div>
              <p id="analysis-model-help" className="mt-3 text-xs text-muted" role="status">{selectedHarness?.message}</p>
              {model && !executionReady && selectedHarness?.available ? <p className="mt-2 text-xs text-bad" role="alert">The previously selected model is unavailable. Select another model.</p> : null}
              {harness === 'copilot' && !selectedHarness?.available ? <a className="mt-2 inline-block text-xs text-accent-ink underline" href="/settings">Manage Copilot connection</a> : null}
            </>}
              </div>

              {scope === 'single' ? <div className="border border-border bg-surface-2 p-4">
                <h3 className="mb-3 flex items-center gap-2 font-display text-base font-bold text-fg"><Users className="h-4 w-4 text-accent" aria-hidden="true" />Reviewers</h3>
            {mergeRequest ? (reviewers.length ? <div className="flex flex-wrap gap-2">{reviewers.map((reviewer) => (
              <span key={reviewer.id} className="inline-flex items-center gap-2 border border-border-2 bg-surface-2 py-1.5 pl-3 pr-1.5 text-sm font-semibold text-fg">
                {reviewer.name}<Badge>GitLab</Badge>
                <button type="button" className="grid h-7 w-7 place-items-center text-faint transition-colors hover:text-bad focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-50" onClick={() => removeReviewer(reviewer.id)} disabled={reviewerMutation !== null} aria-label={`Remove ${reviewer.name}`}><X className="h-4 w-4" aria-hidden="true" /></button>
              </span>
            ))}</div> : <p className="text-sm text-muted">No reviewers are assigned on GitLab.</p>) : <p className="text-sm text-faint">Fetch a merge request to manage its reviewers.</p>}
            <form className="mt-4 flex gap-2" onSubmit={addReviewer}>
              <div className="relative min-w-0 flex-1">
                <Input value={reviewerInput} onChange={(event) => { setReviewerInput(event.target.value); if (event.target.value.trim().length < 2) setReviewerCandidates([]); }} onBlur={() => window.setTimeout(() => setReviewerCandidates([]), 100)} placeholder="Search reviewer by name or handle" aria-label="Search GitLab reviewers" autoComplete="off" disabled={!mergeRequest || reviewerMutation !== null} />
                {reviewerSearching ? <span className="absolute right-3 top-1/2 -translate-y-1/2 font-mono text-[10px] text-faint">Searching…</span> : null}
                {availableReviewerCandidates.length ? <div className="absolute z-20 mt-1 max-h-64 w-full overflow-y-auto border border-border-2 bg-surface-2 shadow-[0_14px_38px_rgba(28,26,22,0.16)]" role="listbox" aria-label="GitLab reviewer suggestions">
                  {availableReviewerCandidates.map((candidate) => (
                    <button key={candidate.id} type="button" className="flex w-full items-center gap-3 border-b border-border px-3 py-2.5 text-left last:border-b-0 hover:bg-[var(--accent-soft)] focus-visible:outline-none focus-visible:bg-[var(--accent-soft)]" onMouseDown={(event) => event.preventDefault()} onClick={() => saveReviewer(candidate.username)} role="option" aria-selected="false">
                      <span className="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-fg font-mono text-[10px] font-bold text-surface">{candidate.name.slice(0, 2).toUpperCase()}</span>
                      <span className="min-w-0"><span className="block truncate text-sm font-semibold text-fg">{candidate.name}</span><span className="block truncate font-mono text-[10px] text-muted">@{candidate.username}</span></span>
                    </button>
                  ))}
                </div> : null}
              </div>
              <Button type="submit" variant="secondary" disabled={!mergeRequest || !reviewerInput.trim() || reviewerMutation !== null}>{reviewerMutation === 'adding' ? 'Adding…' : 'Add'}</Button>
            </form>
            {mergeRequest ? <p className="mt-3 font-mono text-[10px] text-muted">Reviewer changes are saved to GitLab immediately.</p> : null}
            {reviewerError ? <ErrorState className="mt-4" title="Could not update reviewers" error={reviewerError} /> : null}
              </div> : null}
            </div>
          </Step> : null}

          {stepKey === 'summary' ? (
            <section className="min-w-0 border border-border bg-surface p-5 sm:p-6">
              <div className="mb-5 flex gap-4">
                <span className="grid h-9 w-9 shrink-0 place-items-center bg-fg font-mono text-sm font-semibold text-surface">{maxStep}</span>
                <div>
                  <h2 className="font-display text-lg font-bold text-fg">Review and start</h2>
                  <p className="mt-1 text-xs text-muted">Confirm the selection below, then start the analysis.</p>
                </div>
              </div>
              <dl className="divide-y divide-border border-y border-border">
                <div className="py-3"><dt className="font-mono text-xs text-muted">Scope</dt><dd className="mt-1 text-sm font-semibold text-fg">{scope === 'group' ? group?.name || 'MR group' : mergeRequest ? `${mergeRequest.title}` : 'Single MR'}</dd>{scope === 'single' && mergeRequest ? <p className="mt-0.5 font-mono text-[10px] text-muted">{mergeRequest.repositoryPath} · !{mergeRequest.changeNumber}</p> : null}</div>
                <div className="py-3"><dt className="font-mono text-xs text-muted">Workflow</dt><dd className="mt-1 text-sm font-semibold text-fg">{scope === 'group' ? 'Combined review' : 'Clio Improve'}</dd></div>
                <div className="py-3"><dt className="font-mono text-xs text-muted">Harness</dt><dd className="mt-1 text-sm font-semibold text-fg">{selectedHarness?.name || harness}</dd></div>
                <div className="py-3"><dt className="font-mono text-xs text-muted">Model</dt><dd className="mt-1 break-all text-sm font-semibold text-fg">{executionReady ? selectedHarness.models.find((option) => option.id === model)?.name : 'Select a model'}</dd></div>
                <div className="py-3"><dt className="font-mono text-xs text-muted">RFC</dt><dd className="mt-1 text-sm font-semibold text-fg">{contextLinks.some((item) => item.type === 'rfc') ? <a className="break-all font-mono text-xs text-accent-ink underline" href={contextLinks.find((item) => item.type === 'rfc').value} target="_blank" rel="noreferrer">{contextLinks.find((item) => item.type === 'rfc').value}</a> : <span className="text-muted">Not attached</span>}</dd></div>
                <div className="py-3"><dt className="font-mono text-xs text-muted">Jira tickets</dt><dd className="mt-1 text-sm font-semibold text-fg">{contextLinks.some((item) => item.type === 'jira') ? <div className="flex flex-wrap gap-1.5">{contextLinks.filter((item) => item.type === 'jira').map((item) => <span key={item.value} className="border border-border bg-surface-2 px-2 py-0.5 font-mono text-[10px] font-bold uppercase text-fg">{item.value}</span>)}</div> : <span className="text-muted">None attached</span>}</dd></div>
                <div className="py-3"><dt className="font-mono text-xs text-muted">Notes</dt><dd className="mt-1 text-sm font-semibold text-fg">{reviewContext.trim() ? <p className="line-clamp-3 whitespace-pre-wrap text-xs leading-5 text-muted">{reviewContext.trim()}</p> : <span className="text-muted">—</span>}</dd></div>
                <div className="py-3"><dt className="font-mono text-xs text-muted">Personas</dt><dd className="mt-1 text-sm font-semibold text-fg">{selectedPersonaIds.size ? <div className="flex flex-wrap gap-1.5">{[...selectedPersonaIds].map((id) => <span key={id} className="inline-flex items-center gap-1.5 border border-border bg-surface-2 px-2 py-0.5 text-xs font-semibold text-fg"><span className="grid h-5 w-5 place-items-center bg-fg font-display text-[10px] font-bold text-surface">{(personas?.find((persona) => persona.id === id)?.name || id)[0]}</span>{personas?.find((persona) => persona.id === id)?.name || id}</span>)}</div> : 'Standard Improve'}</dd></div>
                {scope === 'single' ? <div className="py-3"><dt className="font-mono text-xs text-muted">Reviewers</dt><dd className="mt-1 text-sm font-semibold text-fg">{mergeRequest ? reviewers.length ? <div className="flex flex-wrap gap-1.5">{reviewers.map((reviewer) => <span key={reviewer.id} className="border border-border bg-surface-2 px-2 py-0.5 text-xs font-semibold text-fg">{reviewer.name}</span>)}</div> : 'None assigned on GitLab' : '—'}</dd></div> : null}
                <div className="flex items-center justify-between gap-4 py-3"><dt className="font-mono text-xs text-muted">Status</dt><dd><Badge variant={selectedTarget ? 'success' : 'warning'}>{selectedTarget ? 'Ready' : 'Select scope'}</Badge></dd></div>
              </dl>
              <p className="mt-4 font-mono text-[10px] leading-4 text-muted">{scope === 'group' ? 'All member reviews and the combined review finish before findings are posted to GitLab.' : 'Improve starts immediately and posts progress and suggestions directly on the GitLab merge request.'}</p>
              {status === 'start-error' ? <ErrorState className="mt-4" title="Could not start analysis" error={error} /> : null}
            </section>
          ) : null}

          <div className="flex flex-wrap items-center justify-between gap-3">
            <Button variant="secondary" onClick={() => setStep((value) => Math.max(1, Math.min(value, maxStep) - 1))} disabled={currentStep === 1}><ArrowLeft className="h-4 w-4" aria-hidden="true" />Back</Button>
            {stepKey !== 'summary' ? <Button onClick={() => setStep((value) => Math.min(maxStep, value + 1))} disabled={currentStep === 1 && !selectedTarget}>Next<ArrowRight className="h-4 w-4" aria-hidden="true" /></Button> : (
              <div className="flex items-center gap-2">
                <Button variant="ghost" onClick={() => router.push('/queue')}>Cancel</Button>
                <Button size="lg" onClick={start} disabled={!executionReady || !selectedTarget || (scope === 'group' && group.members.length < 2) || status === 'starting'}>{status === 'starting' ? 'Starting analysis…' : 'Analyse'}</Button>
              </div>
            )}
          </div>
      </div>
    </div>
  );
}

export default function AnalyzePage() {
  return <Suspense fallback={<LoadingState label="Loading analysis" />}><AnalyzeContent /></Suspense>;
}
