import { useState, useEffect } from 'react'
import { authHeaders } from './supabase'
import { apiUrl } from './api'
import { useReveal, useCountUp } from './anim'

const ICONS = { STYRKE: '🏋️', LØPING: '🏃', SVØMMING: '🏊', SYKKEL: '🚴', BULDRING: '🧗', HIKING: '🥾', FRISTIL: '✨' }

function startOfWeek() {
  const d = new Date()
  const day = (d.getDay() + 6) % 7 // mandag = 0
  d.setHours(0, 0, 0, 0)
  d.setDate(d.getDate() - day)
  return d
}

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
    const iso = new Date().toISOString().slice(0, 10)
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
  const weekCount = workouts.filter((w) => new Date(`${w.date}T00:00:00`) >= weekStart).length
  const weekRef = useCountUp(weekCount)
  const totalRef = useCountUp(workouts.length)

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
    <div className="dash" ref={revealRef}>
      <h2 className="detail-title" data-reveal>Hei 👋</h2>

      <div className="dash-stats" data-reveal>
        <div className="stat"><span className="stat-num" ref={weekRef}>{weekCount}</span><span className="stat-label">økter denne uka</span></div>
        <div className="stat"><span className="stat-num" ref={totalRef}>{workouts.length}</span><span className="stat-label">økter totalt</span></div>
      </div>

      <button className="dash-nutrition" data-reveal onClick={() => onNavigate('kosthold')}>
        <span className="ai-title">🥗 Kostholdet ditt</span>
        {day == null || (day.entries.length === 0 && lows.length === 0) ? (
          <p className="muted">Ingenting logget ennå – begynn kostholdsdagboka her →</p>
        ) : (
          <>
            <p className="dash-nutrition-line">
              I dag: <strong>{day.kcal.toFixed(0)} kcal</strong>
              {day.burnedKcal > 0 && <> · trening −{day.burnedKcal}</>}
              {day.remainingKcal != null && (
                <> · <strong className={day.remainingKcal < 0 ? 'over-budget' : ''}>
                  {day.remainingKcal.toFixed(0)} igjen
                </strong> av målet</>
              )}
            </p>
            {lows.length > 0 && (
              <p className="dash-nutrition-line">
                🧪 Lavt denne uka: <strong>{lows.map((n) => n.name).slice(0, 3).join(', ')}
                {lows.length > 3 ? ` +${lows.length - 3}` : ''}</strong>
                {' '}– <span className="muted">{lows[0].advice.split('.')[0].toLowerCase()}.
                Trykk for råd →</span>
              </p>
            )}
          </>
        )}
      </button>

      <div className="dash-actions" data-reveal>
        <button className="primary" onClick={() => onNavigate('trening')}>Logg økt</button>
        <button onClick={() => onNavigate('rute')}>Planlegg rute</button>
        <button onClick={() => onNavigate('vaersok')}>Finn turvær</button>
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
