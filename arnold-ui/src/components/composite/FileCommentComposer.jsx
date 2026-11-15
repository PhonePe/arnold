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
