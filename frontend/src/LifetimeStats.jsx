import { useI18n } from './i18n.jsx'
import { strengthVolume, countSets, localIso } from './trainingStats.js'

// Samme anslag som backend (WorkoutCalorieEstimator) når en styrkeøkt mangler varighet.
const MINUTES_PER_STRENGTH_SET = 3

const MEDALS = [
  ...[1, 5, 10, 25, 50, 100, 250, 500].map((n) => ({ kind: 'count', n, icon: '🏅', title: 'Økt nr. {n}' })),
  ...[10000, 50000, 100000, 250000, 500000, 1000000].map((n) => ({ kind: 'volume', n, icon: '🏋️', title: '{n} kg løftet totalt' })),
  ...[2, 4, 8, 12, 26, 52].map((n) => ({ kind: 'streak', n, icon: '🔥', title: '{n} uker på rad' })),
]

function minutes(w) {
  const m = Number(w.content?.durationMin)
  if (m > 0) return m
  return w.type === 'STYRKE' ? countSets(w.content) * MINUTES_PER_STRENGTH_SET : 0
}

// Mandagen i uka datoen tilhører, som YYYY-MM-DD (nøkkel for «uker på rad»).
function weekKey(iso) {
  const [y, m, d] = iso.split('-').map(Number)
  const date = new Date(y, m - 1, d)
  date.setDate(date.getDate() - ((date.getDay() + 6) % 7))
  return localIso(date)
}

function previousWeek(key) {
  const [y, m, d] = key.split('-').map(Number)
  return localIso(new Date(y, m - 1, d - 7))
}

// Uker på rad med minst én økt, regnet bakover fra `fromKey`.
function streakFrom(weeks, fromKey) {
  let n = 0
  for (let k = fromKey; weeks.has(k); k = previousWeek(k)) n++
  return n
}

/** Livstidstall + medaljer med datoen de ble nådd. Ren funksjon av øktlisten. */
function lifetime(workouts, now = new Date()) {
  const asc = [...workouts].sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : 0))
  const today = localIso(now)
  const monthStart = today.slice(0, 8) + '01'
  const days30 = localIso(new Date(now.getFullYear(), now.getMonth(), now.getDate() - 29))

  const weeks = new Set(asc.map((w) => weekKey(w.date)))
  const thisWeek = weekKey(today)
  // En uke uten økt ennå bryter ikke rekka før den er over.
  const currentStreak = weeks.has(thisWeek) ? streakFrom(weeks, thisWeek) : streakFrom(weeks, previousWeek(thisWeek))

  const earned = []
  let volume = 0
  let bestStreak = 0
  const seenWeeks = new Set()
  asc.forEach((w, i) => {
    const before = volume
    volume += w.type === 'STYRKE' ? strengthVolume(w.content) : 0
    const wk = weekKey(w.date)
    const newWeek = !seenWeeks.has(wk)
    seenWeeks.add(wk)
    const streak = newWeek ? streakFrom(seenWeeks, wk) : 0
    for (const m of MEDALS) {
      if ((m.kind === 'count' && i + 1 === m.n)
        || (m.kind === 'volume' && before < m.n && volume >= m.n)
        || (m.kind === 'streak' && newWeek && streak === m.n && m.n > bestStreak)) {
        earned.push({ ...m, date: w.date })
      }
    }
    if (streak > bestStreak) bestStreak = streak
  })

  const next = MEDALS.find((m) => !earned.some((e) => e.kind === m.kind && e.n === m.n) && m.kind === 'count')
  return {
    total: asc.length,
    thisMonth: asc.filter((w) => w.date >= monthStart).length,
    avgPerWeek: Math.round((asc.filter((w) => w.date >= days30).length / (30 / 7)) * 10) / 10,
    hours: Math.round(asc.reduce((sum, w) => sum + minutes(w), 0) / 6) / 10,
    volume: Math.round(volume),
    currentStreak,
    earned: earned.reverse(),
    next: next ? { ...next, progress: asc.length } : null,
  }
}

export default function LifetimeStats({ workouts }) {
  const { t, lang } = useI18n()
  if (!workouts.length) return null
  const s = lifetime(workouts)
  const locale = lang === 'en' ? 'en-US' : 'nb-NO'
  const fmt = (n) => n.toLocaleString(locale)
  const compact = (n) => (n >= 1e6 ? `${fmt(Math.round(n / 1e5) / 10)}M` : n >= 1e4 ? `${fmt(Math.round(n / 100) / 10)}k` : fmt(n))

  const tiles = [
    { big: fmt(s.total), label: t('Økter fullført'), sub: t('Denne måneden: +{n}', { n: s.thisMonth }) },
    { big: `≈${fmt(s.avgPerWeek)}`, label: t('Snitt per uke'), sub: t('Siste 30 dager') },
    { big: fmt(s.hours), unit: t('t'), label: t('Tid trent'), sub: t('Inkl. anslag for styrke') },
    { big: compact(s.volume), unit: 'kg', label: t('Totalvolum'), sub: t('Alle styrkeøkter') },
    { big: fmt(s.currentStreak), label: t('Uker på rad'), sub: t('Med minst én økt') },
  ]

  return (
    <section className="lifetime" data-reveal>
      <h3 className="detail-h3">{t('Livstidsstatistikk')}</h3>
      <div className="lifetime-grid">
        {tiles.map((tile) => (
          <div className="lifetime-tile" key={tile.label}>
            <span className="lifetime-big">{tile.big}{tile.unit && <small> {tile.unit}</small>}</span>
            <span className="lifetime-label">{tile.label}</span>
            <span className="lifetime-sub muted">{tile.sub}</span>
          </div>
        ))}
      </div>

      <h3 className="detail-h3">{t('Medaljer')}</h3>
      <div className="medals">
        {s.earned.slice(0, 4).map((m) => (
          <div className="medal" key={`${m.kind}-${m.n}`}>
            <span className="medal-icon">{m.icon}</span>
            <span className="medal-text">
              <strong>{t(m.title, { n: m.kind === 'volume' ? compact(m.n) : m.n })}</strong>
              <span className="muted">{new Date(`${m.date}T12:00`).toLocaleDateString(locale, { day: 'numeric', month: 'short', year: 'numeric' })}</span>
            </span>
          </div>
        ))}
        {s.next && (
          <div className="medal next">
            <span className="medal-icon">{s.next.icon}</span>
            <span className="medal-text">
              <strong>{t(s.next.title, { n: s.next.n })}</strong>
              <span className="medal-progress"><span style={{ width: `${Math.min(100, (s.next.progress / s.next.n) * 100)}%` }} /></span>
              <span className="muted">{s.next.progress}/{s.next.n}</span>
            </span>
          </div>
        )}
      </div>
    </section>
  )
}
