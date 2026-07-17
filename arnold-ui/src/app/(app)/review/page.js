'use client';

import React, { useMemo, useRef, useState, useCallback } from 'react';

// ── data model (ported from the Lattice reference — cross-repo MR group) ────
const L = (t, o, n, x) => ({ t, o, n, x });
const slug = (s) => String(s).replace(/[^a-z0-9]+/gi, '-').replace(/^-|-$/g, '').toLowerCase();
const fmt = (n) => n.toLocaleString();
const esc = (s) => s;

const STATES = ['CREATED', 'ANALYSING', 'ANALYSED', 'REVIEW_READY', 'MERGED'];

const FILES = [
  { f: 'provider.go', path: 'internal/auth/provider.go', mod: 'internal/auth', sev: 3, sevc: 'var(--sev-critical)', cnt: 3, add: 18, del: 4, blocks: [
    { h: { title: '@@ Refresh(ctx) — token refresh path', lines: [
      L('ctx', 104, 104, ' func (p *Provider) Refresh(ctx) error {'),
      L('ctx', 105, 105, '     p.mu.Lock()'),
      L('flag', 106, 106, '     defer p.mu.Unlock()   // ← lock held across net call'),
      L('del', 108, null, '     if p.token == "" { return ErrNoToken }'),
      L('add', null, 108, '     if p.token == "" { return ErrNoToken }  // kept'),
      L('flag', 111, 111, '     newTok, err := p.client.Get(ctx)  // blocks on p.mu'),
    ] } },
    { thread: { id: 'p-crit', kind: 'finding', sevColor: 'var(--sev-critical)', sevLabel: 'critical bug', status: 'open', who: { av: 'SA', nm: 'Sai', hb: '· 2 lanes agree' }, anchor: 'provider.go : 105–111',
      body: 'Mutex <code>p.mu</code> is held across <code>p.client.Get(ctx)</code>. A network stall cascades into a hot-path deadlock. Release before I/O, re-acquire to commit.',
      fix: [['del', '- defer p.mu.Unlock()'], ['add', '+ p.mu.Unlock()                    // release before I/O'], ['add', '+ newTok, err := p.client.Get(ctx)'], ['add', '+ p.mu.Lock(); defer p.mu.Unlock()']] } },
    { thread: { id: 'p-ask', kind: 'ask', status: 'open', who: { av: 'AR', nm: 'Arnold', hb: '· dev-gauge' }, anchor: 'posed to @jdoe',
      body: '<i>"You\u2019ve used a singleton <code>Provider</code> — how does this design handle two goroutines calling <code>Refresh()</code> at once?"</i>' } },
    { h: { title: '@@ logRefresh()', lines: [
      L('del', 131, null, '     log.Debug("refreshed", "token", newTok)   // ← secret in logs'),
      L('add', null, 131, '     log.Debug("refreshed", "token", redact(newTok))'),
    ] } },
    { thread: { id: 'p-sec', kind: 'finding', sevColor: 'var(--sev-security)', sevLabel: 'security', status: 'open', who: { av: 'NE', nm: 'Netra', hb: '· compliance' }, anchor: 'provider.go : 131 · policy SEC-014',
      body: 'Token logged at <code>debug</code> — SEC-014 violation. Redact before merge.',
      fix: [['del', '- log.Debug("refreshed", "token", newTok)'], ['add', '+ log.Debug("refreshed", "token", redact(newTok))']] } },
  ] },
  { f: 'token_store.go', path: 'internal/auth/token_store.go', mod: 'internal/auth', sev: 1, sevc: 'var(--sev-maint)', cnt: 1, add: 22, del: 31, blocks: [
    { h: { title: '@@ Store.Put — bounded cache', lines: [
      L('ctx', 40, 40, ' func (s *Store) Put(k string, t Token) {'),
      L('del', 41, null, '     s.m[k] = t              // unbounded map'),
      L('add', null, 41, '     s.lru.Add(k, t)         // bounded LRU, 4k cap'),
      L('ctx', 42, 42, ' }'),
    ] } },
    { thread: { id: 't-maint', kind: 'finding', sevColor: 'var(--sev-maint)', sevLabel: 'maintainability', status: 'open', who: { av: 'SA', nm: 'Sai', hb: '· structure' }, anchor: 'token_store.go : 41',
      body: 'Good move to a bounded LRU. Add a metric on eviction count so we can alarm if the 4k cap is too small in prod.',
      fix: [['add', '+ metrics.Inc("token_store.evictions")']] } },
  ] },
  { f: 'session.go', path: 'internal/auth/session.go', mod: 'internal/auth', sev: 0, sevc: '', cnt: 0, add: 9, del: 2, blocks: [
    { h: { title: '@@ NewSession — v7 ids', lines: [
      L('ctx', 18, 18, ' func NewSession(u User) *Session {'),
      L('add', null, 19, '     id := uuid.NewV7()      // time-ordered ids'),
      L('ctx', 20, 20, '     return &Session{ID: id, User: u}'),
    ] } },
  ] },
  { f: 'cache_layer.go', path: 'internal/cache/cache_layer.go', mod: 'internal/cache', sev: 2, sevc: 'var(--sev-perf)', cnt: 2, add: 96, del: 12, blocks: [
    { h: { title: '@@ Cache.Warm — batched warmup', lines: [
      L('ctx', 60, 60, ' func (c *Cache) Warm(ctx) error {'),
      L('flag', 62, 62, '     for _, k := range c.keys {        // N sequential round-trips'),
      L('del', 63, null, '         c.store.Get(ctx, k)'),
      L('add', null, 63, '         c.batch = append(c.batch, k)'),
      L('add', null, 64, '     }'),
      L('add', null, 65, '     c.store.MGet(ctx, c.batch)        // single batched call'),
    ] } },
    { thread: { id: 'c-perf', kind: 'finding', sevColor: 'var(--sev-perf)', sevLabel: 'performance', status: 'open', who: { av: 'AA', nm: 'Aarav', hb: '· perf' }, anchor: 'cache_layer.go : 62–65',
      body: 'Batching is the right call — drops warmup from O(N) round-trips to 1. Cap <code>c.batch</code> length so a huge key-set doesn\u2019t blow the request size limit.',
      fix: [['add', '+ const maxBatch = 512'], ['add', '+ for chunk := range chunks(c.batch, maxBatch) { c.store.MGet(ctx, chunk) }']] } },
    { thread: { id: 'c-nit', kind: 'finding', sevColor: 'var(--sev-nit)', sevLabel: 'nit', status: 'open', who: { av: 'CP', nm: 'Copilot', hb: '· default' }, anchor: 'cache_layer.go : 63',
      body: 'Pre-allocate <code>c.batch</code> with <code>make([]string, 0, len(c.keys))</code> to avoid repeated re-grows.' } },
  ] },
  { f: 'cache_warmer.go', path: 'internal/cache/cache_warmer.go', mod: 'internal/cache', sev: 1, sevc: 'var(--sev-perf)', cnt: 1, add: 34, del: 5, blocks: [
    { h: { title: '@@ Warmer.Run — bounded concurrency', lines: [
      L('ctx', 12, 12, ' func (w *Warmer) Run() {'),
      L('del', 13, null, '     go w.warm()             // unbounded goroutine'),
      L('add', null, 13, '     w.sem <- struct{}{}     // bounded by semaphore'),
      L('add', null, 14, '     go func(){ defer func(){ <-w.sem }(); w.warm() }()'),
    ] } },
    { thread: { id: 'w-perf', kind: 'finding', sevColor: 'var(--sev-perf)', sevLabel: 'performance', status: 'open', who: { av: 'AA', nm: 'Aarav', hb: '· perf' }, anchor: 'cache_warmer.go : 13',
      body: 'Semaphore bound is good. Document the buffer size of <code>w.sem</code> — it silently sets max warm concurrency.' } },
  ] },
  { f: 'cache_test.go', path: 'internal/cache/cache_test.go', mod: 'internal/cache', sev: 0, sevc: '', cnt: 0, add: 134, del: 0, blocks: [
    { h: { title: '@@ new test · batched warmup', lines: [
      L('add', null, 80, '+ func TestWarm_Batched(t *testing.T) {'),
      L('add', null, 81, '+     c := newTestCache(1000)'),
      L('add', null, 82, '+     require.NoError(t, c.Warm(ctx))'),
      L('add', null, 83, '+ }'),
    ] } },
  ] },
  { f: 'eviction.go', path: 'internal/cache/eviction.go', mod: 'internal/cache', sev: 1, sevc: 'var(--sev-bug)', cnt: 1, add: 41, del: 9, blocks: [
    { h: { title: '@@ Evictor.evict — cap boundary', lines: [
      L('ctx', 22, 22, ' func (e *Evictor) evict() {'),
      L('flag', 24, 24, '     if e.size > e.cap {              // off-by-one: should be >='),
      L('del', 25, null, '         e.removeOldest()'),
      L('add', null, 25, '         for e.size >= e.cap { e.removeOldest() }'),
    ] } },
    { thread: { id: 'e-bug', kind: 'finding', sevColor: 'var(--sev-bug)', sevLabel: 'logic bug', status: 'open', who: { av: 'SA', nm: 'Sai', hb: '· reasoning' }, anchor: 'eviction.go : 24–25',
      body: 'Single <code>removeOldest()</code> only frees one slot but a batched insert can exceed cap by many. Loop until under cap.',
      fix: [['del', '- if e.size > e.cap { e.removeOldest() }'], ['add', '+ for e.size >= e.cap { e.removeOldest() }']] } },
  ] },
  { f: 'schema.sql', path: 'db/migrations/0042_cache_index.sql', mod: 'db/migrations', sev: 1, sevc: 'var(--sev-bug)', cnt: 1, add: 41, del: 0, blocks: [
    { h: { title: '@@ 0042_cache_index.sql', lines: [
      L('add', null, 1, '+ CREATE INDEX idx_cache_key ON cache_entries(key);'),
      L('add', null, 2, '+ ALTER TABLE cache_entries ADD COLUMN warmed_at timestamptz;'),
    ] } },
    { thread: { id: 's-mig', kind: 'finding', sevColor: 'var(--sev-bug)', sevLabel: 'migration risk', status: 'open', who: { av: 'NE', nm: 'Netra', hb: '· data' }, anchor: '0042_cache_index.sql : 1',
      body: '<code>CREATE INDEX</code> on a large table locks writes. Use <code>CREATE INDEX CONCURRENTLY</code> outside a transaction.',
      fix: [['del', '- CREATE INDEX idx_cache_key ON cache_entries(key);'], ['add', '+ CREATE INDEX CONCURRENTLY idx_cache_key ON cache_entries(key);']] } },
  ] },
  { f: 'handler.go', path: 'api/handler.go', mod: 'api', sev: 1, sevc: 'var(--sev-maint)', cnt: 1, add: 53, del: 14, blocks: [
    { h: { title: '@@ Handler.Get — timeout + error', lines: [
      L('ctx', 88, 88, ' func (h *Handler) Get(w, r) {'),
      L('add', null, 89, '     ctx, cancel := context.WithTimeout(r.Context(), 2*time.Second)'),
      L('add', null, 90, '     defer cancel()'),
      L('flag', 91, 91, '     v, _ := h.cache.Get(ctx, key)   // error ignored'),
    ] } },
    { thread: { id: 'h-maint', kind: 'finding', sevColor: 'var(--sev-maint)', sevLabel: 'error handling', status: 'open', who: { av: 'CP', nm: 'Copilot', hb: '· default' }, anchor: 'handler.go : 91',
      body: 'Error from <code>h.cache.Get</code> is discarded — a cache miss is indistinguishable from a backend failure. Handle the error explicitly.' } },
  ] },
  { f: 'metrics.go', path: 'internal/telemetry/metrics.go', mod: 'internal/telemetry', sev: 1, sevc: 'var(--sev-nit)', cnt: 1, add: 28, del: 3, blocks: [
    { h: { title: '@@ metrics.go — warm duration', lines: [
      L('add', null, 15, '+ var WarmDuration = prometheus.NewHistogram(...)'),
      L('ctx', 16, 16, ' func RecordWarm(d time.Duration) {'),
      L('add', null, 17, '     WarmDuration.Observe(d.Seconds())'),
    ] } },
    { thread: { id: 'm-nit', kind: 'finding', sevColor: 'var(--sev-nit)', sevLabel: 'nit', status: 'open', who: { av: 'CP', nm: 'Copilot', hb: '· default' }, anchor: 'metrics.go : 15',
      body: 'Define explicit histogram buckets — the default buckets are HTTP-latency oriented and will be coarse for warm timings.' } },
  ] },
  { f: 'config.yaml', path: 'config/cache.yaml', mod: 'config', sev: 0, sevc: '', cnt: 0, add: 6, del: 2, blocks: [
    { h: { title: '@@ cache.yaml — warm tuning', lines: [
      L('del', 12, null, '   warm_concurrency: unlimited'),
      L('add', null, 12, '   warm_concurrency: 8'),
      L('add', null, 13, '   warm_batch_size: 512'),
    ] } },
  ] },
  { f: 'README.md', path: 'docs/cache/README.md', mod: 'docs', sev: 0, sevc: '', cnt: 0, add: 12, del: 1, blocks: [
    { h: { title: '@@ README.md — warmup docs', lines: [
      L('add', null, 4, '+ ## Cache warmup'),
      L('add', null, 5, '+ The neural cache layer warms keys in batches of 512 on boot.'),
    ] } },
  ] },
];

