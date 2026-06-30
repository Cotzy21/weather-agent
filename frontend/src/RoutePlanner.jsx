import { useState, useMemo, useEffect } from 'react'
import RouteMap from './RouteMap'
import { readError } from './api'
import { authHeaders } from './supabase'

function distanceKm(a, b) {
  const R = 6371
  const dLat = ((b.lat - a.lat) * Math.PI) / 180
  const dLon = ((b.lon - a.lon) * Math.PI) / 180
  const s =
    Math.sin(dLat / 2) ** 2 +
    Math.cos((a.lat * Math.PI) / 180) * Math.cos((b.lat * Math.PI) / 180) * Math.sin(dLon / 2) ** 2
  return 2 * R * Math.asin(Math.sqrt(s))
}

export default function RoutePlanner({ session }) {
  const [waypoints, setWaypoints] = useState([])
  const [weight, setWeight] = useState(75)
  const [plan, setPlan] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)

  const [routeName, setRouteName] = useState('')
  const [saved, setSaved] = useState([])
  const [shown, setShown] = useState(null) // geometri fra en lagret rute vist på kartet

  const totalKm = useMemo(() => {
    let sum = 0
    for (let i = 1; i < waypoints.length; i++) sum += distanceKm(waypoints[i - 1], waypoints[i])
    return sum
  }, [waypoints])

  useEffect(() => {
    if (!session) {
      setSaved([])
      return
    }
    loadSaved()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session])

  async function loadSaved() {
    try {
      const res = await fetch('/api/ruter', { headers: await authHeaders() })
      if (res.ok) setSaved(await res.json())
    } catch {
      // ignorer – «Mine ruter» er sekundært
    }
  }

  function addWaypoint(lat, lon) {
    setWaypoints((w) => [...w, { lat, lon }])
    setPlan(null)
    setShown(null)
  }
  function undo() {
    setWaypoints((w) => w.slice(0, -1))
    setPlan(null)
  }
  function clearAll() {
    setWaypoints([])
    setPlan(null)
    setShown(null)
  }

  async function calc() {
    if (waypoints.length < 2) return
    setLoading(true)
    setError(null)
    try {
      const res = await fetch('/api/rute', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          waypoints: waypoints.map((w) => ({ lat: w.lat, lon: w.lon })),
          weightKg: weight || 75,
        }),
      })
      if (!res.ok) throw new Error(await readError(res))
      setPlan(await res.json())
      setShown(null)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  async function saveRoute() {
    if (!plan || !routeName.trim()) return
    setError(null)
    try {
      const res = await fetch('/api/ruter', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...(await authHeaders()) },
        body: JSON.stringify({
          name: routeName.trim(),
          distanceKm: plan.distanceKm,
          ascentM: plan.ascentM,
          geometry: plan.geometry,
        }),
      })
      if (!res.ok) throw new Error(await readError(res))
      setRouteName('')
      loadSaved()
    } catch (e) {
      setError(e.message)
    }
  }

  async function deleteRoute(id) {
    try {
      await fetch(`/api/ruter/${id}`, { method: 'DELETE', headers: await authHeaders() })
      loadSaved()
    } catch {
      // ignorer
    }
  }

  function showRoute(r) {
    setWaypoints([])
    setPlan(null)
    setShown(r.geometry)
  }

  return (
    <div className="planner">
      <p className="hint">
        Klikk i kartet for å legge til punkter (start, stopp, teltplass, mål). Linja viser ruta.
      </p>
      <RouteMap waypoints={waypoints} route={shown ?? plan?.geometry ?? []} onAdd={addWaypoint} />

      <div className="planner-row">
        <span>{waypoints.length} punkt · <strong>{totalKm.toFixed(1)} km</strong></span>
        <span className="spacer" />
        <button onClick={undo} disabled={!waypoints.length}>Angre siste</button>
        <button onClick={clearAll} disabled={!waypoints.length}>Tøm</button>
      </div>

      <div className="planner-inputs">
        <label>Vekt (kg)
          <input type="number" value={weight} min="30" max="200"
                 onChange={(e) => setWeight(+e.target.value)} />
        </label>
        <button className="primary" onClick={calc} disabled={loading || waypoints.length < 2}>
          {loading ? 'Beregner …' : 'Beregn'}
        </button>
      </div>

      {error && <p className="error">Beklager – {error}</p>}

      {plan && (
        <div className="estimate">
          <p className="estimate-line">
            <strong>{plan.distanceKm.toFixed(1)} km</strong> · {plan.ascentM.toFixed(0)} m stigning
            {' '}· ~{plan.hours.toFixed(1)} t · <strong>{plan.calories} kcal</strong>
          </p>
          <div className="gear">
            <span className="gear-title">🍫 Mat &amp; drikke</span>
            <ul>{plan.snacks.map((s, i) => <li key={i}>{s}</li>)}</ul>
          </div>
          <p className="muted estimate-note">
            {plan.snappedToTrails
              ? 'Rute langs faktiske stier. Stigning fra høydeprofil. Grovt estimat (~4 km/t, ~6 MET).'
              : 'Rett linje mellom punktene (sti-ruting ikke aktivert). Stigning fra høydeprofil. Grovt estimat (~4 km/t, ~6 MET).'}
          </p>

          {session ? (
            <div className="save-route">
              <input
                placeholder="Navn på ruta"
                value={routeName}
                onChange={(e) => setRouteName(e.target.value)}
              />
              <button className="primary" onClick={saveRoute} disabled={!routeName.trim()}>Lagre rute</button>
            </div>
          ) : (
            <p className="muted">Logg inn for å lagre ruta.</p>
          )}
        </div>
      )}

      {session && saved.length > 0 && (
        <div className="saved-routes">
          <p className="trails-title">📁 Mine ruter</p>
          <ul>
            {saved.map((r) => (
              <li key={r.id}>
                <button className="linklike" onClick={() => showRoute(r)}>{r.name}</button>
                <span className="muted"> · {r.distanceKm.toFixed(1)} km · {r.ascentM.toFixed(0)} m</span>
                <button className="del" onClick={() => deleteRoute(r.id)} aria-label="Slett">✕</button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}
