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

  useEffect(() => {
    if (!session) { setWorkouts([]); return }
    loadWorkouts()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session])

  async function loadWorkouts() {
    try {
      const res = await fetch(apiUrl('/api/treningsokter'), { headers: await authHeaders() })
      if (res.ok) setWorkouts(await res.json())
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
