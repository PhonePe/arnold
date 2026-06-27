'use client';

import React, { useState } from 'react';

// ── data model (ported from the Lattice reference) ─────────────────────────
const L = (t, o, n, x) => ({ t, o, n, x });

const FILES = [
  { f: 'provider.go', path: 'internal/auth/provider.go', ai: '⚑ auth · token refresh', sev: 3, sevc: 'var(--sev-critical)', cnt: 3, add: 18, del: 4, blocks: [
    { h: { title: '@@ Refresh(ctx) — token refresh path', lines: [
      L('ctx', 104, 104, ' func (p *Provider) Refresh(ctx) error {'),
      L('ctx', 105, 105, '     p.mu.Lock()'),
      L('flag', 106, 106, '     defer p.mu.Unlock()   // ← lock held across net call'),
      L('del', 108, null, '     if p.token == "" { return ErrNoToken }'),
      L('add', null, 108, '     if p.token == "" { return ErrNoToken }  // kept'),
      L('flag', 111, 111, '     newTok, err := p.client.Get(ctx)  // blocks on p.mu'),
    ] } },
    { thread: { kind: 'finding', sevColor: 'var(--sev-critical)', sevLabel: 'critical bug', who: { av: 'SA', nm: 'Sai', hb: '· 2 lanes agree' }, anchor: 'provider.go : 105–111',
      body: 'Mutex <code>p.mu</code> is held across <code>p.client.Get(ctx)</code>. A network stall cascades into a hot-path deadlock. Release before I/O, re-acquire to commit.',
      fix: [['del', '- defer p.mu.Unlock()'], ['add', '+ p.mu.Unlock()                    // release before I/O'], ['add', '+ newTok, err := p.client.Get(ctx)'], ['add', '+ p.mu.Lock(); defer p.mu.Unlock()']] } },
    { thread: { kind: 'ask', who: { av: 'AR', nm: 'Arnold', hb: '· dev-gauge' }, anchor: 'posed to @jdoe',
      body: '<i>"You\u2019ve used a singleton <code>Provider</code> — how does this design handle two goroutines calling <code>Refresh()</code> at once?"</i>' } },
    { h: { title: '@@ logRefresh()', lines: [
      L('del', 131, null, '     log.Debug("refreshed", "token", newTok)   // ← secret in logs'),
      L('add', null, 131, '     log.Debug("refreshed", "token", redact(newTok))'),
    ] } },
    { thread: { kind: 'finding', sevColor: 'var(--sev-security)', sevLabel: 'security', who: { av: 'NE', nm: 'Netra', hb: '· compliance' }, anchor: 'provider.go : 131 · policy SEC-014',
      body: 'Token logged at <code>debug</code> — SEC-014 violation. Redact before merge.',
      fix: [['del', '- log.Debug("refreshed", "token", newTok)'], ['add', '+ log.Debug("refreshed", "token", redact(newTok))']] } },
  ] },
  { f: 'token_store.go', path: 'internal/auth/token_store.go', ai: '⚑ auth · token refresh', sev: 1, sevc: 'var(--sev-maint)', cnt: 1, add: 22, del: 31, blocks: [
    { h: { title: '@@ Store.Put — bounded cache', lines: [
      L('ctx', 40, 40, ' func (s *Store) Put(k string, t Token) {'),
      L('del', 41, null, '     s.m[k] = t              // unbounded map'),
      L('add', null, 41, '     s.lru.Add(k, t)         // bounded LRU, 4k cap'),
      L('ctx', 42, 42, ' }'),
    ] } },
    { thread: { kind: 'finding', sevColor: 'var(--sev-maint)', sevLabel: 'maintainability', who: { av: 'SA', nm: 'Sai', hb: '· structure' }, anchor: 'token_store.go : 41',
      body: 'Good move to a bounded LRU. Add a metric on eviction count so we can alarm if the 4k cap is too small in prod.',
      fix: [['add', '+ metrics.Inc("token_store.evictions")']] } },
  ] },
  { f: 'cache_layer.go', path: 'internal/cache/cache_layer.go', ai: '⚑ cache · warmup', sev: 2, sevc: 'var(--sev-perf)', cnt: 2, add: 96, del: 12, blocks: [
    { h: { title: '@@ Cache.Warm — batched warmup', lines: [
      L('ctx', 60, 60, ' func (c *Cache) Warm(ctx) error {'),
      L('flag', 62, 62, '     for _, k := range c.keys {        // N sequential round-trips'),
      L('del', 63, null, '         c.store.Get(ctx, k)'),
      L('add', null, 63, '         c.batch = append(c.batch, k)'),
      L('add', null, 65, '     c.store.MGet(ctx, c.batch)        // single batched call'),
    ] } },
    { thread: { kind: 'finding', sevColor: 'var(--sev-perf)', sevLabel: 'performance', who: { av: 'AA', nm: 'Aarav', hb: '· perf' }, anchor: 'cache_layer.go : 62–65',
      body: 'Batching is the right call — drops warmup from O(N) round-trips to 1. Cap <code>c.batch</code> length so a huge key-set doesn\u2019t blow the request size limit.',
      fix: [['add', '+ const maxBatch = 512'], ['add', '+ for chunk := range chunks(c.batch, maxBatch) { c.store.MGet(ctx, chunk) }']] } },
    { thread: { kind: 'finding', sevColor: 'var(--sev-nit)', sevLabel: 'nit', who: { av: 'CP', nm: 'Copilot', hb: '· default' }, anchor: 'cache_layer.go : 63',
      body: 'Pre-allocate <code>c.batch</code> with <code>make([]string, 0, len(c.keys))</code> to avoid repeated re-grows.' } },
  ] },
  { f: 'eviction.go', path: 'internal/cache/eviction.go', ai: 'cache · eviction', sev: 1, sevc: 'var(--sev-bug)', cnt: 1, add: 41, del: 9, blocks: [
    { h: { title: '@@ Evictor.evict — cap boundary', lines: [
      L('ctx', 22, 22, ' func (e *Evictor) evict() {'),
      L('flag', 24, 24, '     if e.size > e.cap {              // off-by-one: should be >='),
      L('del', 25, null, '         e.removeOldest()'),
      L('add', null, 25, '         for e.size >= e.cap { e.removeOldest() }'),
    ] } },
    { thread: { kind: 'finding', sevColor: 'var(--sev-bug)', sevLabel: 'logic bug', who: { av: 'SA', nm: 'Sai', hb: '· reasoning' }, anchor: 'eviction.go : 24–25',
      body: 'Single <code>removeOldest()</code> only frees one slot but a batched insert can exceed cap by many. Loop until under cap.',
      fix: [['del', '- if e.size > e.cap { e.removeOldest() }'], ['add', '+ for e.size >= e.cap { e.removeOldest() }']] } },
  ] },
  { f: 'schema.sql', path: 'db/migrations/0042_cache_index.sql', ai: 'db · migration', sev: 1, sevc: 'var(--sev-bug)', cnt: 1, add: 41, del: 0, blocks: [
    { h: { title: '@@ 0042_cache_index.sql', lines: [
      L('add', null, 1, '+ CREATE INDEX idx_cache_key ON cache_entries(key);'),
      L('add', null, 2, '+ ALTER TABLE cache_entries ADD COLUMN warmed_at timestamptz;'),
    ] } },
    { thread: { kind: 'finding', sevColor: 'var(--sev-bug)', sevLabel: 'migration risk', who: { av: 'NE', nm: 'Netra', hb: '· data' }, anchor: '0042_cache_index.sql : 1',
      body: '<code>CREATE INDEX</code> on a large table locks writes. Use <code>CREATE INDEX CONCURRENTLY</code> outside a transaction.',
      fix: [['del', '- CREATE INDEX idx_cache_key ON cache_entries(key);'], ['add', '+ CREATE INDEX CONCURRENTLY idx_cache_key ON cache_entries(key);']] } },
  ] },
  { f: 'handler.go', path: 'api/handler.go', ai: 'api · endpoints', sev: 1, sevc: 'var(--sev-maint)', cnt: 1, add: 53, del: 14, blocks: [
    { h: { title: '@@ Handler.Get — timeout + error', lines: [
      L('ctx', 88, 88, ' func (h *Handler) Get(w, r) {'),
      L('add', null, 89, '     ctx, cancel := context.WithTimeout(r.Context(), 2*time.Second)'),
      L('flag', 91, 91, '     v, _ := h.cache.Get(ctx, key)   // error ignored'),
    ] } },
    { thread: { kind: 'finding', sevColor: 'var(--sev-maint)', sevLabel: 'error handling', who: { av: 'CP', nm: 'Copilot', hb: '· default' }, anchor: 'handler.go : 91',
      body: 'Error from <code>h.cache.Get</code> is discarded — a cache miss is indistinguishable from a backend failure. Handle the error explicitly.' } },
  ] },
  { f: 'config.yaml', path: 'config/cache.yaml', ai: 'config', sev: 0, sevc: '', cnt: 0, add: 6, del: 2, blocks: [
    { h: { title: '@@ cache.yaml — warm tuning', lines: [
      L('del', 12, null, '   warm_concurrency: unlimited'),
      L('add', null, 12, '   warm_concurrency: 8'),
      L('add', null, 13, '   warm_batch_size: 512'),
    ] } },
  ] },
  { f: 'README.md', path: 'docs/cache/README.md', ai: 'docs', sev: 0, sevc: '', cnt: 0, add: 12, del: 1, blocks: [
    { h: { title: '@@ README.md — warmup docs', lines: [
      L('add', null, 4, '+ ## Cache warmup'),
      L('add', null, 5, '+ The neural cache layer warms keys in batches of 512 on boot.'),
    ] } },
  ] },
];

