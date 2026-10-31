# Design Spec: Arnold · Lattice (kami / 紙)

> Creative intent. For the implementation contract (tokens, classes, components), see [
`DESIGN_SYSTEM.md`](DESIGN_SYSTEM.md).

## 1. Creative North Star

**"Code review as a precise, document-grade craft."**

Arnold is an AI reviewer that sits inside a serious engineering workflow. Lattice rejects the noisy, neon "AI product"
look. Instead it borrows from fine printed matter and drafting paper — *kami* (紙, "paper"): a warm, calm canvas under a
faint engraved grid, set in type with the care of an editorial layout, marked with a single decisive vermilion signal.

The result should feel **deliberate, legible, and quietly confident** — an instrument an engineer trusts during long
review sessions, not a dashboard that shouts.

## 2. The Three Materials

Everything is built from three materials:

1. **Paper** — the warm canvas (`#f4f2ef` light / `#1b1b1e` dark) carrying a 30px engraved lattice grid at ~4% ink.
   Cards are sheets of slightly brighter paper laid on top.
2. **Ink** — charcoal text in three weights (primary / muted / faint). Hairlines (`border`) are the thinnest possible
   ruled lines, used generously to give structure.
3. **The vermilion signal** (`#E24329`) — one accent, used sparingly, that always means *"attention here"*: the seal,
   primary actions, active navigation, links, the highest-severity findings.

If a fourth color shows up, it is a **severity** (the calibrated warm taxonomy), never decoration.

## 3. Surfaces & Structure

Unlike the previous dark, borderless system, Lattice **embraces the hairline**. Structure is drawn, not implied:

- Three surface levels — `bg` (canvas) → `surface` (cards/sidebar) → `surface-2` (inputs/code) — each separated by a 1px
  hairline.
- The page canvas stays transparent so the lattice grid reads behind every panel.
- No card shadows. Depth = surface step + hairline. Soft shadows belong only to things that genuinely float (menus,
  drawers, toasts).
- **Sharp corners everywhere.** Radius is `0`. The only curves are circular avatars/dots/meters.

## 4. Type

A three-voice system:

- **Space Grotesk** — display. Page titles, section headers, and the big stat figures. Tightly tracked, confident.
- **System sans** — body. Fast, native, neutral; keeps long-form reading crisp.
- **IBM Plex Mono** — the "machine voice." Every number, ID, status, timestamp, label, eyebrow, and code line is mono.
  This is what makes Lattice feel like a precision instrument.

Eyebrows are the signature: **mono, uppercase, tracked, vermilion**. They head sections like a printed kicker.

## 5. The Seal & Motion

The brand is the **seal** — a geometric "A" on a vermilion square engraved with an 8px grid. It is the same lattice
motif, concentrated.

Motion is rare and meaningful:

- The **splash** tears two vermilion paper halves apart to reveal the app, the "A" animating in the seam (once per
  session).
- The **"Run Analysis"** button carries an animation the "A" mark as its loader.

Both are vector, theme-aware, and respect reduced-motion. Never animate for decoration.

## 6. Components

- **Diff viewer** — hairline-framed, mono, with green/red/accent line washes. Quiet by default, loud only on flagged
  lines.
- **Badges** — mono, uppercase, sharp, soft-tinted from the severity scale.
- **Status atoms** — mono dot + label. *Analysing* pulses amber; *review ready* is vermilion; *merged* is green; *issue*
  is the signal.
- **Inputs** — hairline on bright paper, focus = accent border + soft ring. No floating underlines.

## 7. Do / Don't (intent)

**Do** keep it calm: lean on whitespace, hairlines, and the grid. Let the single accent do the pointing. Treat numbers
and labels as machine type (mono).

**Don't** reach for gradients, glow, rounded "app" cards, drop shadows, or a second accent. Don't use blue for links.
Don't let the page get louder than the content it frames.

> Two themes, one feeling. Light (paper) is the default; dark (charcoal) is its night counterpart. Both must feel like
> the same calm, precise instrument.
