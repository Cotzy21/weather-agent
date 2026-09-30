// Kortene på Hjem. Rekkefølgen her er standardoppsettet; `wide` tar hele bredden.
export const WIDGETS = [
  { id: 'week', icon: '🏋️', label: 'Trening denne uka' },
  { id: 'streak', icon: '🔥', label: 'Ukeserie' },
  { id: 'nutrition', icon: '🥗', label: 'Kosthold i dag' },
  { id: 'latest', icon: '⏱️', label: 'Siste økt' },
  { id: 'weather', icon: '🌤️', label: 'Turvær' },
  { id: 'today', icon: '📅', label: 'I dag', wide: true },
  { id: 'bodyweight', icon: '⚖️', label: 'Kroppsvekt', wide: true },
  { id: 'lifetime', icon: '🏅', label: 'Livstidsstatistikk', wide: true },
  { id: 'recent', icon: '🕘', label: 'Nylig aktivitet', wide: true },
]

export const WIDGET_BY_ID = Object.fromEntries(WIDGETS.map((w) => [w.id, w]))

const key = (userId) => `dash-layout:${userId}`

// Oppsettet fra en lagret liste, med nye kort (lagt til etter at brukeren lagret) på slutten, synlige.
export function mergeLayout(saved) {
  saved = Array.isArray(saved) ? saved : []
  const known = saved.filter((w) => WIDGET_BY_ID[w.id])
  const missing = WIDGETS.filter((w) => !known.some((k) => k.id === w.id)).map((w) => ({ id: w.id, on: true }))
  return [...known.map((w) => ({ id: w.id, on: w.on !== false })), ...missing]
}

// Lokalt lagret oppsett (rask oppstart og offline); serveren er fasit på tvers av enheter.
export function loadLayout(userId) {
  let saved = []
  try { saved = JSON.parse(localStorage.getItem(key(userId))) || [] } catch { /* privat modus o.l. */ }
  return mergeLayout(saved)
}

/** Har brukeren et oppsett lagret på denne enheten (før serveren kjente til det)? */
export function hasStoredLayout(userId) {
  try { return localStorage.getItem(key(userId)) != null } catch { return false }
}

export function storeLayout(userId, layout) {
  try { localStorage.setItem(key(userId), JSON.stringify(layout)) } catch { /* privat modus o.l. */ }
}