const TOT_ADD = FILES.reduce((s, f) => s + f.add, 0);
const TOT_DEL = FILES.reduce((s, f) => s + f.del, 0);

// primary persona per file (dominant reviewer)
FILES.forEach((f) => {
  const ps = [];
  f.blocks.forEach((b) => { if (b.thread && b.thread.who) { const nm = b.thread.who.nm; if (!ps.includes(nm)) ps.push(nm); } });
  f.personas = ps;
  f.persona = ps[0] || 'No findings yet';
});

// ── multi-MR model: this review is a cross-repo MR GROUP ──
const MRS = [
  { id: '!487', repo: 'auth-service', title: 'feat: harden token refresh', branch: 'feat/token-refresh → main', paths: ['internal/auth/'] },
  { id: '!482', repo: 'core-infrastructure', title: 'feat: implement neural cache layer', branch: 'feat/neural-cache → main', paths: [] },
];
FILES.forEach((f) => { const m = MRS.find((mr) => mr.paths.some((p) => f.path.startsWith(p))) || MRS[MRS.length - 1]; f.mr = m.id; });
MRS.forEach((m) => {
  const fs = FILES.filter((f) => f.mr === m.id);
  m.files = fs;
  m.add = fs.reduce((s, f) => s + f.add, 0);
  m.del = fs.reduce((s, f) => s + f.del, 0);
  m.cnt = fs.reduce((s, f) => s + f.cnt, 0);
  m.maxSev = Math.max(0, ...fs.map((f) => f.sev));
  m.blockers = fs.filter((f) => f.sev >= 3).length;
});

