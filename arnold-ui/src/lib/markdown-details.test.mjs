import assert from 'node:assert/strict';
import test from 'node:test';
import { normalizeGitLabInlineHtml, splitMarkdownDetails, withoutSuggestionBlocks } from './markdown-details.mjs';

test('keeps GitLab details sections collapsible', () => {
  assert.deepEqual(splitMarkdownDetails('Intro\n<details><summary>Help</summary>\n- First\n</details>\nEnd'), [
    { type: 'markdown', value: 'Intro\n' },
    { type: 'details', summary: 'Help', value: '- First' },
    { type: 'markdown', value: '\nEnd' },
  ]);
});

test('converts safe GitLab summary HTML to Markdown', () => {
  assert.equal(
    normalizeGitLabInlineHtml("<a href='https://gitlab.example/file'><strong>Behavior Change</strong></a> Use <code>null</code>"),
    '[**Behavior Change**](https://gitlab.example/file) Use `null`',
  );
});

test('keeps comment text while removing structured suggestion blocks', () => {
  assert.equal(withoutSuggestionBlocks('Please use this instead.\n\n```suggestion:-1+0\nreturn value;\n```\n\nThanks.'), 'Please use this instead.\n\nThanks.');
});
