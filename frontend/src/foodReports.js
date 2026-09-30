// Rapportering av offentlig delte matvarer og enkel moderering (bare administratorer). Ren logikk over fetch,
// så den kan testes uten nettleser. Backend avgjør hvem som er administrator; klienten kan bare spørre.
import { apiUrl, readError } from './api.js'

export const REPORT_REASONS = [
  { v: 'WRONG_VALUES', label: 'Feil verdier' },
  { v: 'SPAM', label: 'Spam' },
  { v: 'INAPPROPRIATE', label: 'Upassende' },
  { v: 'OTHER', label: 'Annet' },
]

const PREFIX = 'egen:'

/** «egen:<uuid>» (id-en i søketreff) -> uuid-en, eller null for varer fra Matvaretabellen. */
export function customFoodUuid(foodId) {
  return typeof foodId === 'string' && foodId.startsWith(PREFIX) ? foodId.slice(PREFIX.length) : null
}

async function ok(res) {
  if (!res.ok) throw new Error(await readError(res))
}

/** Rapporter en delt matvare. Kaster med en lesbar melding hvis serveren sier nei. */
export async function reportFood({ foodId, reason, note, headers, fetchFn = fetch }) {
  const id = customFoodUuid(foodId)
  if (!id) throw new Error('Bare delte matvarer kan rapporteres.')
  const res = await fetchFn(apiUrl(`/api/kosthold/egne-matvarer/${id}/rapport`), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify({ reason, note: note?.trim() || null }),
  })
  await ok(res)
}

/** Rapporterte varer for administratoren. Returnerer null hvis brukeren ikke er administrator (403). */
export async function fetchReported({ headers, fetchFn = fetch }) {
  const res = await fetchFn(apiUrl('/api/admin/rapporter'), { headers })
  if (res.status === 403) return null
  await ok(res)
  return res.json()
}

/** Administrator: slett en delt vare (og dermed rapportene). */
export async function deleteReportedFood({ id, headers, fetchFn = fetch }) {
  await ok(await fetchFn(apiUrl(`/api/admin/matvarer/${id}`), { method: 'DELETE', headers }))
}

/** Administrator: avvis rapportene og la varen bli. */
export async function dismissReports({ id, headers, fetchFn = fetch }) {
  await ok(await fetchFn(apiUrl(`/api/admin/rapporter/${id}`), { method: 'DELETE', headers }))
}
