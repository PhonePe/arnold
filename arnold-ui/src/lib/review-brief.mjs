const textOf = (node) => node.value || (node.children || []).map(textOf).join('');
const effortLabel = /^(?:estimated\s+)?review efforts?\s*[:(–—-]?\s*/i;
const scorePattern = /\b[1-5]\s*\/\s*5\b/;

// ponytail: recognizes Markdown effort sections; use a structured rationale field if the generator changes format.
export function explainReviewEffort() {
  return (tree) => {
    for (const [index, node] of tree.children.entries()) {
      if (!['heading', 'paragraph'].includes(node.type)) continue;
      const text = textOf(node).trim();
      if (!effortLabel.test(text) || !scorePattern.test(text)) continue;
      const remainder = text.replace(effortLabel, '').replace(scorePattern, '').replace(/^[\s):–—-]+/, '').trim();
      const next = tree.children[index + 1];
      const nextText = next ? textOf(next).trim() : '';
      const hasExplanation = (/[a-z]{2}/i.test(remainder) && !/^(?:very\s+)?(?:low|medium|moderate|high|simple|complex)[.!)]*$/i.test(remainder))
        || (nextText && ['paragraph', 'list'].includes(next?.type) && (/^(basis|rationale|because|why)\b/i.test(nextText)
          || (node.type === 'heading' && next.children?.[0]?.type !== 'strong')));
      if (!hasExplanation) {
        node.children = [{ type: 'text', value: 'Review effort' }];
        tree.children.splice(index + 1, 0, { type: 'paragraph', children: [{ type: 'text', value: 'The AI overview did not explain its effort estimate, so the numeric rating is hidden.' }] });
      }
    }
  };
}
