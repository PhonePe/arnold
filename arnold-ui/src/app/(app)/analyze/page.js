'use client';

import React, { useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Search, Plus, X } from 'lucide-react';
import { Overline } from '@/components/ui/Overline';

const PERSONAS = [
  { n: 'Netra', h: 'security-guild · official', g: '網', c: 'var(--sev-security)', d: 'Compliance & audit. Maps findings to policy IDs.', on: true },
  { n: 'Kōzō', h: 'architecture', g: '構', c: 'var(--sev-maint)', d: 'Structural — nesting, abstractions, contract drift.', on: true },
  { n: 'Hayai', h: 'perf-core', g: '速', c: 'var(--sev-perf)', d: 'Hunts O(n²), N+1 queries, blocking I/O on hot paths.' },
  { n: 'Mamoru', h: 'platform', g: '護', c: 'var(--sev-critical)', d: 'Regression guardian — warns when a pattern broke before.' },
  { n: 'Socratic', h: 'tushar.n · yours', g: '問', c: 'var(--sev-enh)', d: 'Asks understanding-check questions instead of fixing.' },
  { n: 'Tegaki', h: 'frontend-craft', g: '手', c: 'var(--sev-issue)', d: 'Accessibility & semantics — ARIA, focus order, contrast.' },
  { n: 'Shinrai', h: 'reliability', g: '信', c: 'var(--sev-perf)', d: 'Error handling, retries, idempotency and timeouts.' },
  { n: 'Kanyō', h: 'data-platform', g: '寛', c: 'var(--sev-maint)', d: 'Schema & migration safety — backfills, locks, rollbacks.' },
  { n: 'Yōso', h: 'design-systems', g: '要', c: 'var(--sev-enh)', d: 'Token drift, component reuse, visual regressions.' },
  { n: 'Genshi', h: 'cost-eng', g: '源', c: 'var(--sev-issue)', d: 'Cloud spend — egress, hot partitions, wasteful loops.' },
  { n: 'Hokan', h: 'qa-guild', g: '補', c: 'var(--sev-perf)', d: 'Test coverage gaps and flaky-test smells.' },
  { n: 'Rōdoku', h: 'docs', g: '朗', c: 'var(--sev-maint)', d: 'Public API docs, changelog and breaking-change notes.' },
  { n: 'Anzen', h: 'appsec', g: '安', c: 'var(--sev-security)', d: 'Injection, authz gaps, secrets in code and SSRF.' },
  { n: 'Tsuzuki', h: 'i18n', g: '続', c: 'var(--sev-enh)', d: 'Localisation, RTL, date/number formatting.' },
  { n: 'Heikō', h: 'concurrency', g: '並', c: 'var(--sev-critical)', d: 'Races, deadlocks and unsafe shared-state mutation.' },
  { n: 'Kioku', h: 'observability', g: '記', c: 'var(--sev-issue)', d: 'Logging, metrics and trace coverage on new paths.' },
];
const TOTAL_PERSONAS = 214;

const CTX_TYPES = ['RFC doc', 'Google doc', 'JIRA / ticket', 'Link', 'Figma'];

const GROUP_SAMPLES = [
  { repo: 'auth-gateway', num: '493', branch: 'feat/oauth-callback → main', title: 'feat: oauth callback handler', revs: [{ n: 'Sana K.', i: 'SK' }] },
  { repo: 'ledger-service', num: '401', branch: 'feat/session-schema → main', title: 'feat: session token schema', revs: [{ n: 'Ravi V.', i: 'RV' }] },
  { repo: 'platform-secrets', num: '488', branch: 'sec/refresh-rotation → main', title: 'security: refresh-token rotation', revs: [] },
];

const AKEY = 'arnold.analyses.v1';

let rid = 0;
const newId = () => `r${Date.now()}-${rid++}`;
const initials = (name) =>
  name.replace(/^@/, '').split(/[ .]/).filter(Boolean).map((p) => p[0]).slice(0, 2).join('').toUpperCase() || '?';

