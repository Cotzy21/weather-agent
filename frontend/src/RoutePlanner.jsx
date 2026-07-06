import { useState, useMemo, useEffect } from 'react'
import RouteMap from './RouteMap'
import { apiUrl, readError } from './api'
import { authHeaders } from './supabase'
import { useI18n } from './i18n.jsx'

function distanceKm(a, b) {
  const R = 6371
  const dLat = ((b.lat - a.lat) * Math.PI) / 180
  const dLon = ((b.lon - a.lon) * Math.PI) / 180
  const s =
    Math.sin(dLat / 2) ** 2 +
    Math.cos((a.lat * Math.PI) / 180) * Math.cos((b.lat * Math.PI) / 180) * Math.sin(dLon / 2) ** 2
  return 2 * R * Math.asin(Math.sqrt(s))
}

export default function RoutePlanner({ session, target, onClearTarget }) {
  const { t } = useI18n()
  const [waypoints, setWaypoints] = useState([])
  const [weight, setWeight] = useState(75)
  const [plan, setPlan] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)

  const [routeName, setRouteName] = useState('')
  const [saved, setSaved] = useState([])
  const [shown, setShown] = useState(null) // geometri fra en lagret rute vist på kartet
  const [panelOpen, setPanelOpen] = useState(true) // det flytende panelet over kartet

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

  // Sted sendt fra værsøket: foreslå det som rutenavn (uten å overskrive noe
  // brukeren alt har skrevet). Kartet sentreres via focus-proppen på RouteMap.
  useEffect(() => {
    if (target) setRouteName((name) => name || t('Tur til {name}', { name: target.name }))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [target])

  async function loadSaved() {
    try {
      const res = await fetch(apiUrl('/api/ruter'), { headers: await authHeaders() })
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
      const res = await fetch(apiUrl('/api/rute'), {
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
      const res = await fetch(apiUrl('/api/ruter'), {
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
      await fetch(apiUrl(`/api/ruter/${id}`), { method: 'DELETE', headers: await authHeaders() })
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
    <div className="planner atx">
      <RouteMap
        waypoints={waypoints}
        route={shown ?? plan?.geometry ?? []}
        onAdd={addWaypoint}
        focus={target}
      />

      {/* Flytende piller over kartet (à la AllTrails' filterrad). */}
      <div className="map-pills">
        <span className="pill stat">{waypoints.length} {t('punkt')} · <strong>{totalKm.toFixed(1)} km</strong></span>
        <button className="pill" onClick={undo} disabled={!waypoints.length}>{t('↩ Angre siste')}</button>
        <button className="pill" onClick={clearAll} disabled={!waypoints.length}>{t('✕ Tøm')}</button>
      </div>

      {/* Flytende panel til venstre (à la AllTrails' «Explore trails»). */}
      <div className={`map-panel ${panelOpen ? '' : 'closed'}`}>
        <div className="map-panel-head">
          <strong>{t('🧭 Planlegg rute')}</strong>
          <button className="sidebar-toggle" title={panelOpen ? t('Minimer panelet') : t('Utvid panelet')}
                  onClick={() => setPanelOpen(!panelOpen)}>{panelOpen ? '▾' : '▴'}</button>
        </div>

        {panelOpen && (
        <div className="map-panel-body">
          {target && (
            <div className="plan-target">
              {t('🎯 Mål fra værsøket:')} <strong>{target.name}</strong> {t('– klikk i kartet for å tegne ruta di fram til målet.')}
              <button className="del" onClick={onClearTarget} aria-label={t('Fjern mål')}>✕</button>
            </div>
          )}
          <p className="hint left">
            {t('Klikk i kartet for å legge til punkter (start, stopp, teltplass, mål).')}
          </p>

          <div className="planner-inputs">
            <label>{t('Vekt (kg)')}
              <input type="number" value={weight} min="30" max="200"
                     onChange={(e) => setWeight(+e.target.value)} />
            </label>
            <button className="primary" onClick={calc} disabled={loading || waypoints.length < 2}>
              {loading ? t('Beregner …') : t('Beregn')}
            </button>
          </div>

          {error && <p className="error">{t('Beklager –')} {error}</p>}

          {plan && (
        <div className="estimate">
          <p className="estimate-line">
            {plan.assessment && (
              <span className={`diff-badge diff-${plan.assessment.difficulty.toLowerCase()}`}>
                {t(plan.assessment.difficultyLabel)}
              </span>
            )}
            <strong>{plan.distanceKm.toFixed(1)} km</strong> · {plan.ascentM.toFixed(0)} m {t('stigning')}
            {' '}· ~{plan.hours.toFixed(1)} t · <strong>{plan.calories} kcal</strong>
          </p>
          {plan.assessment && (
            <p className="terrain-line muted">
              {t('⛰️ Høyeste punkt ~{m} moh · bratteste parti ~{p} % helning', { m: plan.assessment.highestPointM.toFixed(0), p: plan.assessment.maxGradientPct.toFixed(0) })}
            </p>
          )}
          {plan.assessment?.challenges.length > 0 && (
            <div className="gear">
              <span className="gear-title">{t('⚠️ Vær forberedt på')}</span>
              <ul>{plan.assessment.challenges.map((c, i) => <li key={i}>{c}</li>)}</ul>
            </div>
          )}
          <div className="gear">
            <span className="gear-title">{t('🍫 Mat & drikke')}</span>
            <ul>{plan.snacks.map((s, i) => <li key={i}>{s}</li>)}</ul>
          </div>
          <p className="muted estimate-note">
            {plan.snappedToTrails
              ? t('Rute langs faktiske stier. Stigning fra høydeprofil. Grovt estimat (~4 km/t, ~6 MET).')
              : t('Rett linje mellom punktene (sti-ruting ikke aktivert). Stigning fra høydeprofil. Grovt estimat (~4 km/t, ~6 MET).')}
          </p>

          {session ? (
            <div className="save-route">
              <input
                placeholder={t('Navn på ruta')}
                value={routeName}
                onChange={(e) => setRouteName(e.target.value)}
              />
              <button className="primary" onClick={saveRoute} disabled={!routeName.trim()}>{t('Lagre rute')}</button>
            </div>
          ) : (
            <p className="muted">{t('Logg inn for å lagre ruta.')}</p>
          )}
        </div>
          )}

          {session && saved.length > 0 && (
            <div className="saved-routes">
              <p className="trails-title">{t('📁 Mine ruter')}</p>
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
        )}
      </div>
    </div>
  )
}
