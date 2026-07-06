import { useState, useRef, useEffect } from 'react'
import ResultMap from './ResultMap'
import PlaceDetail from './PlaceDetail'
import RoutePlanner from './RoutePlanner'
import TrainingView from './TrainingView'
import NutritionView from './NutritionView'
import RecoveryView from './RecoveryView'
import Dashboard from './Dashboard'
import AuthView from './AuthView'
import { supabase, authHeaders } from './supabase'
import { apiUrl, readError, clearCache, cachedGet } from './api'
import { useI18n } from './i18n.jsx'
import { useTabTransition } from './anim'
import './App.css'

// De fire faktorene brukeren kan vekte, og nivåene.
const FACTORS = [
  { key: 'temp', label: 'Temperatur', icon: '🌡️' },
  { key: 'rain', label: 'Regn', icon: '🌧️' },
  { key: 'wind', label: 'Vind', icon: '💨' },
  { key: 'elevation', label: 'Høyde', icon: '⛰️' },
]
const LEVELS = [
  { v: 'lav', t: 'Lav' },
  { v: 'middels', t: 'Middels' },
  { v: 'hoy', t: 'Høy' },
]

// Tilpass et PlaceDto til formen kartet (ResultMap) forventer.
function placeForMap(p) {
  return { name: p.name, lat: p.lat, lon: p.lon, temp: p.avgTempC, precip: p.avgPrecipMm, elevation: p.elevationM }
}

function Answer({ result, onSelectPlace, onPlan }) {
  const { t } = useI18n()
  const region = result.region ?? '(ukjent)'
  const isTrail = result.target === 'TUR'
  const isForecast = result.target === 'VARSEL'

  const interp = (
    <p className="interp">
      <strong>{region}</strong> · {result.when} ({result.from} → {result.to}) · {result.tripType}
    </p>
  )

  const places = result.places ?? []
  if (places.length === 0) {
    return (
      <div className="answer">
        {interp}
        <p className="muted">
          {result.region
            ? t('Fant ingen værdata for perioden (kanskje for langt fram?).')
            : t('Jeg fant ingen region i spørsmålet – prøv å nevne et fylke, f.eks. «Rogaland».')}
        </p>
      </div>
    )
  }

  const best = places[0]

  return (
    <div className="answer">
      {interp}
      <p className="winner">
        <span className="medal">{isForecast ? '🌤️' : isTrail ? '🥾' : '🏔️'}</span>{' '}
        {isForecast ? 'Været i ' : isTrail ? `${t('Finest vær på turrute:')} ` : `${t('Finest vær:')} `}
        <strong>{best.name}</strong>{' '}
        <span className="muted">({best.elevationM.toFixed(0)} {t('moh')})</span> – {t('snitt')}{' '}
        {best.avgTempC.toFixed(1)} °C, {best.avgPrecipMm.toFixed(1)} {t('mm regn/dag')}
        <button
          className="plan-btn"
          onClick={() => onPlan({ name: best.name, lat: best.lat, lon: best.lon })}
        >
          {t('🧭 Planlegg tur hit')}
        </button>
      </p>
      {result.clothing?.length > 0 && (
        <div className="gear">
          <span className="gear-title">{t('🎒 Klær & utstyr')}</span>
          <ul>
            {result.clothing.map((c, i) => <li key={i}>{c}</li>)}
          </ul>
        </div>
      )}
      <ResultMap places={places.map(placeForMap)} trails={result.trails ?? []} />
      <p className="table-hint">{t('Trykk på en rad for å åpne detaljside med varsel for de neste dagene.')}</p>
      <table className="ranking">
        <thead>
          <tr><th>{isTrail ? t('Tur') : t('Sted')}</th><th>{t('moh')}</th><th>°C</th><th>mm</th><th>m/s</th><th>{t('score')}</th></tr>
        </thead>
        <tbody>
          {places.map((p, i) => (
            <tr
              key={i}
              className={i === 0 ? 'top' : ''}
              onClick={() => onSelectPlace({ name: p.name, lat: p.lat, lon: p.lon })}
            >
              <td>{p.name}</td>
              <td>{p.elevationM.toFixed(0)}</td>
              <td>{p.avgTempC.toFixed(1)}</td>
              <td>{p.avgPrecipMm.toFixed(1)}</td>
              <td>{p.avgWindMs.toFixed(1)}</td>
              <td>{p.score.toFixed(1)}</td>
            </tr>
          ))}
        </tbody>
      </table>

      {!isTrail && (
        <div className="trails">
          <p className="trails-title">{t('🥾 Merkede turer i området')}</p>
          {result.trails?.length > 0 ? (
            <ul>
              {result.trails.map((trail, i) => (
                <li key={i}>
                  {trail.name}
                  {trail.operator && <span className="muted"> · {trail.operator}</span>}
                </li>
              ))}
            </ul>
          ) : (
            <p className="muted">{t('Fant ingen merkede turer (OpenStreetMap) i nærheten.')}</p>
          )}
        </div>
      )}
    </div>
  )
}

