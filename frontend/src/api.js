// Grunn-URL for API-et. Tom i web (samme opphav / Vite-proxy). I mobil-appen
// (Capacitor) settes VITE_API_BASE til den utplasserte backend-URL-en, siden
// appen ikke kan bruke proxy eller relative /api-kall.
const BASE = import.meta.env.VITE_API_BASE || ''

export function apiUrl(path) {
  return `${BASE}${path}`
}

// --- Enkel stale-while-revalidate-cache (kun i minnet) ---
// Poeng: når du bytter fane, avmonteres viewen og monteres på nytt -> uten cache
// hentes alt fra serveren igjen (tomt/lastende glimt på treg host). Med cachen
// viser vi FORRIGE data straks (getCached), og cachedGet oppdaterer i bakgrunnen.
// Nøkkelen er hele API-stien (inkl. query), så f.eks. hver dato caches for seg.
const cache = new Map()
const inFlight = new Map() // pågående hentinger, så samtidige kall deler forespørsel

// Treningsdata lagres også på enheten, så live-økten kan åpnes uten nett (dårlig dekning på gymmet).
// Kun stier som trengs for det, og tømmes sammen med cachen ved bytte av bruker.
const PERSIST = ['/api/treningsokter', '/api/ovelser/neste', '/api/trening/planer', '/api/trening/minne']
const PERSIST_PREFIX = 'api-cache:'
const PERSIST_MAX_CHARS = 1_000_000
const persisted = (key) => PERSIST.some((p) => key === p || key.startsWith(`${p}?`))

function readPersisted(key) {
  if (!persisted(key)) return undefined
  try {
    const raw = localStorage.getItem(PERSIST_PREFIX + key)
    return raw ? JSON.parse(raw) : undefined
  } catch { return undefined }
}

function writePersisted(key, data) {
  if (!persisted(key)) return
  try {
    const raw = JSON.stringify(data)
    if (raw.length <= PERSIST_MAX_CHARS) localStorage.setItem(PERSIST_PREFIX + key, raw)
  } catch { /* fullt lager / privat modus */ }
}

/** Sist kjente svar for en sti, eller undefined. Bruk til å hydrere state ved mount. */
export function getCached(key) {
  return cache.has(key) ? cache.get(key) : readPersisted(key)
}

// --- «Vekker serveren»: gratis hosting sover, og første kall kan ta ~1 minutt ---
const SLOW_MS = 4000
const slowListeners = new Set()
let slowCount = 0

/** Får beskjed (true/false) når et API-kall har tatt uvanlig lang tid. Returnerer avmelding. */
export function onSlowRequest(fn) {
  slowListeners.add(fn)
  return () => slowListeners.delete(fn)
}

function trackSlow(promise) {
  let counted = false
  const timer = setTimeout(() => {
    counted = true
    if (++slowCount === 1) slowListeners.forEach((fn) => fn(true))
  }, SLOW_MS)
  const done = () => {
    clearTimeout(timer)
    if (counted && --slowCount === 0) slowListeners.forEach((fn) => fn(false))
  }
  promise.then(done, done)
  return promise
}

/**
 * Hent + oppdater cachen. Kaster ved feil, så kalleren kan beholde stale data.
 * Deduplikerer samtidige kall for samme nøkkel (ved innlogging ber prefetch,
 * Hjem og Trening om øktene samtidig -> én forespørsel, ikke tre).
 */
export async function cachedGet(key, headers = {}) {
  if (inFlight.has(key)) return inFlight.get(key)
  const promise = (async () => {
    const res = await fetch(apiUrl(key), { headers })
    if (!res.ok) throw new Error(await readError(res))
    const data = await res.json()
    cache.set(key, data)
    writePersisted(key, data)
    return data
  })()
  trackSlow(promise)
  inFlight.set(key, promise)
  try {
    return await promise
  } finally {
    inFlight.delete(key)
  }
}

const OWNER_KEY = 'api-cache-owner'

/**
 * Kalles når innlogget bruker er kjent/endres. Minnecachen tømmes alltid, men den lagrede
 * kopien (for offline) beholdes når det er samme bruker som sist, og slettes ellers.
 */
export function switchCacheOwner(uid) {
  cache.clear()
  try {
    if (localStorage.getItem(OWNER_KEY) !== (uid ?? '')) {
      for (const k of Object.keys(localStorage)) {
        if (k.startsWith(PERSIST_PREFIX)) localStorage.removeItem(k)
      }
    }
    if (uid) localStorage.setItem(OWNER_KEY, uid)
    else localStorage.removeItem(OWNER_KEY)
  } catch { /* privat modus */ }
}

/** Tøm cachen (alt, eller nøkler som starter med prefix). Ved bytte av bruker. */
export function clearCache(prefix = '') {
  if (!prefix) cache.clear()
  else for (const k of cache.keys()) if (k.startsWith(prefix)) cache.delete(k)
  try {
    for (const k of Object.keys(localStorage)) {
      if (k.startsWith(PERSIST_PREFIX + prefix)) localStorage.removeItem(k)
    }
  } catch { /* privat modus */ }
}

// Leser en vennlig feilmelding fra responsen. Backend sender { error: "..." }
// (se ApiExceptionHandler) ved problemer med eksterne tjenester; vi viser den.
export async function readError(res) {
  // 401 betyr manglende/utløpt innlogging (eller at backend mangler
  // Supabase-nøklene sine) – gi en melding brukeren kan handle på.
  let msg = res.status === 401
    ? 'Du er ikke innlogget, eller innloggingen er utløpt. Logg inn på nytt.'
    : `Noe gikk galt (${res.status})`
  try {
    const body = await res.json()
    if (body?.error) msg = body.error
  } catch {
    // ikke JSON – behold standardmeldingen
  }
  return msg
}
