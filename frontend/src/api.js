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

/** Sist kjente svar for en sti, eller undefined. Bruk til å hydrere state ved mount. */
export function getCached(key) {
  return cache.get(key)
}

/** Hent + oppdater cachen. Kaster ved feil, så kalleren kan beholde stale data. */
export async function cachedGet(key, headers = {}) {
  const res = await fetch(apiUrl(key), { headers })
  if (!res.ok) throw new Error(await readError(res))
  const data = await res.json()
  cache.set(key, data)
  return data
}

/** Tøm cachen (alt, eller nøkler som starter med prefix). Ved bytte av bruker. */
export function clearCache(prefix = '') {
  if (!prefix) { cache.clear(); return }
  for (const k of cache.keys()) if (k.startsWith(prefix)) cache.delete(k)
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
