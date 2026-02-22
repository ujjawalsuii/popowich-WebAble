# Popowich Accessibility (ScreenShield)

This repository contains **ScreenShield**, an accessibility-focused extension with tools for:

- ASL recognition (camera or screen source)
- Text-to-Speech chat reader
- Live subtitles
- Dyslexia-friendly reading mode
- Epilepsy-safe protections
- Color-vision modes (color blindness support)
- Voice personalization

## Website (GitHub Pages)

A GitHub Pages-ready landing page has been added in:

- `docs/index.html`
- `docs/styles.css`

### Local preview

Run from the repository root:

```bash
python -m http.server 5500 --directory docs
```

Then open:

`http://localhost:5500`

### Deploy with GitHub Pages

An automated workflow was added:

- `.github/workflows/deploy-pages.yml`

To publish:

1. Push to `main`
2. In GitHub repo settings, ensure **Pages** is configured to use **GitHub Actions**
3. The workflow deploys the `docs/` folder automatically

## Extension build commands

From `web-dyslexia/`:

```bash
npm install
npm run build:chrome
npm run build:firefox
```
