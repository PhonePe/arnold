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

// Run in the browser console with an editor open and at least two unselected users.
// Adds people to the unsaved form, then removes those additions.
async function checkPersonaContributors() {
  const search = document.querySelector('#persona-contributors');
  if (!search) throw new Error('Open a persona editor first.');
  const tick = () => new Promise((resolve) => setTimeout(resolve, 0));
  const assert = (condition, message) => { if (!condition) throw new Error(message); };
  const chips = () => [...document.querySelectorAll('[aria-label^="Remove contributor "]')];
  const original = new Set(chips());
  const setSearch = async (value) => {
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(search, value);
    search.dispatchEvent(new Event('input', { bubbles: true }));
    await tick();
  };
  const key = async (value) => {
    search.dispatchEvent(new KeyboardEvent('keydown', { key: value, bubbles: true, cancelable: true }));
    await tick();
  };
  const query = search.value;
  try {
    search.focus();
    await setSearch('');
    const options = [...document.querySelectorAll('[role="option"]')];
    assert(options.length >= 2, 'At least two unselected users are needed.');
    const names = options.slice(0, 2).map((option) => option.querySelector('span span').textContent);
    await key('Enter');
    assert(chips().length === original.size, 'Empty Enter must not add a person.');
    for (const [index, name] of names.entries()) {
      await setSearch(name);
      await key('Enter');
      assert(chips().length === original.size + index + 1, 'Enter must add the matching person.');
      assert(search.value === '', 'Adding must clear the search.');
      assert(document.activeElement === search, 'Adding must retain search focus.');
    }
    await setSearch(names[0]);
    assert(![...document.querySelectorAll('[role="option"]')].some((option) => option.querySelector('span span').textContent === names[0]), 'Selected people must be excluded.');
    await setSearch('no-such-contributor-regression-check');
    await key('Enter');
    assert(chips().length === original.size + 2, 'Unmatched Enter must not add a person.');
    await key('Escape');
    assert(search.getAttribute('aria-expanded') === 'false', 'Escape must close results.');
    await setSearch('');
    document.querySelector('#persona-name').focus();
    await tick();
    assert(search.getAttribute('aria-expanded') === 'false', 'Outside focus must close results.');
    return 'PASS: Enter adds multiple people, focus, clearing, duplicate exclusion, no-match, Escape, outside focus';
  } finally {
    for (const chip of chips().filter((chip) => !original.has(chip))) { chip.click(); await tick(); }
    await setSearch(query);
    await key('Escape');
  }
}
checkPersonaContributors();
