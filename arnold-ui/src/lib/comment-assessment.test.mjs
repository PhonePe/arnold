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

import assert from 'node:assert/strict';
import test from 'node:test';
import { commentAssessment } from './comment-assessment.mjs';

test('marks an assessment stale only when the reviewed SHA differs', () => {
  const note = { acceptanceStatus: 'applied', assessedSha: 'a'.repeat(40) };

  assert.deepEqual(commentAssessment(note, 'a'.repeat(40)), {
    label: 'AI · Applied', variant: 'success', stale: false, unverified: false,
  });
  assert.equal(commentAssessment(note, 'b'.repeat(40)).stale, true);
});

test('marks legacy assessments without SHA provenance as unverified', () => {
  assert.equal(commentAssessment({ acceptanceStatus: 'ignored' }, 'a'.repeat(40)).unverified, true);
});

test('returns no badge for comments without an assessment', () => {
  assert.equal(commentAssessment({}, 'a'.repeat(40)), null);
});
