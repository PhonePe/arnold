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

import React, { useCallback, useMemo } from 'react';
import { Combobox } from '@/components/ui/Combobox';
import { pickDefaultExecution } from '@/lib/execution-options.mjs';
import { buildModelIndex, highlightSegments, searchModels } from '@/lib/model-search.mjs';

const LABEL_CLASS = 'font-mono text-[11px] uppercase leading-none tracking-[0.06em] text-muted';

/** Harness + model picker over `fetchAnalysisExecutionOptions()` results. Emits `{ harness, model }`. */
export function ExecutionPicker({
  idPrefix,
  options,
  harness,
  model,
  onChange,
  disabled = false,
  size = 'md',
  className = '',
  ...props
}) {
  const selectedHarness = options?.find((option) => option.id === harness);
  const models = useMemo(() => selectedHarness?.models || [], [selectedHarness]);
  const modelIndex = useMemo(() => buildModelIndex(models), [models]);

  const harnessOptions = useMemo(() => (options || []).map((option) => ({
    value: option.id,
    label: option.name,
    secondary: option.available ? `${option.models.length} model${option.models.length === 1 ? '' : 's'}` : null,
    disabled: !option.available,
    note: option.available ? null : option.message || 'Unavailable',
  })), [options]);

  const modelOptions = useMemo(() => models.map((option) => ({
    value: option.id,
    label: option.name || option.id,
    secondary: option.name && option.name !== option.id ? option.id : null,
    badge: option.isDefault ? 'default' : null,
  })), [models]);

  const filterModels = useCallback((query, items) => {
    const byId = new Map(items.map((item) => [item.value, item]));
    return searchModels(models, query, modelIndex).map((match) => {
      const item = byId.get(match.id);
      return {
        ...item,
        labelSegments: highlightSegments(item.label, query),
        secondarySegments: item.secondary ? highlightSegments(item.secondary, query) : null,
      };
    });
  }, [modelIndex, models]);

  const hasModels = models.length > 0;
  const modelPlaceholder = !selectedHarness?.available ? 'Harness unavailable' : hasModels ? 'Select a model' : 'No models reported';

  return (
    <div className={`grid gap-3 text-left sm:grid-cols-2 ${className}`} {...props}>
      <div className="grid min-w-0 gap-2">
        <span id={`${idPrefix}-harness-label`} className={LABEL_CLASS}>Harness</span>
        <Combobox
          id={`${idPrefix}-harness`}
          aria-labelledby={`${idPrefix}-harness-label`}
          options={harnessOptions}
          value={harness}
          placeholder="Select a harness"
          onChange={(next) => onChange?.(pickDefaultExecution(options, { harness: next, model: '' }))}
          disabled={disabled || !options?.length}
          size={size}
        />
      </div>
      <div className="grid min-w-0 gap-2">
        <span id={`${idPrefix}-model-label`} className={LABEL_CLASS}>Model</span>
        <Combobox
          id={`${idPrefix}-model`}
          aria-labelledby={`${idPrefix}-model-label`}
          aria-describedby={`${idPrefix}-model-help`}
          options={modelOptions}
          value={model}
          placeholder={modelPlaceholder}
          onChange={(next) => onChange?.({ harness, model: next })}
          disabled={disabled || !selectedHarness?.available || !hasModels}
          filterOptions={filterModels}
          searchPlaceholder="Search models, e.g. gpt 5 mini"
          emptyText="No models match"
          size={size}
        />
      </div>
      <p id={`${idPrefix}-model-help`} className="col-span-full text-xs text-muted" role="status">{selectedHarness?.message}</p>
    </div>
  );
}
