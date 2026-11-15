'use client';

/*
 * Copyright (c) 2026 Original Author(s), PhonePe Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { useCallback, useEffect, useMemo, useRef, useState, useSyncExternalStore } from 'react';
import { parsePatchFiles } from '@pierre/diffs';
import { CodeView } from '@pierre/diffs/react';
import { Check, ChevronLeft, ChevronRight, Copy, Eye, EyeOff, Gitlab, Maximize2, MessageSquare, Minimize2, PanelLeftClose, PanelLeftOpen, PanelRightClose, PanelRightOpen, SlidersHorizontal } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { MenuDropdown } from '@/components/ui/MenuDropdown';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { ExecutionPicker } from '@/components/composite/ExecutionPicker';
import { FileCommentComposer } from '@/components/composite/FileCommentComposer';
import { DiscussionComposer } from '@/components/composite/DiscussionComposer';
import { DiscussionThread } from '@/components/composite/DiscussionThread';
import { DiffFileTree } from '@/components/composite/DiffFileTree';
import { HiddenDiscussionThread } from '@/components/composite/HiddenDiscussionThread';
import { PreReview } from '@/components/composite/PreReview';
import { ReviewerFocusDetails } from '@/components/composite/ReviewerFocusDetails';
import { SmartDiffTree } from '@/components/composite/SmartDiffTree';
import { PersonaDiffTree } from '@/components/composite/PersonaDiffTree';
import { Avatar } from '@/components/ui/Avatar';
import { annotationLocation, contentForRange, isCurrentPosition, positionForSelection } from '@/lib/diff-lines.mjs';
import { groupDiscussions } from '@/lib/discussions';
import { fetchAnalysisExecutionOptions, fetchMergeRequestFiles, fetchSmartDiff, fetchViewedFiles, organizeSmartDiff, updateViewedFiles } from '@/lib/api';
import { executionBlocker, pickDefaultExecution } from '@/lib/execution-options.mjs';
import { orderFileTreeItems, toggleViewedPaths } from '@/lib/file-tree.mjs';
import { GENERAL_PERSONA_KEY, groupByPersona, personaInitials } from '@/lib/persona-grouping.mjs';
import { focusFileDiff } from '@/lib/persona-diff.mjs';
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

export function DiffViewer({ patch, discussions = [], headSha = null, baseSha = null, startSha = null, webUrl = null, provider = 'GITLAB', projectId, changeNumber, enabled = false, versions = [], selectedVersion = 'latest', onVersionChange, onChanged, onScrolledChange, focusThreadId = null, insights = null, focusMode = false, onFocusModeChange, className = '', ...props }) {
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
  const [outdatedOpen, setOutdatedOpen] = useState(false);
  const [oneFile, setOneFile] = useState(false);
  const [showGuide, setShowGuide] = useState(true);
  const [showInsights, setShowInsights] = useState(false);
  const [insightsCollapsed, setInsightsCollapsed] = useState(false);
  const [focusInsightsOpen, setFocusInsightsOpen] = useState(false);
  const insightsHidden = focusMode ? !focusInsightsOpen : insightsCollapsed;
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
  const [executionOptions, setExecutionOptions] = useState(null);
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
      if (!SMART_DIFF_FIXTURE && ['tree', 'smart', 'persona'].includes(saved.organizationMode)) {
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
  const [execution, setExecution] = useState({ harness: '', model: '' });
  const { harness, model } = execution;
  const [regenerateOpen, setRegenerateOpen] = useState(false);
  const [navExpanded, setNavExpanded] = useState(false);
  const navRef = useRef(null);

  useEffect(() => {
    if (!navExpanded) return undefined;
    function onPointerDown(event) {
      if (navRef.current && !navRef.current.contains(event.target)) setNavExpanded(false);
    }
    function onKey(event) {
      if (event.key === 'Escape') setNavExpanded(false);
    }
    document.addEventListener('mousedown', onPointerDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onPointerDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [navExpanded]);
  const selectedHarness = executionOptions?.find((option) => option.id === harness);
  const executionIssue = executionBlocker(executionOptions, execution);
  const executionGate = SMART_DIFF_FIXTURE ? null : executionIssue;

  useEffect(() => {
    if (organizationMode !== 'smart' || SMART_DIFF_FIXTURE || executionOptions) return undefined;
    const controller = new AbortController();
    fetchAnalysisExecutionOptions({ signal: controller.signal })
      .then((options) => {
        if (controller.signal.aborted) return;
        setExecutionOptions(options);
        setExecution((previous) => pickDefaultExecution(options, previous));
      })
      .catch(() => { if (!controller.signal.aborted) setExecutionOptions([]); });
    return () => controller.abort();
  }, [organizationMode, executionOptions]);
  useEffect(() => {
    if (organizationMode !== 'smart' || !headSha) return undefined;
    const controller = new AbortController();
    setSmartLoading(true);
    setSmartError(null);
    const fixture = createSmartDiffFixture(parsed.items, headSha);
    (SMART_DIFF_FIXTURE ? Promise.resolve(fixture) : fetchSmartDiff(projectId, changeNumber, headSha, { provider, signal: controller.signal }))
      .then(setSmartDiff)
      .catch((error) => { if (!controller.signal.aborted) setSmartError(error); })
      .finally(() => { if (!controller.signal.aborted) setSmartLoading(false); });
    return () => controller.abort();
  }, [changeNumber, headSha, organizationMode, parsed.items, projectId, smartRefreshToken, provider]);
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
        const next = await fetchSmartDiff(projectId, changeNumber, headSha, { provider, signal: controller.signal });
        if (next.headSha !== headSha) return;
        setSmartDiff(next);
        setSmartError(null);
        failures = 0;
        attempt += 1;
        if (next.status === 'completed') {
          setOrganizationMode('smart');
          onChanged?.();
        }
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
  }, [changeNumber, headSha, onChanged, projectId, smartHeadSha, smartStatus, smartTimedOut, provider]);
  useEffect(() => {
    if (!headSha) return undefined;
    const controller = new AbortController();
    fetchViewedFiles(projectId, changeNumber, headSha, { provider, signal: controller.signal })
      .then((progress) => { setViewed(new Set(progress.filePaths)); setStoryViewed(new Set(progress.storyStepIds)); setFocusRatings(new Map(Object.entries(progress.focusAreaFeedback))); })
      .catch((error) => { if (!controller.signal.aborted) setViewedError(error); });
    return () => controller.abort();
  }, [changeNumber, headSha, projectId, provider]);

  // GitLab system notes (e.g. "changed this line in version N") are activity, not review comments.
  const diffThreads = useMemo(() => groupDiscussions(discussions.filter((note) => note.state !== 'system')), [discussions]);
  const positionedThreads = useMemo(() => diffThreads.filter((thread) => isCurrentPosition(thread.position, headSha)), [diffThreads, headSha]);
  const outdatedThreads = useMemo(() => diffThreads.filter((thread) => thread.position && !isCurrentPosition(thread.position, headSha) && !(hideResolved && thread.resolved)), [diffThreads, headSha, hideResolved]);
  const collapseEntries = useMemo(() => collapseState.patch === patch ? collapseState.entries : {}, [collapseState, patch]);
  const decoratedItems = useMemo(() => parsed.items.map((item) => {
    const threads = positionedThreads.filter((thread) => thread.position.newPath === item.fileDiff.name || thread.position.oldPath === item.fileDiff.name);
    const shownThreads = hideResolved ? threads.filter((thread) => !thread.resolved) : threads;
    const annotations = shownThreads.map((thread) => ({ ...(annotationLocation(thread.position) || { side: fileDiffSide(item.fileDiff), lineNumber: 0 }), metadata: { kind: 'thread', thread, hidden: collapsedThreads.has(thread.id) } }));
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
  const personaMode = organizationMode === 'persona';
  const personaGroups = useMemo(() => personaMode ? groupByPersona(treeItems, outdatedThreads).map((group) => ({ ...group, items: group.items.map((item) => ({ ...item, fileDiff: focusFileDiff(item.fileDiff, item.threads) })) })) : [], [outdatedThreads, personaMode, treeItems]);
  const preReviewActive = smartMode && activeItemId === 'pre-review';
  const items = useMemo(() => smartMode ? smartOrganization.items : personaMode ? personaGroups.flatMap((group) => group.items) : treeItems, [personaGroups, personaMode, smartMode, smartOrganization, treeItems]);
  const smartSteps = useMemo(() => smartMode ? smartOrganization.steps.filter((step) => (!search || step.items.some((item) => item.fileDiff.name.toLowerCase().includes(search.toLowerCase()))) && (!onlyThreads || step.items.some((item) => item.threads.length))) : [], [onlyThreads, search, smartMode, smartOrganization]);
  const indexedItems = useMemo(() => smartMode ? smartSteps.flatMap((step) => step.items) : items.filter((item) => (!search || item.fileDiff.name.toLowerCase().includes(search.toLowerCase())) && (!onlyThreads || item.threads.length)), [items, onlyThreads, search, smartMode, smartSteps]);
  const effectiveActiveItemId = indexedItems.some((item) => item.id === activeItemId) ? activeItemId : indexedItems[0]?.id;
  const activeStep = smartMode ? smartSteps.find((step) => step.items.some((item) => item.id === effectiveActiveItemId)) : null;
  const activeFileIndex = smartMode ? smartSteps.indexOf(activeStep) : indexedItems.findIndex((item) => item.id === effectiveActiveItemId);
  const filteredItems = useMemo(() => oneFile ? smartMode ? activeStep?.items || [] : indexedItems.filter((item) => item.id === effectiveActiveItemId) : indexedItems, [activeStep, effectiveActiveItemId, indexedItems, oneFile, smartMode]);
  // CodeView updates existing items only when their numeric version changes.
  const [renderState, setRenderState] = useState({ items: filteredItems, revision: 0 });
  if (renderState.items !== filteredItems) setRenderState({ items: filteredItems, revision: renderState.revision + 1 });
  const versionedItems = useMemo(() => filteredItems.map((item) => ({ ...item, version: renderState.revision })), [filteredItems, renderState.revision]);
  const indexedIds = useMemo(() => new Set(indexedItems.map((item) => item.id)), [indexedItems]);
  const smartGroups = useMemo(() => smartMode ? smartOrganization.groups.map((group) => {
    const steps = group.steps.filter((step) => step.items.some((item) => indexedIds.has(item.id)));
    return { ...group, steps, items: steps.flatMap((step) => step.items) };
  }).filter((group) => group.steps.length) : [], [indexedIds, smartMode, smartOrganization]);
  const visiblePersonaGroups = useMemo(() => personaGroups.map((group) => ({ ...group, items: group.items.filter((item) => indexedIds.has(item.id)) })).filter((group) => group.items.length || group.outdatedThreads.length), [indexedIds, personaGroups]);
  const personaOutdatedGroups = useMemo(() => personaGroups.filter((group) => group.outdatedThreads.length), [personaGroups]);
  const navigableThreads = useMemo(() => items.flatMap((item) => item.threads.filter((thread) => !thread.resolved).map((thread) => ({ item, thread, location: annotationLocation(thread.position) }))).filter((entry) => entry.location?.lineNumber != null), [items]);

  const requestSmartOrganization = useCallback(async (regenerate = false) => {
    if (!headSha) return;
    setSmartLoading(true);
    setSmartError(null);
    try {
      const next = SMART_DIFF_FIXTURE
        ? createSmartDiffFixture(parsed.items, headSha)
        : await organizeSmartDiff(projectId, changeNumber, headSha, { provider, regenerate, execution: { harness, model } });
      if (next.headSha !== headSha) throw new Error('Arnold returned organization for an outdated diff.');
      setSmartDiff(next);
      if (next.status === 'completed') setOrganizationMode('smart');
    } catch (error) {
      setSmartError(error);
    } finally {
      setSmartLoading(false);
    }
  }, [changeNumber, harness, headSha, model, parsed.items, projectId, provider]);

  const persistViewed = useCallback((nextFiles, nextSteps, nextRatings, previousFiles, previousSteps, previousRatings) => {
    setViewed(nextFiles);
    setStoryViewed(nextSteps);
    setFocusRatings(nextRatings);
    setViewedError(null);
    const version = ++viewedWriteVersion.current;
    viewedWriteRef.current = viewedWriteRef.current.catch(() => {}).then(() => updateViewedFiles(projectId, changeNumber, headSha, [...nextFiles], [...nextSteps], Object.fromEntries(nextRatings), { provider }))
      .then((progress) => { if (version === viewedWriteVersion.current) { setViewed(new Set(progress.filePaths)); setStoryViewed(new Set(progress.storyStepIds)); setFocusRatings(new Map(Object.entries(progress.focusAreaFeedback))); } })
      .catch((error) => { if (version === viewedWriteVersion.current) { setViewed(previousFiles); setStoryViewed(previousSteps); setFocusRatings(previousRatings); setViewedError(error); } });
  }, [changeNumber, headSha, projectId, provider]);
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
    const files = await fetchMergeRequestFiles(projectId, changeNumber, oldPath, fileDiff.name, { baseSha, headSha }, { provider });
    const newFile = { name: fileDiff.name, contents: files.newContent, cacheKey: `${projectId}:${headSha || fileDiff.newObjectId}:${fileDiff.name}` };
    return trimStoryFileContext(fileDiff, fileDiff.type === 'rename-pure' ? { oldFile: null, newFile } : { oldFile: { name: oldPath, contents: files.oldContent, cacheKey: `${projectId}:${baseSha || fileDiff.prevObjectId}:${oldPath}` }, newFile });
  }, [baseSha, changeNumber, headSha, projectId, provider]);

  const openLineComposer = useCallback((item, range) => {
    const position = positionForSelection(item.fileDiff, range);
    if (!position) return;
    setInlineComposer({ itemId: item.sourceId || item.id, viewItemId: item.id, location: { side: range.side || 'additions', lineNumber: Math.max(range.start, range.end) }, position: { ...position, baseSha, startSha, headSha }, suggestion: contentForRange(item.fileDiff, range.side || 'additions', range.start, range.end) });
  }, [baseSha, headSha, startSha]);
  const toggleFileComposer = useCallback((item, anchor) => {
    setSelectedLines(null);
    setInlineComposer((current) => current?.file && current.itemId === item.id ? null : { itemId: item.id, file: true, anchor, position: { baseSha, startSha, headSha, oldPath: item.fileDiff.prevName || item.fileDiff.name, newPath: item.fileDiff.name, oldLine: null, newLine: null }, suggestion: '' });
  }, [baseSha, headSha, startSha]);
  const options = useMemo(() => ({
    theme: { dark: 'pierre-dark-soft', light: 'pierre-light' }, themeType: theme, diffStyle, overflow: wrap ? 'wrap' : 'scroll', diffIndicators: 'classic', stickyHeaders: true,
    enableGutterUtility: enabled, enableLineSelection: false, lineHoverHighlight: enabled ? 'both' : 'disabled', loadDiffFiles: personaMode ? undefined : loadDiffFiles, hunkSeparators: personaMode ? 'simple' : 'line-info', expansionLineCount: 20, lineDiffType: smartMode ? 'none' : 'word-alt',
    onGutterUtilityClick: enabled ? (range, context) => openLineComposer(context.item, range) : undefined,
    layout: { paddingTop: 12, paddingBottom: 12, gap: 12 },
  }), [diffStyle, enabled, loadDiffFiles, openLineComposer, personaMode, smartMode, theme, wrap]);

  const closeComposer = useCallback(() => { setInlineComposer(null); setSelectedLines(null); }, [setInlineComposer]);
  const toggleFocusVisibility = useCallback((stepId) => setHiddenFocusAreas((current) => toggleViewedPaths(current, [stepId])), []);
  const toggleThreadVisibility = useCallback((threadId) => setCollapsedThreads((current) => toggleViewedPaths(current, [threadId])), []);
  const toggleFocusMode = useCallback(() => {
    if (!onFocusModeChange) return;
    setFocusInsightsOpen(false);
    onFocusModeChange(!focusMode);
  }, [focusMode, onFocusModeChange]);
  const toggleInsightsCollapsed = useCallback(() => focusMode ? setFocusInsightsOpen((value) => !value) : setInsightsCollapsed((value) => !value), [focusMode]);
  const renderComposer = useCallback((composer) => <DiscussionComposer className="m-2" provider={provider} projectId={projectId} changeNumber={changeNumber} position={composer.position} suggestion={composer.suggestion} label={`Comment on ${composer.position.newPath}`} enabled={enabled} onChanged={() => { closeComposer(); onChanged?.(); }} onRefresh={onChanged} onCancel={closeComposer} />, [changeNumber, closeComposer, enabled, onChanged, projectId, provider]);
  const renderAnnotation = useCallback((annotation) => annotation.metadata.kind === 'focus'
    ? hiddenFocusAreas.has(annotation.metadata.stepId)
      ? <div className="flex items-center justify-between gap-3 border-y border-border bg-surface px-4 py-1.5"><span className="font-mono text-xs uppercase tracking-[0.08em] text-muted">Reviewer focus hidden · lines {annotation.metadata.startLine}–{annotation.metadata.endLine}</span><Button className="!h-6 !px-2 !text-xs" size="xs" variant="ghost" onClick={() => toggleFocusVisibility(annotation.metadata.stepId)}><Eye className="h-3.5 w-3.5" /> Show</Button></div>
      : <div className="flex flex-wrap items-end justify-between gap-4 border-y border-accent bg-[var(--accent-soft)] px-4 py-3"><div className="min-w-0 max-w-2xl flex-1"><div className="flex items-center gap-1"><p className="font-mono text-xs uppercase tracking-[0.08em] text-accent">Reviewer focus · lines {annotation.metadata.startLine}–{annotation.metadata.endLine}</p><Button className="!h-5 !px-1.5" size="xs" variant="ghost" title="Hide reviewer focus" aria-label="Hide reviewer focus" onClick={() => toggleFocusVisibility(annotation.metadata.stepId)}><EyeOff className="h-3.5 w-3.5" /></Button></div><ReviewerFocusDetails className="mt-1" title={annotation.metadata.title} explanation={annotation.metadata.explanation} /></div><div className="flex items-center gap-1"><span className="mr-1 font-mono text-xs text-muted">Was this useful?</span><Button className="!h-6 !px-2 !text-xs" size="xs" variant={focusRatings.get(annotation.metadata.stepId) === 'useful' ? 'primary' : 'secondary'} aria-pressed={focusRatings.get(annotation.metadata.stepId) === 'useful'} onClick={() => rateFocus(annotation.metadata.stepId, 'useful')}>Useful</Button><Button className="!h-6 !px-2 !text-xs" size="xs" variant={focusRatings.get(annotation.metadata.stepId) === 'not-useful' ? 'primary' : 'secondary'} aria-pressed={focusRatings.get(annotation.metadata.stepId) === 'not-useful'} onClick={() => rateFocus(annotation.metadata.stepId, 'not-useful')}>Not useful</Button></div></div>
    : annotation.metadata.kind === 'thread'
    ? annotation.metadata.hidden
      ? <HiddenDiscussionThread thread={annotation.metadata.thread} headSha={headSha} onShow={() => toggleThreadVisibility(annotation.metadata.thread.id)} />
      : <DiscussionThread className="m-2" thread={annotation.metadata.thread} provider={provider} projectId={projectId} changeNumber={changeNumber} headSha={headSha} enabled={enabled} collapsible onCollapsedChange={() => toggleThreadVisibility(annotation.metadata.thread.id)} onChanged={onChanged} />
    : renderComposer(annotation.metadata),
  [changeNumber, enabled, focusRatings, headSha, hiddenFocusAreas, onChanged, projectId, provider, rateFocus, renderComposer, toggleFocusVisibility, toggleThreadVisibility]);
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
  const showOutdated = useCallback((key) => {
    setOutdatedOpen(true);
    requestAnimationFrame(() => document.getElementById(`persona-outdated-${key}`)?.scrollIntoView({ block: 'start', behavior: 'smooth' }));
  }, []);
  const moveFile = useCallback((offset) => scrollTo(smartMode ? smartSteps[activeFileIndex + offset]?.items[0] : indexedItems[activeFileIndex + offset]), [activeFileIndex, indexedItems, scrollTo, smartMode, smartSteps]);
  const scrollToThread = useCallback((entry) => {
    if (!entry) return;
    setActiveItemId(entry.item.id);
    setActiveThreadId(entry.thread.id);
    setCollapsedThreads((current) => {
      if (!current.has(entry.thread.id)) return current;
      const next = new Set(current);
      next.delete(entry.thread.id);
      return next;
    });
    toggleCollapsed(entry.item.id, false);
    const target = { type: 'line', id: entry.item.id, lineNumber: entry.location.lineNumber, side: entry.location.side, align: 'center', behavior: 'smooth-auto' };
    let frames = 0;
    let timer;
    const poll = () => {
      const viewer = viewerRef.current;
      if (viewer?.getInstance() && viewer.getItem(entry.item.id)) {
        timer = window.setTimeout(() => viewer.scrollTo(target), 60);
      } else if (frames++ < 240) {
        requestAnimationFrame(poll);
      }
    };
    requestAnimationFrame(poll);
    return () => { window.clearTimeout(timer); };
  }, [setCollapsedThreads, toggleCollapsed]);

  const lastFocusKeyRef = useRef(null);
  useEffect(() => {
    if (!focusThreadId) return undefined;
    const id = typeof focusThreadId === 'object' ? focusThreadId.discussionId : focusThreadId;
    const key = typeof focusThreadId === 'object' ? `${id}:${focusThreadId.at}` : id;
    if (lastFocusKeyRef.current === key) return undefined;
    lastFocusKeyRef.current = key;
    const locate = (thread) => thread.discussionId === id || thread.id === id;
    const entry = navigableThreads.find(({ thread }) => locate(thread))
      ?? (() => {
        const thread = positionedThreads.find(locate);
        const item = items.find((candidate) => thread && (thread.position.newPath === candidate.fileDiff.name || thread.position.oldPath === candidate.fileDiff.name));
        const location = thread && annotationLocation(thread.position);
        return thread && item && location?.lineNumber != null ? { item, thread, location } : null;
      })();
    if (entry) scrollToThread(entry);
    return undefined;
  }, [focusThreadId, items, navigableThreads, positionedThreads, scrollToThread]);

  const syncActiveFile = useCallback((scrollTop, viewer) => {
    onScrolledChange?.(scrollTop > 32);
    const id = viewer.getScrollAnchor(scrollTop)?.id;
    if (id) setActiveItemId((current) => current === id ? current : id);
  }, [onScrolledChange, setActiveItemId]);

  useEffect(() => {
    function onKeyDown(event) {
      if (event.defaultPrevented || event.metaKey || event.ctrlKey || event.altKey || event.composedPath().some((target) => /^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName) || target.isContentEditable)) return;
      if (event.key === 'f' && event.shiftKey) { event.preventDefault(); searchRef.current?.focus(); return; }
      if (event.key.toLowerCase() === 'd' && event.shiftKey) { event.preventDefault(); setDiffStyle((value) => value === 'split' ? 'unified' : 'split'); return; }
      if (event.key === 'z' && !event.shiftKey) { event.preventDefault(); toggleFocusMode(); return; }
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
  }, [activeStep, activeThreadId, effectiveActiveItemId, indexedItems, items, navigableThreads, preReviewActive, scrollTo, scrollToThread, smartMode, smartSteps, toggleCollapsed, toggleFocusMode, toggleStoryViewed, toggleViewed]);

  const pipelineLink = smartDiff?.pipelineUrl ? <a className="text-accent-ink hover:underline" href={smartDiff.pipelineUrl} target="_blank" rel="noreferrer">View pipeline{smartDiff.pipelineId ? ` #${smartDiff.pipelineId}` : ''} ↗</a> : null;
  const executionPicker = (idPrefix, className = '') => <>
    {!SMART_DIFF_FIXTURE && executionOptions ? (
      <ExecutionPicker className={className} idPrefix={idPrefix} options={executionOptions} harness={harness} model={model} onChange={setExecution} disabled={smartLoading} size="sm" />
    ) : null}
    {harness === 'copilot' && selectedHarness && !selectedHarness.available ? <p className="mt-3 text-sm text-bad" role="alert">Story generation needs a Copilot connection. <a className="text-accent-ink underline" href="/settings">Connect Copilot in Settings</a> first.</p> : null}
  </>;
  if (parsed.error) return <ErrorState className={className} title="Could not render merge request diff" error={parsed.error} {...props} />;
  if (!parsed.items.length) return <EmptyState className={className} title="No source changes" description="GitLab returned no changed files for this merge request." {...props} />;
  return <div className={`flex min-h-0 flex-1 flex-col bg-surface ${className}`} {...props}>
    <div className="chead">
      <div className="modetabs" aria-label="Review organization">
        <button className={organizationMode === 'tree' ? 'on' : ''} aria-pressed={organizationMode === 'tree'} onClick={() => setOrganizationMode('tree')}>Classic</button>
        <button className={organizationMode === 'smart' ? 'on' : ''} aria-pressed={organizationMode === 'smart'} onClick={() => { setOrganizationMode('smart'); setActiveItemId('pre-review'); }}>Story</button>
        <button className={personaMode ? 'on' : ''} aria-pressed={personaMode} onClick={() => setOrganizationMode('persona')}>Persona</button>
      </div>
      {smartMode && smartDiff.status === 'completed' ? <Button size="xs" variant="secondary" onClick={() => setRegenerateOpen((value) => !value)} disabled={smartLoading} aria-expanded={regenerateOpen} aria-controls="story-regenerate">Regenerate</Button> : null}
      {focusMode && selectedVersion === 'latest' ? null : <select className="h-8 min-w-0 max-w-full border border-border-2 bg-surface-2 px-2 text-[13px] text-fg" value={selectedVersion} onChange={(event) => onVersionChange?.(event.target.value)} aria-label="Diff version"><option value="latest">Latest · {versions[0]?.commitTitle || versions[0]?.headSha?.slice(0, 8) || 'changes'}</option>{versions.slice(1).map((version) => <option key={version.id} value={String(version.id)}>{version.commitTitle || version.headSha.slice(0, 8)} · {new Date(version.createdAt).toLocaleString()}</option>)}</select>}
      <input ref={searchRef} className="h-8 min-w-48 flex-1 border border-border-2 bg-surface-2 px-2 text-[13px] text-fg focus:border-accent focus:outline-none" type="search" placeholder="Filter files (Shift+F)" value={search} onChange={(event) => setSearch(event.target.value)} />
      <MenuDropdown label="Diff preferences" icon={SlidersHorizontal} onSelect={() => {}}>
        <button type="button" role="menuitemcheckbox" aria-checked={oneFile} className="flex w-full items-center gap-2.5 px-3 py-1.5 text-left font-mono text-[12px] text-muted hover:bg-surface-2 hover:text-fg" onClick={() => setOneFile((value) => !value)}>
          <span className={`grid h-3.5 w-3.5 place-items-center border ${oneFile ? 'border-accent bg-accent text-on-accent' : 'border-border-2'}`} aria-hidden="true">{oneFile ? <Check className="h-2.5 w-2.5" strokeWidth={3} /> : null}</span>
          Show one file at a time
        </button>
        <button type="button" role="menuitemcheckbox" aria-checked={wrap} className="flex w-full items-center gap-2.5 px-3 py-1.5 text-left font-mono text-[12px] text-muted hover:bg-surface-2 hover:text-fg" onClick={() => setWrap((value) => !value)}>
          <span className={`grid h-3.5 w-3.5 place-items-center border ${wrap ? 'border-accent bg-accent text-on-accent' : 'border-border-2'}`} aria-hidden="true">{wrap ? <Check className="h-2.5 w-2.5" strokeWidth={3} /> : null}</span>
          Wrap lines
        </button>
        <button type="button" role="menuitemcheckbox" aria-checked={hideResolved} className="flex w-full items-center gap-2.5 px-3 py-1.5 text-left font-mono text-[12px] text-muted hover:bg-surface-2 hover:text-fg" onClick={() => setHideResolved((value) => !value)}>
          <span className={`grid h-3.5 w-3.5 place-items-center border ${hideResolved ? 'border-accent bg-accent text-on-accent' : 'border-border-2'}`} aria-hidden="true">{hideResolved ? <Check className="h-2.5 w-2.5" strokeWidth={3} /> : null}</span>
          Unresolved comments only
        </button>
        <button type="button" role="menuitemcheckbox" aria-checked={onlyThreads} className="flex w-full items-center gap-2.5 px-3 py-1.5 text-left font-mono text-[12px] text-muted hover:bg-surface-2 hover:text-fg" onClick={() => setOnlyThreads((value) => !value)}>
          <span className={`grid h-3.5 w-3.5 place-items-center border ${onlyThreads ? 'border-accent bg-accent text-on-accent' : 'border-border-2'}`} aria-hidden="true">{onlyThreads ? <Check className="h-2.5 w-2.5" strokeWidth={3} /> : null}</span>
          Files with discussions only
        </button>
        <button type="button" role="menuitemcheckbox" aria-checked={collapsedThreads.size > 0} className="flex w-full items-center gap-2.5 px-3 py-1.5 text-left font-mono text-[12px] text-muted hover:bg-surface-2 hover:text-fg" onClick={() => setCollapsedThreads((current) => current.size ? new Set() : new Set(positionedThreads.map((thread) => thread.id)))}>
          <span className={`grid h-3.5 w-3.5 place-items-center border ${collapsedThreads.size ? 'border-accent bg-accent text-on-accent' : 'border-border-2'}`} aria-hidden="true">{collapsedThreads.size ? <Check className="h-2.5 w-2.5" strokeWidth={3} /> : null}</span>
          Hide all comments{collapsedThreads.size ? ` (${collapsedThreads.size})` : ''}
        </button>
        <div className="my-1 h-px bg-border" />
        {['split', 'unified'].map((style) => (
          <button key={style} type="button" role="menuitemradio" aria-checked={diffStyle === style} className={`flex w-full items-center gap-2.5 px-3 py-1.5 text-left font-mono text-[12px] ${diffStyle === style ? 'text-fg' : 'text-muted hover:bg-surface-2 hover:text-fg'}`} onClick={() => setDiffStyle(style)}>
            <span className={`h-1.5 w-1.5 rounded-full ${diffStyle === style ? 'bg-accent' : 'bg-transparent'}`} aria-hidden="true" />
            {style === 'split' ? 'Side-by-side' : 'Inline'}
          </button>
        ))}
      </MenuDropdown>
      {onFocusModeChange ? <button type="button" className={`flex h-8 items-center gap-1.5 border px-2 font-mono text-[11px] font-semibold transition-colors active:translate-y-px focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring ${focusMode ? 'border-accent bg-[var(--accent-soft)] text-accent-ink' : 'border-border-2 bg-surface text-muted hover:bg-surface-2 hover:text-fg'}`} aria-pressed={focusMode} title={`${focusMode ? 'Exit' : 'Enter'} focus mode (Z)`} onClick={toggleFocusMode}>{focusMode ? <Minimize2 className="h-4 w-4" aria-hidden="true" /> : <Maximize2 className="h-4 w-4" aria-hidden="true" />}{focusMode ? 'Exit focus' : 'Focus'}</button> : null}
      {insights ? <button className={`panetog panetog-mobile ${showInsights ? '' : 'off'}`} aria-expanded={showInsights} onClick={() => setShowInsights((value) => !value)}>Insights</button> : null}
    </div>
    {smartMode && regenerateOpen ? <section id="story-regenerate" className="border-b border-border bg-surface px-3 py-3" aria-label="Regenerate story">
      {executionPicker('story-regenerate')}
      <div className="mt-3 flex justify-end gap-2">
        {executionGate ? <span id="story-regenerate-reason" className="self-center font-mono text-[11px] text-muted" role="status">{executionGate}</span> : null}
        <Button size="xs" variant="secondary" onClick={() => setRegenerateOpen(false)}>Cancel</Button>
        <Button size="xs" variant="primary" onClick={() => { setRegenerateOpen(false); requestSmartOrganization(true); }} disabled={smartLoading || Boolean(executionGate)} aria-describedby={executionGate ? 'story-regenerate-reason' : undefined}>Regenerate story</Button>
      </div>
    </section> : null}
    {viewedError ? <div role="alert" className="border-b border-border bg-[color-mix(in_oklch,var(--bad)_8%,transparent)] px-3 py-2 text-sm text-bad">Could not synchronize review progress. Try again.</div> : null}
    {!storyUnavailable && smartError ? <div role="alert" className="flex items-center justify-between gap-3 border-b border-border bg-[color-mix(in_oklch,var(--bad)_8%,transparent)] px-3 py-2 text-sm text-bad"><span>{smartError.message || 'Could not load smart organization.'} The standard diff is still available.</span><Button size="xs" variant="secondary" onClick={() => { setSmartError(null); setOrganizationMode('smart'); setSmartRefreshToken((value) => value + 1); }}>Retry</Button></div> : null}
    {!storyUnavailable && !smartError && storyPending ? <div className="border-b border-border px-3 py-2 font-mono text-xs text-muted">Generating story and assessing comments at {headSha?.slice(0, 8)}… You can keep reviewing in Classic mode.{pipelineLink ? <> {pipelineLink}</> : null}</div> : null}
    {!storyUnavailable && smartDiff?.status === 'completed' && smartDiff.commentAssessmentStatus === 'failed' ? <div role="alert" className="border-b border-border bg-[color-mix(in_oklch,var(--bad)_8%,transparent)] px-3 py-2 text-sm text-bad">Story generated, but comment assessment failed. Regenerate to retry.</div> : null}
    {!storyUnavailable && !smartError && smartTimedOut ? <div role="alert" className="flex items-center justify-between gap-3 border-b border-border bg-[color-mix(in_oklch,var(--bad)_8%,transparent)] px-3 py-2 text-sm text-bad"><span>Organization timed out. The standard diff is still available.</span><Button size="xs" variant="secondary" onClick={() => requestSmartOrganization()} disabled={smartLoading}>Retry</Button></div> : null}
    {!storyUnavailable && !smartError && smartDiff?.headSha === headSha && smartDiff.status === 'failed' ? <div role="alert" className="flex items-center justify-between gap-3 border-b border-border bg-[color-mix(in_oklch,var(--bad)_8%,transparent)] px-3 py-2 text-sm text-bad"><span>{smartDiff.errorMessage || 'Smart organization failed.'} The standard diff is still available.</span><Button size="xs" variant="secondary" onClick={() => requestSmartOrganization()} disabled={smartLoading}>Retry</Button></div> : null}
    {storyUnavailable ? <div className="grid min-h-[420px] flex-1 place-items-center overflow-y-auto p-6">
      <section className="w-full max-w-xl border border-border bg-surface p-6 text-center sm:p-10" aria-label="Generate story">
        <h2 className="font-display text-2xl font-semibold text-fg">Review this change as a story</h2>
        <p className="mt-3 text-sm text-muted" role="status">{smartLoading ? 'Checking story…' : storyPending ? `Generating story and assessing comments at ${headSha?.slice(0, 8)}… You can continue reviewing in Classic mode.` : 'Generate an AI overview, guided review steps, and comment assessment for this change.'}</p>
        {storyPending && pipelineLink ? <p className="mt-2 font-mono text-xs">{pipelineLink}</p> : null}
        {smartError || smartTimedOut || smartDiff?.status === 'failed' ? <p className="mt-3 text-sm text-bad" role="alert">{smartError?.message || (smartTimedOut ? 'Story generation timed out.' : smartDiff.errorMessage || 'Story generation failed.')}</p> : null}
        {executionPicker('story', 'mt-4')}
        <Button className="mt-6" variant="primary" onClick={() => smartError ? setSmartRefreshToken((value) => value + 1) : requestSmartOrganization()} disabled={!headSha || smartLoading || (storyPending && !smartError) || (!smartError && Boolean(executionGate))} aria-describedby={executionGate && !smartError ? 'story-generate-reason' : undefined}>{smartError ? 'Retry loading story' : smartTimedOut || smartDiff?.status === 'failed' ? 'Retry story generation' : 'Generate story'}</Button>
        {executionGate && !smartError ? <p id="story-generate-reason" className="mt-2 font-mono text-[11px] text-muted" role="status">{executionGate}</p> : null}
      </section>
    </div> :
    <div ref={workspaceRef} className="review-workspace relative grid min-h-0 min-w-0 flex-1" data-guide-open={showGuide} data-has-insights={Boolean(insights)} data-insights-open={Boolean(insights && !insightsHidden)}>
      <aside className="relative hidden min-h-0 w-full flex-col overflow-hidden border-r border-border bg-surface lg:flex">
        <div className={`flex h-11 shrink-0 items-center border-b border-border ${showGuide ? 'justify-between px-3' : 'justify-center'}`}>
          {showGuide ? <span className="font-mono text-xs font-semibold uppercase tracking-[0.08em] text-muted">Guide</span> : null}
          <button type="button" className="grid h-8 w-8 place-items-center text-muted hover:text-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" aria-label={`${showGuide ? 'Collapse' : 'Expand'} guide`} aria-expanded={showGuide} onClick={() => setShowGuide((value) => !value)}>{showGuide ? <PanelLeftClose className="h-4 w-4" /> : <PanelLeftOpen className="h-4 w-4" />}</button>
        </div>
        {showGuide ? personaMode ? <PersonaDiffTree className="!max-h-none min-h-0 flex-1" groups={visiblePersonaGroups} activeItemId={effectiveActiveItemId} onNavigate={scrollTo} onShowOutdated={showOutdated} /> : smartMode ? <SmartDiffTree className="!max-h-none min-h-0 flex-1" groups={smartGroups} storyViewed={storyViewed} activeItemId={preReviewActive ? 'pre-review' : effectiveActiveItemId} onNavigate={scrollTo} onPreReview={() => setActiveItemId('pre-review')} onToggleSteps={toggleStoryViewed} /> : <DiffFileTree className="!max-h-none min-h-0 flex-1" items={indexedItems} viewed={viewed} activeItemId={effectiveActiveItemId} onNavigate={scrollTo} onToggleViewed={toggleViewed} onToggleCollapsed={toggleCollapsed} /> : null}
        {showGuide ? <div className="pane-resizer -right-1 hidden lg:block" role="separator" aria-label="Resize review guide" aria-orientation="vertical" tabIndex={0} onPointerDown={(event) => startPaneResize('guide', event)} onPointerMove={(event) => continuePaneResize('guide', event)} onKeyDown={(event) => resizePaneWithKeyboard('guide', event)} /> : null}
      </aside>
      <main className="relative flex min-h-0 min-w-0 flex-col overflow-hidden bg-bg">{preReviewActive ? <PreReview summary={smartDiff.summary} usage={smartDiff.usage} onStart={() => scrollTo(smartGroups[0]?.steps[0]?.items[0])} /> : <>{activeStep ? <section className="max-h-[40%] shrink-0 overflow-y-auto border-b border-border bg-surface px-4 py-3" aria-label="Current review step"><p className="font-mono text-xs uppercase tracking-[0.08em] text-accent">Review step</p><h3 className="mt-1 text-sm font-semibold text-fg">{activeStep.title}</h3><details key={activeStep.id} className="mt-2 text-sm text-muted"><summary className="cursor-pointer font-medium text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">Why this matters and what to inspect</summary><p className="mt-2 leading-5">{activeStep.explanation}</p></details></section> : null}{personaMode && !personaGroups.length ? <PersonaEmptyState /> : personaMode && !items.length ? null : <CodeView ref={viewerRef} items={versionedItems} selectedLines={selectedLines} onSelectedLinesChange={setSelectedLines} onScroll={syncActiveFile} className="min-h-0 min-w-0 flex-1 overflow-auto bg-surface-2" options={options} renderAnnotation={renderAnnotation} renderHeaderFilenameSuffix={(item) => <Button className="!px-2" size="xs" variant="ghost" title="Copy file path" aria-label={`Copy path for ${item.fileDiff.name}`} onClick={() => navigator.clipboard.writeText(item.fileDiff.name)}><Copy className="h-4 w-4" /></Button>} renderHeaderMetadata={(item) => {
        const step = smartMode ? smartSteps.find((candidate) => candidate.items.some((candidateItem) => candidateItem.id === item.id)) : null;
        const reviewed = step ? storyViewed.has(step.id) : viewed.has(item.fileDiff.name);
        return <div className="flex items-center gap-1">{item.persona ? <span className="mr-1 font-mono text-xs text-accent-ink" title={`Findings from ${item.persona.name}`}>{item.persona.name}</span> : null}<Button className="!px-2" size="xs" variant="ghost" title="Comment on file" aria-label={`Comment on ${item.fileDiff.name}`} disabled={!enabled} aria-haspopup="dialog" aria-expanded={Boolean(inlineComposer?.file && inlineComposer.itemId === item.id)} onClick={(event) => toggleFileComposer(item, event.currentTarget)}><MessageSquare className="h-4 w-4" /></Button><label className="flex h-7 cursor-pointer items-center gap-1.5 px-2 font-mono text-xs text-muted hover:text-fg"><input className="h-3.5 w-3.5 accent-accent" type="checkbox" checked={reviewed} aria-label={`Mark ${step ? 'step' : 'file'} viewed`} onChange={() => step ? toggleStoryViewed([step.id]) : toggleViewed(item.fileDiff.name)} /><span>Viewed</span></label>{webUrl ? <a className="grid h-7 w-8 place-items-center text-muted hover:text-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" href={`${webUrl}/diffs`} target="_blank" rel="noreferrer" title="Open changes in GitLab" aria-label="Open changes in GitLab"><Gitlab className="h-4 w-4" /></a> : null}</div>;
      }} />}{personaMode && personaOutdatedGroups.length ? <PersonaOutdatedThreads groups={personaOutdatedGroups} fill={!items.length} open={outdatedOpen || !items.length} onToggle={setOutdatedOpen} provider={provider} projectId={projectId} changeNumber={changeNumber} headSha={headSha} enabled={enabled} onChanged={onChanged} /> : null}{oneFile && !(personaMode && !items.length) ? (
        <div ref={navRef} className="pointer-events-none absolute inset-x-0 bottom-0 z-[25] flex justify-center pb-3">
          <div className="pointer-events-auto relative flex items-center gap-1 rounded-full border border-border-2 bg-surface/95 py-1 pl-1 pr-2 shadow-md backdrop-blur">
            <button type="button" className="grid h-7 w-7 place-items-center rounded-full text-muted hover:bg-surface-2 hover:text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-40" aria-label={`Previous ${smartMode ? 'step' : 'file'}`} disabled={activeFileIndex <= 0} onClick={() => moveFile(-1)}><ChevronLeft className="h-4 w-4" /></button>
            <button type="button" className="min-w-16 rounded-full px-1 py-1 font-mono text-xs font-semibold text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" aria-label={`Show ${smartMode ? 'steps' : 'files'}`} aria-expanded={navExpanded} onClick={() => setNavExpanded((value) => !value)}>
              {smartMode ? 'Step' : 'File'} {activeFileIndex + 1} / {smartMode ? smartSteps.length : indexedItems.length}
            </button>
            <button type="button" className="grid h-7 w-7 place-items-center rounded-full text-muted hover:bg-surface-2 hover:text-fg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-40" aria-label={`Next ${smartMode ? 'step' : 'file'}`} disabled={activeFileIndex >= (smartMode ? smartSteps.length : indexedItems.length) - 1} onClick={() => moveFile(1)}><ChevronRight className="h-4 w-4" /></button>
            {navExpanded ? <div role="menu" className="absolute bottom-11 left-1/2 z-30 max-h-72 w-72 -translate-x-1/2 overflow-y-auto border border-border-2 bg-surface py-1 shadow-lg">
              {(smartMode ? smartSteps : indexedItems).map((entry, index) => (
                <button key={entry.id} type="button" role="menuitem" className={`flex w-full items-center gap-2 px-3 py-1.5 text-left font-mono text-[12px] ${index === activeFileIndex ? 'bg-[var(--accent-soft)] text-fg' : 'text-muted hover:bg-surface-2 hover:text-fg'}`} onClick={() => { setNavExpanded(false); scrollTo(smartMode ? entry.items[0] : entry); }}>
                  <span className={`h-1.5 w-1.5 shrink-0 ${index === activeFileIndex ? 'bg-accent' : 'bg-transparent'}`} aria-hidden="true" />
                  <span className="truncate">{entry.title || entry.fileDiff?.name || entry.id}</span>
                </button>
              ))}
            </div> : null}
          </div>
        </div>
      ) : null}</>}</main>
      {insights ? <aside className={`${showInsights ? 'block' : 'hidden'} absolute inset-y-0 right-0 z-20 min-h-0 w-[min(332px,100%)] overflow-y-auto border-l border-border bg-surface shadow-lg xl:relative xl:block xl:w-full xl:shadow-none`}><div className={`sticky top-0 z-10 flex h-11 items-center border-b border-border bg-surface ${insightsHidden ? 'justify-center' : 'justify-between px-3'}`}>{insightsHidden ? null : <span className="font-mono text-xs font-semibold uppercase tracking-[0.08em] text-muted">Insights</span>}<button type="button" className="hidden h-8 w-8 place-items-center text-muted hover:text-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring xl:grid" aria-label={`${insightsHidden ? 'Expand' : 'Collapse'} insights`} aria-expanded={!insightsHidden} onClick={toggleInsightsCollapsed}>{insightsHidden ? <PanelRightOpen className="h-4 w-4" /> : <PanelRightClose className="h-4 w-4" />}</button><button type="button" className="grid h-8 w-8 place-items-center text-muted hover:text-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring xl:hidden" aria-label="Close insights" onClick={() => setShowInsights(false)}><PanelRightClose className="h-4 w-4" /></button></div>{insightsHidden ? null : <div className="min-h-full">{insights}</div>}{insightsHidden ? null : <div className="pane-resizer -left-1 hidden xl:block" role="separator" aria-label="Resize insights" aria-orientation="vertical" tabIndex={0} onPointerDown={(event) => startPaneResize('insights', event)} onPointerMove={(event) => continuePaneResize('insights', event)} onKeyDown={(event) => resizePaneWithKeyboard('insights', event)} />}</aside> : null}
    </div>}
    {inlineComposer?.file ? <FileCommentComposer key={inlineComposer.itemId} anchor={inlineComposer.anchor} provider={provider} projectId={projectId} changeNumber={changeNumber} position={inlineComposer.position} label={`Comment on ${inlineComposer.position.newPath}`} enabled={enabled} onChanged={() => { closeComposer(); onChanged?.(); }} onRefresh={onChanged} onCancel={closeComposer} /> : null}
  </div>;
}

function PersonaOutdatedThreads({ groups, fill, open, onToggle, provider, projectId, changeNumber, headSha, enabled, onChanged }) {
  const total = groups.reduce((sum, group) => sum + group.outdatedThreads.length, 0);
  return <details className={`${fill ? 'min-h-0 flex-1' : 'max-h-[45%] shrink-0 border-t'} overflow-y-auto border-border bg-surface`} open={open} onToggle={(event) => onToggle(event.currentTarget.open)}>
    <summary className="sticky top-0 z-10 cursor-pointer border-b border-border bg-surface px-4 py-2 font-mono text-sm text-accent-ink">{total} {total === 1 ? 'finding' : 'findings'} on earlier versions of this diff</summary>
    {groups.map((group) => <section key={group.key} id={`persona-outdated-${group.key}`} className="border-b border-border px-4 py-3 last:border-b-0" aria-label={`${group.name} findings on earlier versions`}>
      <h3 className="mb-2 flex items-center gap-2 text-sm font-semibold text-fg"><Avatar size="sm" initials={personaInitials(group.name)} variant={group.key === GENERAL_PERSONA_KEY ? 'ai' : 'default'} alt={group.name} />{group.name}<span className="font-mono text-xs font-normal text-muted">{group.outdatedThreads.length} outdated</span></h3>
      <div className="space-y-3">{group.outdatedThreads.map((thread) => <DiscussionThread key={thread.id} thread={thread} provider={provider} projectId={projectId} changeNumber={changeNumber} headSha={headSha} enabled={enabled} showPosition onChanged={onChanged} />)}</div>
    </section>)}
  </details>;
}

function PersonaEmptyState() {
  return <EmptyState className="m-4" title="No AI findings" description="Arnold and review personas have not commented on the current diff yet. Human comments stay in Classic view." />;
}
