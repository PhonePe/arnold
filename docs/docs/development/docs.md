# Writing docs

This site is built with [Zensical](https://zensical.org) from `docs/`:

```text
docs/
├── zensical.toml          # site config and navigation
├── requirements.txt       # pinned zensical version
├── serve.sh               # local preview
└── docs/                  # pages
    ├── assets/            # logo, fonts, screenshots
    └── stylesheets/arnold.css
```

## Preview

```bash
./docs/serve.sh            # http://127.0.0.1:8000, reloads on change
```

or build once:

```bash
cd docs && pip install -r requirements.txt && python -m zensical build --clean   # output in docs/site/
```

## Publishing

`.github/workflows/docs.yml` builds the site and deploys it to GitHub Pages on every push to `main` that touches
`docs/`, the embedded config files, or the workflow. It can also be run manually (**Actions → Documentation → Run
workflow**). The repository's **Settings → Pages → Source** must be set to **GitHub Actions**.

## Conventions

- Add new pages to `nav` in `zensical.toml`.
- Embed real files rather than copying them, so examples never drift:
  `;--8<-- "arnold-runner/ci/github/arnold-runner.yml"` (paths are relative to the repository root).
- Screenshots go in `docs/assets/screenshots/`, use the dark theme, and must not show private repositories, names or
  tokens.
- Write for the reader: say what a setting does and when to change it. Keep secrets out of examples.
- The look follows the Arnold UI design system (*Lattice*): vermilion accent, Space Grotesk headings, IBM Plex Mono
  labels, sharp corners. Styles live in `stylesheets/arnold.css`.
