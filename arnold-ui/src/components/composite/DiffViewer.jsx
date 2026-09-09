'use client';

import { useCallback, useEffect, useMemo, useRef, useState, useSyncExternalStore } from 'react';
import { parsePatchFiles } from '@pierre/diffs';
import { CodeView } from '@pierre/diffs/react';
import { Copy, Eye, EyeOff, Gitlab, MessageSquare, PanelLeftClose, PanelLeftOpen, PanelRightClose, PanelRightOpen } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { DiscussionComposer } from '@/components/composite/DiscussionComposer';
import { DiscussionThread } from '@/components/composite/DiscussionThread';
import { DiffFileTree } from '@/components/composite/DiffFileTree';
import { PreReview } from '@/components/composite/PreReview';
import { SmartDiffTree } from '@/components/composite/SmartDiffTree';
import { annotationLocation, contentForRange, isCurrentPosition, positionForSelection, lastDiffLocation } from '@/lib/diff-lines.mjs';
import { groupDiscussions } from '@/lib/discussions';
import { fetchMergeRequestFiles, fetchSmartDiff, fetchViewedFiles, organizeSmartDiff, updateViewedFiles } from '@/lib/api';
import { orderFileTreeItems, toggleViewedPaths } from '@/lib/file-tree.mjs';
import { applyFocusAreas, createSmartDiffFixture, reconcileSmartGroups, trimStoryFileContext } from '@/lib/smart-diff.mjs';

const DIFF_PREFERENCES_KEY = 'arnold.review.diff-preferences';
const SMART_DIFF_TIMEOUT_MS = 135 * 60 * 1000;
const delayForAttempt = (attempt) => attempt <= 5 ? 2000 : attempt <= 20 ? 5000 : 10000;
const SMART_DIFF_FIXTURE = process.env.NEXT_PUBLIC_SMART_DIFF_FIXTURE === 'true';

function subscribeToTheme(callback) {
  const observer = new MutationObserver(callback);
  observer.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
  return () => observer.disconnect();
}

const getTheme = () => document.documentElement.getAttribute('data-theme') || 'light';

function parseItems(patch) {
  try {
    return { items: parsePatchFiles(patch, undefined, true).flatMap((parsedPatch, patchIndex) => parsedPatch.files.map((fileDiff, fileIndex) => ({ id: `diff:${patchIndex}:${fileIndex}:${fileDiff.name}`, type: 'diff', fileDiff }))), error: null };
  } catch (error) {
    return { items: [], error };
  }
}

function stats(fileDiff) {
  let additions = 0;
  let deletions = 0;
  for (const hunk of fileDiff.hunks || []) for (const content of hunk.hunkContent || []) {
    additions += content.additions || 0;
    deletions += content.deletions || 0;
  }
  return { additions, deletions };
}