const SECTIONS = [
  { n: 1, nm: 'auth · token refresh', why: 'review first · blockers', files: ['provider.go', 'token_store.go'] },
  { n: 2, nm: 'cache · warmup', why: 'perf / correctness', files: ['cache_layer.go', 'eviction.go'] },
  { n: 3, nm: 'db · migration', why: 'minor · risk', files: ['schema.sql'] },
  { n: 4, nm: 'api · endpoints', why: 'minor', files: ['handler.go'] },
  { n: 5, nm: 'config · docs', why: 'clean · skim', files: ['config.yaml', 'README.md'] },
];

const STATES = ['CREATED', 'ANALYSING', 'ANALYSED', 'REVIEW_READY', 'MERGED'];
const TOT_ADD = FILES.reduce((s, f) => s + f.add, 0);
const TOT_DEL = FILES.reduce((s, f) => s + f.del, 0);

const Check = () => (
  <svg viewBox="0 0 24 24" strokeWidth={3}><path d="M5 13l4 4L19 7" /></svg>
);

function DiffPre({ block }) {
  return (
    <div className="diff">
      <div className="fh"><span className="fn">{block.h.title}</span></div>
      <pre>
        {block.h.lines.map((ln, i) => (
          <div key={i} className={`ln ${ln.t === 'add' ? 'add' : ln.t === 'del' ? 'del' : ln.t === 'flag' ? 'flag' : ''}`}>
            <span className="g">{ln.o ?? ln.n ?? ''}</span>
            <span className="c">{ln.x}</span>
          </div>
        ))}
      </pre>
    </div>
  );
}