// ── story model: a LINEAR review narrative; a file may appear in >1 chapter ──
const FILE_BY_NAME = {}; FILES.forEach((f) => { FILE_BY_NAME[f.f] = f; });
const STORY = [
  { id: 'st-token', t: 'Token refresh: the race & the secret', sev: 3, why: 'start here · blockers',
    narr: 'Start here. A mutex is held across a network call — a stall cascades into a hot-path deadlock — and the refreshed token is written to debug logs (SEC-014). Fix both before anything else.',
    files: ['provider.go'] },
  { id: 'st-conc', t: 'Concurrency & locking', sev: 2, why: 'correctness',
    narr: 'The two places this change touches goroutine scheduling: the refresh lock (<span class="mono">provider.go</span>) and the warmer\u2019s semaphore. Read them together — the reasoning is the same.',
    files: ['provider.go', 'cache_warmer.go'] },
  { id: 'st-bound', t: 'Bounding unbounded growth', sev: 2, why: 'spans both MRs',
    narr: 'Three independent caps that stop memory and warm-fanout from running away — an LRU on the token store, a cap-loop on eviction, and a semaphore on the warmer. This chapter deliberately spans <b>!487</b> and <b>!482</b>.',
    files: ['token_store.go', 'eviction.go', 'cache_warmer.go'] },
  { id: 'st-warm', t: 'Batched cache warmup', sev: 2, why: 'performance win',
    narr: 'The core performance win: N sequential round-trips collapse into a single batched call, shipped with its config knobs and a covering test.',
    files: ['cache_layer.go', 'config.yaml', 'cache_test.go'] },
  { id: 'st-mig', t: 'Migration & data safety', sev: 1, why: 'minor · caution',
    narr: 'A schema change that can lock writes on a large table if run inside a transaction.',
    files: ['schema.sql'] },
  { id: 'st-api', t: 'API surface & observability', sev: 1, why: 'minor',
    narr: 'Request timeouts, explicit error handling, and the histogram that measures warmup latency.',
    files: ['handler.go', 'metrics.go'] },
  { id: 'st-sup', t: 'Supporting changes', sev: 0, why: 'clean · skim',
    narr: 'Low-risk supporting work: time-ordered session ids and updated docs.',
    files: ['session.go', 'README.md'] },
];
const STORY_REPEATS = {};
FILES.forEach((f) => { const chs = STORY.filter((c) => c.files.includes(f.f)); if (chs.length > 1) STORY_REPEATS[f.f] = chs.length; });

function guidance(sev) {
  if (sev >= 3) return 'review first · blockers';
  if (sev >= 2) return 'perf / correctness';
  if (sev >= 1) return 'minor · nits';
  return 'clean · skim';
}

// mode-aware grouping — the SAME file/finding entities, three lenses
function groupsFor(mode) {
  if (mode === 'story') {
    return STORY.map((c, ci) => {
      const files = c.files.map((n) => FILE_BY_NAME[n]).filter(Boolean);
      return { ci, key: c.id, label: c.t, why: c.why, narr: c.narr, files,
        add: files.reduce((s, f) => s + f.add, 0), del: files.reduce((s, f) => s + f.del, 0),
        cnt: files.reduce((s, f) => s + f.cnt, 0), sev: c.sev };
    });
  }
  if (mode === 'persona') {
    const map = {};
    FILES.forEach((f) => { (map[f.persona] || (map[f.persona] = { key: f.persona, label: f.persona, files: [] })).files.push(f); });
    const arr = Object.values(map);
    arr.forEach((g) => {
      g.add = g.files.reduce((s, f) => s + f.add, 0); g.del = g.files.reduce((s, f) => s + f.del, 0);
      g.cnt = g.files.reduce((s, f) => s + f.cnt, 0); g.sev = Math.max(0, ...g.files.map((f) => f.sev));
    });
    return arr.sort((a, b) => (a.key === 'No findings yet' ? 1 : 0) - (b.key === 'No findings yet' ? 1 : 0) || b.sev - a.sev || b.cnt - a.cnt);
  }
  return MRS.map((m) => ({ key: m.id, label: m.id, repo: m.repo, sub: m.title, mr: m, files: m.files.slice(),
    add: m.add, del: m.del, cnt: m.cnt, sev: m.maxSev }));
}

function visGroups(mode, sevFilter) {
  const pass = (file) => (sevFilter < 0 ? true : file.sev >= sevFilter);
  return groupsFor(mode).map((g) => ({ ...g, vfiles: g.files.filter(pass) })).filter((g) => g.vfiles.length);
}

// ── presentational atoms ────────────────────────────────────────────────────
const Check = () => (<svg viewBox="0 0 24 24" strokeWidth={3} fill="none"><path d="M5 13l4 4L19 7" /></svg>);
const Html = ({ html, ...p }) => <span {...p} dangerouslySetInnerHTML={{ __html: html }} />;

function DiffBlock({ block, fname, split, onAddComment }) {
  const h = block.h;
  if (split) {
    // pair del/add rows for split view
    const rows = [];
    const lines = h.lines;
    let i = 0;
    while (i < lines.length) {
      const l = lines[i];
      if (l.t === 'ctx' || l.t === 'flag') { rows.push({ l, r: l }); i++; continue; }
      const dels = []; const adds = [];
      while (i < lines.length && lines[i].t === 'del') { dels.push(lines[i]); i++; }
      while (i < lines.length && lines[i].t === 'add') { adds.push(lines[i]); i++; }
      const n = Math.max(dels.length, adds.length);
      if (n === 0) { i++; continue; }
      for (let k = 0; k < n; k++) rows.push({ l: dels[k] || null, r: adds[k] || null });
    }
    const sideCls = (l, which) => {
      if (!l) return 'empty';
      if (l.t === 'flag') return 'flag';
      if (which === 'l' && l.t === 'del') return 'del';
      if (which === 'r' && l.t === 'add') return 'add';
      return '';
    };
    return (
      <div className="diff split">
        <div className="fh"><span className="fn">{h.title}</span><span className="mono faint" style={{ fontSize: 10, marginLeft: 'auto' }}>old ◂▸ new</span></div>
        <pre>
          {rows.map((row, ri) => {
            const R1 = row.r; const L1 = row.l;
            const lc = sideCls(L1, 'l'); const rc = sideCls(R1, 'r');
            const no = R1 ? (R1.n ?? '') : '';
            return (
              <span key={ri} className="ln">
                <span className={`g ${lc}`}>{L1 ? (L1.o ?? '') : ''}</span>
                <span className={`c ${lc}`}>{L1 ? esc(L1.x) : ''}</span>
                {R1 && <button className="addc" title={`Comment on line ${no}`} onClick={() => onAddComment(fname, no)}>+</button>}
                <span className={`g2 ${rc}`}>{no}</span>
                <span className={`c2 ${rc}`}>{R1 ? esc(R1.x) : ''}</span>
              </span>
            );
          })}
        </pre>
      </div>
    );
  }
  return (
    <div className="diff">
      <div className="fh"><span className="fn">{h.title}</span></div>
      <pre>
        {h.lines.map((ln, i) => {
          const no = ln.t === 'del' ? (ln.o ?? '') : (ln.n ?? '');
          return (
            <span key={i} className={`ln ${ln.t}`}>
              {ln.t !== 'del' && <button className="addc" title={`Comment on line ${no}`} onClick={() => onAddComment(fname, no)}>+</button>}
              <span className="g">{no}</span>
              <span className="c">{esc(ln.x)}</span>
            </span>
          );
        })}
      </pre>
    </div>
  );
}

