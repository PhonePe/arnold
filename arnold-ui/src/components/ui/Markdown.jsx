'use client';

import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { splitMarkdownDetails } from '@/lib/markdown-details.mjs';

const components = {
  a({ href, children, ...props }) {
    const external = /^https?:\/\//i.test(href || '');
    return <a href={href} target={external ? '_blank' : undefined} rel={external ? 'noreferrer' : undefined} {...props}>{children}</a>;
  },
};
const collapsibleCodeComponents = {
  ...components,
  pre: ({ children }) => <details className="my-2 border border-border bg-surface"><summary className="cursor-pointer px-3 py-2 font-mono text-xs font-semibold text-fg hover:bg-[var(--ink-wash)]">Suggested code</summary><pre className="!m-0 border-0 !border-t border-border">{children}</pre></details>,
};
const summaryComponents = { ...components, p: ({ children }) => <span>{children}</span> };

function MarkdownContent({ source, collapseCodeBlocks, remarkPlugins = [] }) {
  return splitMarkdownDetails(source).map((part, index) => part.type === 'details'
    ? <details key={index} className="my-2 border border-border bg-surface"><summary className="cursor-pointer break-words px-3 py-2 text-fg hover:bg-[var(--ink-wash)]"><ReactMarkdown remarkPlugins={[remarkGfm]} components={summaryComponents} skipHtml>{part.summary}</ReactMarkdown></summary><div className="border-t border-border px-3 py-2"><MarkdownContent source={part.value} collapseCodeBlocks={collapseCodeBlocks} remarkPlugins={remarkPlugins} /></div></details>
    : <ReactMarkdown key={index} remarkPlugins={[remarkGfm, ...remarkPlugins]} components={collapseCodeBlocks ? collapsibleCodeComponents : components} skipHtml>{part.value}</ReactMarkdown>);
}

export function Markdown({ children, className = '', collapseCodeBlocks = false, remarkPlugins, ...props }) {
  return (
    <div className={`markdown min-w-0 max-w-full overflow-x-auto text-sm leading-6 text-fg ${className}`} {...props}>
      <MarkdownContent source={String(children || '')} collapseCodeBlocks={collapseCodeBlocks} remarkPlugins={remarkPlugins} />
    </div>
  );
}
