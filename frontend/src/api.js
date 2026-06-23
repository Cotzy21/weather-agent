// Leser en vennlig feilmelding fra responsen. Backend sender { error: "..." }
// (se ApiExceptionHandler) ved problemer med eksterne tjenester; vi viser den.
export async function readError(res) {
  let msg = `Noe gikk galt (${res.status})`
  try {
    const body = await res.json()
    if (body?.error) msg = body.error
  } catch {
    // ikke JSON – behold standardmeldingen
  }
  return msg
}
