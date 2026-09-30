// Ukeseriens innstillinger (mål og pausede uker): lokal kopi for rask oppstart og offline, kontoen er fasit på tvers av enheter.
// Samme mønster som dashboard-oppsettet (dashboardLayout.js). Nettverk og lagring sendes inn, så alt kan testes uten nettleser.
import { apiUrl, readError } from './api.js'
import { normalizeSettings } from './weeklyStreak.js'

const storageKey = (userId) => `streak-settings:${userId}`
const defaultStorage = () => { try { return globalThis.localStorage } catch { return undefined } }

/** Lokalt lagrede innstillinger, eller standardverdier (mål 3, ingen pauser). Tåler manglende/ødelagt lagring. */
export function loadStreakSettings(userId, storage = defaultStorage()) {
  try { return normalizeSettings(JSON.parse(storage?.getItem(storageKey(userId)))) } catch { return normalizeSettings(null) }
}

/** Finnes det lagrede innstillinger på denne enheten (før serveren kjente til dem)? */
export function hasStoredStreakSettings(userId, storage = defaultStorage()) {
  try { return storage?.getItem(storageKey(userId)) != null } catch { return false }
}

export function storeStreakSettings(userId, settings, storage = defaultStorage()) {
  try { storage?.setItem(storageKey(userId), JSON.stringify(normalizeSettings(settings))) } catch { /* privat modus o.l. */ }
}

/** Henter fra kontoen. `null` = ikke lagret ennå (204). Kaster ved nettverks- eller serverfeil. */
export async function pullStreakSettings(headers, fetchFn = fetch) {
  const res = await fetchFn(apiUrl('/api/innstillinger/streak'), { headers })
  if (res.status === 204) return null
  if (!res.ok) throw new Error(await readError(res))
  return normalizeSettings(await res.json())
}

/** Lagrer på kontoen og returnerer det serveren lagret (validert og sortert). */
export async function pushStreakSettings(settings, headers, fetchFn = fetch) {
  const res = await fetchFn(apiUrl('/api/innstillinger/streak'), {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify(normalizeSettings(settings)),
  })
  if (!res.ok) throw new Error(await readError(res))
  return normalizeSettings(await res.json())
}