function Thread({ thread, role, onAct }) {
  const status = thread.status || 'open';
  const [reply, setReply] = useState(false);
  const [replyVal, setReplyVal] = useState('');
  const [reply2, setReply2] = useState(null);
  const [applied, setApplied] = useState(false);
  const ask = thread.kind === 'ask';
  const usr = thread.kind === 'usr';

  if (ask) {
    return (
      <div className="thread ask" data-status={status}>
        <div className="anchorline">▸ {thread.anchor}</div>
        <div className="ch">
          <span className="pill solid">understanding check</span>
          <span className="who"><span className="avatar sm ai">{thread.who.av}</span><span className="nm">{thread.who.nm}</span><span className="hb">{thread.who.hb}</span></span>
        </div>
        <div className="tbody"><Html html={thread.body} /></div>
        {role === 'author' ? (
          <div className="acts">
            <button className="btn btn-sec btn-sm" onClick={() => setReply(true)}>Answer in thread</button>
            <button className="btn btn-ghost btn-sm" onClick={() => onAct('dismiss', thread)}>Skip</button>
          </div>
        ) : (
          <div className="acts"><span className="mono muted" style={{ fontSize: 11 }}>Awaiting author answer…</span></div>
        )}
      </div>
    );
  }

  const sevColor = thread.sevColor;
  return (
    <div className="thread" data-status={status} data-fid={thread.id} style={{ borderLeftColor: usr ? 'var(--sev-maint)' : sevColor }}>
      <div className="anchorline">▸ {thread.anchor}</div>
      <div className="ch">
        {!usr && (
          <span className="sev-pill" style={{ color: sevColor, background: `color-mix(in oklch, ${sevColor} 12%, transparent)` }}>
            <span className="sev-dot" style={{ background: sevColor }} />{thread.sevLabel}
          </span>
        )}
        <span className="who"><span className="avatar sm">{thread.who.av}</span><span className="nm">{thread.who.nm}</span><span className="hb">{thread.who.hb}</span></span>
      </div>
      <div className="tbody"><Html html={thread.body} /></div>
      {thread.fix && (
        <div className="fix">
          <div className="fixh">
            <svg viewBox="0 0 24 24" strokeWidth={2} fill="none"><path d="M12 2v6m0 0 3-3m-3 3L9 5" /><rect x="4" y="10" width="16" height="11" rx="1" /></svg>
            Suggested fix
          </div>
          <pre>{thread.fix.map((l, i) => (<div key={i} className={l[0]}>{esc(l[1])}</div>))}</pre>
        </div>
      )}
      {reply2 && (
        <div className="tbody" style={{ borderTop: '1px solid var(--border)', marginTop: 8, paddingTop: 9 }}>
          <span className="who" style={{ display: 'inline-flex', gap: 6, alignItems: 'center', marginRight: 6 }}>
            <span className="avatar sm">{role === 'reviewer' ? 'MR' : 'JD'}</span><b style={{ fontSize: 12 }}>{role === 'reviewer' ? 'Meera R.' : 'Jaya D.'}</b>
          </span>{reply2}
        </div>
      )}
      {reply && (
        <div className="linecomposer">
          <textarea rows={2} placeholder="Reply… (syncs to GitLab on publish)" value={replyVal} onChange={(e) => setReplyVal(e.target.value)} autoFocus />
          <div className="lc-acts">
            <button className="btn btn-pri btn-xs" onClick={() => { setReply2(replyVal.trim() || '(reply)'); setReply(false); setReplyVal(''); onAct('reply-post', thread); }}>Reply</button>
            <button className="btn btn-ghost btn-xs" onClick={() => { setReply(false); setReplyVal(''); }}>Cancel</button>
          </div>
        </div>
      )}
      <div className="acts">
        {thread.fix && role === 'author' && (
          <button className={`btn btn-sm ${applied ? 'btn-ok' : 'btn-ok'}`} disabled={applied} onClick={() => { setApplied(true); onAct('apply', thread); }}>
            {applied ? 'Applied ✓' : (<><svg viewBox="0 0 24 24" strokeWidth={2.2} fill="none" style={{ width: 15, height: 15 }}><path d="M5 13l4 4L19 7" /></svg>Apply &amp; commit</>)}
          </button>
        )}
        {role === 'author' && <button className="btn btn-pri btn-sm" onClick={() => onAct('keep', thread)}>Keep</button>}
        {role === 'reviewer' && <button className="btn btn-sec btn-sm" onClick={() => onAct('resolve', thread)}>Resolve</button>}
        <button className="btn btn-ghost btn-sm" onClick={() => setReply(true)}>Reply</button>
        <button className="btn btn-ghost btn-sm" onClick={() => onAct('dismiss', thread)}>{usr ? 'Delete' : 'Dismiss'}</button>
        <span className="eff acc" style={{ marginLeft: 'auto', fontFamily: 'var(--font-mono)', fontSize: 10, color: 'var(--ok)', display: 'inline-flex', alignItems: 'center', gap: 5 }}>
          <span style={{ width: 5, height: 5, borderRadius: '50%', background: 'currentColor' }} />synced
        </span>
      </div>
    </div>
  );
}

// ── file accordion card ─────────────────────────────────────────────────────
function FileCard({ file, domId, mode, viewed, collapsed, split, onToggleViewed, onToggleCollapse, onAddComment, role, onAct, lineComments, refFn }) {
  const isCol = collapsed.has(file.f);
  const isViewed = viewed.has(file.f);
  const repeat = mode === 'story' && STORY_REPEATS[file.f];
  const extra = lineComments.filter((c) => c.file === file.f);
  return (
    <article className={`cfile${isCol ? ' col' : ''}${isViewed ? ' viewed' : ''}`} id={domId} data-file={file.f} ref={refFn}>
      <header className="cfile-h" onClick={() => onToggleCollapse(file.f)}>
        <svg className="cfile-tw" viewBox="0 0 24 24" strokeWidth={2.4} fill="none"><path d="M6 9l6 6 6-6" /></svg>
        <span className="vchk" onClick={(e) => { e.stopPropagation(); onToggleViewed(file.f); }}><Check /></span>
        <span className="cfile-nm">{file.path}</span>
        <span className="mrchip">{file.mr}</span>
        <span className="cfile-st"><span className="ad">+{file.add}</span> <span className="dl">−{file.del}</span></span>
        {file.cnt > 0 ? (
          <span className="cfile-cnt" style={{ color: file.sevc, background: `color-mix(in oklch, ${file.sevc} 12%, transparent)` }}>
            <span className="badge" style={{ background: file.sevc }} />{file.cnt}
          </span>
        ) : (<span className="clean">clean</span>)}
        <span className="spacer" />
        <span className="tag">{file.del === 0 ? 'added' : 'modified'}</span>
      </header>
      <div className="cfile-body">
        {repeat ? (
          <span className="repeat-note">
            <svg viewBox="0 0 24 24" width="11" height="11" strokeWidth={2} fill="none"><path d="M4 4v6h6M20 20v-6h-6M20 8a8 8 0 0 0-14-3M4 16a8 8 0 0 0 14 3" /></svg>
            also appears in {repeat - 1} other chapter{repeat - 1 > 1 ? 's' : ''}
          </span>
        ) : null}
        {file.blocks.map((b, i) => (
          b.h
            ? <DiffBlock key={i} block={b} fname={file.f} split={split && mode !== 'persona'} onAddComment={onAddComment} />
            : <Thread key={i} thread={b.thread} role={role} onAct={onAct} />
        ))}
        {extra.map((c) => (
          <Thread key={c.id} thread={{ id: c.id, kind: 'usr', status: 'open', who: { av: c.av, nm: c.nm, hb: '· just now' }, anchor: `${c.file} : ${c.line}`, body: c.val }} role={role} onAct={onAct} />
        ))}
      </div>
    </article>
  );
}

