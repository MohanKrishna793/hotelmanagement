const CACHE_NAME = "smarthotel-static-v11";

// Only cache truly static assets (images, manifest, icons).
// JS and CSS are loaded with version query params so cache-first is safe for them too.
const ASSETS = [
  "/manifest.webmanifest",
  "/icon.svg",
  "/icon.svg?v=3",
  "/logo-wordmark.svg",
  "/logo-wordmark.svg?v=2",
  "/styles.css?v=11",
  "/app.js",
  "/admin.js?v=11"
];

// URLs that should ALWAYS be fetched from the network (never served stale from cache).
function isNetworkFirst(url) {
  const u = new URL(url);
  // HTML pages and versioned JS/CSS go network-first
  return u.pathname.endsWith(".html") ||
         u.pathname === "/" ||
         u.search.startsWith("?v=");
}

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      // Add assets one-by-one; ignore failures so a missing asset doesn't break install
      return Promise.allSettled(
        ASSETS.map((url) => cache.add(url).catch((e) => console.warn("SW precache failed:", url, e)))
      );
    })
  );
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(
        keys
          .filter((key) => key !== CACHE_NAME)
          .map((key) => caches.delete(key))
      )
    )
  );
  self.clients.claim();
});

self.addEventListener("fetch", (event) => {
  if (event.request.method !== "GET") return;

  const url = event.request.url;

  // Network-first: HTML pages, versioned assets (?v=X), API calls
  if (event.request.mode === "navigate" || isNetworkFirst(url)) {
    event.respondWith(
      fetch(event.request)
        .then((response) => {
          if (response.ok) {
            const copy = response.clone();
            caches.open(CACHE_NAME).then((cache) => cache.put(event.request, copy));
          }
          return response;
        })
        .catch(() =>
          caches.match(event.request).then((cached) => cached || caches.match("/index.html"))
        )
    );
    return;
  }

  // Cache-first for everything else (icons, manifest, etc.)
  event.respondWith(
    caches.match(event.request).then((cached) => {
      if (cached) return cached;
      return fetch(event.request)
        .then((response) => {
          const copy = response.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put(event.request, copy));
          return response;
        })
        .catch(() => caches.match("/index.html"));
    })
  );
});
