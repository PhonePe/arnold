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

import { Overline } from '@/components/ui/Overline';
import { UnavailableState } from '@/components/ui/UnavailableState';

export default function PoliciesPage() {
  return <div className="mx-auto max-w-[1000px]"><div className="mb-6"><Overline>Governance</Overline><h1 className="mt-1 font-display text-[clamp(30px,4vw,42px)] font-bold text-fg">Review policies</h1></div><UnavailableState title="Policy management unavailable" description="No backend policy read or mutation API is available. Prototype policies have been removed from the production route." /></div>;
}
