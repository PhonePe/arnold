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

import { Overline } from '@/components/ui/Overline';
import { EmptyState } from '@/components/ui/EmptyState';

export default function StatsPage() {
  return (
    <div className="mx-auto max-w-[1280px]">
      <div className="mb-6">
        <Overline>Metrics</Overline>
        <h1 className="mt-1 font-display text-[clamp(30px,4vw,42px)] font-bold leading-[1.05] tracking-[-0.02em] text-fg">
          Organisation insights
        </h1>
        <p className="mt-2 max-w-[72ch] text-[15px] text-muted">
          Organisation-wide throughput, review load, and delivery trends.
        </p>
      </div>
      <EmptyState
        title="Organisation insights are coming soon"
        description="Personal productivity remains available on Home while we build organisation-wide reporting."
      />
    </div>
  );
}
