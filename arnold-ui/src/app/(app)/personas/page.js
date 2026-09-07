'use client';

import React, { useEffect, useMemo, useState } from 'react';
import { Edit3, Plus, Search, Trash2, X } from 'lucide-react';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { EmptyState } from '@/components/ui/EmptyState';
import { ErrorState } from '@/components/ui/ErrorState';
import { Input } from '@/components/ui/Input';
import { Label } from '@/components/ui/Label';
import { LoadingState } from '@/components/ui/LoadingState';
import { Overline } from '@/components/ui/Overline';
import { Tabs } from '@/components/ui/Tabs';
import { useUser } from '@/components/layout/AppLayout';
import { createPersona, deletePersona, fetchPersonas, fetchUsers, publishPersona, unpublishPersona, updatePersona } from '@/lib/api';

const tabs = [{ key: 'discover', label: 'Discover' }, { key: 'mine', label: 'Managed by me' }];
const emptyPolicy = () => ({ version: 1, guidance: '', rules: [], prerequisites: [], ignore: [] });
const emptyForm = (userId = null) => ({
  name: '', summary: '', policy: emptyPolicy(), tags: '', contributorIds: userId ? [userId] : [],
});

function PersonaCard({ persona, managed, onEdit, onPublish }) {
  return (
    <article className="flex flex-col border border-border bg-surface p-5">
      <div className="flex items-start gap-3">
        <span className="grid h-11 w-11 shrink-0 place-items-center bg-fg font-display text-lg font-bold text-surface">{persona.name.slice(0, 1).toUpperCase()}</span>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2"><h2 className="font-display text-lg font-bold text-fg">{persona.name}</h2><Badge variant={persona.status === 'PUBLISHED' ? 'success' : 'warning'}>{persona.status.toLowerCase()}</Badge></div>
          <p className="mt-1 font-mono text-[10px] text-faint">{persona.systemOwned ? 'Arnold approved' : `${persona.contributorIds.length} contributor${persona.contributorIds.length === 1 ? '' : 's'}`}</p>
        </div>
      </div>
      <p className="mt-4 text-sm leading-6 text-muted">{persona.summary}</p>
      {persona.policy.guidance ? <p className="mt-3 border-l-2 border-border pl-3 text-xs leading-5 text-muted">{persona.policy.guidance}</p> : null}
      <div className="mt-4 flex flex-wrap gap-1.5">
        {persona.policy.rules.length ? <Badge>{persona.policy.rules.length} rules</Badge> : null}
        {persona.policy.prerequisites.length ? <Badge>{persona.policy.prerequisites.length} prerequisites</Badge> : null}
        {persona.policy.ignore.length ? <Badge>{persona.policy.ignore.length} exclusions</Badge> : null}
        {persona.tags.map((tag) => <Badge key={tag}>{tag}</Badge>)}
      </div>
      <details className="mt-3 border border-border bg-surface-2">
        <summary className="cursor-pointer px-3 py-2 font-mono text-[10px] uppercase tracking-[0.06em] text-muted">Generated YAML</summary>
        <pre className="max-h-52 overflow-auto whitespace-pre-wrap border-t border-border p-3 font-mono text-[11px] leading-5 text-muted">{persona.instructions}</pre>
      </details>
      <div className="mt-auto flex items-center justify-end gap-2 pt-5">
        {managed && !persona.systemOwned ? <Button variant="ghost" size="sm" onClick={() => onEdit(persona)}><Edit3 className="h-3.5 w-3.5" />Edit</Button> : null}
        {managed && !persona.systemOwned ? <Button variant="secondary" size="sm" onClick={() => onPublish(persona)}>{persona.status === 'PUBLISHED' ? 'Unpublish' : 'Publish'}</Button> : null}
      </div>
    </article>
  );
}

