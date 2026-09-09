// Run in the browser console with the persona editor open and at least two registered users.
// Changes only unsaved contributor selections, then restores them.
async function checkPersonaContributors() {
  const summary = document.querySelector('#persona-contributors');
  const dropdown = summary?.parentElement;
  if (!dropdown) throw new Error('Open a persona editor first.');
  const search = dropdown.querySelector('input');
  const tick = () => new Promise((resolve) => setTimeout(resolve, 0));
  const assert = (condition, message) => { if (!condition) throw new Error(message); };
  const setSearch = async (value) => {
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(search, value);
    search.dispatchEvent(new Event('input', { bubbles: true }));
    await tick();
  };
  const query = search.value;
  const wasOpen = dropdown.open;
  await setSearch('');
  const inputs = [...dropdown.querySelectorAll('[type="checkbox"]')];
  const selected = inputs.map((input) => input.checked);
  try {
    assert(inputs.length >= 2, 'At least two users are needed to check multiple selection.');
    dropdown.open = true;
    for (const input of inputs.filter((input) => input.checked)) {
      input.click();
      await tick();
    }
    for (const input of inputs.slice(0, 2)) {
      search.focus();
      search.blur();
      input.closest('label').click();
      await tick();
      assert(dropdown.open, 'Clicking a person must not close the dropdown.');
      assert(input.checked, 'Clicking a person must select their checkbox.');
    }
    await setSearch('no-such-contributor-regression-check');
    assert(!dropdown.querySelector('[type="checkbox"]'), 'An unmatched search must show no options.');
    await setSearch('');
    assert(dropdown.querySelectorAll(':checked').length === 2, 'Search must retain both selections.');
    search.focus();
    search.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    assert(!dropdown.open && document.activeElement === summary, 'Escape must close and restore focus.');
    dropdown.open = true;
    document.querySelector('#persona-name').focus();
    assert(!dropdown.open, 'Moving focus outside must close the dropdown.');
    return 'PASS: multiple selection, label clicks, filtering, selection retention, Escape, outside focus';
  } finally {
    await setSearch('');
    const current = [...dropdown.querySelectorAll('[type="checkbox"]')];
    for (const [index, input] of current.entries()) {
      if (input.checked !== selected[index]) { input.click(); await tick(); }
    }
    await setSearch(query);
    dropdown.open = wasOpen;
  }
}
checkPersonaContributors();
