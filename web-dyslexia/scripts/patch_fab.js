const fs = require('fs');
let c = fs.readFileSync('src/content/contentScript.js', 'utf8');

const startMarker = '// \u2500\u2500 Quick Access FAB';
const endMarker = '// \u2500\u2500 Boot';

const si = c.indexOf(startMarker);
const ei = c.indexOf(endMarker);

if (si < 0 || ei < 0) {
    console.error('Markers not found', si, ei);
    process.exit(1);
}

const before = c.substring(0, si);
const after = c.substring(ei);

const fab = [
    '// \u2500\u2500 Quick Access FAB \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500',
    '',
    "const SS_FAB_HOST_ID = 'screenshield-fab-host';",
    'let fabShadow = null;',
    'let fabMenu = null;',
    '',
    'function injectQuickAccessFAB() {',
    '  if (document.getElementById(SS_FAB_HOST_ID)) return;',
    '',
    "  const host = document.createElement('div');",
    '  host.id = SS_FAB_HOST_ID;',
    "  host.style.cssText = 'position:fixed;top:8px;right:8px;z-index:2147483647;pointer-events:none;';",
    '',
    "  fabShadow = host.attachShadow({ mode: 'open' });",
    '',
    "  const fabBtn = document.createElement('button');",
    "  fabBtn.className = 'ss-pill';",
    "  fabBtn.setAttribute('aria-label', 'ScreenShield Quick Access');",
    '  fabBtn.innerHTML = \'<svg viewBox="0 0 32 32" width="14" height="14" fill="none"><path d="M16 2L4 7v9c0 6.6 5.1 12.7 12 14 6.9-1.3 12-7.4 12-14V7L16 2z" fill="currentColor" opacity="0.6"/><path d="M11 16l3.5 3.5L21 12" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/></svg>\';',
    '',
    "  fabMenu = document.createElement('div');",
    "  fabMenu.className = 'ss-strip hidden';",
    '  fabMenu.innerHTML =',
    "    '<label class=\"ss-chip\" title=\"ASL Recognition\"><input type=\"checkbox\" id=\"fab-asl\"' + (settings.aslMode ? ' checked' : '') + '/><span>ASL</span></label>' +",
    "    '<label class=\"ss-chip\" title=\"Dyslexia Friendly\"><input type=\"checkbox\" id=\"fab-dyslexia\"' + (settings.dyslexiaMode ? ' checked' : '') + '/><span>Dyslexia</span></label>' +",
    "    '<label class=\"ss-chip\" title=\"Speech to Text\"><input type=\"checkbox\" id=\"fab-tts\"' + (settings.ttsMode ? ' checked' : '') + '/><span>TTS</span></label>' +",
    "    '<label class=\"ss-chip\" title=\"Epilepsy Safe\"><input type=\"checkbox\" id=\"fab-seizure\"' + (settings.seizureSafeMode ? ' checked' : '') + '/><span>Epilepsy</span></label>' +",
    "    '<label class=\"ss-chip\" title=\"Live Captions\"><input type=\"checkbox\" id=\"fab-subtitles\"' + (settings.subtitleMode ? ' checked' : '') + '/><span>Captions</span></label>';",
    '',
    "  fabBtn.addEventListener('click', () => {",
    "    const isHidden = fabMenu.classList.toggle('hidden');",
    "    fabBtn.classList.toggle('open', !isHidden);",
    '  });',
    '',
    '  const toggleMap = {',
    "    'fab-asl': 'aslMode',",
    "    'fab-dyslexia': 'dyslexiaMode',",
    "    'fab-tts': 'ttsMode',",
    "    'fab-seizure': 'seizureSafeMode',",
    "    'fab-subtitles': 'subtitleMode'",
    '  };',
    '',
    '  Object.entries(toggleMap).forEach(([id, key]) => {',
    "    const el = fabMenu.querySelector('#' + id);",
    '    if (!el) return;',
    "    el.addEventListener('change', (e) => {",
    '      browser.storage.sync.set({ [key]: e.target.checked });',
    '    });',
    '  });',
    '',
    "  const wrapper = document.createElement('div');",
    "  wrapper.className = 'ss-fab-wrap';",
    '  wrapper.append(fabMenu, fabBtn);',
    '  fabShadow.appendChild(wrapper);',
    '',
    '  const sheet = new CSSStyleSheet();',
    '  sheet.replaceSync(',
    "    ':host{all:initial}' +",
    "    '.ss-fab-wrap{display:flex;align-items:center;gap:6px;pointer-events:auto;justify-content:flex-end}' +",
    "    '.ss-pill{all:unset;width:28px;height:28px;border-radius:14px;background:rgba(74,144,217,0.85);color:#fff;display:flex;align-items:center;justify-content:center;cursor:pointer;pointer-events:auto;box-shadow:0 2px 8px rgba(0,0,0,0.25);transition:background 0.2s,transform 0.15s,border-radius 0.2s;flex-shrink:0}' +",
    "    '.ss-pill:hover{background:rgba(58,123,194,0.95);transform:scale(1.08)}' +",
    "    '.ss-pill.open{background:rgba(74,144,217,1);border-radius:8px}' +",
    "    '.ss-strip{display:flex;align-items:center;gap:4px;background:rgba(20,20,35,0.92);border:1px solid rgba(61,61,92,0.6);border-radius:16px;padding:3px 8px;backdrop-filter:blur(10px);transition:opacity 0.15s,transform 0.15s;transform-origin:right center}' +",
    "    '.ss-strip.hidden{opacity:0;transform:scaleX(0.3);pointer-events:none;width:0;padding:0;border:none;overflow:hidden}' +",
    "    '.ss-chip{all:unset;display:flex;align-items:center;gap:3px;cursor:pointer;font-family:-apple-system,BlinkMacSystemFont,sans-serif;font-size:11px;color:#c8c8e0;padding:2px 4px;border-radius:6px;transition:background 0.15s;white-space:nowrap}' +",
    "    '.ss-chip:hover{background:rgba(74,144,217,0.15)}' +",
    "    '.ss-chip input{width:13px;height:13px;cursor:pointer;accent-color:#4a90d9;margin:0}'",
    '  );',
    '  fabShadow.adoptedStyleSheets = [sheet];',
    '',
    '  document.documentElement.appendChild(host);',
    '}',
    '',
    'function updateFABUI(changes) {',
    '  if (!fabShadow) return;',
    '  const toggleMap = {',
    "    aslMode: 'fab-asl',",
    "    dyslexiaMode: 'fab-dyslexia',",
    "    ttsMode: 'fab-tts',",
    "    seizureSafeMode: 'fab-seizure',",
    "    subtitleMode: 'fab-subtitles'",
    '  };',
    '  for (const [key, { newValue }] of Object.entries(changes)) {',
    '    if (toggleMap[key]) {',
    "      const el = fabShadow.querySelector('#' + toggleMap[key]);",
    '      if (el) el.checked = !!newValue;',
    '    }',
    '  }',
    '}',
    '',
].join('\n');

fs.writeFileSync('src/content/contentScript.js', before + fab + after);
console.log('Done! Patched FAB section.');