function Thread({ thread }) {
  const ask = thread.kind === 'ask';
  return (
    <div className={`thread ${ask ? 'ask' : ''}`}>
      <div className="anchorline">{thread.anchor}</div>
      <div className="ch">
        {!ask && (
          <span className="sev-pill" style={{ color: thread.sevColor }}>
            <span className="sev-dot" style={{ background: thread.sevColor }} />
            {thread.sevLabel}
          </span>
        )}
        <div className="who">
          <span className="avatar sm">{thread.who.av}</span>
          <span className="nm">{thread.who.nm}</span>
          <span className="hb">{thread.who.hb}</span>
        </div>
      </div>
      <div className="tbody" dangerouslySetInnerHTML={{ __html: thread.body }} />
      {thread.fix && (
        <div className="fix">
          <div className="fixh">
            <svg viewBox="0 0 24 24" strokeWidth={2}><path d="M9 11l3 3L22 4M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11" /></svg>
            Suggested fix
          </div>
          <pre>{thread.fix.map((f, i) => (<div key={i} className={f[0]}>{f[1]}</div>))}</pre>
        </div>
      )}
      <div className="acts">
        <button className="inline-flex h-7 items-center rounded-none bg-ok px-2.5 text-[11.5px] font-semibold text-white hover:brightness-95">Apply fix</button>
        <button className="inline-flex h-7 items-center rounded-none px-2.5 text-[11.5px] font-semibold text-muted hover:text-accent">Reply</button>
        <button className="inline-flex h-7 items-center rounded-none px-2.5 text-[11.5px] font-semibold text-muted hover:text-accent">Dismiss</button>
      </div>
    </div>
  );
}

