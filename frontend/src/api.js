// Grunn-URL for API-et. Tom i web (samme opphav / Vite-proxy). I mobil-appen
// (Capacitor) settes VITE_API_BASE til den utplasserte backend-URL-en, siden
// appen ikke kan bruke proxy eller relative /api-kall.
const BASE = import.meta.env.VITE_API_BASE || ''

export function apiUrl(path) {
  return `${BASE}${path}`
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
