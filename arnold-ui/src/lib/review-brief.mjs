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

export function formatReviewBrief() {
  return (tree) => {
    tree.children = tree.children.flatMap((node) => {
      if (node.type !== 'paragraph') return [node];
      if (/^Scale: 1 trivial · 2 small · 3 medium · 4 large · 5 very large\.$/.test(textOf(node).trim())) return [];
      const [label, ...body] = node.children;
      if (label?.type !== 'strong' || !/^(?:overview|summary|review brief|(?:estimated\s+)?review efforts?|tests?|relevant tests|testing|security(?: concerns)?|(?:jira|ticket|rfc) compliance|scope)\b/i.test(textOf(label))) return [node];
      const heading = { type: 'heading', depth: 3, children: label.children };
      const content = body.filter((child, index) => !(index === 0 && child.type === 'break'));
      return content.some((child) => textOf(child).trim()) ? [heading, { ...node, children: content }] : [heading];
    });
  };
}
