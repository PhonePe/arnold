function containsRange(start, end, rangeStart, rangeEnd) {
  return rangeStart != null && rangeEnd != null && rangeStart >= start && rangeEnd < end;
}

function hunkKey(filePath, oldStartLine, newStartLine) {
  return `${filePath}\0${oldStartLine ?? ''}\0${newStartLine ?? ''}`;
}

const contentLines = (contents) => contents.match(/[^\n]*\n|[^\n]+$/g) || [];

export function trimStoryFileContext(fileDiff, files) {
  const hunk = fileDiff.hunks?.length === 1 && fileDiff.hunks[0];
  if (!hunk || !files.oldFile || !files.newFile) return files;
  const oldLines = contentLines(files.oldFile.contents);
  const newLines = contentLines(files.newFile.contents);
  const oldEnd = hunk.deletionStart - (hunk.deletionCount ? 1 : 0) + hunk.deletionCount;
  const newEnd = hunk.additionStart - (hunk.additionCount ? 1 : 0) + hunk.additionCount;
  const trailing = Math.max(0, Math.min(oldLines.length - oldEnd, newLines.length - newEnd));
  if (oldLines.length - oldEnd === newLines.length - newEnd) return files;
  return {
    oldFile: { ...files.oldFile, contents: oldLines.slice(0, oldEnd + trailing).join('') },
    newFile: { ...files.newFile, contents: newLines.slice(0, newEnd + trailing).join('') },
  };
}

function containsLine(hunk, annotation) {
  if (annotation.lineNumber === 0) return false;
  const start = annotation.side === 'deletions' ? hunk.deletionStart : hunk.additionStart;
  const count = annotation.side === 'deletions' ? hunk.deletionCount : hunk.additionCount;
  return annotation.lineNumber >= start && annotation.lineNumber < start + count;
}

function threadBelongsToHunk(thread, hunk) {
  const position = thread.position;
  if (!position || (position.oldLine == null && position.newLine == null)) return false;
  return position.newLine != null
    ? position.newLine >= hunk.additionStart && position.newLine < hunk.additionStart + hunk.additionCount
    : position.oldLine >= hunk.deletionStart && position.oldLine < hunk.deletionStart + hunk.deletionCount;
}

function reviewUnit(item, hunk, id, firstForFile) {
  const fileDiff = hunk ? (() => {
    const deletionOffset = hunk.deletionLineIndex;
    const additionOffset = hunk.additionLineIndex;
    const slicedHunk = {
      ...hunk,
      deletionLineIndex: 0,
      additionLineIndex: 0,
      splitLineStart: 0,
      unifiedLineStart: 0,
      hunkContent: hunk.hunkContent.map((content) => ({
        ...content,
        deletionLineIndex: content.deletionLineIndex - deletionOffset,
        additionLineIndex: content.additionLineIndex - additionOffset,
      })),
    };
    return {
      ...item.fileDiff,
      hunks: [slicedHunk],
      deletionLines: item.fileDiff.deletionLines.slice(deletionOffset, deletionOffset + hunk.deletionCount),
      additionLines: item.fileDiff.additionLines.slice(additionOffset, additionOffset + hunk.additionCount),
      splitLineCount: hunk.splitLineCount,
      unifiedLineCount: hunk.unifiedLineCount,
      cacheKey: `${item.fileDiff.cacheKey || item.fileDiff.name}:${hunk.deletionStart}:${hunk.additionStart}`,
    };
  })() : item.fileDiff;
  const annotations = (item.annotations || []).filter((annotation) => annotation.lineNumber === 0 ? firstForFile : hunk && containsLine(hunk, annotation));
  const threads = (item.threads || []).filter((thread) => {
    const fileLevel = !thread.position || (thread.position.oldLine == null && thread.position.newLine == null);
    return fileLevel ? firstForFile : hunk && threadBelongsToHunk(thread, hunk);
  });
  const stats = hunk ? { additions: hunk.additionLines, deletions: hunk.deletionLines } : item.stats;
  return { ...item, id, sourceId: item.sourceId || item.id, fileDiff, annotations, threads, stats };
}

function focusLocation(change, fileDiff) {
  for (const hunk of fileDiff.hunks || []) {
    if (containsRange(hunk.additionStart, hunk.additionStart + hunk.additionCount, change.newStartLine, change.newEndLine)) {
      return { side: 'additions', lineNumber: change.newStartLine, endLine: change.newEndLine };
    }
    if (containsRange(hunk.deletionStart, hunk.deletionStart + hunk.deletionCount, change.oldStartLine, change.oldEndLine)) {
      return { side: 'deletions', lineNumber: change.oldStartLine, endLine: change.oldEndLine };
    }
  }
  return null;
}

export function applyFocusAreas(items, smartDiff, headSha) {
  if (!smartDiff || smartDiff.status !== 'completed' || smartDiff.headSha !== headSha) return null;

  const byPath = new Map(items.map((item) => [item.fileDiff.name, { ...item, annotations: [...(item.annotations || [])] }]));
  const focusGroup = smartDiff.groups.find((group) => group.id === 'review-focus');
  for (const step of focusGroup?.steps || []) for (const change of step.changes) {
    const item = byPath.get(change.filePath);
    const location = item && focusLocation(change, item.fileDiff);
    if (!location) continue;
    item.annotations.push({
      side: location.side,
      lineNumber: location.lineNumber,
      metadata: {
        kind: 'focus',
        stepId: step.id,
        title: step.title,
        explanation: step.explanation,
        groupTitle: focusGroup.title,
        startLine: location.lineNumber,
        endLine: location.endLine,
      },
    });
  }
  return items.map((item) => {
    const focused = byPath.get(item.fileDiff.name);
    return { ...focused, version: (focused.version || 0) + focused.annotations.filter((annotation) => annotation.metadata.kind === 'focus').length };
  });
}

