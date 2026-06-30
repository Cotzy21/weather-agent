import { useState, useEffect } from 'react'
import { authHeaders } from './supabase'

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
      const res = await fetch('/api/treningsokter', { headers: await authHeaders() })
      if (res.ok) setWorkouts(await res.json())
    } catch { /* hjem er sekundært */ }
  }

  if (!session) {
    return (
      <div className="dash">
        <h2 className="detail-title">⛰️ Velkommen til Turvær</h2>
        <p className="muted">Finn finest turvær, planlegg ruter, og før treningsdagbok – på ett sted.</p>
        <div className="dash-actions">
          <button className="primary" onClick={() => onNavigate('konto')}>Logg inn</button>
          <button onClick={() => onNavigate('vaersok')}>Finn turvær</button>
        </div>
      </div>
    )
  }

  const weekStart = startOfWeek()
  const weekCount = workouts.filter((w) => new Date(`${w.date}T00:00:00`) >= weekStart).length

  return (
    <div className="dash">
      <h2 className="detail-title">Hei 👋</h2>

      <div className="dash-stats">
        <div className="stat"><span className="stat-num">{weekCount}</span><span className="stat-label">økter denne uka</span></div>
        <div className="stat"><span className="stat-num">{workouts.length}</span><span className="stat-label">økter totalt</span></div>
      </div>

      <div className="dash-actions">
        <button className="primary" onClick={() => onNavigate('trening')}>Logg økt</button>
        <button onClick={() => onNavigate('rute')}>Planlegg rute</button>
        <button onClick={() => onNavigate('vaersok')}>Finn turvær</button>
      </div>

      <h3 className="detail-h3">Nylig aktivitet</h3>
      {workouts.length === 0 ? (
        <p className="muted">Ingen økter ennå – logg din første under «Trening».</p>
      ) : (
        <div className="feed">
          {workouts.slice(0, 8).map((w) => (
            <div className="feed-item" key={w.id}>
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
