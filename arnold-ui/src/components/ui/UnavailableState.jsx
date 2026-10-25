'use client';

/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
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

export function UnavailableState({ title, description, className = '', ...props }) {
  return (
    <div className={`border border-border border-l-[3px] border-l-tertiary bg-surface px-5 py-6 ${className}`} {...props}>
      <div className="font-mono text-[11px] uppercase tracking-[0.12em] text-tertiary-ink">Unavailable</div>
      <h2 className="mt-1 font-display text-lg font-bold text-fg">{title}</h2>
      {description ? <p className="mt-2 max-w-[70ch] text-sm text-muted">{description}</p> : null}
    </div>
  );
}
