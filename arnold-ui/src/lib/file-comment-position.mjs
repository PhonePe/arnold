export function fileCommentPosition(anchor, popup, viewport) {
  const gap = 8;
  const below = viewport.height - anchor.bottom - gap;
  const above = anchor.top - gap;
  const top = below >= popup.height || below >= above ? anchor.bottom + gap : anchor.top - popup.height - gap;
  return {
    left: Math.max(gap, Math.min(anchor.right - popup.width, viewport.width - popup.width - gap)),
    top: Math.max(gap, Math.min(top, viewport.height - popup.height - gap)),
  };
}
