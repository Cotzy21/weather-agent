// Service worker: appen åpner uten nett (viktig på gymmet med dårlig dekning). API-kall røres ikke;
// dem håndterer appen selv (lagret kopi av treningsdata + offline-kø for fullførte økter).
const CACHE = 'app-shell-v1'

self.addEventListener('install', () => self.skipWaiting())

self.addEventListener('activate', (event) => {
  event.waitUntil((async () => {
    for (const key of await caches.keys()) {
      if (key !== CACHE) await caches.delete(key)
    }
    await self.clients.claim()
  })())
})

self.addEventListener('fetch', (event) => {
  const req = event.request
  const url = new URL(req.url)
  if (req.method !== 'GET' || url.origin !== self.location.origin || url.pathname.startsWith('/api/')) return

  // Sider: nettet først (alltid ferskeste app), siste kopi når nettet er nede.
  if (req.mode === 'navigate') {
    event.respondWith((async () => {
      const cache = await caches.open(CACHE)
      try {
        const fresh = await fetch(req)
        if (fresh.ok) cache.put('/', fresh.clone())
        return fresh
      } catch {
        return (await cache.match('/')) || Response.error()
      }
    })())
    return
  }

  // Statiske filer (hash i filnavnet for JS/CSS): cache først, oppdater i bakgrunnen.
  event.respondWith((async () => {
    const cache = await caches.open(CACHE)
    const cached = await cache.match(req)
    const network = fetch(req).then((res) => {
      if (res.ok) cache.put(req, res.clone())
      return res
    }).catch(() => undefined)
    return cached || (await network) || Response.error()
  })())
})
