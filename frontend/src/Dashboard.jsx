import { useState, useEffect } from 'react'
import { authHeaders } from './supabase'
import { apiUrl } from './api'
import { useReveal, useCountUp } from './anim'

const ICONS = { STYRKE: '🏋️', LØPING: '🏃', SVØMMING: '🏊', SYKKEL: '🚴', BULDRING: '🧗', HIKING: '🥾', FRISTIL: '✨' }
const DAY_LETTERS = ['M', 'T', 'O', 'T', 'F', 'L', 'S'] // mandag først

function startOfWeek() {
  const d = new Date()
  const day = (d.getDay() + 6) % 7 // mandag = 0
  d.setHours(0, 0, 0, 0)
  d.setDate(d.getDate() - day)
  return d
}

const isoToday = () => new Date().toISOString().slice(0, 10)

/**
 * Hjem-dashboard i Garmin Connect-stil: «I fokus»-kort i et grid til venstre
 * (ukas trening med stolper per dag, kosthold, siste økt) og en «I dag»-kolonne
 * til høyre med dagens aktivitet og snarveier.
 */
export default function Dashboard({ session, onNavigate }) {
  const [workouts, setWorkouts] = useState([])
  const [day, setDay] = useState(null)   // dagens kaloribalanse
  const [lows, setLows] = useState([])   // næringsstoffer med lavt ukesinntak

  useEffect(() => {
    if (!session) { setWorkouts([]); setDay(null); setLows([]); return }
    loadWorkouts()
    loadNutrition()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session])

  async function loadWorkouts() {
    try {
      const res = await fetch(apiUrl('/api/treningsokter'), { headers: await authHeaders() })
      if (res.ok) setWorkouts(await res.json())
    } catch { /* hjem er sekundært */ }
  }

  async function loadNutrition() {
    const iso = isoToday()
    try {
      const [dagRes, ukeRes] = await Promise.all([
        fetch(apiUrl(`/api/kosthold/dag?dato=${iso}`), { headers: await authHeaders() }),
        fetch(apiUrl(`/api/kosthold/uke?til=${iso}`), { headers: await authHeaders() }),
      ])
      if (dagRes.ok) setDay(await dagRes.json())
      if (ukeRes.ok) setLows((await ukeRes.json()).filter((n) => n.advice))
    } catch { /* hjem er sekundært */ }
  }

  const revealRef = useReveal([session, workouts.length])

  const weekStart = startOfWeek()
  const weekWorkouts = workouts.filter((w) => new Date(`${w.date}T00:00:00`) >= weekStart)
  const weekCount = weekWorkouts.length
  const weekRef = useCountUp(weekCount)

  // Økter per ukedag (man-søn) til stolpediagrammet, à la Garmins ukeskort.
  const dayCounts = Array(7).fill(0)
  weekWorkouts.forEach((w) => {
    dayCounts[(new Date(`${w.date}T00:00:00`).getDay() + 6) % 7]++
  })
  const maxCount = Math.max(1, ...dayCounts)
  const todayIdx = (new Date().getDay() + 6) % 7

  const todays = workouts.filter((w) => w.date === isoToday())
  const latest = workouts[0]

  if (!session) {
    return (
      <div className="dash hero" ref={revealRef}>
        <h2 className="hero-title" data-reveal>Finn eventyret.<br /><span className="grad">Følg formen.</span></h2>
        <p className="muted hero-sub" data-reveal>
          Finn finest turvær, planlegg ruter, og før treningsdagbok – på ett sted.
        </p>
        <div className="dash-actions" data-reveal>
          <button className="primary" onClick={() => onNavigate('konto')}>Logg inn</button>
          <button onClick={() => onNavigate('vaersok')}>Finn turvær</button>
        </div>
      </div>
    )
  }

  return (
    <div className="dash garmin" ref={revealRef}>
      <h2 className="detail-title" data-reveal>I fokus</h2>

      <div className="dash-layout">
        <div className="focus-grid">
          <div className="focus-card" data-reveal>
            <span className="focus-head">🏋️ Trening denne uka</span>
            <div className="focus-main">
              <span className="focus-big" ref={weekRef}>{weekCount}</span>
              <span className="focus-sub muted">økter · {workouts.length} totalt</span>
            </div>
            <div className="week-bars" aria-label="Økter per ukedag">
              {dayCounts.map((n, i) => (
                <span className={`week-day ${i === todayIdx ? 'today' : ''}`} key={i}>
                  <span className="week-bar">
                    <span style={{ height: `${(n / maxCount) * 100}%` }} />
                  </span>
                  <span className="week-letter">{DAY_LETTERS[i]}</span>
                </span>
              ))}
            </div>
          </div>

          <button className="focus-card clickable" data-reveal onClick={() => onNavigate('kosthold')}>
            <span className="focus-head">🥗 Kosthold i dag</span>
            {day == null || (day.entries.length === 0 && lows.length === 0) ? (
              <p className="muted">Ingenting logget ennå – begynn kostholdsdagboka her →</p>
            ) : (
              <>
                <div className="focus-main">
                  <span className="focus-big">{day.kcal.toFixed(0)}</span>
                  <span className="focus-sub muted">
                    kcal
                    {day.burnedKcal > 0 && <> · trening −{day.burnedKcal}</>}
                  </span>
                </div>
                {day.remainingKcal != null && (
                  <p className={`focus-line ${day.remainingKcal < 0 ? 'over-budget' : ''}`}>
                    {day.remainingKcal.toFixed(0)} kcal igjen av målet
                  </p>
                )}
                {lows.length > 0 && (
                  <p className="focus-line muted">
                    🧪 Lavt denne uka: {lows.map((n) => n.name).slice(0, 3).join(', ')}
                    {lows.length > 3 ? ` +${lows.length - 3}` : ''} →
                  </p>
                )}
              </>
            )}
          </button>

          <div className="focus-card" data-reveal>
            <span className="focus-head">⏱️ Siste økt</span>
            {latest ? (
              <>
                <div className="focus-main">
                  <span className="focus-big small">{ICONS[latest.type] || '•'}</span>
                  <span>
                    <strong className="focus-line">{latest.title}</strong>
                    <span className="focus-line muted">{latest.date}</span>
                  </span>
                </div>
              </>
            ) : (
              <p className="muted">Ingen økter ennå – logg din første under «Trening».</p>
            )}
          </div>

          <div className="focus-card" data-reveal>
            <span className="focus-head">🌤️ Turvær</span>
            <p className="focus-line">Hvor er det finest i helga?</p>
            <p className="focus-line muted">Spør værsøket – rangerer topper og turruter etter vær.</p>
            <button className="mini" onClick={() => onNavigate('vaersok')}>Finn turvær →</button>
          </div>
        </div>

        <aside className="today-col" data-reveal>
          <h3 className="detail-h3">I dag</h3>
          {todays.length === 0 ? (
            <div className="today-card muted">Ingen aktivitet logget i dag ennå.</div>
          ) : (
            todays.map((w) => (
              <div className="today-card" key={w.id}>
                <span className="feed-icon">{ICONS[w.type] || '•'}</span>
                <span>
                  <strong className="focus-line">{w.title}</strong>
                  <span className="focus-line muted">{w.type}</span>
                </span>
              </div>
            ))
          )}
          <div className="dash-actions column">
            <button className="primary" onClick={() => onNavigate('trening')}>Logg økt</button>
            <button onClick={() => onNavigate('rute')}>Planlegg rute</button>
            <button onClick={() => onNavigate('vaersok')}>Finn turvær</button>
          </div>
        </aside>
      </div>

      <h3 className="detail-h3" data-reveal>Nylig aktivitet</h3>
      {workouts.length === 0 ? (
        <p className="muted" data-reveal>Ingen økter ennå – logg din første under «Trening».</p>
      ) : (
        <div className="feed">
          {workouts.slice(0, 8).map((w) => (
            <div className="feed-item" data-reveal key={w.id}>
              <span className="feed-icon">{ICONS[w.type] || '•'}</span>
              <span className="feed-title">{w.title}</span>
              <span className="muted">{w.date}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
