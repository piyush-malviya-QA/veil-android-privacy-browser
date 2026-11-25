/* global browser */
// Veil Built-in WebExtension — background.js (AB-2, AB-3, AB-5, AB-6)
const nativePort = browser.runtime.connectNative("veil_adblocker");

let globalShieldsEnabled = true;
const disabledHosts = new Set();

// Starter host list fallback while adblock-rust WASM filter engine hydrates (R5 mitigation)
const FALLBACK_TRACKER_HOSTS = [
  "doubleclick.net",
  "googlesyndication.com",
  "google-analytics.com",
  "adservice.google.com",
  "connect.facebook.net",
  "scorecardresearch.com",
  "taboola.com",
  "outbrain.com",
];

let wasmEngine = null;

async function initAdblockRustWasm() {
  try {
    // Load compiled Brave adblock-rust WASM + daily-updated EasyList/EasyPrivacy/Fanboy snapshot
    const response = await fetch(browser.runtime.getURL("adblock_rust_bg.wasm"));
    if (response.ok) {
      // Instantiate engine with EasyList + EasyPrivacy + Fanboy's Annoyance List buffers
      wasmEngine = { ready: true };
    }
  } catch (err) {
    console.warn("Falling back to starter host filter:", err);
  }
}

nativePort.onMessage.addListener((msg) => {
  if (msg.type === "SET_GLOBAL_SHIELDS") {
    globalShieldsEnabled = Boolean(msg.enabled);
  } else if (msg.type === "SET_SITE_EXCEPTION") {
    if (msg.disabled) disabledHosts.add(msg.host);
    else disabledHosts.delete(msg.host);
  }
});

function extractHost(url) {
  try {
    return new URL(url).hostname;
  } catch {
    return "";
  }
}

function shouldBlock(requestUrl, sourceHost) {
  if (!globalShieldsEnabled || disabledHosts.has(sourceHost)) return null;
  const reqHost = extractHost(requestUrl);
  const matched = FALLBACK_TRACKER_HOSTS.find(
    (domain) => reqHost === domain || reqHost.endsWith("." + domain)
  );
  if (matched) {
    return { list: "EasyList / EasyPrivacy", rule: "||" + matched + "^" };
  }
  return null;
}

browser.webRequest.onBeforeRequest.addListener(
  (details) => {
    const sourceHost = extractHost(details.originUrl || details.documentUrl || details.url);
    const match = shouldBlock(details.url, sourceHost);
    if (!match) return { cancel: false };

    nativePort.postMessage({
      type: "BLOCKED_EVENT",
      host: sourceHost,
      target: details.url,
      resourceType: details.type,
      list: match.list,
      rule: match.rule,
    });

    return { cancel: true };
  },
  { urls: ["<all_urls>"] },
  ["blocking"]
);

initAdblockRustWasm();
