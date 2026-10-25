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

import { Button } from './Button';

export function ErrorState({ title = 'Something went wrong', error, onRetry, className = '', ...props }) {
  return (
    <div className={`border border-bad bg-surface px-5 py-6 ${className}`} role="alert" {...props}>
      <h2 className="font-display text-lg font-bold text-fg">{title}</h2>
      <p className="mt-2 text-sm text-muted">{error?.message || 'The request could not be completed.'}</p>
      {onRetry ? <Button className="mt-4" variant="secondary" size="sm" onClick={onRetry}>Retry</Button> : null}
    </div>
  );
}