function VaersokView({ onPlan }) {
  const { t } = useI18n()
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [weights, setWeights] = useState({
    temp: 'middels', rain: 'middels', wind: 'middels', elevation: 'middels',
  })
  const [detail, setDetail] = useState(null) // valgt sted -> detaljside
  const endRef = useRef(null)

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, loading])

  function setWeight(key, value) {
    setWeights((w) => ({ ...w, [key]: value }))
  }

  async function ask(e) {
    e.preventDefault()
    const query = input.trim()
    if (!query || loading) return

    setMessages((m) => [...m, { role: 'user', text: query }])
    setInput('')
    setLoading(true)

    try {
      const params = new URLSearchParams({ q: query, ...weights })
      const res = await fetch(apiUrl(`/api/turvaer?${params}`))
      if (!res.ok) throw new Error(await readError(res))
      const result = await res.json()
      setMessages((m) => [...m, { role: 'bot', result }])
    } catch (err) {
      setMessages((m) => [...m, { role: 'bot', error: err.message }])
    } finally {
      setLoading(false)
    }
  }

  return (
    <>
      <section className="weights" aria-label="Vekting av faktorer">
        <span className="weights-title">{t('Hva betyr mest for deg?')}</span>
        <div className="weights-grid">
          {FACTORS.map((f) => (
            <label key={f.key} className="weight">
              <span className="weight-label">{f.icon} {t(f.label)}</span>
              <select value={weights[f.key]} onChange={(e) => setWeight(f.key, e.target.value)}>
                {LEVELS.map((l) => (
                  <option key={l.v} value={l.v}>{t(l.t)}</option>
                ))}
              </select>
            </label>
          ))}
        </div>
      </section>

      {detail ? (
        <PlaceDetail place={detail} onBack={() => setDetail(null)} onPlan={onPlan} />
      ) : (
        <>
          <div className="chat">
            {messages.length === 0 && (
              <p className="hint">{t('Prøv: «hvor er det finest fjellvær i Møre og Romsdal i helga»')}</p>
            )}
            {messages.map((m, i) =>
              m.role === 'user' ? (
                <div key={i} className="msg user">{m.text}</div>
              ) : (
                <div key={i} className="msg bot">
                  {m.error
                    ? <p className="error">{t('Beklager –')} {m.error}</p>
                    : <Answer result={m.result} onSelectPlace={setDetail} onPlan={onPlan} />}
                </div>
              )
            )}
            {loading && (
              <div className="msg bot">
                <p className="muted typing">{t('Henter vær')} <span>·</span><span>·</span><span>·</span></p>
              </div>
            )}
            <div ref={endRef} />
          </div>

          <form className="composer" onSubmit={ask}>
            <input
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder={t('Skriv et spørsmål …')}
              autoFocus
            />
            <button type="submit" disabled={loading || !input.trim()}>{t('Send')}</button>
          </form>
        </>
      )}
    </>
  )
}

