// Offline cache for the web version (GitHub Pages). The Android app does not use this.
const CACHE = 'qm-v1';
const CORE = ['./', 'index.html', 'manifest.webmanifest', 'icons/icon-192.png', 'icons/icon-512.png',
  'lib/html2canvas.min.js', 'lib/jspdf.umd.min.js', 'lib/fonts.css'];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => Promise.all(CORE.map(u => c.add(u).catch(() => null)))));
  self.skipWaiting();
});

self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k)))));
  self.clients.claim();
});

// Network first for the page (so updates arrive), cache first for everything else.
self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const isPage = req.mode === 'navigate';
  e.respondWith(
    isPage
      ? fetch(req).then(r => { const copy = r.clone(); caches.open(CACHE).then(c => c.put(req, copy)); return r; })
          .catch(() => caches.match(req).then(r => r || caches.match('index.html')))
      : caches.match(req).then(hit => hit || fetch(req).then(r => {
          if (r.ok && (req.url.startsWith(self.location.origin) || req.url.includes('fonts.g'))) {
            const copy = r.clone(); caches.open(CACHE).then(c => c.put(req, copy));
          }
          return r;
        }))
  );
});