export default function ReviewPage() {
  const [role, setRole] = useState('author');
  const [state, setState] = useState('ANALYSED');
  const [tab, setTab] = useState('changes');
  const [viewed, setViewed] = useState(() => new Set());

  const toggleViewed = (f) =>
    setViewed((prev) => {
      const next = new Set(prev);
      next.has(f) ? next.delete(f) : next.add(f);
      return next;
    });

  const stateIdx = STATES.indexOf(state);
  const fileByName = Object.fromEntries(FILES.map((f) => [f.f, f]));

  return (
    <div className="rvx" data-role={role} data-state={state}>
      {/* header strip */}
      <div className="rvx-head">
        <div>
          <p className="eyebrow">MR !482 · core-infrastructure</p>
          <div className="ttl">feat: implement neural cache layer</div>
          <div className="meta">12 files · author @jdoe · feat/neural-cache → main</div>
        </div>
        <span className="spacer" />
        <div className="flex items-center gap-[7px]">
          <span className="glab">Role</span>
          <div className="role-toggle">
            <button className={role === 'author' ? 'on' : ''} onClick={() => setRole('author')}>
              <span className="avatar">JD</span>Author
            </button>
            <button className={role === 'reviewer' ? 'on' : ''} onClick={() => setRole('reviewer')}>
              <span className="avatar">MR</span>Reviewer
            </button>
          </div>
        </div>
        <div className="flex items-center">
          <span className="glab">State</span>
          <div className="state-rail">
            {STATES.map((s, i) => (
              <React.Fragment key={s}>
                {i > 0 && <span className="sep">›</span>}
                <span
                  className={`st ${s === state ? 'now' : i < stateIdx ? 'done' : ''}`}
                  onClick={() => setState(s)}
                >
                  {s}
                </span>
              </React.Fragment>
            ))}
          </div>
        </div>
        <div className="flex items-center gap-2">
          <button className="inline-flex h-8 items-center gap-1.5 rounded-none border border-border-2 bg-surface px-3 text-xs font-semibold text-fg hover:border-fg">View on GitLab ↗</button>
          <button className="inline-flex h-8 items-center gap-1.5 rounded-none bg-accent px-3 text-xs font-semibold text-on-accent opacity-[0.42]" disabled>Publish</button>
        </div>
      </div>

      <div className="rvx-body">
        {/* LEFT: file tree */}
        <aside className="rvx-side">
          <div className="rvx-blk">
            <h4>Review progress</h4>
            <div className="prog">
              {FILES.map((f) => (
                <i key={f.f} className={viewed.has(f.f) ? 'done' : ''} />
              ))}
            </div>
            <div className="mt-[7px] font-mono text-[11px] text-muted">
              <b>{viewed.size}</b> of {FILES.length} files viewed · <b>6</b> findings open
            </div>
          </div>
          <div className="rvx-blk fblk" style={{ flex: 1 }}>
            <div className="fbar">
              <h4 style={{ margin: 0 }}>Changed files</h4>
              <span className="fsum font-mono">{FILES.length} files</span>
            </div>
            <div className="ftree">
              {SECTIONS.map((sec) => (
                <React.Fragment key={sec.n}>
                  <div className="grp">{sec.n} · {sec.nm}</div>
                  {sec.files.map((fn) => {
                    const f = fileByName[fn];
                    return (
                      <div
                        key={fn}
                        className={`fitem ${viewed.has(fn) ? 'viewed' : ''}`}
                        onClick={() => toggleViewed(fn)}
                      >
                        <span className="vchk"><Check /></span>
                        <span className="nm">{f.f}</span>
                        <span className="fmeta flex items-center gap-1.5">
                          <span className="fst"><span className="ad">+{f.add}</span> <span className="dl">−{f.del}</span></span>
                          {f.cnt > 0 ? (
                            <span className="cnt"><span className="badge" style={{ background: f.sevc }} /> {f.cnt}</span>
                          ) : (
                            <span className="clean">clean</span>
                          )}
                        </span>
                      </div>
                    );
                  })}
                </React.Fragment>
              ))}
            </div>
          </div>
        </aside>

        {/* CENTER */}
        <main className="rvx-center">
          <div className="chead">
            <div className="ctabs">
              <button className={tab === 'changes' ? 'on' : ''} onClick={() => setTab('changes')}>Changes</button>
              <button className={tab === 'commits' ? 'on' : ''} onClick={() => setTab('commits')}>Commits · 7</button>
            </div>
            <span className="chead-sub">10 sections · 12 files · 11 findings</span>
          </div>

          {tab === 'changes' && (
            <div className="center-body">
              {/* suggested review path */}
              <div className="aistory">
                <h3>Suggested review path</h3>
                <p>
                  Arnold clustered the 12 files into 10 cohesive sections and ordered them so the
                  riskiest change-sets come first. Walk them top-to-bottom — jump straight to any section.
                </p>
                <div className="storysteps">
                  {SECTIONS.map((sec) => (
                    <button key={sec.n} className="storystep">
                      <span className="num">{sec.n}</span>
                      <span className="snm">{sec.nm}</span>
                      <span className="why">{sec.why}</span>
                    </button>
                  ))}
                </div>
              </div>

              {/* sections with file accordions */}
              {SECTIONS.map((sec) => (
                <div key={sec.n} className="csec">
                  <div className="csec-h">
                    <span className="csec-num">{sec.n}</span>
                    <span className="csec-nm">{sec.nm}</span>
                    <span className="csec-why">{sec.why}</span>
                  </div>
                  {sec.files.map((fn) => {
                    const f = fileByName[fn];
                    return (
                      <div key={fn} className={`cfile ${viewed.has(fn) ? 'viewed' : ''}`}>
                        <div className="cfile-h" onClick={() => toggleViewed(fn)}>
                          <span className="vchk"><Check /></span>
                          <span className="cfile-nm">{f.path}</span>
                          <span className="cfile-st"><span className="ad">+{f.add}</span> <span className="dl">−{f.del}</span></span>
                          {f.cnt > 0 ? (
                            <span className="cfile-cnt" style={{ color: f.sevc }}>
                              <span className="badge" style={{ background: f.sevc }} /> {f.cnt}
                            </span>
                          ) : (
                            <span className="clean">clean</span>
                          )}
                          <span className="spacer" />
                        </div>
                        <div className="cfile-body">
                          {f.blocks.map((b, i) =>
                            b.h ? <DiffPre key={i} block={b} /> : <Thread key={i} thread={b.thread} />,
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              ))}
            </div>
          )}

          {tab === 'commits' && (
            <div className="center-body">
              <div className="rc"><div className="rcb">
                {[
                  { msg: 'fix: redact token before debug logging', meta: '@jdoe · 2h ago', sha: 'a3f91c2', diff: '+4 −2', dot: 'var(--bad)', stat: 'fail', statLabel: 'pipeline failed' },
                  { msg: 'refactor: release mutex before network call', meta: '@jdoe · 4h ago', sha: '7d20e8b', diff: '+18 −4', dot: 'var(--ok)', stat: 'ok', statLabel: 'passed' },
                  { msg: 'feat: warm cache on provider boot', meta: '@jdoe · 6h ago', sha: 'c1b8f44', diff: '+96 −12', dot: 'var(--ok)', stat: 'ok', statLabel: 'passed' },
                  { msg: 'chore: schema migration for cache index', meta: '@jdoe · 1d ago', sha: 'b44c1de', diff: '+41 −0', dot: 'var(--sev-maint)', stat: 'run', statLabel: 'running' },
                ].map((c) => (
                  <div key={c.sha} className="commit">
                    <span className="cdot" style={{ borderColor: c.dot }} />
                    <div>
                      <div className="cmsg">{c.msg}</div>
                      <div className="cmeta">{c.meta} · <span className="csha">{c.sha}</span> · {c.diff}</div>
                    </div>
                    <span className={`pstat ${c.stat}`}><span className="pd" />{c.statLabel}</span>
                  </div>
                ))}
              </div></div>
            </div>
          )}
        </main>

        {/* RIGHT RAIL */}
        <aside className="rvx-rail">
          <div className="rc">
            <div className="rch"><h4>Change summary</h4></div>
            <div className="rcb">
              <div className="sumgrid">
                <div><div className="big">{FILES.length}</div><div className="lab2">files</div></div>
                <div><div className="big" style={{ color: 'var(--ok)' }}>+{TOT_ADD}</div><div className="lab2">added</div></div>
                <div><div className="big" style={{ color: 'var(--bad)' }}>−{TOT_DEL}</div><div className="lab2">removed</div></div>
              </div>
            </div>
          </div>

          <div className="rc">
            <div className="rch"><h4>Overall rating &amp; health</h4></div>
            <div className="rcb">
              <div className="score-row">
                <div className="score"><div className="lab2">Overall rating</div><div className="big bad">B−</div><div className="sub">2 critical · 6 open</div></div>
                <div className="score"><div className="lab2">Security health</div><div className="big" style={{ color: 'var(--bad)' }}>Risk</div><div className="meter" style={{ marginTop: 2 }}><i style={{ width: '88%', background: 'var(--bad)' }} /></div><div className="sub" style={{ marginTop: 6 }}>88 / 100 risk</div></div>
              </div>
              <p className="mt-[11px] text-[12.5px] text-muted">
                Critical race condition + a logged secret in the token-refresh path. Overall risk{' '}
                <b className="text-accent-ink">HIGH</b>.
              </p>
            </div>
          </div>

          {role === 'author' ? (
            <div className="rc">
              <div className="rch"><h4>Author next steps</h4></div>
              <div className="rcb">
                <button className="mb-2 inline-flex h-8 w-full items-center justify-center gap-1.5 rounded-none bg-accent text-xs font-semibold text-on-accent hover:bg-accent-ink">Push fixes &amp; re-run analysis</button>
                <button className="inline-flex h-8 w-full items-center justify-center rounded-none border border-border-2 bg-surface text-xs font-semibold text-fg hover:border-fg" onClick={() => setState('REVIEW_READY')}>Mark REVIEW_READY</button>
              </div>
            </div>
          ) : (
            <div className="rc">
              <div className="rch"><h4>Reviewer actions</h4></div>
              <div className="rcb">
                <button className="mb-2 inline-flex h-8 w-full items-center justify-center rounded-none bg-accent text-xs font-semibold text-on-accent hover:bg-accent-ink">Approve</button>
                <button className="inline-flex h-8 w-full items-center justify-center rounded-none border border-border-2 bg-surface text-xs font-semibold text-fg hover:border-fg">Request changes</button>
              </div>
            </div>
          )}

          <div className="rc">
            <div className="rch"><h4>Pipeline #4821</h4><span className="pstat fail ra"><span className="pd" />failed</span></div>
            <div className="rcb">
              <div className="pipe">
                <span className="pstage ok"><span className="pi"><Check /></span>build</span><span className="parrow">›</span>
                <span className="pstage ok"><span className="pi"><Check /></span>lint</span><span className="parrow">›</span>
                <span className="pstage fail"><span className="pi"><svg viewBox="0 0 24 24" strokeWidth={3}><path d="M6 6l12 12M18 6 6 18" /></svg></span>test</span><span className="parrow">›</span>
                <span className="pstage skip"><span className="pi" />deploy</span>
              </div>
              <div className="failbox">
                <div className="fl">
                  <svg viewBox="0 0 24 24" width="13" height="13" strokeWidth={2}><path d="M12 3l9 16H3Z" /><path d="M12 10v4" /></svg>
                  test · TestConcurrentRefresh — data race detected
                </div>
                <p className="mb-[9px] text-[11.5px] text-muted">go test -race flagged provider.go:108. Same root cause as the critical finding.</p>
                <button className="kadhai-btn">
                  <svg viewBox="0 0 24 24" strokeWidth={1.8}><path d="M12 2a4 4 0 0 1 4 4v1a4 4 0 0 1-8 0V6a4 4 0 0 1 4-4Z" /><path d="M5 21v-2a4 4 0 0 1 4-4h6a4 4 0 0 1 4 4v2" /></svg>
                  Delegate fix to Kadhai
                </button>
              </div>
            </div>
          </div>

          <div className="rc">
            <div className="rch"><h4>Merge request</h4></div>
            <div className="rcb">
              <div className="mrow"><span className="mk">Assignee</span><div className="mv"><span className="avatar sm">JD</span>Jaya D.</div></div>
              <div className="mrow"><span className="mk">Reviewers</span><div className="mv"><span className="ava-stack"><span className="avatar sm">MR</span><span className="avatar sm">PK</span></span> Meera R., Priya K.</div></div>
              <div className="mrow"><span className="mk">Labels</span><div className="mv"><span className="label-chip"><span className="ld" style={{ background: 'var(--sev-security)' }} />security</span><span className="label-chip"><span className="ld" style={{ background: 'var(--sev-perf)' }} />performance</span></div></div>
              <div className="mrow"><span className="mk">Sync</span><div className="mv"><span className="sync live"><span className="dot2" /><span className="text-[11px] text-[var(--sev-maint)]">GitLab live</span></span></div></div>
            </div>
          </div>

          <div className="rc">
            <div className="rch"><h4>Linked context</h4></div>
            <div className="rcb">
              <div className="lcx"><div className="lt"><span className="sev-dot" style={{ background: 'var(--sev-maint)' }} />JIRA-1442</div><div className="ld">Secondary auth factor for enterprise.</div></div>
              <div className="lcx"><div className="lt"><span className="sev-dot" style={{ background: 'var(--sev-enh)' }} />RFC-089 · Auth V2</div><div className="ld">Functional agent verified against this.</div></div>
              <div className="lcx"><div className="lt"><span className="sev-dot" style={{ background: 'var(--bad)' }} />!318 · prior deadlock</div><div className="ld">Same lock pattern broke Mar-24.</div></div>
            </div>
          </div>
        </aside>
      </div>
    </div>
  );
}
