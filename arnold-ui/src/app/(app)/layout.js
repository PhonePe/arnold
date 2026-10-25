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

import { AppLayout } from '@/components/layout/AppLayout';
import { Splash } from '@/components/layout/Splash';

const splashBootstrap = `(function(){
  try {
    var key = 'arnold.splash.lastShownAt';
    var repeatAfter = 7 * 24 * 60 * 60 * 1000;
    var now = Date.now();
    var lastShownAt = Number(localStorage.getItem(key));
    var shouldShow = !Number.isFinite(lastShownAt)
      || lastShownAt <= 0
      || lastShownAt > now
      || now - lastShownAt >= repeatAfter;

    if (shouldShow) {
      localStorage.setItem(key, String(now));
      document.documentElement.setAttribute('data-splash', 'show');
    } else {
      document.documentElement.setAttribute('data-splash', 'hide');
    }
  } catch (e) {
    document.documentElement.setAttribute('data-splash', 'show');
  }
})();`;

export default function DashboardLayout({ children }) {
  return (
    <>
      <script dangerouslySetInnerHTML={{ __html: splashBootstrap }} />
      <Splash />
      <AppLayout>{children}</AppLayout>
    </>
  );
}
