// Kontosletting i nettleseren: be serveren slette alt (DELETE /api/konto), og rydd bort det som ligger igjen lokalt.
import { apiUrl, readError, clearCache } from './api.js'

/** Ordet brukeren må skrive for å bekrefte (norsk eller engelsk, uavhengig av store/små bokstaver). */
export function isDeleteConfirmation(text) {
  const word = String(text ?? '').trim().toLowerCase()
  return word === 'slett' || word === 'delete'
}

/**
 * Fjerner det som er lagret lokalt for brukeren: API-cachen, en påbegynt live-økt og dashboard-oppsettet
 * (nøkler som ender på :<bruker-id>), og de lokale favorittene/måltidene i kosthold. Køen med økter som venter
 * på nett tømmes av kalleren (offlineQueue). Språk og tema beholdes: de er ikke kontodata.
 */
export function eraseLocalUserData(userId, storage = globalThis.localStorage) {
  clearCache()
  try {
    for (const key of Object.keys(storage)) {
      if (userId && key.endsWith(`:${userId}`)) storage.removeItem(key)
    }
    storage.removeItem('kosthold-favoritter')
    storage.removeItem('kosthold-maaltider')
  } catch { /* privat modus o.l. */ }
}

/** DELETE /api/konto. Returnerer { deletedRows, loginRemoved }, eller kaster med en lesbar melding. */
export async function deleteAccount({ headers, fetchFn = fetch }) {
  const res = await fetchFn(apiUrl('/api/konto'), { method: 'DELETE', headers })
  if (!res.ok) throw new Error(await readError(res))
  return res.json()
}
