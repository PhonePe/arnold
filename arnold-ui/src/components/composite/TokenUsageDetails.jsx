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

const cached = (tokens) => tokens === null ? '' : ` (${tokens.toLocaleString()} cached)`;

export function TokenUsageDetails({ usage, className = '', ...props }) {
  if (!usage) return null;
  return (
    <section className={`border border-border bg-surface-2 p-3 ${className}`} aria-label="Token usage" {...props}>
      <div className="flex items-baseline justify-between gap-3">
        <p className="font-mono text-[10px] uppercase tracking-[0.08em] text-faint">Token usage</p>
        {usage.jobUrl ? <a href={usage.jobUrl} className="font-mono text-xs text-accent-ink hover:underline" target="_blank" rel="noreferrer">Clio job ↗</a> : null}
      </div>
      <p className="mt-1 font-mono text-sm text-fg">
        {(usage.inputTokens + usage.outputTokens).toLocaleString()} tokens
        <span className="text-muted"> · {usage.inputTokens.toLocaleString()} in{cached(usage.cacheReadTokens)} / {usage.outputTokens.toLocaleString()} out</span>
      </p>
      {usage.models.length ? <ul className="mt-2 divide-y divide-border border-t border-border">
        {usage.models.map((model) => <li key={model.model} className="flex justify-between gap-3 py-1.5 font-mono text-xs">
          <span className="break-all text-fg">{model.model}</span>
          <span className="shrink-0 text-muted">{model.inputTokens.toLocaleString()}{cached(model.cacheReadTokens)} / {model.outputTokens.toLocaleString()}</span>
        </li>)}
      </ul> : null}
    </section>
  );
}
