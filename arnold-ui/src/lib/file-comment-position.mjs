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

export function fileCommentPosition(anchor, popup, viewport, pane) {
  const gap = 8;
  const below = viewport.height - anchor.bottom - gap;
  const above = anchor.top - gap;
  const top = below >= popup.height || below >= above ? anchor.bottom + gap : anchor.top - popup.height - gap;
  return {
    left: Math.max(gap, Math.min(pane.left, viewport.width - popup.width - gap)),
    top: Math.max(gap, Math.min(top, viewport.height - popup.height - gap)),
  };
}
