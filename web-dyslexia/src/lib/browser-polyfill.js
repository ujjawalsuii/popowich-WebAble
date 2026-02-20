/**
 * Minimal Chrome/Firefox API shim.
 * Chrome MV3: chrome.* APIs return Promises natively.
 * Firefox MV3: browser.* APIs return Promises natively.
 * This shim aliases chrome -> browser so all code can use browser.* uniformly.
 */
if (typeof globalThis.browser === 'undefined') {
  // eslint-disable-next-line no-undef
  globalThis.browser = chrome;
}