export default function ReviewPage() {
  const [role, setRole] = useState('author');
  const [state, setState] = useState('ANALYSED');
  const [mode, setMode] = useState('classic'); // classic | story | persona
  const [sevFilter, setSevFilter] = useState(-1); // -1 = all
  const [split, setSplit] = useState(false);
  const [drawerTab, setDrawerTab] = useState('verdict');
  const [viewed, setViewed] = useState(() => new Set());
  const [collapsed, setCollapsed] = useState(() => new Set());
  const [treeCollapsed, setTreeCollapsed] = useState(() => new Set());
  const [pending, setPending] = useState(() => new Set());
  const [lcOpen, setLcOpen] = useState(null); // { file, line }
  const [lcVal, setLcVal] = useState('');
  const [lineComments, setLineComments] = useState([]);
  const [activeFile, setActiveFile] = useState(null);
  const [toast, setToast] = useState(null);
  const [lcol, setLcol] = useState(false);
  const [rcol, setRcol] = useState(true); // insights drawer collapsed by default

  const centerRef = useRef(null);
  const cardRefs = useRef({});

  const showToast = useCallback((msg) => {
    setToast(msg);
    window.clearTimeout(showToast._t);
    showToast._t = window.setTimeout(() => setToast(null), 2200);
  }, []);

  const groups = useMemo(() => visGroups(mode, sevFilter), [mode, sevFilter]);
  const stateIdx = STATES.indexOf(state);

  const allFindings = useMemo(() => {
    const arr = [];
    FILES.forEach((f) => f.blocks.forEach((b) => { if (b.thread) arr.push(b.thread.id); }));
    return arr;
  }, []);
  const [findingStatus, setFindingStatus] = useState({});
  const openCount = useMemo(() => {
    let n = 0;
    FILES.forEach((f) => f.blocks.forEach((b) => {
      if (b.thread) { const s = findingStatus[b.thread.id] || 'open'; if (s !== 'dismissed' && s !== 'resolved') n++; }
    }));
    return n;
  }, [findingStatus]);

  const toggleViewed = (f) => setViewed((prev) => { const n = new Set(prev); n.has(f) ? n.delete(f) : n.add(f); return n; });
  const toggleCollapse = (f) => setCollapsed((prev) => { const n = new Set(prev); n.has(f) ? n.delete(f) : n.add(f); return n; });
  const toggleTree = (k) => setTreeCollapsed((prev) => { const n = new Set(prev); n.has(k) ? n.delete(k) : n.add(k); return n; });
  const markPending = (id) => setPending((prev) => new Set(prev).add(id));

  const domIdFor = (g, file) => (mode === 'story' ? `story-${g.ci}-${slug(file.f)}` : `file-${slug(file.f)}`);

  const jumpTo = (targetId, f) => {
    setCollapsed((prev) => { const n = new Set(prev); n.delete(f); return n; });
    setActiveFile(f);
    window.requestAnimationFrame(() => {
      const el = cardRefs.current[targetId] || document.getElementById(targetId);
      if (el && centerRef.current) centerRef.current.scrollTo({ top: el.offsetTop - 8, behavior: 'smooth' });
    });
  };

  const openLineComment = (file, line) => { setLcOpen({ file, line }); setLcVal(''); };
  const addLineComment = () => {
    const who = role === 'reviewer' ? { av: 'MR', nm: 'Meera R.' } : { av: 'JD', nm: 'Jaya D.' };
    const id = `lc-${Date.now()}`;
    setLineComments((prev) => [...prev, { id, file: lcOpen.file, line: lcOpen.line, val: lcVal.trim() || '(comment)', ...who }]);
    markPending(id);
    setLcOpen(null); setLcVal('');
    showToast('Comment added · will post on publish');
  };

  const onAct = (kind, thread) => {
    if (kind === 'dismiss') { setFindingStatus((s) => ({ ...s, [thread.id]: 'dismissed' })); markPending(`dismiss-${thread.id}`); showToast('Dismissed · removed from GitLab thread'); }
    if (kind === 'resolve') { setFindingStatus((s) => ({ ...s, [thread.id]: 'resolved' })); markPending(`resolve-${thread.id}`); showToast('Resolved · will sync on publish'); }
    if (kind === 'keep') { setFindingStatus((s) => ({ ...s, [thread.id]: 'kept' })); markPending(`keep-${thread.id}`); showToast('Kept · will post on publish'); }
    if (kind === 'apply') { markPending(`apply-${thread.id}`); showToast('Suggestion applied · commit staged'); }
    if (kind === 'reply-post') { markPending(`reply-${thread.id}`); showToast('Reply added · will post on publish'); }
  };

  const changeMode = (m) => {
    setMode(m);
    if (m === 'persona') setSplit(false);
    if (centerRef.current) centerRef.current.scrollTo({ top: 0 });
    showToast(`Lens · ${m.charAt(0).toUpperCase() + m.slice(1)}`);
  };

  const openDrawerTab = (t) => { setDrawerTab(t); setRcol(false); };

  const rerun = () => { showToast('Pushed 2 fixes · re-running analysis…'); setState('ANALYSING'); window.setTimeout(() => setState('ANALYSED'), 2400); };

  const withStatus = (thread) => ({ ...thread, status: findingStatus[thread.id] || thread.status });

  const navTitle = mode === 'classic' ? 'Changed files · by MR' : mode === 'story' ? 'Review story · chapters' : 'Findings · by persona';
  const noun = mode === 'classic' ? 'MRs' : mode === 'story' ? 'chapters' : 'personas';
  const visFiles = new Set(); groups.forEach((g) => g.vfiles.forEach((f) => visFiles.add(f.f)));

  return (
    <div className="rvx" data-role={role} data-state={state} data-mode={mode} data-sev={sevFilter < 0 ? 'all' : String(sevFilter)}>
      {/* header strip */}
      <div className="rvx-head">
        <div className="rvx-head-t">
          <p className="eyebrow">MR group · cross-repo · {MRS.length} MRs</p>
          <div className="ttl serif">Auth V2 · neural-cache rollout</div>
          <div className="meta">{MRS.length} MRs · {FILES.length} files · +{fmt(TOT_ADD)} −{fmt(TOT_DEL)} · feat/token-refresh, feat/neural-cache → main</div>
        </div>
        <span className="spacer" style={{ flex: '1 1 auto' }} />
        <span className="statepill" title="Analysis state">{state}</span>
        <div className="role-toggle" title="Whose view you are in">
          <button className={role === 'author' ? 'on' : ''} onClick={() => setRole('author')}><span className="avatar">JD</span>Author</button>
          <button className={role === 'reviewer' ? 'on' : ''} onClick={() => setRole('reviewer')}><span className="avatar">MR</span>Reviewer</button>
        </div>
        {role === 'author' && <button className="btn btn-sec btn-sm" onClick={rerun}>Re-run analysis</button>}
        {role === 'reviewer' && <button className="btn btn-sec btn-sm" onClick={() => showToast('Requested changes · synced to both MRs')}>Request changes</button>}
        {role === 'reviewer' && <button className="btn btn-sec btn-sm" onClick={() => showToast('Approved · GitLab updated')}>Approve</button>}
        <button className="btn btn-pri btn-sm" disabled={pending.size === 0} title={pending.size === 0 ? 'Enabled once you have unpublished changes' : `${pending.size} unpublished changes`}
          onClick={() => { setPending(new Set()); showToast('Published to GitLab'); }}>
          <svg viewBox="0 0 24 24" strokeWidth={2} fill="none" style={{ width: 15, height: 15 }}><path d="M12 19V5M5 12l7-7 7 7" /></svg>
          Publish{pending.size ? ` selected · ${pending.size}` : ''}
        </button>
      </div>

      <div className={`rvx-body${lcol ? ' lcol' : ''}${rcol ? ' rcol' : ''}`}>
        {/* LEFT: navigator */}
        <aside className="rvx-side">
          <div className="rvx-blk">
            <h4>Review progress</h4>
            <div className="prog">
              {FILES.map((f, i) => (<i key={f.f} className={i < viewed.size ? 'done' : ''} />))}
            </div>
            <div className="mt-[7px] font-mono text-[11px] text-muted">
              <b>{viewed.size}</b> of {FILES.length} files viewed · <b>{openCount}</b> findings open
            </div>
          </div>
          <div className="rvx-blk fblk" style={{ flex: 1 }}>
            <div className="fbar">
              <h4 style={{ margin: 0 }}>{navTitle}</h4>
              <span className="fsum font-mono">{FILES.length} files</span>
            </div>
            <div className="ftree">
              {groups.map((g, gi) => {
                const isCol = treeCollapsed.has(g.key);
                return (
                  <React.Fragment key={g.key}>
                    <div className={`grp${isCol ? ' collapsed' : ''}${mode === 'classic' ? ' mrgrp' : ''}`} onClick={() => toggleTree(g.key)}>
                      <svg className="tw" viewBox="0 0 24 24" strokeWidth={2.4} fill="none"><path d="M6 9l6 6 6-6" /></svg>
                      {mode === 'story' && <span className="gnum">{gi + 1}</span>}
                      <span className="gnm">{g.label}</span>
                      {mode === 'classic' && <span className="grepo">{g.repo}</span>}
                      <span className="gct"><span className="gst"><span className="ad">+{g.add}</span> <span className="dl">−{g.del}</span></span> · {g.vfiles.length}f</span>
                    </div>
                    {!isCol && (
                      <div className="gwrap">
                        {g.vfiles.map((file) => {
                          const target = domIdFor(g, file);
                          return (
                            <div key={file.f + target} className={`fitem${viewed.has(file.f) ? ' viewed' : ''}${activeFile === file.f ? ' active' : ''}`} onClick={() => jumpTo(target, file.f)}>
                              <span className="vchk" onClick={(e) => { e.stopPropagation(); toggleViewed(file.f); }}><Check /></span>
                              <span className="nm">{file.f}{mode !== 'classic' && <span className="mrchip">{file.mr}</span>}</span>
                              <span className="fmeta">
                                <span className="fst"><span className="ad">+{file.add}</span> <span className="dl">−{file.del}</span></span>
                                {file.cnt ? (
                                  <span className="fbadge"><span className="badge" style={{ background: file.sevc }} /><span className="cnt">{file.cnt}</span></span>
                                ) : (<span className="clean">clean</span>)}
                              </span>
                            </div>
                          );
                        })}
                      </div>
                    )}
                  </React.Fragment>
                );
              })}
            </div>
          </div>
        </aside>

        {/* CENTER */}
        <main className="rvx-center" ref={centerRef}>
          <div className="chead">
            <div className="modetabs" title="Same files, three lenses — pick how the review reads">
              <button className={mode === 'classic' ? 'on' : ''} onClick={() => changeMode('classic')}>
                <svg viewBox="0 0 24 24" strokeWidth={1.8} fill="none"><path d="M4 5h16M4 12h16M4 19h10" /></svg>Classic
              </button>
              <button className={mode === 'story' ? 'on' : ''} onClick={() => changeMode('story')}>
                <svg viewBox="0 0 24 24" strokeWidth={1.8} fill="none"><path d="M4 5h9M4 10h13M4 15h7M4 20h11" /><circle cx="19" cy="7" r="1.6" /></svg>Story
              </button>
              <button className={mode === 'persona' ? 'on' : ''} onClick={() => changeMode('persona')}>
                <svg viewBox="0 0 24 24" strokeWidth={1.8} fill="none"><circle cx="9" cy="8" r="3" /><path d="M3.5 20c0-3 2.5-5 5.5-5s5.5 2 5.5 5" /><circle cx="17.5" cy="9" r="2.2" /></svg>Persona
              </button>
            </div>
            <span className="chead-div" />
            <div className="sevfilter" title="Filter — hides files below the chosen severity in every mode">
              <span className="sflab">Severity</span>
              {[['all', 'All', -1], ['3', 'Blockers', 3], ['2', 'Major+', 2], ['1', 'Findings', 1]].map(([k, lbl, v]) => (
                <button key={k} data-sev={k} className={sevFilter === v ? 'on' : ''} onClick={() => setSevFilter(v)}>{lbl}</button>
              ))}
            </div>
            <span className="chead-sub">{groups.length} {noun} · {visFiles.size} of {FILES.length} files{sevFilter >= 0 ? ' · filtered' : ''}</span>
            <span className="spacer" style={{ flex: 1 }} />
            <button className={`panetog${lcol ? ' off' : ''}`} title="Toggle navigator" onClick={() => setLcol((v) => !v)}>
              <svg viewBox="0 0 24 24" strokeWidth={1.8} fill="none"><rect x="3" y="4" width="18" height="16" /><path d="M9 4v16" /></svg>Files
            </button>
            {mode !== 'persona' && (
              <div className="seg sm">
                <button className={!split ? 'on' : ''} onClick={() => setSplit(false)}>Unified</button>
                <button className={split ? 'on' : ''} onClick={() => setSplit(true)}>Split</button>
              </div>
            )}
            <button className={`panetog${rcol ? ' off' : ''}`} title="Toggle insights drawer" onClick={() => setRcol((v) => !v)}>
              <svg viewBox="0 0 24 24" strokeWidth={1.8} fill="none"><rect x="3" y="4" width="18" height="16" /><path d="M15 4v16" /></svg>Insights
            </button>
          </div>

          <div className="center-body">
            {stateIdx < STATES.indexOf('ANALYSED') && (
              <div className="gate">
                <svg viewBox="0 0 24 24" strokeWidth={2} fill="none"><circle cx="12" cy="12" r="9" /><path d="M12 8v5M12 16h.01" /></svg>
                <div><b>Analysis not ready.</b> <span className="muted">This page is most useful from ANALYSED onward. Invoke analysis to populate findings.</span></div>
              </div>
            )}

            {groups.length === 0 ? (
              <div className="emptyfilter">
                <div><b>No files at this severity.</b><br /><span className="muted">The severity filter is hiding everything in this lens.</span></div>
                <button className="btn btn-ghost btn-sm" onClick={() => setSevFilter(-1)}>Clear filter</button>
              </div>
            ) : (
              <>
                {mode === 'persona' && (
                  <div className="pbanner">
                    <h3>Findings by persona</h3>
                    <p style={{ fontSize: '12.5px', color: 'var(--muted)', maxWidth: '70ch' }}>
                      Same files, grouped by which reviewer persona raised the finding. Every file keeps its <span className="mono" style={{ fontSize: 11 }}>MR tag</span> so you never lose track of which merge request it belongs to.
                    </p>
                    <div className="pcards">
                      {(() => {
                        const agg = {};
                        FILES.forEach((f) => f.blocks.forEach((b) => { if (b.thread && b.thread.who) {
                          const nm = b.thread.who.nm; const a = agg[nm] || (agg[nm] = { nm, av: b.thread.who.av, count: 0, files: new Set(), maxSev: 0 });
                          a.count++; a.files.add(f.f); a.maxSev = Math.max(a.maxSev, f.sev);
                        } }));
                        return Object.values(agg).sort((a, b) => b.maxSev - a.maxSev).map((a) => (
                          <div key={a.nm} className="pcard">
                            <div className="ph2"><span className="avatar sm">{a.av}</span><span className="pnm">{a.nm}</span></div>
                            <div className="pd2">{guidance(a.maxSev)}</div>
                            <div className="pct"><span>{a.count} findings</span><span>{a.files.size} files</span></div>
                          </div>
                        ));
                      })()}
                    </div>
                  </div>
                )}
                {mode === 'story' && (
                  <div className="aistory">
                    <h3>Review story · linear walkthrough</h3>
                    <p>
                      Arnold reordered the {FILES.length} files across {MRS.length} merge requests into a {STORY.length}-chapter narrative, riskiest first.
                      A file can appear in more than one chapter when it matters to more than one idea (e.g. <span className="mono" style={{ fontSize: 11, color: 'var(--accent-ink)' }}>provider.go</span> and <span className="mono" style={{ fontSize: 11, color: 'var(--accent-ink)' }}>cache_warmer.go</span>).
                      The left rail jumps between chapters and the files inside them — MR tags travel with each file.
                    </p>
                  </div>
                )}

                {groups.map((g, gi) => (
                  <section key={g.key} className={`csec${mode === 'story' ? ' storysec' : mode === 'classic' ? ' mrsec' : ''}`} id={`sec-${slug(g.key)}`}>
                    {mode === 'story' ? (
                      <header className="story-h">
                        <span className="csec-num">{gi + 1}</span>
                        <span className="story-ht"><span className="story-nm">{g.label}</span><p className="story-narr"><Html html={g.narr} /></p></span>
                        <span className="csec-st"><span className="ad">+{g.add}</span> <span className="dl">−{g.del}</span> · {g.vfiles.length} files · {g.why}</span>
                      </header>
                    ) : mode === 'classic' ? (
                      <header className="mr-h">
                        <span className="mr-id">{g.mr.id}</span><span className="mr-repo">{g.repo}</span><span className="mr-title">{g.sub}</span>
                        <span className="csec-st" style={{ marginLeft: 'auto' }}><span className="mono" style={{ fontSize: '10.5px' }}>{g.mr.branch}</span> · <span className="ad">+{g.add}</span> <span className="dl">−{g.del}</span> · {g.vfiles.length} files</span>
                      </header>
                    ) : (
                      <header className="csec-h">
                        <span className="csec-nm">{g.label}</span><span className="csec-why">{guidance(g.sev)}</span>
                        <span className="csec-st"><span className="ad">+{g.add}</span> <span className="dl">−{g.del}</span> · {g.vfiles.length} files</span>
                      </header>
                    )}
                    {g.vfiles.map((file) => {
                      const domId = domIdFor(g, file);
                      // inject live status into threads
                      const patched = { ...file, blocks: file.blocks.map((b) => (b.thread ? { ...b, thread: withStatus(b.thread) } : b)) };
                      return (
                        <FileCard
                          key={domId}
                          file={patched}
                          domId={domId}
                          mode={mode}
                          viewed={viewed}
                          collapsed={collapsed}
                          split={split}
                          role={role}
                          onAct={onAct}
                          onToggleViewed={toggleViewed}
                          onToggleCollapse={toggleCollapse}
                          onAddComment={openLineComment}
                          lineComments={lineComments}
                          refFn={(el) => { if (el) cardRefs.current[domId] = el; }}
                        />
                      );
                    })}
                  </section>
                ))}
              </>
            )}
          </div>
        </main>

        {/* RIGHT: insights drawer */}
        <aside className="rvx-rail">
          <div className="drawer-tabs">
            {[['verdict', 'Verdict'], ['build', 'Build'], ['details', 'Details'], ['context', 'Context']].map(([k, lbl]) => (
              <button key={k} className={drawerTab === k ? 'on' : ''} onClick={() => openDrawerTab(k)}>{lbl}</button>
            ))}
          </div>
          <div className="drawer-body">
            {/* VERDICT */}
            <div className="dpanel" hidden={drawerTab !== 'verdict'}>
              <div className="rc">
                <div className="rch"><h4>Change summary · group</h4></div>
                <div className="rcb">
                  <div className="sumgrid">
                    <div><div className="big">{FILES.length}</div><div className="lab2">files</div></div>
                    <div><div className="big" style={{ color: 'var(--ok)' }}>+{fmt(TOT_ADD)}</div><div className="lab2">added</div></div>
                    <div><div className="big" style={{ color: 'var(--bad)' }}>−{fmt(TOT_DEL)}</div><div className="lab2">removed</div></div>
                  </div>
                  <div className="mrsplit">
                    {MRS.map((m) => (
                      <div key={m.id} className="mrline">
                        <span className="mrtag">{m.id}</span> {m.repo}
                        <span className="mrf"><span className="ad">+{fmt(m.add)}</span> <span className="dl">−{fmt(m.del)}</span> · {m.files.length}f{m.blockers ? <> · <b style={{ color: 'var(--bad)' }}>{m.blockers} blocker{m.blockers > 1 ? 's' : ''}</b></> : null}</span>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
              <div className="rc">
                <div className="rch"><h4>Overall rating &amp; health</h4></div>
                <div className="rcb">
                  <div className="score-row">
                    <div className="score"><div className="lab2">Group rating</div><div className="big bad">B−</div><div className="sub">2 critical · 6 open</div></div>
                    <div className="score"><div className="lab2">Security health</div><div className="big" style={{ color: 'var(--bad)' }}>Risk</div><div className="meter" style={{ marginTop: 2 }}><i style={{ width: '88%', background: 'var(--bad)' }} /></div><div className="sub" style={{ marginTop: 6 }}>88 / 100 risk</div></div>
                  </div>
                  <p className="muted" style={{ fontSize: '12.5px', marginTop: 11 }}>Critical race condition + a logged secret in the token-refresh path (<b>!487</b>). Overall group risk <b style={{ color: 'var(--accent-ink)' }}>HIGH</b>.</p>
                </div>
              </div>
            </div>

            {/* BUILD */}
            <div className="dpanel" hidden={drawerTab !== 'build'}>
              <div className="rc">
                <div className="rch"><h4>Pipelines</h4></div>
                <div className="rcb">
                  <div className="pipe-mr"><span className="mrtag">!482</span><span className="pmr-repo">core-infrastructure</span><span className="pstat fail ra"><span className="pd" />failed</span></div>
                  <div className="pipe">
                    <span className="pstage ok"><span className="pi"><Check /></span>build</span><span className="parrow">›</span>
                    <span className="pstage ok"><span className="pi"><Check /></span>lint</span><span className="parrow">›</span>
                    <span className="pstage fail"><span className="pi"><svg viewBox="0 0 24 24" strokeWidth={3} fill="none"><path d="M6 6l12 12M18 6 6 18" /></svg></span>test</span><span className="parrow">›</span>
                    <span className="pstage skip"><span className="pi" />deploy</span>
                  </div>
                  <div className="failbox">
                    <div className="fl"><svg viewBox="0 0 24 24" width="13" height="13" strokeWidth={2} fill="none"><path d="M12 3l9 16H3Z" /><path d="M12 10v4" /></svg>test · TestConcurrentRefresh — data race detected</div>
                    <p className="muted" style={{ fontSize: '11.5px', marginBottom: 9 }}>go test -race flagged provider.go:108. Same root cause as the critical finding.</p>
                    <KadhaiButton onDone={() => showToast('Kadhai pushed fix to !483 · pipeline re-running')} onStart={() => showToast('Delegated to Kadhai · automated fix branch opening…')} />
                  </div>
                  <div className="pipe-mr" style={{ marginTop: 14 }}><span className="mrtag">!487</span><span className="pmr-repo">auth-service</span><span className="pstat ok ra"><span className="pd" />passed</span></div>
                  <div className="pipe">
                    <span className="pstage ok"><span className="pi"><Check /></span>build</span><span className="parrow">›</span>
                    <span className="pstage ok"><span className="pi"><Check /></span>lint</span><span className="parrow">›</span>
                    <span className="pstage ok"><span className="pi"><Check /></span>test</span><span className="parrow">›</span>
                    <span className="pstage skip"><span className="pi" />deploy</span>
                  </div>
                  <div className="row" style={{ marginTop: 11, gap: 7 }}>
                    <button className="btn btn-ghost btn-xs" onClick={() => showToast('Retrying pipeline…')}>Retry !482</button>
                    <button className="btn btn-ghost btn-xs">View logs ↗</button>
                  </div>
                </div>
              </div>
              <div className="rc">
                <div className="rch"><h4>Commits · 7</h4></div>
                <div className="rcb">
                  {[
                    { mr: '!487', msg: 'fix: redact token before debug logging', meta: '@jdoe · 2h', sha: 'a3f91c2', dot: 'var(--bad)', stat: 'fail', lbl: 'failed' },
                    { mr: '!487', msg: 'refactor: release mutex before network call', meta: '@jdoe · 4h', sha: '7d20e8b', dot: 'var(--ok)', stat: 'ok', lbl: 'passed' },
                    { mr: '!482', msg: 'feat: warm cache on provider boot', meta: '@jdoe · 6h', sha: 'c1b8f44', dot: 'var(--ok)', stat: 'ok', lbl: 'passed' },
                    { mr: '!482', msg: 'feat: add neural cache layer scaffolding', meta: '@jdoe · 1d', sha: '5e9a017', dot: 'var(--ok)', stat: 'ok', lbl: 'passed' },
                    { mr: '!482', msg: 'chore: schema migration for cache index', meta: '@jdoe · 1d', sha: 'b44c1de', dot: 'var(--sev-maint)', stat: 'run', lbl: 'running' },
                    { mr: '!487', msg: 'wip: token store refactor', meta: '@jdoe · 2d', sha: '90fb2aa', dot: 'var(--border-2)', stat: 'ok', lbl: 'passed' },
                    { mr: '!482', msg: 'test: cover concurrent refresh path', meta: '@jdoe · 2d', sha: '12ee6c0', dot: 'var(--border-2)', stat: 'ok', lbl: 'passed' },
                  ].map((c) => (
                    <div key={c.sha} className="commit">
                      <span className="cdot" style={{ borderColor: c.dot }} />
                      <div><div className="cmsg">{c.msg}</div><div className="cmeta"><span className="mrtag xs">{c.mr}</span> {c.meta} · <span className="csha">{c.sha}</span></div></div>
                      <span className={`pstat ${c.stat}`}><span className="pd" />{c.lbl}</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>

            {/* DETAILS */}
            <div className="dpanel" hidden={drawerTab !== 'details'}>
              <div className="rc">
                <div className="rch"><h4>Analysis state</h4></div>
                <div className="rcb">
                  <div className="state-rail">
                    {STATES.map((s, i) => (
                      <React.Fragment key={s}>
                        {i > 0 && <span className="sep">›</span>}
                        <span className={`st ${s === state ? 'now' : i < stateIdx ? 'done' : ''}`} onClick={() => setState(s)}>{s}</span>
                      </React.Fragment>
                    ))}
                  </div>
                </div>
              </div>
              {role === 'author' ? (
                <div className="rc">
                  <div className="rch"><h4>Author next steps</h4></div>
                  <div className="rcb">
                    <button className="btn btn-pri btn-sm" style={{ width: '100%', justifyContent: 'center', marginBottom: 8 }} onClick={rerun}>
                      <svg viewBox="0 0 24 24" strokeWidth={2} fill="none" style={{ width: 15, height: 15 }}><path d="M21 12a9 9 0 1 1-3-6.7M21 4v4h-4" /></svg>Push fixes &amp; re-run analysis
                    </button>
                    <button className="btn btn-sec btn-sm" style={{ width: '100%', justifyContent: 'center' }} onClick={() => setState('REVIEW_READY')}>Mark REVIEW_READY</button>
                  </div>
                </div>
              ) : (
                <div className="rc">
                  <div className="rch"><h4>Reviewer actions</h4></div>
                  <div className="rcb">
                    <button className="btn btn-pri btn-sm" style={{ width: '100%', justifyContent: 'center', marginBottom: 8 }} onClick={() => showToast('Approved · GitLab updated')}>Approve group</button>
                    <button className="btn btn-sec btn-sm" style={{ width: '100%', justifyContent: 'center' }} onClick={() => showToast('Requested changes · synced')}>Request changes</button>
                  </div>
                </div>
              )}
              <div className="rc">
                <div className="rch"><h4>Merge requests</h4><button className="btn btn-ghost btn-xs ra" onClick={() => showToast('Edit synced to GitLab')}>Edit</button></div>
                <div className="rcb">
                  <div className="mr-meta"><span className="mrtag">!487</span> auth-service <a className="mr-link" onClick={() => showToast('Opening !487 on GitLab…')}>↗</a></div>
                  <div className="mrow"><span className="mk">Branch</span><div className="mv mono">feat/token-refresh → main</div></div>
                  <div className="mrow"><span className="mk">Assignee</span><div className="mv"><span className="avatar sm">JD</span>Jaya D.</div></div>
                  <div className="mrow"><span className="mk">Labels</span><div className="mv"><span className="label-chip"><span className="ld" style={{ background: 'var(--sev-security)' }} />security</span><span className="label-chip"><span className="ld" style={{ background: 'var(--sev-maint)' }} />auth-v2</span></div></div>
                  <div className="mr-meta" style={{ marginTop: 13 }}><span className="mrtag">!482</span> core-infrastructure <a className="mr-link" onClick={() => showToast('Opening !482 on GitLab…')}>↗</a></div>
                  <div className="mrow"><span className="mk">Branch</span><div className="mv mono">feat/neural-cache → main</div></div>
                  <div className="mrow"><span className="mk">Assignee</span><div className="mv"><span className="avatar sm">JD</span>Jaya D.</div></div>
                  <div className="mrow"><span className="mk">Labels</span><div className="mv"><span className="label-chip"><span className="ld" style={{ background: 'var(--sev-perf)' }} />performance</span><span className="label-chip"><span className="ld" style={{ background: 'var(--sev-bug)' }} />migration</span></div></div>
                  <div className="mrow"><span className="mk">Reviewers</span><div className="mv"><span className="ava-stack"><span className="avatar sm">MR</span><span className="avatar sm">PK</span></span> Meera R., Priya K.</div></div>
                  <div className="mrow"><span className="mk">Milestone</span><div className="mv"><span className="pill" style={{ fontSize: '10.5px' }}>Q3 · Auth hardening</span></div></div>
                  <div className="mrow"><span className="mk">Sync</span><div className="mv"><span className="sync live"><span className="dot2" /><span style={{ fontSize: 11, color: 'var(--sev-maint)' }}>GitLab live</span></span></div></div>
                </div>
              </div>
            </div>

            {/* CONTEXT */}
            <div className="dpanel" hidden={drawerTab !== 'context'}>
              <div className="rc">
                <div className="rch"><h4>Personas in this analysis</h4></div>
                <div className="rcb">
                  {[
                    { av: 'SA', nm: 'Sai', meta: 'Claude · reasoning', tag: '3 findings' },
                    { av: 'NE', nm: 'Netra', meta: 'compliance', tag: '2 findings' },
                    { av: 'AA', nm: 'Aarav', meta: 'performance', tag: '2 findings' },
                    { av: 'CP', nm: 'Copilot', meta: 'GPT-5 · default', tag: '3 nits' },
                  ].map((p) => (
                    <div key={p.nm} className="prow"><span className="avatar sm">{p.av}</span><div><div style={{ fontWeight: 600 }}>{p.nm}</div><div className="pmeta">{p.meta}</div></div><span className="tag" style={{ marginLeft: 'auto' }}>{p.tag}</span></div>
                  ))}
                  <button className="btn btn-sec btn-sm" style={{ width: '100%', justifyContent: 'center', marginTop: 10 }}
                    onClick={() => { showToast('Persona added · re-running analysis…'); setState('ANALYSING'); window.setTimeout(() => setState('ANALYSED'), 2400); }}>
                    + Add persona &amp; re-run
                  </button>
                </div>
              </div>
              <div className="rc">
                <div className="rch"><h4>Linked context</h4></div>
                <div className="rcb">
                  <div className="lcx"><div className="lt"><span className="sev-dot" style={{ background: 'var(--sev-maint)' }} />JIRA-1442<span className="pill" style={{ marginLeft: 'auto', fontSize: 10 }}>in progress</span></div><div className="ld">Secondary auth factor for enterprise.</div></div>
                  <div className="lcx"><div className="lt"><span className="sev-dot" style={{ background: 'var(--sev-enh)' }} />RFC-089 · Auth V2</div><div className="ld">Functional agent verified against this.</div></div>
                  <div className="lcx"><div className="lt"><span className="sev-dot" style={{ background: 'var(--bad)' }} />!318 · prior deadlock</div><div className="ld">Same lock pattern broke Mar-24.</div></div>
                </div>
              </div>
            </div>
          </div>
        </aside>
      </div>

      {/* line-comment composer (modal-ish, anchored) */}
      {lcOpen && (
        <div className="rvx-scrim scrim" onClick={() => setLcOpen(null)}>
          <div className="linecomposer" style={{ position: 'fixed', top: '50%', left: '50%', transform: 'translate(-50%,-50%)', width: 'min(460px, 92vw)', margin: 0, zIndex: 90 }} onClick={(e) => e.stopPropagation()}>
            <div className="lc-anchor">▸ {lcOpen.file} : {lcOpen.line} · new comment</div>
            <textarea rows={3} placeholder={`Comment on line ${lcOpen.line}… (syncs to GitLab on publish)`} value={lcVal} onChange={(e) => setLcVal(e.target.value)} autoFocus />
            <div className="lc-acts">
              <button className="btn btn-pri btn-xs" onClick={addLineComment}>Add comment</button>
              <button className="btn btn-ghost btn-xs" onClick={() => setLcOpen(null)}>Cancel</button>
              <span className="mono faint" style={{ fontSize: '10.5px', marginLeft: 'auto' }}>unpublished</span>
            </div>
          </div>
        </div>
      )}

      {toast && (
        <div className="toast show"><span className="sd" />{toast}</div>
      )}
    </div>
  );
}

function KadhaiButton({ onStart, onDone }) {
  const [phase, setPhase] = useState('idle'); // idle | working | done
  const run = () => {
    setPhase('working');
    onStart && onStart();
    window.setTimeout(() => { setPhase('done'); onDone && onDone(); }, 2600);
  };
  if (phase === 'done') {
    return <button className="kadhai-btn" style={{ background: 'var(--ok)' }} disabled>Kadhai opened MR !483 ↗</button>;
  }
  if (phase === 'working') {
    return <button className="kadhai-btn" disabled><svg viewBox="0 0 24 24" strokeWidth={1.8} fill="none"><path d="M21 12a9 9 0 1 1-3-6.7" /></svg> Kadhai working…</button>;
  }
  return (
    <button className="kadhai-btn" onClick={run}>
      <svg viewBox="0 0 24 24" strokeWidth={1.8} fill="none"><path d="M12 2a4 4 0 0 1 4 4v1a4 4 0 0 1-8 0V6a4 4 0 0 1 4-4Z" /><path d="M5 21v-2a4 4 0 0 1 4-4h6a4 4 0 0 1 4 4v2" /></svg>
      Delegate fix to Kadhai
    </button>
  );
}