export default function App() {
  const { t, lang, setLang } = useI18n()
  const [tab, setTab] = useState('hjem')
  const [session, setSession] = useState(null)
  const [theme, setTheme] = useState(() => localStorage.getItem('theme') ?? 'light')
  // Sidemenyen har to states (som Garmin): utvidet og kollapset ikon-rail.
  const [collapsed, setCollapsed] = useState(() => localStorage.getItem('sidebar-collapsed') === '1')

  // Sted valgt i værsøket som brukeren vil planlegge tur til. Ligger her (ikke
  // i planleggeren) fordi tabbene demonteres ved bytte - staten må overleve.
  const [planTarget, setPlanTarget] = useState(null)

  function planTrip(place) {
    setPlanTarget(place)
    setTab('rute')
  }

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    try { localStorage.setItem('theme', theme) } catch { /* privat modus o.l. */ }
  }, [theme])

  useEffect(() => {
    try { localStorage.setItem('sidebar-collapsed', collapsed ? '1' : '0') } catch { /* privat modus o.l. */ }
  }, [collapsed])

  useEffect(() => {
    if (!supabase) return undefined
    supabase.auth.getSession().then(({ data }) => setSession(data.session))
    // Tøm data-cachen når brukeren endres (inn/ut-logging), så ingen ser
    // forrige brukers cachede økter/kosthold.
    let lastUser = null
    const { data: sub } = supabase.auth.onAuthStateChange((_e, s) => {
      const uid = s?.user?.id ?? null
      if (uid !== lastUser) { clearCache(); lastUser = uid }
      setSession(s)
    })
    return () => sub.subscription.unsubscribe()
  }, [])

  // Forhåndshent all fane-data ÉN gang når brukeren logger inn, så hver fane
  // tegnes umiddelbart fra cachen (getCached) i stedet for å hente ved besøk.
  // Rekkefølge: siste uke med økter FØRST (rask, liten), så resten i bakgrunnen.
  useEffect(() => {
    if (!session) return
    let alive = true
    ;(async () => {
      try {
        const headers = await authHeaders()
        if (!alive) return
        const iso = new Date().toISOString().slice(0, 10)
        const weekAgo = new Date(Date.now() - 7 * 86400000).toISOString().slice(0, 10)
        const lang = localStorage.getItem('lang') ?? 'en' // recovery-tekst genereres på språket
        // Rask først: siste ukes økter (fremsiden blir klar raskt).
        await cachedGet(`/api/treningsokter?siden=${weekAgo}`, headers).catch(() => {})
        if (!alive) return
        // Så resten i bakgrunnen (rekkefølgen er ikke kritisk).
        const rest = [
          '/api/treningsokter',
          `/api/kosthold/dag?dato=${iso}`,
          `/api/kosthold/uke?til=${iso}`,
          `/api/recovery?lang=${lang}`,
          '/api/trening/planer',
          '/api/kosthold/favoritter',
          '/api/kosthold/maaltider',
        ]
        for (const path of rest) {
          if (!alive) return
          cachedGet(path, headers).catch(() => {})
        }
      } catch { /* prefetch er best-effort */ }
    })()
    return () => { alive = false }
  }, [session])

  const mainRef = useTabTransition(tab)

  return (
    <div className="app">
      <aside className={`sidebar ${collapsed ? 'collapsed' : ''}`}>
        <button
          className="sidebar-toggle"
          title={collapsed ? t('Utvid menyen') : t('Minimer menyen')}
          aria-label={collapsed ? t('Utvid menyen') : t('Minimer menyen')}
          onClick={() => setCollapsed(!collapsed)}
        >
          {collapsed ? '›' : '‹'}
        </button>
        <div className="brand" title="Turvær">
          <span className="brand-icon">⛰️</span>
          <span className="brand-name">Turvær</span>
        </div>
        <nav className="nav" aria-label="Moduser">
          <button className={`nav-item ${tab === 'hjem' ? 'active' : ''}`} title={t('Hjem')} onClick={() => setTab('hjem')}>
            🏠 <span>{t('Hjem')}</span>
          </button>
          <button className={`nav-item ${tab === 'vaersok' ? 'active' : ''}`} title={t('Værsøk')} onClick={() => setTab('vaersok')}>
            🔎 <span>{t('Værsøk')}</span>
          </button>
          <button className={`nav-item ${tab === 'rute' ? 'active' : ''}`} title={t('Ruteplanlegger')} onClick={() => setTab('rute')}>
            🧭 <span>{t('Ruteplanlegger')}</span>
          </button>
          <button className={`nav-item ${tab === 'trening' ? 'active' : ''}`} title={t('Trening')} onClick={() => setTab('trening')}>
            🏋️ <span>{t('Trening')}</span>
          </button>
          <button className={`nav-item ${tab === 'kosthold' ? 'active' : ''}`} title={t('Kosthold')} onClick={() => setTab('kosthold')}>
            🥗 <span>{t('Kosthold')}</span>
          </button>
          <button className={`nav-item ${tab === 'restitusjon' ? 'active' : ''}`} title={t('Restitusjon')} onClick={() => setTab('restitusjon')}>
            🧘 <span>{t('Restitusjon')}</span>
          </button>
        </nav>
        <button
          className="nav-item account-btn"
          onClick={() => setLang(lang === 'nb' ? 'en' : 'nb')}
          title={lang === 'nb' ? 'Switch to English' : 'Bytt til norsk'}
        >
          🌐 <span>{lang === 'nb' ? t('English') : t('Norsk')}</span>
        </button>
        <button
          className="nav-item account-btn"
          onClick={() => setTheme(theme === 'light' ? 'dark' : 'light')}
          title={t('Bytt mellom lys og mørk modus')}
        >
          {theme === 'light' ? '🌙' : '☀️'} <span>{theme === 'light' ? t('Mørk modus') : t('Lys modus')}</span>
        </button>
        <button
          className={`nav-item ${tab === 'konto' ? 'active' : ''}`}
          onClick={() => setTab('konto')}
        >
          👤 <span>{session ? t('Min konto') : t('Logg inn')}</span>
        </button>
      </aside>

      <main className={`main ${tab === 'rute' ? 'wide' : ''}`} ref={mainRef}>
        {tab === 'hjem' && <Dashboard session={session} onNavigate={setTab} />}
        {tab === 'vaersok' && <VaersokView onPlan={planTrip} />}
        {tab === 'rute' && (
          <RoutePlanner
            session={session}
            target={planTarget}
            onClearTarget={() => setPlanTarget(null)}
          />
        )}
        {tab === 'trening' && <TrainingView session={session} />}
        {tab === 'kosthold' && <NutritionView session={session} />}
        {tab === 'restitusjon' && <RecoveryView session={session} />}
        {tab === 'konto' && <AuthView session={session} />}
      </main>
    </div>
  )
}
