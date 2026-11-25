// Veil Built-in WebExtension — cosmetic.js (AB-4)
// Injected at document_start to eliminate layout shift and empty ad placeholders.
(() => {
  const COSMETIC_SELECTORS = [
    ".ad-slot-banner",
    ".sponsored-rail",
    "[data-ad-unit]",
    "[id^='google_ads_iframe']",
    ".cookie-annoyance-overlay",
    ".taboola-widget",
    ".outbrain-recommended",
  ];

  const style = document.createElement("style");
  style.id = "veil-cosmetic-filters";
  style.textContent = `
    ${COSMETIC_SELECTORS.join(",\n    ")} {
      display: none !important;
      visibility: hidden !important;
      height: 0 !important;
      margin: 0 !important;
      padding: 0 !important;
    }
  `;

  (document.head || document.documentElement).appendChild(style);
})();