function fileKind(fileDiff) {
  const name = fileDiff.name.toLowerCase();
  if (!fileDiff.hunks?.length) return 'binary';
  if (/\.(min\.js|map|lock)$/.test(name) || /(^|\/)(vendor|generated|dist)\//.test(name) || /\.pb\.(js|java|go)$/.test(name)) return 'generated';
  const count = stats(fileDiff);
  return count.additions + count.deletions > 1500 ? 'large' : null;
}

const fileDiffSide = (fileDiff) => fileDiff.type === 'deleted' ? 'deletions' : 'additions';

export function DiffViewer({ patch, discussions = [], headSha = null, baseSha = null, startSha = null, webUrl = null, provider = 'GITLAB', projectId, changeNumber, enabled = false, versions = [], selectedVersion = 'latest', onVersionChange, onChanged, insights = null, className = '', ...props }) {
  const viewerRef = useRef(null);
  const workspaceRef = useRef(null);
  const searchRef = useRef(null);
  const pendingScrollRef = useRef(null);
  const viewedWriteRef = useRef(Promise.resolve());
  const viewedWriteVersion = useRef(0);
  const theme = useSyncExternalStore(subscribeToTheme, getTheme, () => 'light');
  const [diffStyle, setDiffStyle] = useState('split');
  const [wrap, setWrap] = useState(false);
  const [search, setSearch] = useState('');
  const [onlyThreads, setOnlyThreads] = useState(false);
  const [hideResolved, setHideResolved] = useState(false);
  const [oneFile, setOneFile] = useState(false);
  const [showGuide, setShowGuide] = useState(true);
  const [showInsights, setShowInsights] = useState(false);
  const [insightsCollapsed, setInsightsCollapsed] = useState(false);
  const [organizationMode, setOrganizationMode] = useState(SMART_DIFF_FIXTURE ? 'smart' : 'tree');
  const [preferencesLoaded, setPreferencesLoaded] = useState(false);
  const [activeItemId, setActiveItemId] = useState(SMART_DIFF_FIXTURE ? 'pre-review' : null);
  const [activeThreadId, setActiveThreadId] = useState(null);
  const [collapseState, setCollapseState] = useState({ patch, entries: {} });
  const [inlineComposer, setInlineComposer] = useState(null);
  const [selectedLines, setSelectedLines] = useState(null);
  const [collapsedThreads, setCollapsedThreads] = useState(new Set());
  const [hiddenFocusAreas, setHiddenFocusAreas] = useState(new Set());
  const parsed = useMemo(() => parseItems(patch), [patch]);
  const [viewed, setViewed] = useState(new Set());
  const [storyViewed, setStoryViewed] = useState(new Set());
  const [focusRatings, setFocusRatings] = useState(new Map());
  const [viewedError, setViewedError] = useState(null);
  const [smartDiff, setSmartDiff] = useState(null);
  const [smartError, setSmartError] = useState(null);
  const [smartLoading, setSmartLoading] = useState(false);
  const [smartRefreshToken, setSmartRefreshToken] = useState(0);
  const smartHeadSha = smartDiff?.headSha;
  const smartStatus = smartDiff?.status;
  const smartTimedOut = smartHeadSha === headSha && ['queued', 'running'].includes(smartStatus)
    && Date.now() - smartDiff.updatedAt > SMART_DIFF_TIMEOUT_MS;

  function setPaneWidth(side, width) {
    const maximum = Math.min(window.innerWidth * 0.45, 720);
    const minimum = side === 'guide' ? 240 : 260;
    workspaceRef.current?.style.setProperty(`--${side}-width`, `${Math.max(minimum, Math.min(maximum, width))}px`);
  }

  function startPaneResize(side, event) {
    const pane = event.currentTarget.parentElement;
    const startX = event.clientX;
    const startWidth = pane.getBoundingClientRect().width;
    event.currentTarget.setPointerCapture(event.pointerId);
    event.currentTarget.dataset.startX = String(startX);
    event.currentTarget.dataset.startWidth = String(startWidth);
    event.preventDefault();
  }

  function continuePaneResize(side, event) {
    if (!event.currentTarget.hasPointerCapture(event.pointerId)) return;
    const startX = Number(event.currentTarget.dataset.startX);
    const startWidth = Number(event.currentTarget.dataset.startWidth);
    const delta = side === 'guide' ? event.clientX - startX : startX - event.clientX;
    setPaneWidth(side, startWidth + delta);
  }

  function resizePaneWithKeyboard(side, event) {
    if (!['ArrowLeft', 'ArrowRight'].includes(event.key)) return;
    const currentWidth = event.currentTarget.parentElement.getBoundingClientRect().width;
    const direction = event.key === 'ArrowRight' ? 1 : -1;
    setPaneWidth(side, currentWidth + direction * (side === 'guide' ? 24 : -24));
    event.preventDefault();
  }
  useEffect(() => {
    try {
      const saved = JSON.parse(localStorage.getItem(DIFF_PREFERENCES_KEY) || '{}');
      if (saved.diffStyle === 'split' || saved.diffStyle === 'unified') setDiffStyle(saved.diffStyle);
      if (typeof saved.wrap === 'boolean') setWrap(saved.wrap);
      if (typeof saved.oneFile === 'boolean') setOneFile(saved.oneFile);
      if (!SMART_DIFF_FIXTURE && (saved.organizationMode === 'tree' || saved.organizationMode === 'smart')) {
        setOrganizationMode(saved.organizationMode);
        if (saved.organizationMode === 'smart') setActiveItemId('pre-review');
      }
    } catch {
      localStorage.removeItem(DIFF_PREFERENCES_KEY);
    } finally {
      setPreferencesLoaded(true);
    }
  }, []);
  useEffect(() => {
    if (preferencesLoaded) localStorage.setItem(DIFF_PREFERENCES_KEY, JSON.stringify({ diffStyle, wrap, oneFile, organizationMode }));
  }, [diffStyle, oneFile, organizationMode, preferencesLoaded, wrap]);
  useEffect(() => {
    setSmartDiff(null);
    setSmartError(null);
    setHiddenFocusAreas(new Set());
  }, [headSha]);
  useEffect(() => {
    if (organizationMode !== 'smart' || !headSha) return undefined;
    const controller = new AbortController();
    setSmartLoading(true);
    setSmartError(null);
    const fixture = createSmartDiffFixture(parsed.items, headSha);
    (SMART_DIFF_FIXTURE ? Promise.resolve(fixture) : fetchSmartDiff(projectId, changeNumber, headSha, { signal: controller.signal }))
      .then(setSmartDiff)
      .catch((error) => { if (!controller.signal.aborted) setSmartError(error); })
      .finally(() => { if (!controller.signal.aborted) setSmartLoading(false); });
    return () => controller.abort();
  }, [changeNumber, headSha, organizationMode, parsed.items, projectId, smartRefreshToken]);
  useEffect(() => {
    if (!headSha || smartHeadSha !== headSha || !['queued', 'running'].includes(smartStatus) || smartTimedOut) return undefined;
    const controller = new AbortController();
    let attempt = 0;
    let failures = 0;
    let timer;
    async function poll() {
      if (controller.signal.aborted) return;
      if (document.hidden) { timer = window.setTimeout(poll, 2000); return; }
      try {
        const next = await fetchSmartDiff(projectId, changeNumber, headSha, { signal: controller.signal });
        if (next.headSha !== headSha) return;
        setSmartDiff(next);
        setSmartError(null);
        failures = 0;
        attempt += 1;
        if (next.status === 'completed') setOrganizationMode('smart');
        else if (next.status === 'queued' || next.status === 'running') timer = window.setTimeout(poll, delayForAttempt(attempt));
      } catch (error) {
        if (controller.signal.aborted) return;
        failures += 1;
        if (failures >= 5) setSmartError(error);
        else timer = window.setTimeout(poll, delayForAttempt(attempt + 1));
      }
    }
    timer = window.setTimeout(poll, delayForAttempt(attempt));
    return () => { controller.abort(); window.clearTimeout(timer); };
  }, [changeNumber, headSha, projectId, smartHeadSha, smartStatus, smartTimedOut]);
  useEffect(() => {
    if (!headSha) return undefined;
    const controller = new AbortController();
    fetchViewedFiles(projectId, changeNumber, headSha, { signal: controller.signal })
      .then((progress) => { setViewed(new Set(progress.filePaths)); setStoryViewed(new Set(progress.storyStepIds)); setFocusRatings(new Map(Object.entries(progress.focusAreaFeedback))); })
      .catch((error) => { if (!controller.signal.aborted) setViewedError(error); });
    return () => controller.abort();
  }, [changeNumber, headSha, projectId]);

  const positionedThreads = useMemo(() => groupDiscussions(discussions).filter((thread) => isCurrentPosition(thread.position, headSha)), [discussions, headSha]);
  const collapseEntries = useMemo(() => collapseState.patch === patch ? collapseState.entries : {}, [collapseState, patch]);
  const decoratedItems = useMemo(() => parsed.items.map((item) => {
    const threads = positionedThreads.filter((thread) => thread.position.newPath === item.fileDiff.name || thread.position.oldPath === item.fileDiff.name);
    const shownThreads = hideResolved ? threads.filter((thread) => !thread.resolved) : threads;
    const annotations = shownThreads.filter((thread) => !collapsedThreads.has(thread.id)).map((thread) => ({ ...(annotationLocation(thread.position) || { side: fileDiffSide(item.fileDiff), lineNumber: 0 }), metadata: { kind: 'thread', thread } }));
    if (!inlineComposer?.file && inlineComposer?.itemId === item.id) {
      const location = inlineComposer.location;
      annotations.push({ ...location, metadata: { kind: 'composer', ...inlineComposer } });
    }
    const kind = fileKind(item.fileDiff);
    return { ...item, annotations, threads, kind, stats: stats(item.fileDiff), collapsed: collapseEntries[item.id]?.collapsed ?? Boolean(kind), version: (collapseEntries[item.id]?.version || 0) + annotations.reduce((sum, annotation) => sum + (annotation.metadata.thread?.notes.length || 1), 0) };
  }), [collapseEntries, collapsedThreads, hideResolved, inlineComposer, parsed.items, positionedThreads]);
  const treeItems = useMemo(() => orderFileTreeItems(decoratedItems), [decoratedItems]);
  const focusedItems = useMemo(() => applyFocusAreas(decoratedItems, smartDiff, headSha), [decoratedItems, headSha, smartDiff]);
  const smartOrganization = useMemo(() => reconcileSmartGroups(focusedItems || decoratedItems, smartDiff, headSha), [decoratedItems, focusedItems, headSha, smartDiff]);
  const smartMode = organizationMode === 'smart' && Boolean(smartOrganization);
  const storyUnavailable = organizationMode === 'smart' && !smartMode;
  const storyPending = smartDiff?.headSha === headSha && ['queued', 'running'].includes(smartDiff.status) && !smartTimedOut;
  const preReviewActive = smartMode && activeItemId === 'pre-review';
  const items = smartMode ? smartOrganization.items : treeItems;
  const smartSteps = useMemo(() => smartMode ? smartOrganization.steps.filter((step) => (!search || step.items.some((item) => item.fileDiff.name.toLowerCase().includes(search.toLowerCase()))) && (!onlyThreads || step.items.some((item) => item.threads.length))) : [], [onlyThreads, search, smartMode, smartOrganization]);
  const indexedItems = useMemo(() => smartMode ? smartSteps.flatMap((step) => step.items) : items.filter((item) => (!search || item.fileDiff.name.toLowerCase().includes(search.toLowerCase())) && (!onlyThreads || item.threads.length)), [items, onlyThreads, search, smartMode, smartSteps]);
  const effectiveActiveItemId = indexedItems.some((item) => item.id === activeItemId) ? activeItemId : indexedItems[0]?.id;
  const activeStep = smartMode ? smartSteps.find((step) => step.items.some((item) => item.id === effectiveActiveItemId)) : null;
  const activeFileIndex = smartMode ? smartSteps.indexOf(activeStep) : indexedItems.findIndex((item) => item.id === effectiveActiveItemId);
  const filteredItems = useMemo(() => oneFile ? smartMode ? activeStep?.items || [] : indexedItems.filter((item) => item.id === effectiveActiveItemId) : indexedItems, [activeStep, effectiveActiveItemId, indexedItems, oneFile, smartMode]);
  const displayItems = useMemo(() => filteredItems.map((item) => {
    const { side, lineNumber } = lastDiffLocation(item.fileDiff);
    const composer = inlineComposer?.file && inlineComposer.itemId === item.id ? inlineComposer : null;
    const annotations = [...item.annotations];
    if (enabled) annotations.push({ side, lineNumber, metadata: { kind: 'file-actions', item, composer: composer?.anchor === 'bottom' ? composer : null } });
    if (composer?.anchor === 'top') annotations.unshift({ side, lineNumber: 0, metadata: { kind: 'composer', ...composer } });
    return { ...item, annotations };
  }), [enabled, filteredItems, inlineComposer]);
  // CodeView updates existing items only when their numeric version changes.
  const [renderState, setRenderState] = useState({ items: displayItems, revision: 0 });
  if (renderState.items !== displayItems) setRenderState({ items: displayItems, revision: renderState.revision + 1 });
  const versionedItems = useMemo(() => displayItems.map((item) => ({ ...item, version: renderState.revision })), [displayItems, renderState.revision]);
  const indexedIds = useMemo(() => new Set(indexedItems.map((item) => item.id)), [indexedItems]);
  const smartGroups = useMemo(() => smartMode ? smartOrganization.groups.map((group) => {
    const steps = group.steps.filter((step) => step.items.some((item) => indexedIds.has(item.id)));
    return { ...group, steps, items: steps.flatMap((step) => step.items) };
  }).filter((group) => group.steps.length) : [], [indexedIds, smartMode, smartOrganization]);
  const navigableThreads = useMemo(() => items.flatMap((item) => item.threads.filter((thread) => !thread.resolved).map((thread) => ({ item, thread, location: annotationLocation(thread.position) }))).filter((entry) => entry.location?.lineNumber != null), [items]);

  const requestSmartOrganization = useCallback(async (regenerate = false) => {
    if (!headSha) return;
    setSmartLoading(true);
    setSmartError(null);
    try {
      const next = SMART_DIFF_FIXTURE
        ? createSmartDiffFixture(parsed.items, headSha)
        : await organizeSmartDiff(projectId, changeNumber, headSha, { regenerate });
      if (next.headSha !== headSha) throw new Error('Arnold returned organization for an outdated diff.');
      setSmartDiff(next);
      if (next.status === 'completed') setOrganizationMode('smart');
    } catch (error) {
      setSmartError(error);
    } finally {
      setSmartLoading(false);
    }
  }, [changeNumber, headSha, parsed.items, projectId]);

  const persistViewed = useCallback((nextFiles, nextSteps, nextRatings, previousFiles, previousSteps, previousRatings) => {
    setViewed(nextFiles);
    setStoryViewed(nextSteps);
    setFocusRatings(nextRatings);
    setViewedError(null);
    const version = ++viewedWriteVersion.current;
    viewedWriteRef.current = viewedWriteRef.current.catch(() => {}).then(() => updateViewedFiles(projectId, changeNumber, headSha, [...nextFiles], [...nextSteps], Object.fromEntries(nextRatings)))
      .then((progress) => { if (version === viewedWriteVersion.current) { setViewed(new Set(progress.filePaths)); setStoryViewed(new Set(progress.storyStepIds)); setFocusRatings(new Map(Object.entries(progress.focusAreaFeedback))); } })
      .catch((error) => { if (version === viewedWriteVersion.current) { setViewed(previousFiles); setStoryViewed(previousSteps); setFocusRatings(previousRatings); setViewedError(error); } });
  }, [changeNumber, headSha, projectId]);
  const toggleViewed = useCallback((value) => {
    const paths = Array.isArray(value) ? value : [value];
    if (!headSha || !paths.length) return;
    persistViewed(toggleViewedPaths(viewed, paths), storyViewed, focusRatings, viewed, storyViewed, focusRatings);
  }, [focusRatings, headSha, persistViewed, storyViewed, viewed]);
  const toggleStoryViewed = useCallback((stepIds) => {
    if (!headSha || !stepIds.length) return;
    persistViewed(viewed, toggleViewedPaths(storyViewed, stepIds), focusRatings, viewed, storyViewed, focusRatings);
  }, [focusRatings, headSha, persistViewed, storyViewed, viewed]);
  const rateFocus = useCallback((stepId, value) => {
    if (!headSha) return;
    const nextRatings = new Map(focusRatings);
    nextRatings.set(stepId, value);
    persistViewed(viewed, storyViewed, nextRatings, viewed, storyViewed, focusRatings);
  }, [focusRatings, headSha, persistViewed, storyViewed, viewed]);

  const loadDiffFiles = useCallback(async (fileDiff) => {
    const oldPath = fileDiff.prevName || fileDiff.name;
    const files = await fetchMergeRequestFiles(projectId, changeNumber, oldPath, fileDiff.name, { baseSha, headSha });
    const newFile = { name: fileDiff.name, contents: files.newContent, cacheKey: `${projectId}:${headSha || fileDiff.newObjectId}:${fileDiff.name}` };
    return trimStoryFileContext(fileDiff, fileDiff.type === 'rename-pure' ? { oldFile: null, newFile } : { oldFile: { name: oldPath, contents: files.oldContent, cacheKey: `${projectId}:${baseSha || fileDiff.prevObjectId}:${oldPath}` }, newFile });
  }, [baseSha, changeNumber, headSha, projectId]);

  const openLineComposer = useCallback((item, range) => {
    const position = positionForSelection(item.fileDiff, range);
    if (!position) return;
    setInlineComposer({ itemId: item.sourceId || item.id, location: { side: range.side || 'additions', lineNumber: Math.max(range.start, range.end) }, position: { ...position, baseSha, startSha, headSha }, suggestion: contentForRange(item.fileDiff, range.side || 'additions', range.start, range.end) });
  }, [baseSha, headSha, startSha]);
  const openFileComposer = useCallback((item, anchor) => {
    setInlineComposer({ itemId: item.id, file: true, anchor, position: { baseSha, startSha, headSha, oldPath: item.fileDiff.prevName || item.fileDiff.name, newPath: item.fileDiff.name, oldLine: null, newLine: null }, suggestion: '' });
  }, [baseSha, headSha, startSha]);
  const options = useMemo(() => ({
    theme: { dark: 'pierre-dark-soft', light: 'pierre-light' }, themeType: theme, diffStyle, overflow: wrap ? 'wrap' : 'scroll', diffIndicators: 'classic', stickyHeaders: true,
    enableGutterUtility: enabled, enableLineSelection: enabled, lineHoverHighlight: enabled ? 'both' : 'disabled', loadDiffFiles, hunkSeparators: 'line-info', expansionLineCount: 20, lineDiffType: smartMode ? 'none' : 'word-alt',
    onGutterUtilityClick: enabled ? (range, context) => openLineComposer(context.item, range) : undefined,
    layout: { paddingTop: 12, paddingBottom: 12, gap: 12 },
  }), [diffStyle, enabled, loadDiffFiles, openLineComposer, smartMode, theme, wrap]);
  const selectedItem = items.find((item) => item.id === selectedLines?.id);
  const validSelection = selectedItem && positionForSelection(selectedItem.fileDiff, selectedLines.range);

  const closeComposer = useCallback(() => { setInlineComposer(null); setSelectedLines(null); }, [setInlineComposer]);
  const toggleFocusVisibility = useCallback((stepId) => setHiddenFocusAreas((current) => toggleViewedPaths(current, [stepId])), []);
  const renderComposer = useCallback((composer) => <DiscussionComposer className="m-2" provider={provider} projectId={projectId} changeNumber={changeNumber} position={composer.position} suggestion={composer.suggestion} label={`Comment on ${composer.position.newPath}`} enabled={enabled} onChanged={() => { closeComposer(); onChanged?.(); }} onRefresh={onChanged} onCancel={closeComposer} />, [changeNumber, closeComposer, enabled, onChanged, projectId, provider]);
  const renderAnnotation = useCallback((annotation) => annotation.metadata.kind === 'file-actions'
    ? <div className="border-t border-border bg-surface p-2"><Button size="xs" variant="secondary" onClick={() => openFileComposer(annotation.metadata.item, 'bottom')}>Comment on file</Button>{annotation.metadata.composer ? renderComposer(annotation.metadata.composer) : null}</div>
    : annotation.metadata.kind === 'focus'
    ? hiddenFocusAreas.has(annotation.metadata.stepId)
      ? <div className="flex items-center justify-between gap-3 border-y border-border bg-surface px-4 py-1.5"><span className="font-mono text-xs uppercase tracking-[0.08em] text-muted">Reviewer focus hidden · lines {annotation.metadata.startLine}–{annotation.metadata.endLine}</span><Button className="!h-6 !px-2 !text-xs" size="xs" variant="ghost" onClick={() => toggleFocusVisibility(annotation.metadata.stepId)}><Eye className="h-3.5 w-3.5" /> Show</Button></div>
      : <div className="flex flex-wrap items-end justify-between gap-4 border-y border-accent bg-[var(--accent-soft)] px-4 py-3"><div className="max-w-2xl"><div className="flex items-center gap-1"><p className="font-mono text-xs uppercase tracking-[0.08em] text-accent">Reviewer focus · lines {annotation.metadata.startLine}–{annotation.metadata.endLine}</p><Button className="!h-5 !px-1.5" size="xs" variant="ghost" title="Hide reviewer focus" aria-label="Hide reviewer focus" onClick={() => toggleFocusVisibility(annotation.metadata.stepId)}><EyeOff className="h-3.5 w-3.5" /></Button></div><p className="mt-1 text-sm leading-6 text-muted"><span className="font-semibold text-fg">{annotation.metadata.title}:</span> {annotation.metadata.explanation}</p></div><div className="flex items-center gap-1"><span className="mr-1 font-mono text-xs text-muted">Was this useful?</span><Button className="!h-6 !px-2 !text-xs" size="xs" variant={focusRatings.get(annotation.metadata.stepId) === 'useful' ? 'primary' : 'secondary'} aria-pressed={focusRatings.get(annotation.metadata.stepId) === 'useful'} onClick={() => rateFocus(annotation.metadata.stepId, 'useful')}>Useful</Button><Button className="!h-6 !px-2 !text-xs" size="xs" variant={focusRatings.get(annotation.metadata.stepId) === 'not-useful' ? 'primary' : 'secondary'} aria-pressed={focusRatings.get(annotation.metadata.stepId) === 'not-useful'} onClick={() => rateFocus(annotation.metadata.stepId, 'not-useful')}>Not useful</Button></div></div>
    : annotation.metadata.kind === 'thread'
    ? <DiscussionThread className="m-2" thread={annotation.metadata.thread} provider={provider} projectId={projectId} changeNumber={changeNumber} enabled={enabled} collapsible onCollapsedChange={() => setCollapsedThreads((current) => new Set([...current, annotation.metadata.thread.id]))} onChanged={onChanged} />
    : renderComposer(annotation.metadata),
  [changeNumber, enabled, focusRatings, hiddenFocusAreas, onChanged, openFileComposer, projectId, provider, rateFocus, renderComposer, toggleFocusVisibility]);
  const toggleCollapsed = useCallback((id, collapsed) => setCollapseState((current) => {
    const sourceId = items.find((item) => item.id === id)?.sourceId || id;
    const entries = current.patch === patch ? current.entries : {};
    return { patch, entries: { ...entries, [sourceId]: { collapsed: collapsed ?? !entries[sourceId]?.collapsed, version: (entries[sourceId]?.version || 0) + 1 } } };
  }), [items, patch]);
  useEffect(() => {
    if (preReviewActive || !pendingScrollRef.current) return;
    let frame;
    let timer;
    const scrollWhenReady = () => {
      const viewer = viewerRef.current;
      if (!viewer?.getInstance() || !viewer.getItem(pendingScrollRef.current.id)) {
        frame = requestAnimationFrame(scrollWhenReady);
        return;
      }
      timer = window.setTimeout(() => {
        viewer.scrollTo(pendingScrollRef.current);
        pendingScrollRef.current = null;
      }, 100);
    };
    frame = requestAnimationFrame(scrollWhenReady);
    return () => { cancelAnimationFrame(frame); window.clearTimeout(timer); };
  }, [preReviewActive, versionedItems]);
  const scrollTo = useCallback((item) => {
    if (!item) return;
    const target = { type: 'item', id: item.id, align: 'start', behavior: 'smooth-auto' };
    setActiveItemId(item.id);
    if (preReviewActive || oneFile) pendingScrollRef.current = target;
    else viewerRef.current?.scrollTo(target);
  }, [oneFile, preReviewActive]);
  const moveFile = useCallback((offset) => scrollTo(smartMode ? smartSteps[activeFileIndex + offset]?.items[0] : indexedItems[activeFileIndex + offset]), [activeFileIndex, indexedItems, scrollTo, smartMode, smartSteps]);
  const scrollToThread = useCallback((entry) => {
    if (!entry) return;
    setActiveItemId(entry.item.id);
    setActiveThreadId(entry.thread.id);
    setCollapsedThreads((current) => { const next = new Set(current); next.delete(entry.thread.id); return next; });
    toggleCollapsed(entry.item.id, false);
    requestAnimationFrame(() => requestAnimationFrame(() => viewerRef.current?.scrollTo({ type: 'line', id: entry.item.id, lineNumber: entry.location.lineNumber, side: entry.location.side, align: 'center', behavior: 'smooth-auto' })));
  }, [setCollapsedThreads, toggleCollapsed]);
  const syncActiveFile = useCallback((scrollTop, viewer) => {
    const id = viewer.getScrollAnchor(scrollTop)?.id;
    if (id) setActiveItemId((current) => current === id ? current : id);
  }, [setActiveItemId]);

  useEffect(() => {
    function onKeyDown(event) {
      if (event.defaultPrevented || event.metaKey || event.ctrlKey || event.altKey || event.composedPath().some((target) => /^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName) || target.isContentEditable)) return;
      if (event.key === 'f' && event.shiftKey) { event.preventDefault(); searchRef.current?.focus(); return; }
      if (event.key.toLowerCase() === 'd' && event.shiftKey) { event.preventDefault(); setDiffStyle((value) => value === 'split' ? 'unified' : 'split'); return; }
      const current = Math.max(0, smartMode ? smartSteps.findIndex((step) => step.items.some((item) => item.id === effectiveActiveItemId)) : indexedItems.findIndex((item) => item.id === effectiveActiveItemId));
      if (['j', 'k', '[', ']'].includes(event.key)) { event.preventDefault(); const index = Math.min((smartMode ? smartSteps : indexedItems).length - 1, Math.max(0, current + (['j', ']'].includes(event.key) ? 1 : -1))); scrollTo(smartMode ? smartSteps[index]?.items[0] : indexedItems[index]); }
      if (event.key === 'n' || event.key === 'p') {
        event.preventDefault();
        const currentThread = navigableThreads.findIndex((entry) => entry.thread.id === activeThreadId);
        const currentFile = items.findIndex((item) => item.id === effectiveActiveItemId);
        const fallback = event.key === 'n' ? navigableThreads.findIndex((entry) => items.indexOf(entry.item) >= currentFile)
          : navigableThreads.findLastIndex((entry) => items.indexOf(entry.item) <= currentFile);
        const index = currentThread < 0 ? fallback : Math.min(navigableThreads.length - 1, Math.max(0, currentThread + (event.key === 'n' ? 1 : -1)));
        scrollToThread(navigableThreads[index]);
      }
      if (event.key === 'v') {
        if (preReviewActive) return;
        if (smartMode && activeStep) toggleStoryViewed([activeStep.id]);
        else {
          const activeItem = indexedItems.find((item) => item.id === effectiveActiveItemId);
          if (activeItem) toggleViewed(activeItem.fileDiff.name);
        }
      }
      if (event.key === ';') items.forEach((item) => toggleCollapsed(item.id, event.shiftKey));
    }
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [activeStep, activeThreadId, effectiveActiveItemId, indexedItems, items, navigableThreads, preReviewActive, scrollTo, scrollToThread, smartMode, smartSteps, toggleCollapsed, toggleStoryViewed, toggleViewed]);

  if (parsed.error) return <ErrorState className={className} title="Could not render merge request diff" error={parsed.error} {...props} />;
  if (!items.length) return <EmptyState className={className} title="No source changes" description="GitLab returned no changed files for this merge request." {...props} />;
  return <div className={`flex min-h-0 flex-1 flex-col bg-surface ${className}`} {...props}>
    <div className="chead">
      <div className="modetabs" aria-label="Review organization">
        <button className={organizationMode === 'tree' ? 'on' : ''} aria-pressed={organizationMode === 'tree'} onClick={() => setOrganizationMode('tree')}>Classic</button>
        <button className={organizationMode === 'smart' ? 'on' : ''} aria-pressed={organizationMode === 'smart'} onClick={() => { setOrganizationMode('smart'); setActiveItemId('pre-review'); }}>Story</button>
      </div>
      {smartMode && smartDiff.status === 'completed' ? <Button size="xs" variant="secondary" onClick={() => requestSmartOrganization(true)} disabled={smartLoading}>Regenerate</Button> : null}
      <select className="h-8 min-w-0 max-w-full border border-border-2 bg-surface-2 px-2 text-[13px] text-fg" value={selectedVersion} onChange={(event) => onVersionChange?.(event.target.value)} aria-label="Diff version"><option value="latest">Latest · {versions[0]?.commitTitle || versions[0]?.headSha?.slice(0, 8) || 'changes'}</option>{versions.slice(1).map((version) => <option key={version.id} value={String(version.id)}>{version.commitTitle || version.headSha.slice(0, 8)} · {new Date(version.createdAt).toLocaleString()}</option>)}</select>
      <input ref={searchRef} className="h-8 min-w-48 flex-1 border border-border-2 bg-surface-2 px-2 text-[13px] text-fg focus:border-accent focus:outline-none" type="search" placeholder="Filter files (Shift+F)" value={search} onChange={(event) => setSearch(event.target.value)} />
      <Button size="xs" variant={onlyThreads ? 'primary' : 'secondary'} aria-pressed={onlyThreads} onClick={() => setOnlyThreads((value) => !value)}>Discussions</Button>
      <Button size="xs" variant={hideResolved ? 'primary' : 'secondary'} aria-pressed={hideResolved} onClick={() => setHideResolved((value) => !value)}>Unresolved</Button>
      <Button size="xs" variant="secondary" onClick={() => setCollapsedThreads((current) => current.size ? new Set() : new Set(positionedThreads.map((thread) => thread.id)))}>{collapsedThreads.size ? `Show hidden comments (${collapsedThreads.size})` : 'Hide comments'}</Button>
      <Button size="xs" variant={wrap ? 'primary' : 'secondary'} aria-pressed={wrap} onClick={() => setWrap((value) => !value)}>Wrap</Button>
      <Button size="xs" variant={oneFile ? 'primary' : 'secondary'} aria-pressed={oneFile} onClick={() => setOneFile((value) => !value)}>One file</Button>
      <div className="flex"><Button variant={diffStyle === 'split' ? 'primary' : 'secondary'} size="xs" onClick={() => setDiffStyle('split')}>Split</Button><Button className="-ml-px" variant={diffStyle === 'unified' ? 'primary' : 'secondary'} size="xs" onClick={() => setDiffStyle('unified')}>Unified</Button></div>
      {insights ? <button className={`panetog panetog-mobile ${showInsights ? '' : 'off'}`} aria-expanded={showInsights} onClick={() => setShowInsights((value) => !value)}>Insights</button> : null}
    </div>
    {viewedError ? <div role="alert" className="border-b border-border bg-[color-mix(in_oklch,var(--bad)_8%,transparent)] px-3 py-2 text-sm text-bad">Could not synchronize review progress. Try again.</div> : null}
    {!storyUnavailable && smartError ? <div role="alert" className="flex items-center justify-between gap-3 border-b border-border bg-[color-mix(in_oklch,var(--bad)_8%,transparent)] px-3 py-2 text-sm text-bad"><span>{smartError.message || 'Could not load smart organization.'} The standard diff is still available.</span><Button size="xs" variant="secondary" onClick={() => { setSmartError(null); setOrganizationMode('smart'); setSmartRefreshToken((value) => value + 1); }}>Retry</Button></div> : null}
    {!storyUnavailable && !smartError && storyPending ? <div className="border-b border-border px-3 py-2 font-mono text-xs text-muted">Organizing changes… You can keep reviewing in Classic mode.</div> : null}
    {!storyUnavailable && !smartError && smartTimedOut ? <div role="alert" className="flex items-center justify-between gap-3 border-b border-border bg-[color-mix(in_oklch,var(--bad)_8%,transparent)] px-3 py-2 text-sm text-bad"><span>Organization timed out. The standard diff is still available.</span><Button size="xs" variant="secondary" onClick={() => requestSmartOrganization()} disabled={smartLoading}>Retry</Button></div> : null}
    {!storyUnavailable && !smartError && smartDiff?.headSha === headSha && smartDiff.status === 'failed' ? <div role="alert" className="flex items-center justify-between gap-3 border-b border-border bg-[color-mix(in_oklch,var(--bad)_8%,transparent)] px-3 py-2 text-sm text-bad"><span>{smartDiff.errorMessage || 'Smart organization failed.'} The standard diff is still available.</span><Button size="xs" variant="secondary" onClick={() => requestSmartOrganization()} disabled={smartLoading}>Retry</Button></div> : null}
    {enabled && !storyUnavailable ? <div className="flex flex-wrap items-center gap-3 border-b border-border px-3 py-2 text-xs text-muted"><span>Previous / next: [ / ] · Drag line numbers or Shift+click to select multiple lines.</span>{selectedLines ? <><Button size="xs" variant="secondary" disabled={!validSelection} onClick={() => openLineComposer(selectedItem, selectedLines.range)}>Comment on selected lines</Button>{!validSelection ? <span role="status">Select lines on the same side of the diff.</span> : null}</> : null}</div> : null}
    {storyUnavailable ? <div className="grid min-h-[420px] flex-1 place-items-center overflow-y-auto p-6">
      <section className="w-full max-w-xl border border-border bg-surface p-6 text-center sm:p-10" aria-label="Generate story">
        <h2 className="font-display text-2xl font-semibold text-fg">Review this change as a story</h2>
        <p className="mt-3 text-sm text-muted" role="status">{smartLoading ? 'Checking story…' : storyPending ? 'Generating story… You can continue reviewing in Classic mode.' : 'Generate an AI overview and guided review steps for this change.'}</p>
        {smartError || smartTimedOut || smartDiff?.status === 'failed' ? <p className="mt-3 text-sm text-bad" role="alert">{smartError?.message || (smartTimedOut ? 'Story generation timed out.' : smartDiff.errorMessage || 'Story generation failed.')}</p> : null}
        <Button className="mt-6" variant="primary" onClick={() => smartError ? setSmartRefreshToken((value) => value + 1) : requestSmartOrganization()} disabled={!headSha || smartLoading || (storyPending && !smartError)}>{smartError ? 'Retry loading story' : smartTimedOut || smartDiff?.status === 'failed' ? 'Retry story generation' : 'Generate story'}</Button>
      </section>
    </div> :
    <div ref={workspaceRef} className="review-workspace relative grid min-h-0 min-w-0 flex-1" data-guide-open={showGuide} data-has-insights={Boolean(insights)} data-insights-open={Boolean(insights && !insightsCollapsed)}>
      <aside className="relative hidden min-h-0 w-full flex-col overflow-hidden border-r border-border bg-surface lg:flex">
        <div className={`flex h-11 shrink-0 items-center border-b border-border ${showGuide ? 'justify-between px-3' : 'justify-center'}`}>
          {showGuide ? <span className="font-mono text-xs font-semibold uppercase tracking-[0.08em] text-muted">Guide</span> : null}
          <button type="button" className="grid h-8 w-8 place-items-center text-muted hover:text-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" aria-label={`${showGuide ? 'Collapse' : 'Expand'} guide`} aria-expanded={showGuide} onClick={() => setShowGuide((value) => !value)}>{showGuide ? <PanelLeftClose className="h-4 w-4" /> : <PanelLeftOpen className="h-4 w-4" />}</button>
        </div>
        {showGuide ? smartMode ? <SmartDiffTree className="!max-h-none min-h-0 flex-1" groups={smartGroups} storyViewed={storyViewed} activeItemId={preReviewActive ? 'pre-review' : effectiveActiveItemId} onNavigate={scrollTo} onPreReview={() => setActiveItemId('pre-review')} onToggleSteps={toggleStoryViewed} /> : <DiffFileTree className="!max-h-none min-h-0 flex-1" items={indexedItems} viewed={viewed} activeItemId={effectiveActiveItemId} onNavigate={scrollTo} onToggleViewed={toggleViewed} onToggleCollapsed={toggleCollapsed} /> : null}
        {showGuide ? <div className="pane-resizer -right-1 hidden lg:block" role="separator" aria-label="Resize review guide" aria-orientation="vertical" tabIndex={0} onPointerDown={(event) => startPaneResize('guide', event)} onPointerMove={(event) => continuePaneResize('guide', event)} onKeyDown={(event) => resizePaneWithKeyboard('guide', event)} /> : null}
      </aside>
      <main className="flex min-h-0 min-w-0 flex-col overflow-hidden bg-bg">{preReviewActive ? <PreReview summary={smartDiff.summary} onStart={() => scrollTo(smartGroups[0]?.steps[0]?.items[0])} /> : <>{activeStep ? <section className="max-h-[40%] shrink-0 overflow-y-auto border-b border-border bg-surface px-4 py-3" aria-label="Current review step"><p className="font-mono text-xs uppercase tracking-[0.08em] text-accent">Review step</p><h3 className="mt-1 text-sm font-semibold text-fg">{activeStep.title}</h3><details key={activeStep.id} className="mt-2 text-sm text-muted"><summary className="cursor-pointer font-medium text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">Why this matters and what to inspect</summary><p className="mt-2 leading-5">{activeStep.explanation}</p></details></section> : null}<CodeView ref={viewerRef} items={versionedItems} selectedLines={selectedLines} onSelectedLinesChange={setSelectedLines} onScroll={syncActiveFile} className="min-h-0 min-w-0 flex-1 overflow-auto bg-surface-2" options={options} renderAnnotation={renderAnnotation} renderHeaderFilenameSuffix={(item) => <Button className="!px-2" size="xs" variant="ghost" title="Copy file path" aria-label={`Copy path for ${item.fileDiff.name}`} onClick={() => navigator.clipboard.writeText(item.fileDiff.name)}><Copy className="h-4 w-4" /></Button>} renderHeaderMetadata={(item) => {
        const step = smartMode ? smartSteps.find((candidate) => candidate.items.some((candidateItem) => candidateItem.id === item.id)) : null;
        const reviewed = step ? storyViewed.has(step.id) : viewed.has(item.fileDiff.name);
        return <div className="flex items-center gap-1"><Button className="!px-2" size="xs" variant="ghost" title="Comment on file" aria-label={`Comment on ${item.fileDiff.name}`} disabled={!enabled} onClick={() => openFileComposer(item, 'top')}><MessageSquare className="h-4 w-4" /></Button><label className="flex h-7 cursor-pointer items-center gap-1.5 px-2 font-mono text-xs text-muted hover:text-fg"><input className="h-3.5 w-3.5 accent-accent" type="checkbox" checked={reviewed} aria-label={`Mark ${step ? 'step' : 'file'} viewed`} onChange={() => step ? toggleStoryViewed([step.id]) : toggleViewed(item.fileDiff.name)} /><span>Viewed</span></label>{webUrl ? <a className="grid h-7 w-8 place-items-center text-muted hover:text-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" href={`${webUrl}/diffs`} target="_blank" rel="noreferrer" title="Open changes in GitLab" aria-label="Open changes in GitLab"><Gitlab className="h-4 w-4" /></a> : null}</div>;
      }} />{oneFile ? <nav className="flex items-center justify-center gap-4 border-t border-border px-3 py-4" aria-label={smartMode ? 'Review step navigation' : 'File navigation'}><Button size="xs" variant="ghost" disabled={activeFileIndex <= 0} onClick={() => moveFile(-1)}>← Previous</Button><span className="font-mono text-sm text-muted">{smartMode ? 'Step' : 'File'} {activeFileIndex + 1} of {smartMode ? smartSteps.length : indexedItems.length}</span><Button size="xs" variant="ghost" disabled={activeFileIndex >= (smartMode ? smartSteps.length : indexedItems.length) - 1} onClick={() => moveFile(1)}>Next →</Button></nav> : null}</>}</main>
      {insights ? <aside className={`${showInsights ? 'block' : 'hidden'} absolute inset-y-0 right-0 z-20 min-h-0 w-[min(332px,100%)] overflow-y-auto border-l border-border bg-surface shadow-[0_14px_38px_rgba(28,26,22,0.16)] xl:relative xl:block xl:w-full xl:shadow-none`}><div className={`sticky top-0 z-10 flex h-11 items-center border-b border-border bg-surface ${insightsCollapsed ? 'justify-center' : 'justify-between px-3'}`}>{insightsCollapsed ? null : <span className="font-mono text-xs font-semibold uppercase tracking-[0.08em] text-muted">Insights</span>}<button type="button" className="hidden h-8 w-8 place-items-center text-muted hover:text-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring xl:grid" aria-label={`${insightsCollapsed ? 'Expand' : 'Collapse'} insights`} aria-expanded={!insightsCollapsed} onClick={() => setInsightsCollapsed((value) => !value)}>{insightsCollapsed ? <PanelRightOpen className="h-4 w-4" /> : <PanelRightClose className="h-4 w-4" />}</button><button type="button" className="grid h-8 w-8 place-items-center text-muted hover:text-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring xl:hidden" aria-label="Close insights" onClick={() => setShowInsights(false)}><PanelRightClose className="h-4 w-4" /></button></div>{insightsCollapsed ? null : <div className="min-h-full">{insights}</div>}{insightsCollapsed ? null : <div className="pane-resizer -left-1 hidden xl:block" role="separator" aria-label="Resize insights" aria-orientation="vertical" tabIndex={0} onPointerDown={(event) => startPaneResize('insights', event)} onPointerMove={(event) => continuePaneResize('insights', event)} onKeyDown={(event) => resizePaneWithKeyboard('insights', event)} />}</aside> : null}
    </div>}
  </div>;
}
