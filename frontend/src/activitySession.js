// Aktivitetsøkt: start en økt som ikke er styrke (løping, sykkel, tur, svømming, buldring, kampsport, fristil) med en klokke, og logg
// den som gjennomført først når den fullføres. Styrkeøkter har egen live-økt (liveSession.js); dette er det tilsvarende for resten.
// Mellomlagres per bruker så økta overlever at Safari lukker/laster siden på nytt (som live-økta).
import { newId } from './liveSession.js'
import { localIso } from './trainingStats.js'

const storeKey = (userId) => `activity-session:${userId}`

export const DISTANCE_TYPES = ['LØPING', 'SYKKEL', 'SVØMMING', 'HIKING']
export const usesDistance = (type) => DISTANCE_TYPES.includes(type)
export const usesAscent = (type) => type === 'HIKING'

export function loadActivity(userId, storage = globalThis.localStorage) {
  try {
    const s = JSON.parse(storage.getItem(storeKey(userId)))
    return s && typeof s.type === 'string' && Number.isFinite(s.startedAt) ? s : null
  } catch { return null }
}

export function saveActivity(userId, state, storage = globalThis.localStorage) {
  try { storage.setItem(storeKey(userId), JSON.stringify(state)) } catch { /* privat modus o.l. */ }
}

export function clearActivity(userId, storage = globalThis.localStorage) {
  try { storage.removeItem(storeKey(userId)) } catch { /* privat modus o.l. */ }
}

/** Ny økt. `plan` er malen den startes fra (eller null for en tom økt av valgt type). clientId lages én gang, så innsending på nytt aldri lager en duplikat. */
export function newActivity(type, plan = null, now = Date.now()) {
  const c = plan?.content ?? {}
  return {
    type,
    title: plan?.title ?? '',
    startedAt: now,
    targets: { distanceKm: c.distanceKm ?? null, durationMin: c.durationMin ?? null, ascentM: c.ascentM ?? null },
    plannedId: plan?.id ?? null,
    clientId: newId(),
  }
}

/** Minutter siden start (minst 1). */
export function elapsedMinutes(state, now = Date.now()) {
  return Math.max(1, Math.round((now - state.startedAt) / 60000))
}

const toNum = (x) => {
  const s = String(x ?? '').trim().replace(',', '.')
  return s === '' ? null : Number(s)
}

/** Utfylt skjema ved fullføring: varighet foreslås fra klokka, distanse og stigning fra malens mål. */
export function defaultForm(state, now = Date.now()) {
  const t = state.targets ?? {}
  return {
    durationMin: String(elapsedMinutes(state, now)),
    distanceKm: usesDistance(state.type) && t.distanceKm != null ? String(t.distanceKm) : '',
    ascentM: usesAscent(state.type) && t.ascentM != null ? String(t.ascentM) : '',
  }
}

/**
 * Kroppen som sendes til /api/treningsokter, eller { error } med en norsk feilmelding (oversettes i grensesnittet).
 * Datoen er dagen økta startet (en økt over midnatt hører til dagen den startet).
 */
export function toActivityBody(state, form, title) {
  const duration = toNum(form.durationMin)
  if (duration == null || !Number.isFinite(duration) || duration < 1 || duration > 1440) {
    return { error: 'Varigheten må være mellom 1 og 1440 minutter.' }
  }
  const content = { durationMin: Math.round(duration) }
  if (usesDistance(state.type)) {
    const km = toNum(form.distanceKm)
    if (km != null && (!Number.isFinite(km) || km < 0 || km > 1000)) return { error: 'Distansen må være mellom 0 og 1000 km.' }
    if (km != null) content.distanceKm = km
  }
  if (usesAscent(state.type)) {
    const m = toNum(form.ascentM)
    if (m != null && (!Number.isFinite(m) || m < 0 || m > 10000)) return { error: 'Stigningen må være mellom 0 og 10 000 m.' }
    if (m != null) content.ascentM = m
  }
  return {
    body: {
      date: localIso(new Date(state.startedAt)),
      title: (title ?? '').trim() || state.title || state.type,
      type: state.type,
      content,
      notes: null,
      clientId: state.clientId,
      plannedId: state.plannedId ?? null,
    },
  }
}
