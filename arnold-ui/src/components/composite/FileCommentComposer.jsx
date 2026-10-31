'use client';

import { useEffect, useRef } from 'react';
import { DiscussionComposer } from '@/components/composite/DiscussionComposer';
import { fileCommentPosition } from '@/lib/file-comment-position.mjs';

export function FileCommentComposer({ anchor, onCancel, className = '', ...props }) {
  const popoverRef = useRef(null);
  useEffect(() => {
    const popover = popoverRef.current;
    const pane = anchor.closest('main');
    function position() {
      if (!anchor.isConnected) return;
      const bounds = pane.getBoundingClientRect();
      popover.style.setProperty('--comment-width', `${bounds.width}px`);
      const point = fileCommentPosition(anchor.getBoundingClientRect(), popover.getBoundingClientRect(), { width: window.innerWidth, height: window.innerHeight }, bounds);
      popover.style.setProperty('--comment-left', `${point.left}px`);
      popover.style.setProperty('--comment-top', `${point.top}px`);
    }
    function closeOnEscape(event) {
      if (event.key !== 'Escape' || popover.querySelector('[aria-busy="true"]')) return;
      event.preventDefault();
      onCancel();
    }
    popover.showPopover();
    position();
    popover.querySelector('textarea')?.focus({ preventScroll: true });
    const observer = new ResizeObserver(position);
    observer.observe(popover);
    observer.observe(pane);
    window.addEventListener('resize', position);
    window.addEventListener('scroll', position, true);
    popover.addEventListener('keydown', closeOnEscape);
    return () => {
      observer.disconnect();
      window.removeEventListener('resize', position);
      window.removeEventListener('scroll', position, true);
      popover.removeEventListener('keydown', closeOnEscape);
      if (anchor.isConnected) anchor.focus({ preventScroll: true });
    };
  }, [anchor, onCancel]);

  return <div ref={popoverRef} popover="manual" role="dialog" aria-label={props.label} className={`file-comment-popover m-0 border-0 bg-transparent p-0 text-fg ${className}`}>
    <DiscussionComposer {...props} onCancel={onCancel} />
  </div>;
}