export function reconcileSmartGroups(items, smartDiff, headSha) {
  if (!smartDiff || smartDiff.status !== 'completed' || smartDiff.headSha !== headSha) return null;

  const byPath = new Map(items.map((item) => [item.fileDiff.name, item]));
  const targets = new Map();
  for (const item of items) {
    if (item.fileDiff.hunks?.length) {
      item.fileDiff.hunks.forEach((hunk) => targets.set(hunkKey(
        item.fileDiff.name,
        hunk.deletionCount ? hunk.deletionStart : null,
        hunk.additionCount ? hunk.additionStart : null,
      ), { item, hunk }));
    } else {
      targets.set(hunkKey(item.fileDiff.name, null, null), { item, hunk: null });
    }
  }

  const assigned = new Set();
  const seenFiles = new Set();
  let serial = 0;
  const makeUnit = ({ item, hunk }) => {
    const firstForFile = !seenFiles.has(item.fileDiff.name);
    seenFiles.add(item.fileDiff.name);
    return reviewUnit(item, hunk, `smart:${serial++}:${item.id}`, firstForFile);
  };
  const groups = smartDiff.groups.filter((group) => group.id !== 'review-focus').flatMap((group) => {
    const steps = group.steps.flatMap((step) => {
      const stepItems = step.changes.flatMap((change) => {
        const key = hunkKey(change.filePath, change.oldStartLine, change.newStartLine);
        const target = targets.get(key);
        if (!target || assigned.has(key)) return [];
        assigned.add(key);
        return [makeUnit(target)];
      });
      if (!stepItems.length) return [];
      stepItems[0].annotations = [{ side: stepItems[0].fileDiff.type === 'deleted' ? 'deletions' : 'additions', lineNumber: 0, metadata: { kind: 'narration', title: step.title, explanation: step.explanation } }, ...stepItems[0].annotations];
      return [{ ...step, items: stepItems }];
    });
    return steps.length ? [{ ...group, steps, items: steps.flatMap((step) => step.items) }] : [];
  });

  const otherSteps = [...byPath.keys()].sort().flatMap((filePath) => {
    const item = byPath.get(filePath);
    const entries = item.fileDiff.hunks?.length
      ? item.fileDiff.hunks.map((hunk) => ({ key: hunkKey(
        filePath,
        hunk.deletionCount ? hunk.deletionStart : null,
        hunk.additionCount ? hunk.additionStart : null,
      ), item, hunk }))
      : [{ key: hunkKey(filePath, null, null), item, hunk: null }];
    const stepItems = entries.filter(({ key }) => !assigned.has(key)).map(makeUnit);
    if (!stepItems.length) return [];
    const explanation = 'Not assigned to a narrated review step.';
    stepItems[0].annotations = [{ side: stepItems[0].fileDiff.type === 'deleted' ? 'deletions' : 'additions', lineNumber: 0, metadata: { kind: 'narration', title: filePath, explanation } }, ...stepItems[0].annotations];
    return [{ id: `other:${filePath}`, title: filePath, explanation, items: stepItems }];
  });
  if (otherSteps.length) groups.push({ id: 'other-changes', title: 'Other changes', rationale: 'Hunks not assigned to an AI-generated review step.', steps: otherSteps, items: otherSteps.flatMap((step) => step.items) });

  const steps = groups.flatMap((group) => group.steps.map((step) => ({ ...step, groupId: group.id })));
  return { groups, steps, items: steps.flatMap((step) => step.items) };
}

export function smartFileItems(items) {
  const files = new Map();
  for (const item of items) {
    const path = item.fileDiff.name;
    const current = files.get(path);
    if (!current) {
      files.set(path, { ...item, stats: { ...item.stats }, threads: [...item.threads] });
    } else {
      files.set(path, {
        ...current,
        stats: {
          additions: current.stats.additions + item.stats.additions,
          deletions: current.stats.deletions + item.stats.deletions,
        },
        threads: [...current.threads, ...item.threads],
      });
    }
  }
  return [...files.values()];
}

export function createSmartDiffFixture(items, headSha) {
  const changes = items.flatMap((item) => (item.fileDiff.hunks || []).map((hunk) => ({
    filePath: item.fileDiff.name,
    oldStartLine: hunk.deletionCount ? hunk.deletionStart : null,
    oldEndLine: hunk.deletionCount ? hunk.deletionStart + hunk.deletionCount - 1 : null,
    newStartLine: hunk.additionCount ? hunk.additionStart : null,
    newEndLine: hunk.additionCount ? hunk.additionStart + hunk.additionCount - 1 : null,
  })));
  return {
    headSha,
    status: 'completed',
    summary: 'Review the highlighted focus areas in the diff.',
    groups: changes.length ? [{
      id: 'demo-story',
      title: 'Core workflow',
      rationale: 'Follow the implementation path through the change.',
      steps: changes.map((change, index) => ({
        id: `demo-story-${index + 1}`,
        title: `Review ${change.filePath.split('/').pop()}`,
        explanation: 'Understand how this change contributes to the workflow.',
        changes: [change],
      })),
    }, {
      id: 'review-focus',
      title: 'Review focus areas',
      rationale: 'Start with the changes that carry the most review risk.',
      steps: changes.slice(0, 1).map((change, index) => ({
        id: `demo-focus-${index + 1}`,
        title: `Inspect ${change.filePath.split('/').pop()}`,
        explanation: 'Inspect this line range in the context of the surrounding change.',
        changes: [change],
      })),
    }] : [],
    updatedAt: 0,
    errorMessage: null,
  };
}
