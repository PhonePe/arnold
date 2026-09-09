export function positionForSelection(fileDiff, { side = 'additions', endSide = side, start, end }) {
  if (!['additions', 'deletions'].includes(side) || endSide !== side
    || !Number.isInteger(start) || !Number.isInteger(end) || start < 1 || end < 1) return null;
  return start === end ? positionForLine(fileDiff, side, start) : positionForRange(fileDiff, side, start, end);
}

export function lastDiffLocation(fileDiff) {
  const hunk = fileDiff.hunks?.at(-1);
  const side = fileDiff.type === 'deleted' || (hunk && !hunk.additionCount) ? 'deletions' : 'additions';
  return { side, lineNumber: hunk ? side === 'deletions' ? hunk.deletionStart + hunk.deletionCount - 1 : hunk.additionStart + hunk.additionCount - 1 : 0 };
}

export function positionForLine(fileDiff, side, lineNumber) {
  for (const hunk of fileDiff.hunks || []) {
    let oldLine = hunk.deletionStart;
    let newLine = hunk.additionStart;
    for (const content of hunk.hunkContent) {
      if (content.type === 'context') {
        const start = side === 'deletions' ? oldLine : newLine;
        if (lineNumber >= start && lineNumber < start + content.lines) {
          const offset = lineNumber - start;
          return paths(fileDiff, oldLine + offset, newLine + offset);
        }
        oldLine += content.lines;
        newLine += content.lines;
      } else {
        if (side === 'deletions' && lineNumber >= oldLine && lineNumber < oldLine + content.deletions) {
          return paths(fileDiff, lineNumber, null);
        }
        if (side === 'additions' && lineNumber >= newLine && lineNumber < newLine + content.additions) {
          return paths(fileDiff, null, lineNumber);
        }
        oldLine += content.deletions;
        newLine += content.additions;
      }
    }
  }
  return paths(fileDiff, side === 'deletions' ? lineNumber : null, side === 'additions' ? lineNumber : null);
}

export function positionForRange(fileDiff, side, start, end) {
  const firstLine = Math.min(start, end);
  const lastLine = Math.max(start, end);
  const first = positionForLine(fileDiff, side, firstLine);
  const last = positionForLine(fileDiff, side, lastLine);
  return {
    ...last,
    lineRange: {
      side: side === 'deletions' ? 'old' : 'new',
      startOldLine: first.oldLine,
      startNewLine: first.newLine,
      endOldLine: last.oldLine,
      endNewLine: last.newLine,
    },
  };
}

export function contentForRange(fileDiff, side, start, end) {
  const lines = side === 'deletions' ? fileDiff.deletionLines : fileDiff.additionLines;
  if (!Array.isArray(lines)) return '';
  if (!fileDiff.isPartial) return lines.slice(Math.min(start, end) - 1, Math.max(start, end)).join('\n');
  const selected = [];
  const firstLine = Math.min(start, end);
  const lastLine = Math.max(start, end);
  for (const hunk of fileDiff.hunks || []) {
    let oldLine = hunk.deletionStart;
    let newLine = hunk.additionStart;
    for (const content of hunk.hunkContent) {
      const count = content.type === 'context' ? content.lines
        : side === 'deletions' ? content.deletions : content.additions;
      const line = side === 'deletions' ? oldLine : newLine;
      const index = side === 'deletions' ? content.deletionLineIndex : content.additionLineIndex;
      for (let offset = 0; offset < count; offset += 1) {
        if (line + offset >= firstLine && line + offset <= lastLine) selected.push(lines[index + offset]);
      }
      oldLine += content.type === 'context' ? content.lines : content.deletions;
      newLine += content.type === 'context' ? content.lines : content.additions;
    }
  }
  return selected.join('\n');
}

function paths(fileDiff, oldLine, newLine) {
  return {
    oldPath: fileDiff.prevName || fileDiff.name,
    newPath: fileDiff.name,
    oldLine,
    newLine,
  };
}

export function annotationLocation(position) {
  if (position?.lineRange?.side === 'old' && position.oldLine != null) return { side: 'deletions', lineNumber: position.oldLine };
  return position?.newLine != null
    ? { side: 'additions', lineNumber: position.newLine }
    : position?.oldLine != null ? { side: 'deletions', lineNumber: position.oldLine } : null;
}

export function isCurrentPosition(position, headSha) {
  return Boolean(position && (!position.headSha || !headSha || position.headSha === headSha));
}
