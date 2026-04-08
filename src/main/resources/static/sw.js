const CACHE_NAME = "smarthotel-static-v12";

// Static assets safe to cache (images, manifest, icons only).
// admin.html and admin.js are intentionally EXCLUDED — always served from network.
const ASSETS = [
  "/manifest.webmanifest",
  "/icon.svg",
  "/icon.svg?v=3",
  "/logo-wordmark.svg",
  "/logo-wordmark.svg?v=2"
];

// NEVER cache these — they must always be fresh so charts/features update immediately.
const NEVER_CACHE = [
  "/admin.html", "/admin.js", "/styles.css",
  "/index.html", "/my-bookings.html", "/app.js"
];

// URLs that should ALWAYS be fetched from the network.
function isNetworkFirst(url) {
  try {
    const u = new URL(url);
    // HTML pages, JS files, CSS files, versioned assets, API calls
    if (u.pathname.endsWith(".html") || u.pathname === "/") return true;
    if (u.pathname.endsWith(".js")   || u.pathname.endsWith(".css")) return true;
    if (u.search.startsWith("?v="))  return true;
    if (u.pathname.startsWith("/api/")) return true;
    for (const nc of NEVER_CACHE) { if (u.pathname === nc) return true; }
    return false;
  } catch { return false; }
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
