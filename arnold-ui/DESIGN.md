# Design System Specification: The Intelligent Review

## 1. Overview & Creative North Star
The Creative North Star for this design system is **"The Sentient Code Reviewer."**

Moving beyond the utilitarian "box-and-border" aesthetic of legacy version control tools, this system treats code review as a high-stakes editorial process. It is designed to feel like a premium, data-dense instrument—think of a glass-cockpit display for a high-end spacecraft. By utilizing intentional asymmetry, deep tonal layering, and "WestWorld"-inspired topographical motifs, we transform a standard developer tool into a sophisticated AI partnership. We prioritize information density without the visual "noise" of traditional borders.

## 2. Colors & Surface Philosophy
The palette balances a foundation of technical grays and deep blues with a "System Intelligence" accent (`primary: #ffb4a6`). This organic, terracotta-leaning tone represents the human-like intuition of the AI.

### The "No-Line" Rule
To achieve a "Pro" editorial feel, **1px solid borders for sectioning are strictly prohibited.** Layout boundaries must be defined through background color shifts or subtle tonal transitions. Use `surface-container-low` against `surface` to define a sidebar, or `surface-container-highest` to call out a specific code block.

### Surface Hierarchy & Nesting
Treat the UI as a series of physical layers. Each level of nesting should step one tier up or down the container scale to create natural depth:
- **Base Layer:** `surface` (#121316)
- **Primary Sidebar/Navigation:** `surface-container-low` (#1b1b1f)
- **Main Content Cards:** `surface-container` (#1f1f23)
- **Active Code Diff/Intelligence Panels:** `surface-container-high` (#292a2d)

### The Glass & Gradient Rule
For floating elements, such as "Intelligence" badges or AI-generated modals, use **Glassmorphism**. Apply a semi-transparent `surface-variant` with a 12px backdrop-blur.
- **Signature CTA Texture:** Use a subtle linear gradient from `primary` (#ffb4a6) to `primary-container` (#d63b22) at a 135-degree angle to give the AI interaction points a "living" energy.

## 3. Typography
The system utilizes a dual-font strategy: **Manrope** for high-level editorial headers and **Inter** for dense, functional data.

| Level | Token | Font | Weight | Character |
| :--- | :--- | :--- | :--- | :--- |
| **Display** | `display-lg` | Manrope | 700 | Authoritative, sparse tracking (-2%) |
| **Headline** | `headline-sm`| Manrope | 600 | Used for major file headers |
| **Title** | `title-md` | Inter | 500 | Repository names, PR titles |
| **Body** | `body-md` | Inter | 400 | Comments, documentation |
| **Label** | `label-sm` | Inter | 600 | Metadata, "Intelligence" badges (All-caps) |

**Code Blocks:** Must use a high-contrast Monospace (JetBrains Mono or similar) at `0.875rem` with a line-height of `1.6` for maximum readability during long-form review sessions.

## 4. Elevation & Depth
Depth is a functional tool, not a stylistic choice. We move away from structural lines toward **Tonal Layering**.

- **The Layering Principle:** To "lift" a component, simply move it one step up the surface scale. A floating "Agent Status" indicator should sit on `surface-container-highest` to naturally separate it from the code below.
- **Ambient Shadows:** For floating menus or modals, use an extra-diffused shadow: `box-shadow: 0 24px 48px rgba(0, 0, 0, 0.4)`. The shadow should feel like a soft glow rather than a harsh cutout.
- **The "Ghost Border" Fallback:** If accessibility requires a stroke (e.g., in Light Mode), use the `outline-variant` token at **15% opacity**. This provides a "suggestion" of a boundary without cluttering the density.

## 5. Components

### High-Density Tables (The Diff Viewer)
* **Structure:** No vertical or horizontal lines. Use a `surface-container-low` background on hover for rows.
* **Diff Colors:** Use `error_container` (93000a) for removals and a custom subtle green for additions, ensuring the text remains `on-surface`.

### Intelligence Badges
* **Visuals:** Utilize a `tertiary_container` (#9d6a2c) background with a glassmorphism blur.
* **Iconography:** Accompanied by a "Maze" inspired mark—a minimalist geometric spiral—representing the AI's deep-traversal analysis.

### Agent Status Indicators
* **States:**
* *Analyzing:* Pulsing `tertiary` (#faba75) glow.
* *Ready:* Static `primary` (#ffb4a6) dot.
* *Issue Found:* Sharp `error` (#ffb4ab) accent.

### Inputs & Fields
* **Styling:** Fields are flush with the background, using only a `surface-container-highest` fill and a `2px` bottom-only highlight of `primary` when focused.

### Checkboxes & Radios
* **Design:** Avoid standard rounded boxes. Use sharp, `0.125rem` (sm) corners for a more "pro" and technical appearance.

## 6. Do’s and Don'ts

### Do
* **DO** use whitespace (Spacing Scale 16 or 20) to separate major code modules.
* **DO** overlay a subtle "Neural Line" (topographical) svg pattern at 3% opacity on the `surface` layer to add texture to empty states.
* **DO** use `secondary_container` for secondary actions to keep them visually distinct but subordinate to AI-driven tasks.

### Don’t
* **DON'T** use 100% opaque borders. They create "visual noise" that fatigues the developer's eyes during long reviews.
* **DON'T** use standard blue for links. Use `primary_fixed_dim` (#ffb4a6) to maintain the "Intelligence" brand identity.
* **DON'T** use heavy drop shadows on cards; rely on the `surface-container` shifts for hierarchy.

## 7. The "Maze" Logo Integration
The logo should never be a standalone "sticker." It should be integrated into the UI as a subtle watermark in the background of the `surface-container-lowest` or as a loading shimmer effect where the "Maze" lines animate as the AI processes code. This reinforces the idea that the "Intelligence" is baked into the very fabric of the tool.