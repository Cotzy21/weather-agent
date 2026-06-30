import { useState, useEffect } from 'react'
import { authHeaders } from './supabase'
import { readError } from './api'

const COMMON = [
  'Benkpress', 'Knebøy', 'Markløft', 'Skulderpress', 'Nedtrekk', 'Stående roing',
  'Biceps curl', 'Triceps pushdown', 'Utfall', 'Leg press', 'Pull-ups', 'Planke',
]

const today = () => new Date().toISOString().slice(0, 10)
const emptyRow = () => ({ exercise: '', reps: '', weight: '' })

export default function TrainingView({ session }) {
  const [date, setDate] = useState(today())
  const [title, setTitle] = useState('')
  const [rows, setRows] = useState([emptyRow()])
  const [workouts, setWorkouts] = useState([])
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const [progressName, setProgressName] = useState('')
  const [progress, setProgress] = useState(null)

  useEffect(() => {
    if (session) loadWorkouts()
    else setWorkouts([])
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session])

  async function loadWorkouts() {
    try {
      const res = await fetch('/api/treningsokter', { headers: await authHeaders() })
      if (res.ok) setWorkouts(await res.json())
    } catch {
      // sekundært
    }
  }

  function updateRow(i, field, value) {
    setRows((rs) => rs.map((r, idx) => (idx === i ? { ...r, [field]: value } : r)))
  }
  function addRow() { setRows((rs) => [...rs, emptyRow()]) }
  function removeRow(i) { setRows((rs) => (rs.length > 1 ? rs.filter((_, idx) => idx !== i) : rs)) }

  async function submit() {
    const sets = rows
      .filter((r) => r.exercise.trim() && r.reps)
      .map((r) => ({ exercise: r.exercise.trim(), reps: Number(r.reps), weightKg: Number(r.weight) || 0 }))
    if (!title.trim() || sets.length === 0) {
      setError('Gi økta en tittel og minst ett sett.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      const res = await fetch('/api/treningsokter', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...(await authHeaders()) },
        body: JSON.stringify({ date, title: title.trim(), sets }),
      })
      if (!res.ok) throw new Error(await readError(res))
      setTitle('')
      setRows([emptyRow()])
      loadWorkouts()
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }

  async function deleteWorkout(id) {
    try {
      await fetch(`/api/treningsokter/${id}`, { method: 'DELETE', headers: await authHeaders() })
      loadWorkouts()
    } catch {
      // ignorer
    }
  }

  async function loadProgress() {
    if (!progressName.trim()) return
    try {
      const params = new URLSearchParams({ navn: progressName.trim() })
      const res = await fetch(`/api/ovelser/progresjon?${params}`, { headers: await authHeaders() })
      if (res.ok) setProgress(await res.json())
    } catch {
      setProgress([])
    }
  }

  if (!session) {
    return (
      <div className="training">
        <h2 className="detail-title">Trening</h2>
        <p className="muted">Logg inn for å bruke treningsdagboka.</p>
      </div>
    )
  }

  const maxW = progress && progress.length ? Math.max(...progress.map((p) => p.maxWeight)) : 0

  return (
    <div className="training">
      <h2 className="detail-title">Logg økt</h2>
      <datalist id="exercises">{COMMON.map((e) => <option key={e} value={e} />)}</datalist>

      <div className="log-meta">
        <label>Dato<input type="date" value={date} onChange={(e) => setDate(e.target.value)} /></label>
        <label className="grow">Tittel<input placeholder="f.eks. Push A" value={title} onChange={(e) => setTitle(e.target.value)} /></label>
      </div>

      <div className="set-rows">
        <div className="set-head"><span>Øvelse</span><span>Reps</span><span>Kg</span><span /></div>
        {rows.map((r, i) => (
          <div className="set-row" key={i}>
            <input list="exercises" placeholder="Øvelse" value={r.exercise} onChange={(e) => updateRow(i, 'exercise', e.target.value)} />
            <input type="number" min="1" value={r.reps} onChange={(e) => updateRow(i, 'reps', e.target.value)} />
            <input type="number" min="0" step="2.5" value={r.weight} onChange={(e) => updateRow(i, 'weight', e.target.value)} />
            <button className="del" onClick={() => removeRow(i)} aria-label="Fjern">✕</button>
          </div>
        ))}
      </div>
      <div className="log-actions">
        <button onClick={addRow}>+ Sett</button>
        <button className="primary" onClick={submit} disabled={busy}>{busy ? 'Lagrer …' : 'Lagre økt'}</button>
      </div>
      {error && <p className="error">{error}</p>}

      <h3 className="detail-h3">Progresjon</h3>
      <div className="progress-search">
        <input list="exercises" placeholder="Øvelse, f.eks. Benkpress" value={progressName} onChange={(e) => setProgressName(e.target.value)} />
        <button onClick={loadProgress}>Vis</button>
      </div>
      {progress && (progress.length === 0
        ? <p className="muted">Ingen logget for denne øvelsen ennå.</p>
        : (
          <div className="progress">
            {progress.map((p, i) => (
              <div className="prog-row" key={i}>
                <span className="prog-date">{p.date}</span>
                <span className="prog-bar"><span style={{ width: `${maxW ? (p.maxWeight / maxW) * 100 : 0}%` }} /></span>
                <span className="prog-val">{p.maxWeight.toFixed(1)} kg</span>
              </div>
            ))}
          </div>
        ))}

      <h3 className="detail-h3">Tidligere økter</h3>
      {workouts.length === 0 ? (
        <p className="muted">Ingen økter ennå.</p>
      ) : (
        <div className="workouts">
          {workouts.map((w) => (
            <div className="workout" key={w.id}>
              <div className="workout-head">
                <strong>{w.title}</strong>
                <span className="muted">{w.date}</span>
                <button className="del" onClick={() => deleteWorkout(w.id)} aria-label="Slett">✕</button>
              </div>
              <ul>
                {w.sets.map((s, i) => (
                  <li key={i}>{s.exercise} – {s.reps} × {s.weightKg} kg</li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