export default function AnalyzePage() {
  const router = useRouter();

  const [scope, setScope] = useState('single');
  // single fields
  const [mrLink, setMrLink] = useState('');
  const [title, setTitle] = useState('');
  const [repo, setRepo] = useState('');
  const [mrNo, setMrNo] = useState('');
  const [branch, setBranch] = useState('');
  const [fetching, setFetching] = useState(false);
  const [parsed, setParsed] = useState(null);
  // group fields
  const [groupLink, setGroupLink] = useState('');
  const [groupName, setGroupName] = useState('');
  const [mrRows, setMrRows] = useState([
    { id: newId(), repo: 'auth-gateway', mr: '!493', branch: '' },
    { id: newId(), repo: 'ledger-service', mr: '!401', branch: '' },
  ]);
  const [groupFetching, setGroupFetching] = useState(false);
  const [groupParsed, setGroupParsed] = useState(null);
  const groupFetchN = React.useRef(0);
  // context
  const [ctxRows, setCtxRows] = useState([
    { id: newId(), type: 'RFC doc', url: 'https://rfc.internal/auth-v2-session-tokens' },
  ]);
  const [desc, setDesc] = useState('');
  // personas
  const [personaState, setPersonaState] = useState(() => PERSONAS.map((p) => !!p.on));
  const [search, setSearch] = useState('');
  // reviewers
  const [reviewers, setReviewers] = useState([
    { id: newId(), n: 'Sana K.', i: 'SK', kind: 'sync' },
    { id: newId(), n: 'Ravi V.', i: 'RV', kind: 'sync' },
  ]);
  const [revInput, setRevInput] = useState('');
  // visibility
  const [ready, setReady] = useState(false);

  // ── derived ──
  const personaSel = personaState.filter(Boolean).length;
  const filteredPersonas = useMemo(() => {
    const q = search.trim().toLowerCase();
    return PERSONAS.map((p, i) => ({ ...p, i }))
      .filter((p) => !q || `${p.n} ${p.h} ${p.d}`.toLowerCase().includes(q));
  }, [search]);

  const sumScope = scope === 'single' ? 'Single MR' : `Group · ${mrRows.length} MRs`;

  // ── handlers ──
  const togglePersona = (i) => setPersonaState((s) => s.map((v, idx) => (idx === i ? !v : v)));

  const addReviewerByName = (name, kind = 'added') => {
    const clean = name.replace(/^@/, '').trim();
    if (!clean) return;
    setReviewers((rs) => {
      if (rs.some((r) => r.n.toLowerCase() === clean.toLowerCase())) return rs;
      return [...rs, { id: newId(), n: clean, i: initials(clean), kind }];
    });
  };

  const fetchMr = () => {
    if (!mrLink.trim()) return;
    const m = mrLink.match(/([\w.-]+)\/-\/merge_requests\/(\d+)/) || mrLink.match(/([\w.-]+)[#!](\d+)/);
    const r = m ? m[1].split('/').pop() : 'gateway-service';
    const num = m ? m[2] : String(500 + Math.floor(Math.random() * 99));
    setFetching(true);
    setParsed(null);
    setTimeout(() => {
      const mr = {
        repo: r,
        num,
        title: 'feat: add idempotency keys to webhook dispatcher',
        ah: 'mei.t',
        av: 'MT',
        branch: 'feat/idempotency-keys → main',
        changes: '+412 −86 · 9 files',
        desc: 'Adds idempotency-key support to the webhook dispatcher so retried deliveries are de-duplicated server-side. Introduces a `dispatch_dedupe` table (migration 0142) with a 24h TTL sweep job, and wraps the send path behind the `webhooks.idempotency` flag for staged rollout. Backfill is online and lock-free.',
        reviewers: [{ n: 'Sana K.', i: 'SK' }, { n: 'Ravi V.', i: 'RV' }],
        labels: ['backend', 'migration', 'feature-flag'],
      };
      setTitle(mr.title);
      setRepo(r);
      setMrNo(`!${num}`);
      setBranch(mr.branch);
      setDesc(mr.desc);
      setReviewers(mr.reviewers.map((rv) => ({ id: newId(), n: rv.n, i: rv.i, kind: 'sync' })));
      setParsed(mr);
      setFetching(false);
    }, 1150);
  };

  const fetchGroupMr = () => {
    if (!groupLink.trim()) return;
    const m = groupLink.match(/([\w.-]+)\/-\/merge_requests\/(\d+)/) || groupLink.match(/([\w.-]+)[#!](\d+)/);
    setGroupFetching(true);
    setGroupParsed(null);
    setTimeout(() => {
      const s = GROUP_SAMPLES[groupFetchN.current % GROUP_SAMPLES.length];
      groupFetchN.current += 1;
      const r = m ? m[1].split('/').pop() : s.repo;
      const num = m ? m[2] : s.num;
      setMrRows((rows) => [...rows, { id: newId(), repo: r, mr: `!${num}`, branch: s.branch }]);
      s.revs.forEach((rv) => addReviewerByName(rv.n, 'sync'));
      setGroupParsed({ ...s, repo: r, num });
      setGroupLink('');
      setGroupFetching(false);
    }, 1000);
  };

  const submit = () => {
    let id;
    let recTitle;
    let recRepo;
    if (scope === 'single') {
      recTitle = title.trim() || 'Untitled change';
      recRepo = repo.trim() || 'unassigned-repo';
      id = mrNo.trim() || `!${500 + Math.floor(Math.random() * 99)}`;
    } else {
      recTitle = `${groupName.trim() || 'Untitled group'} · ${mrRows.length} MRs`;
      recRepo = 'cross-repo group';
      id = 'SET';
    }
    const rec = { id, title: recTitle, repo: recRepo, ready, personas: personaSel, revs: reviewers.length, scope, t: Date.now() };
    try {
      const list = JSON.parse(localStorage.getItem(AKEY) || '[]');
      list.unshift(rec);
      localStorage.setItem(AKEY, JSON.stringify(list));
    } catch (e) {
      /* ignore */
    }
    router.push('/');
  };

  return (
    <div className="mx-auto max-w-[1680px]">
      <div className="mb-6">
        <Overline>New analysis</Overline>
        <h1 className="mt-1 font-display text-[clamp(30px,4vw,42px)] font-bold leading-[1.05] tracking-[-0.02em] text-fg">
          Run analysis on your code
        </h1>
        <p className="mt-2 max-w-[72ch] text-[15px] text-muted">
          Give Arnold everything it needs to review your change — context, the personas to review
          against, and who should see it. Submit when you&apos;re ready, and it lands in{' '}
          <b className="text-fg">your open MRs</b>.
        </p>
      </div>

      <form className="af-grid" onSubmit={(e) => e.preventDefault()}>
        {/* ── left: the steps ── */}
        <div>
          {/* 1 · scope */}
          <div className="af-step">
            <div className="af-head">
              <span className="af-num">1</span>
              <div>
                <h3>What are you analysing?</h3>
                <div className="hint">A single merge request, or a group of related MRs spanning multiple codebases reviewed as one unit.</div>
              </div>
            </div>
            <div className="seg" style={{ marginBottom: 18 }}>
              <button type="button" className={scope === 'single' ? 'on' : ''} onClick={() => setScope('single')}>Single MR</button>
              <button type="button" className={scope === 'group' ? 'on' : ''} onClick={() => setScope('group')}>MR group · cross-repo</button>
            </div>

            {scope === 'single' ? (
              <div>
                <div className="field">
                  <label>Paste a GitLab MR <span className="opt">· Arnold reads it and fills in the rest</span></label>
                  <div className="mr-paste">
                    <input className="inp mono" value={mrLink} onChange={(e) => setMrLink(e.target.value)} onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); fetchMr(); } }} placeholder="https://gitlab.com/acme/gateway-service/-/merge_requests/512" />
                    <button type="button" className="inline-flex h-9 items-center gap-1.5 rounded-none border border-border-2 bg-surface px-3 text-xs font-semibold text-fg hover:border-fg disabled:opacity-50" onClick={fetchMr} disabled={fetching}>
                      <Search className="h-[14px] w-[14px]" strokeWidth={2} />Fetch details
                    </button>
                  </div>
                  {(fetching || parsed) && (
                    <div className={`mr-parsed ${parsed ? 'ok' : ''}`}>
                      {fetching ? (
                        <div className="mr-loading"><span className="spinner" />Reading from GitLab — description, author, reviewers…</div>
                      ) : (
                        <>
                          <div className="mrp-head">
                            <div className="mrp-av">{parsed.av}</div>
                            <div style={{ flex: 1 }}>
                              <div className="mrp-t">{parsed.title}</div>
                              <div className="mrp-meta">
                                <span><b>{parsed.repo} !{parsed.num}</b></span>
                                <span>@{parsed.ah}</span>
                                <span>{parsed.branch}</span>
                                <span>{parsed.changes}</span>
                              </div>
                            </div>
                            <span className="mrp-sync">✓ pulled</span>
                          </div>
                          <div className="mrp-desc">{parsed.desc}</div>
                          <div className="mrp-sub">
                            <div>
                              <span className="k">Reviewers on GitLab</span>
                              <div className="mrp-revs">
                                {parsed.reviewers.map((rv) => (
                                  <span key={rv.i} className="mrp-rev"><span className="avatar">{rv.i}</span>{rv.n}</span>
                                ))}
                              </div>
                            </div>
                            <div>
                              <span className="k">Labels</span>
                              <div className="mrp-labels">
                                {parsed.labels.map((l) => (<span key={l} className="mrp-lbl">{l}</span>))}
                              </div>
                            </div>
                          </div>
                        </>
                      )}
                    </div>
                  )}
                </div>
                <div className="af-or">or enter details manually</div>
                <div className="field">
                  <label>Title <span className="opt">· what is this change</span></label>
                  <input className="inp" value={title} onChange={(e) => setTitle(e.target.value)} placeholder="feat: add idempotency keys to webhook dispatcher" />
                </div>
                <div className="field">
                  <label>Merge request</label>
                  <div className="field-row three">
                    <input className="inp mono" value={repo} onChange={(e) => setRepo(e.target.value)} placeholder="gateway-service" />
                    <input className="inp mono" value={mrNo} onChange={(e) => setMrNo(e.target.value)} placeholder="!512" />
                    <input className="inp mono" value={branch} onChange={(e) => setBranch(e.target.value)} placeholder="feat/idempotency → main" />
                  </div>
                </div>
              </div>
            ) : (
              <div>
                <div className="field">
                  <label>Paste GitLab MRs <span className="opt">· add each MR across repos — Arnold reads repo, branch &amp; reviewers</span></label>
                  <div className="mr-paste">
                    <input className="inp mono" value={groupLink} onChange={(e) => setGroupLink(e.target.value)} onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); fetchGroupMr(); } }} placeholder="https://gitlab.com/acme/auth-gateway/-/merge_requests/493" />
                    <button type="button" className="inline-flex h-9 items-center gap-1.5 rounded-none border border-border-2 bg-surface px-3 text-xs font-semibold text-fg hover:border-fg disabled:opacity-50" onClick={fetchGroupMr} disabled={groupFetching}>
                      <Plus className="h-[14px] w-[14px]" strokeWidth={2} />Add to group
                    </button>
                  </div>
                  {(groupFetching || groupParsed) && (
                    <div className={`mr-parsed ${groupParsed ? 'ok' : ''}`}>
                      {groupFetching ? (
                        <div className="mr-loading"><span className="spinner" />Reading the merge request from GitLab — title, branch, reviewers…</div>
                      ) : (
                        <div className="mrp-head">
                          <div className="mrp-av">＋</div>
                          <div style={{ flex: 1 }}>
                            <div className="mrp-t">{groupParsed.title}</div>
                            <div className="mrp-meta">
                              <span><b>{groupParsed.repo} !{groupParsed.num}</b></span>
                              <span>{groupParsed.branch}</span>
                              <span>{groupParsed.revs.length ? `${groupParsed.revs.length} reviewer${groupParsed.revs.length === 1 ? '' : 's'} synced` : 'no reviewers yet'}</span>
                            </div>
                          </div>
                          <span className="mrp-sync">✓ added</span>
                        </div>
                      )}
                    </div>
                  )}
                </div>
                <div className="af-or">or enter details manually</div>
                <div className="field">
                  <label>Group name</label>
                  <input className="inp" value={groupName} onChange={(e) => setGroupName(e.target.value)} placeholder="Auth V2 rollout" />
                </div>
                <div className="field">
                  <label>Merge requests <span className="opt">· one per row, across repositories</span></label>
                  <div>
                    {mrRows.map((row) => (
                      <div key={row.id} className="mr-row">
                        <input className="inp mono" placeholder="repository" value={row.repo} onChange={(e) => setMrRows((rs) => rs.map((r) => (r.id === row.id ? { ...r, repo: e.target.value } : r)))} />
                        <input className="inp mono" placeholder="!MR" value={row.mr} onChange={(e) => setMrRows((rs) => rs.map((r) => (r.id === row.id ? { ...r, mr: e.target.value } : r)))} />
                        <input className="inp mono" placeholder="branch" value={row.branch} onChange={(e) => setMrRows((rs) => rs.map((r) => (r.id === row.id ? { ...r, branch: e.target.value } : r)))} />
                        <button type="button" className="row-x" title="Remove" onClick={() => setMrRows((rs) => rs.filter((r) => r.id !== row.id))}><X /></button>
                      </div>
                    ))}
                  </div>
                  <button type="button" className="add-row" onClick={() => setMrRows((rs) => [...rs, { id: newId(), repo: '', mr: '', branch: '' }])}><Plus />Add another MR</button>
                </div>
              </div>
            )}
          </div>

          {/* 2 · context */}
          <div className="af-step">
            <div className="af-head">
              <span className="af-num">2</span>
              <div>
                <h3>Context for the review</h3>
                <div className="hint">RFCs, design docs, tickets, links — anything that helps Arnold understand intent. Paste a description too.</div>
              </div>
            </div>
            <div className="field">
              <label>Attached references <span className="opt">· optional</span></label>
              <div>
                {ctxRows.map((row) => (
                  <div key={row.id} className="ctx-row">
                    <select className="inp" value={row.type} onChange={(e) => setCtxRows((rs) => rs.map((r) => (r.id === row.id ? { ...r, type: e.target.value } : r)))}>
                      {CTX_TYPES.map((t) => (<option key={t}>{t}</option>))}
                    </select>
                    <input className="inp" placeholder="https://…" value={row.url} onChange={(e) => setCtxRows((rs) => rs.map((r) => (r.id === row.id ? { ...r, url: e.target.value } : r)))} />
                    <button type="button" className="row-x" title="Remove" onClick={() => setCtxRows((rs) => rs.filter((r) => r.id !== row.id))}><X /></button>
                  </div>
                ))}
              </div>
              <button type="button" className="add-row" onClick={() => setCtxRows((rs) => [...rs, { id: newId(), type: 'RFC doc', url: '' }])}><Plus />Add reference</button>
            </div>
            <div className="field" style={{ marginTop: 18 }}>
              <label>Description <span className="opt">· the more you give, the sharper the review</span></label>
              <textarea className="ta" value={desc} onChange={(e) => setDesc(e.target.value)} placeholder="What does this change do, why now, and what should reviewers pay attention to? Note any migration steps, rollout flags, or areas you're unsure about…" />
            </div>
          </div>

          {/* 3 · personas */}
          <div className="af-step">
            <div className="af-head">
              <span className="af-num">3</span>
              <div>
                <h3>Review against these personas</h3>
                <div className="hint">Each persona reviews in its own context window. Pick the lenses that matter for this change.</div>
              </div>
            </div>
            <div className="persona-pick-wrap">
              <div className="persona-search">
                <Search />
                <input className="inp" value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search 214 personas by name, team or focus…" autoComplete="off" />
              </div>
              <div className="persona-scroll">
                {filteredPersonas.length === 0 ? (
                  <div className="persona-empty">No personas match your search.</div>
                ) : (
                  <div className="persona-grid">
                    {filteredPersonas.map((p) => (
                      <button type="button" key={p.n} className={`pp ${personaState[p.i] ? 'on' : ''}`} onClick={() => togglePersona(p.i)}>
                        <div className="pic" style={{ background: p.c }}>{p.g}</div>
                        <div>
                          <div className="pn">{p.n}</div>
                          <div className="pby">@{p.h}</div>
                          <div className="pd">{p.d}</div>
                        </div>
                        <span className="pck"><svg viewBox="0 0 24 24" strokeWidth={3}><path d="M5 13l4 4L19 7" /></svg></span>
                      </button>
                    ))}
                  </div>
                )}
              </div>
              <div className="persona-meta">
                <span>{search ? `Showing ${filteredPersonas.length} of ${TOTAL_PERSONAS}` : `Showing ${PERSONAS.length} of ${TOTAL_PERSONAS}`}</span>
                <span><b>{personaSel}</b> selected</span>
              </div>
            </div>
          </div>

          {/* 4 · reviewers */}
          <div className="af-step">
            <div className="af-head">
              <span className="af-num">4</span>
              <div>
                <h3>Reviewers</h3>
                <div className="hint">Synced both ways with GitLab. Reviewers already on the MR appear here; add more and Arnold keeps both sides in step.</div>
              </div>
            </div>
            <div className="rev-wrap">
              {reviewers.map((r) => (
                <span key={r.id} className="rev-chip">
                  <span className="avatar">{r.i}</span>
                  <span className="rn">{r.n}</span>
                  <span className={`rt ${r.kind}`}>{r.kind === 'sync' ? 'from gitlab' : 'added'}</span>
                  <button type="button" className="rx" title="Remove" onClick={() => setReviewers((rs) => rs.filter((x) => x.id !== r.id))}><X /></button>
                </span>
              ))}
            </div>
            <div className="rev-add">
              <input className="inp" value={revInput} onChange={(e) => setRevInput(e.target.value)} onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); addReviewerByName(revInput); setRevInput(''); } }} placeholder="Add reviewer by name or @handle…" />
              <button type="button" className="inline-flex h-9 flex-none items-center rounded-none border border-border-2 bg-surface px-3 text-xs font-semibold text-fg hover:border-fg" onClick={() => { addReviewerByName(revInput); setRevInput(''); }}>Add</button>
            </div>
            <div className="gl-note">
              <svg viewBox="0 0 24 24" strokeWidth={2}><path d="M7 7l10 10M7 17 17 7" /><circle cx="12" cy="12" r="9" /></svg>
              2-way sync · changes here are pushed back to the GitLab MR&apos;s reviewer list.
            </div>
          </div>

          {/* 5 · visibility */}
          <div className="af-step">
            <div className="af-head">
              <span className="af-num">5</span>
              <div>
                <h3>Visibility</h3>
                <div className="hint">Control when reviewers see this in their merge queues.</div>
              </div>
            </div>
            <div className={`ready-box ${ready ? 'armed' : ''}`}>
              <span className={`sw ${ready ? 'on' : ''}`} role="switch" aria-checked={ready} onClick={() => setReady((v) => !v)} />
              <div className="rb-tx">
                <h4>Mark as ready for review</h4>
                <p>Off — this stays a private draft. It will <b>not</b> appear in any reviewer&apos;s merge queue until you turn this on. You can flip it later from your open MRs.</p>
              </div>
            </div>
          </div>
        </div>

        {/* ── right: live summary + submit ── */}
        <aside className="af-aside">
          <div className="sum-card">
            <h4>Analysis summary</h4>
            <div className="sum-row"><span className="sk">Scope</span><span className="sv">{sumScope}</span></div>
            <div className="sum-row"><span className="sk">References</span><span className="sv">{ctxRows.length}</span></div>
            <div className="sum-row"><span className="sk">Personas</span><span className="sv">{personaSel}</span></div>
            <div className="sum-row"><span className="sk">Reviewers</span><span className="sv">{reviewers.length}</span></div>
            <div className="sum-row"><span className="sk">Status</span><span className={`sv ${ready ? 'ok' : 'warn'}`}>{ready ? 'Ready for review' : 'Draft'}</span></div>
          </div>
          <div className="sum-card" style={{ display: 'grid', gap: 10 }}>
            <button type="button" className="inline-flex h-9 items-center justify-center rounded-none bg-accent px-4 text-[13px] font-semibold text-on-accent hover:bg-accent-ink" onClick={submit}>
              {ready ? 'Submit · ready for review' : 'Submit as draft'}
            </button>
            <button type="button" className="inline-flex h-9 items-center justify-center rounded-none px-4 text-[13px] font-semibold text-muted hover:text-accent" onClick={() => router.push('/queue')}>Cancel</button>
            <p className="mt-0.5 text-center font-mono text-[11px] leading-[1.5] text-muted">Lands in your open MRs. Arnold begins analysing immediately; reviewers are notified only once it&apos;s ready.</p>
          </div>
        </aside>
      </form>
    </div>
  );
}