export default function PersonasPage() {
  const user = useUser();
  const [personas, setPersonas] = useState(null);
  const [users, setUsers] = useState([]);
  const [error, setError] = useState(null);
  const [tab, setTab] = useState('discover');
  const [query, setQuery] = useState('');
  const [editing, setEditing] = useState(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [mutation, setMutation] = useState(null);

  async function reload() {
    try {
      const [nextPersonas, nextUsers] = await Promise.all([fetchPersonas(), fetchUsers()]);
      setPersonas(nextPersonas); setUsers(nextUsers); setError(null);
    } catch (nextError) { setError(nextError); }
  }

  useEffect(() => { reload(); }, []);

  const visible = useMemo(() => (personas || []).filter((persona) => {
    const managed = persona.contributorIds.includes(user?.id);
    const matchesTab = tab === 'discover' ? persona.status === 'PUBLISHED' : managed;
    const normalized = query.trim().toLowerCase();
    return matchesTab && (!normalized || `${persona.name} ${persona.summary} ${persona.tags.join(' ')}`.toLowerCase().includes(normalized));
  }), [personas, query, tab, user?.id]);

  function openEditor(persona = null) {
    setEditing(persona);
    setEditorOpen(true);
    setForm(persona ? {
      name: persona.name,
      summary: persona.summary,
      policy: {
        ...persona.policy,
        rules: persona.policy.rules.map((rule) => ({ ...rule })),
        prerequisites: persona.policy.prerequisites.map((prerequisite) => ({ ...prerequisite })),
        ignore: [...persona.policy.ignore],
      },
      tags: persona.tags.join(', '),
      contributorIds: [...persona.contributorIds],
    } : emptyForm(user?.id));
    setError(null);
  }

  function closeEditor() {
    setEditing(null); setEditorOpen(false); setForm(emptyForm());
  }

  async function save(event) {
    event.preventDefault();
    setMutation('save'); setError(null);
    const payload = {
      name: form.name.trim(),
      summary: form.summary.trim(),
      policy: {
        version: 1,
        guidance: form.policy.guidance.trim(),
        rules: form.policy.rules.map((rule) => ({ id: rule.id.trim(), requirement: rule.requirement.trim() })),
        prerequisites: form.policy.prerequisites.map((prerequisite) => ({
          id: prerequisite.id.trim(), appliesWhen: prerequisite.appliesWhen.trim(), requirement: prerequisite.requirement.trim(),
        })),
        ignore: form.policy.ignore.map((value) => value.trim()),
      },
      tags: form.tags.split(',').map((tag) => tag.trim()).filter(Boolean).slice(0, 5),
      contributorIds: form.contributorIds,
    };
    try {
      if (editing) await updatePersona(editing.id, { ...payload, version: editing.version });
      else await createPersona(payload);
      closeEditor(); setTab('mine'); await reload();
    } catch (nextError) { setError(nextError); } finally { setMutation(null); }
  }

  async function changePublication(persona) {
    setMutation(persona.id); setError(null);
    try {
      if (persona.status === 'PUBLISHED') await unpublishPersona(persona.id, persona.version);
      else await publishPersona(persona.id, persona.version);
      await reload();
    } catch (nextError) { setError(nextError); } finally { setMutation(null); }
  }

  async function remove() {
    if (!editing || editing.status !== 'DRAFT') return;
    setMutation('delete'); setError(null);
    try { await deletePersona(editing.id, editing.version); closeEditor(); await reload(); }
    catch (nextError) { setError(nextError); } finally { setMutation(null); }
  }

  function setGuidance(guidance) {
    setForm((current) => ({ ...current, policy: { ...current.policy, guidance } }));
  }

  function addPolicyEntry(section, value) {
    setForm((current) => ({
      ...current,
      policy: { ...current.policy, [section]: [...current.policy[section], value] },
    }));
  }

  function updatePolicyEntry(section, index, field, value) {
    setForm((current) => ({
      ...current,
      policy: {
        ...current.policy,
        [section]: current.policy[section].map((entry, entryIndex) => entryIndex === index
          ? (field ? { ...entry, [field]: value } : value) : entry),
      },
    }));
  }

  function removePolicyEntry(section, index) {
    setForm((current) => ({
      ...current,
      policy: { ...current.policy, [section]: current.policy[section].filter((_, entryIndex) => entryIndex !== index) },
    }));
  }

  const policyHasContent = form.policy.guidance.trim() || form.policy.rules.length
    || form.policy.prerequisites.length || form.policy.ignore.length;
  const contributorOptions = [...users];
  form.contributorIds.filter((id) => !users.some((candidate) => candidate.id === id))
    .forEach((id) => contributorOptions.push({ id, displayName: id, email: '' }));

  return (
    <div className="mx-auto max-w-[1480px]">
      <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <div><Overline>Review marketplace</Overline><h1 className="mt-1 font-display text-[clamp(30px,4vw,42px)] font-bold leading-tight text-fg">Review personas</h1><p className="mt-2 max-w-[72ch] text-[15px] text-muted">Create versioned review policies and publish them to the organization. Choose personas when you configure an analysis.</p></div>
        <Button onClick={() => openEditor()}><Plus className="h-4 w-4" />Create persona</Button>
      </div>

      {editorOpen ? (
        <form onSubmit={save} className="mb-6 border border-border-2 bg-surface">
          <div className="flex items-center justify-between border-b border-border bg-surface-2 px-5 py-3"><div><h2 className="font-display text-lg font-bold text-fg">{editing ? `Edit ${editing.name}` : 'Create persona'}</h2><p className="text-xs text-muted">Define guidance, rules, prerequisites, and exclusions as a version 1 review policy.</p></div><Button type="button" variant="ghost" size="xs" onClick={closeEditor} aria-label="Close editor"><X className="h-4 w-4" /></Button></div>
          <div className="grid gap-4 p-5 lg:grid-cols-2">
            <div><Label htmlFor="persona-name">Name</Label><Input id="persona-name" className="mt-2" maxLength={80} value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} required /></div>
            <div><Label htmlFor="persona-tags">Focus tags</Label><Input id="persona-tags" className="mt-2" value={form.tags} onChange={(event) => setForm({ ...form, tags: event.target.value })} placeholder="security, payments, reliability" /></div>
            <div className="lg:col-span-2"><Label htmlFor="persona-summary">Summary</Label><Input id="persona-summary" className="mt-2" maxLength={240} value={form.summary} onChange={(event) => setForm({ ...form, summary: event.target.value })} required /></div>
            <div className="lg:col-span-2"><Label htmlFor="persona-contributors">Contributors</Label><select id="persona-contributors" className="mt-2 min-h-32 w-full border border-border-2 bg-surface-2 p-2 text-sm text-fg focus:border-accent focus:outline-none" multiple value={form.contributorIds} onChange={(event) => setForm({ ...form, contributorIds: Array.from(event.target.selectedOptions, (option) => option.value) })} required>{contributorOptions.map((candidate) => <option key={candidate.id} value={candidate.id}>{candidate.displayName || candidate.username || candidate.email} · {candidate.email || candidate.id}</option>)}</select><p className="mt-1 text-xs text-faint">Select everyone who can edit, publish, or delete this persona.</p></div>
            <div className="lg:col-span-2"><Label htmlFor="persona-guidance">Guidance</Label><textarea id="persona-guidance" className="mt-2 min-h-28 w-full resize-y border border-border-2 bg-surface-2 p-3 text-sm leading-6 text-fg focus:border-accent focus:outline-none" maxLength={2000} value={form.policy.guidance} onChange={(event) => setGuidance(event.target.value)} placeholder="Prioritize backward compatibility, failure visibility, and rollout safety." /></div>

            <section className="border border-border lg:col-span-2">
              <div className="flex items-center justify-between border-b border-border bg-surface-2 px-4 py-3"><div><h3 className="font-display font-bold text-fg">Rules</h3><p className="text-xs text-muted">Concrete requirements the reviewer should enforce.</p></div><Button type="button" variant="secondary" size="xs" disabled={form.policy.rules.length >= 20} onClick={() => addPolicyEntry('rules', { id: '', requirement: '' })}><Plus className="h-3.5 w-3.5" />Add rule</Button></div>
              {form.policy.rules.length === 0 ? <p className="p-4 text-sm text-faint">No rules added.</p> : form.policy.rules.map((rule, index) => (
                <div key={`rule-${index}`} className="grid gap-3 border-b border-border p-4 last:border-b-0 lg:grid-cols-[minmax(180px,0.7fr)_minmax(0,1.6fr)_auto]">
                  <div><Label htmlFor={`rule-id-${index}`}>Rule ID</Label><Input id={`rule-id-${index}`} className="mt-2 font-mono" maxLength={128} value={rule.id} onChange={(event) => updatePolicyEntry('rules', index, 'id', event.target.value)} placeholder="payments.money-minor-units" required /></div>
                  <div><Label htmlFor={`rule-requirement-${index}`}>Requirement</Label><Input id={`rule-requirement-${index}`} className="mt-2" maxLength={500} value={rule.requirement} onChange={(event) => updatePolicyEntry('rules', index, 'requirement', event.target.value)} placeholder="Store monetary amounts as integer minor units." required /></div>
                  <Button type="button" variant="ghost" size="xs" className="self-end" aria-label={`Remove rule ${index + 1}`} onClick={() => removePolicyEntry('rules', index)}><X className="h-4 w-4" /></Button>
                </div>
              ))}
            </section>

            <section className="border border-border lg:col-span-2">
              <div className="flex items-center justify-between border-b border-border bg-surface-2 px-4 py-3"><div><h3 className="font-display font-bold text-fg">Prerequisites</h3><p className="text-xs text-muted">External work required when a condition applies.</p></div><Button type="button" variant="secondary" size="xs" disabled={form.policy.prerequisites.length >= 20} onClick={() => addPolicyEntry('prerequisites', { id: '', appliesWhen: '', requirement: '' })}><Plus className="h-3.5 w-3.5" />Add prerequisite</Button></div>
              {form.policy.prerequisites.length === 0 ? <p className="p-4 text-sm text-faint">No prerequisites added.</p> : form.policy.prerequisites.map((prerequisite, index) => (
                <div key={`prerequisite-${index}`} className="grid gap-3 border-b border-border p-4 last:border-b-0 lg:grid-cols-[minmax(160px,0.6fr)_minmax(0,1fr)_minmax(0,1fr)_auto]">
                  <div><Label htmlFor={`prerequisite-id-${index}`}>Prerequisite ID</Label><Input id={`prerequisite-id-${index}`} className="mt-2 font-mono" maxLength={128} value={prerequisite.id} onChange={(event) => updatePolicyEntry('prerequisites', index, 'id', event.target.value)} placeholder="api.registration" required /></div>
                  <div><Label htmlFor={`prerequisite-condition-${index}`}>Applies when</Label><Input id={`prerequisite-condition-${index}`} className="mt-2" maxLength={500} value={prerequisite.appliesWhen} onChange={(event) => updatePolicyEntry('prerequisites', index, 'appliesWhen', event.target.value)} placeholder="An API endpoint changes." required /></div>
                  <div><Label htmlFor={`prerequisite-requirement-${index}`}>Requirement</Label><Input id={`prerequisite-requirement-${index}`} className="mt-2" maxLength={500} value={prerequisite.requirement} onChange={(event) => updatePolicyEntry('prerequisites', index, 'requirement', event.target.value)} placeholder="Update the API registration." required /></div>
                  <Button type="button" variant="ghost" size="xs" className="self-end" aria-label={`Remove prerequisite ${index + 1}`} onClick={() => removePolicyEntry('prerequisites', index)}><X className="h-4 w-4" /></Button>
                </div>
              ))}
            </section>

            <section className="border border-border lg:col-span-2">
              <div className="flex items-center justify-between border-b border-border bg-surface-2 px-4 py-3"><div><h3 className="font-display font-bold text-fg">Ignore</h3><p className="text-xs text-muted">Patterns or actions the reviewer should not report.</p></div><Button type="button" variant="secondary" size="xs" disabled={form.policy.ignore.length >= 20} onClick={() => addPolicyEntry('ignore', '')}><Plus className="h-3.5 w-3.5" />Add exclusion</Button></div>
              {form.policy.ignore.length === 0 ? <p className="p-4 text-sm text-faint">No exclusions added.</p> : form.policy.ignore.map((value, index) => (
                <div key={`ignore-${index}`} className="flex items-end gap-3 border-b border-border p-4 last:border-b-0"><div className="min-w-0 flex-1"><Label htmlFor={`ignore-${index}`}>Exclusion</Label><Input id={`ignore-${index}`} className="mt-2" maxLength={500} value={value} onChange={(event) => updatePolicyEntry('ignore', index, null, event.target.value)} placeholder="Do not report generated files." required /></div><Button type="button" variant="ghost" size="xs" aria-label={`Remove exclusion ${index + 1}`} onClick={() => removePolicyEntry('ignore', index)}><X className="h-4 w-4" /></Button></div>
              ))}
            </section>
          </div>
          <div className="flex flex-wrap justify-between gap-3 border-t border-border px-5 py-3">{editing?.status === 'DRAFT' ? <Button type="button" variant="ghost" onClick={remove} disabled={mutation !== null}><Trash2 className="h-4 w-4" />Delete draft</Button> : <span />}<Button disabled={mutation !== null || !form.name.trim() || !form.summary.trim() || !form.contributorIds.length || !policyHasContent}>{mutation === 'save' ? 'Saving…' : editing ? 'Save changes' : 'Save draft'}</Button></div>
        </form>
      ) : null}

      {error ? <ErrorState className="mb-5" title="Persona operation failed" error={error} onRetry={reload} /> : null}
      <div className="mb-5 flex flex-wrap items-center justify-between gap-3 border-y border-border py-4"><Tabs items={tabs} activeKey={tab} onTabChange={setTab} variant="segment" /><label className="relative w-full sm:w-80"><Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-faint" /><Input className="pl-9" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search personas or tags" aria-label="Search personas" /></label></div>
      {!personas && !error ? <LoadingState label="Loading persona marketplace" /> : null}
      {personas && visible.length === 0 ? <EmptyState title={tab === 'mine' ? 'No personas managed yet' : 'No personas match'} description={tab === 'mine' ? 'Create a persona or ask a contributor to add you.' : 'Try another search.'} /> : null}
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">{visible.map((persona) => <PersonaCard key={persona.id} persona={persona} managed={persona.contributorIds.includes(user?.id)} onEdit={openEditor} onPublish={changePublication} />)}</div>
    </div>
  );
}
