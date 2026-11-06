import { useState, useRef, useEffect, lazy, Suspense } from 'react'
import { supabase } from './supabase'
import ChoiceChips from './ChoiceChips.jsx'
import AppFooter from './AppFooter.jsx'
import { apiUrl, readError, switchCacheOwner, onSlowRequest } from './api'
import { useI18n } from './i18n.jsx'
import { useReveal } from './anim'
import WeatherScene from './WeatherScene.jsx'
import TerrainMasthead from './TerrainMasthead.jsx'
import AppShell from './AppShell.jsx'
import { DESTINATIONS } from './navigation.js'
import './App.css'
import './interfaceMotion.css'
import './mobileRedesign.css'
import './terrainTheme.css'

// Kodedeling: de tunge fanene (og Leaflet-kartene) lastes on-demand som egne
// chunks, så første innlasting (hjem/innlogging) blir vesentlig lettere.
const ResultMap = lazy(() => import('./ResultMap'))
const PlaceDetail = lazy(() => import('./PlaceDetail'))
const RoutePlanner = lazy(() => import('./RoutePlanner'))
const TrainingView = lazy(() => import('./TrainingView'))
const NutritionView = lazy(() => import('./NutritionView'))
const RecoveryView = lazy(() => import('./RecoveryView'))
const Dashboard = lazy(() => import('./Dashboard'))
const AuthView = lazy(() => import('./AuthView'))
const PrivacyView = lazy(() => import('./PrivacyView'))

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
  const revealRef = useReveal([result])
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
    <div className="answer" ref={revealRef}>
      {interp}
      <p className="winner" data-reveal>
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
      <Suspense fallback={<div className="map lazy-map" />}>
        <ResultMap places={places.map(placeForMap)} trails={result.trails ?? []} />
      </Suspense>
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
              data-reveal
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
            <p className="muted">{t('Fant ingen merkede turer i nærheten.')}</p>
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
    if (messages.length === 0) return
    endRef.current?.scrollIntoView({ behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth' })
  }, [messages, loading])

  function setWeight(key, value) {
    setWeights((w) => ({ ...w, [key]: value }))
  }

  async function ask(e, preset) {
    if (e && e.preventDefault) e.preventDefault()
    const query = (typeof preset === 'string' ? preset : input).trim()
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
      <TerrainMasthead title={t('Værsøk')} description={t('Sjekk været der du vil gå, eller finn en tur med bedre forhold.')} />
      {detail ? (
        <Suspense fallback={<p className="lazy-fallback muted">…</p>}>
          <PlaceDetail place={detail} onBack={() => setDetail(null)} onPlan={onPlan} />
        </Suspense>
      ) : (
        <>
          {messages.length > 0 && <div className="chat">
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
              <div className="msg bot weather-loading">
                <WeatherScene busy compact />
                <p className="muted typing" role="status">{t('Henter vær')} <span>·</span><span>·</span><span>·</span></p>
              </div>
            )}
            <div ref={endRef} />
          </div>}

          <form className="composer" onSubmit={ask}>
            <input
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder={t('Skriv et spørsmål …')}
              aria-label={t('Skriv et spørsmål …')}
            />
            <button type="submit" disabled={loading || !input.trim()}>{t('Send')}</button>
          </form>
          {messages.length === 0 && (
            <div className="weather-examples">
              <p>{t('Prøv et søk')}</p>
              <div className="ai-examples chat-examples">
                {[
                  t('Finest fjellvær i Rogaland i helga'),
                  t('Hvordan blir været i Oslo i morgen'),
                  t('Beste turvær på Sunnmøre neste uke'),
                ].map((example) => (
                  <button key={example} className="ai-example" onClick={() => ask(null, example)}>{example}</button>
                ))}
              </div>
            </div>
          )}
          <details className="weights weather-preferences">
            <summary className="weights-title">{t('Hva betyr mest for deg?')}</summary>
            <div className="weights-grid">
              {FACTORS.map((f) => (
                <div key={f.key} className="weight">
                  <span className="weight-label">{f.icon} {t(f.label)}</span>
                  <ChoiceChips label={t(f.label)} value={weights[f.key]} onChange={(v) => setWeight(f.key, v)}
                               options={LEVELS.map((l) => ({ v: l.v, t: t(l.t) }))} />
                </div>
              ))}
            </div>
          </details>
        </>
      )}
    </>
  )
}

// Alt unntatt værsøket krever innlogging (auth: true).
const NAV = DESTINATIONS

export default function App() {
  const { t } = useI18n()
  // `/#personvern` åpner personvernerklæringen direkte: App Store Connect krever en offentlig URL til den.
  const [tab, setTab] = useState(() => (
    location.hash === '#personvern' ? 'personvern' : supabase ? 'hjem' : 'vaersok'))
  const [session, setSession] = useState(null)
  // Til getSession har svart vet vi ikke om brukeren er innlogget - unngår
  // at innloggingsveggen blinker forbi ved oppstart.
  const [authReady, setAuthReady] = useState(!supabase)
  // Gratis hosting sover; første kall kan ta ~1 minutt. Si fra i stedet for å la appen se ødelagt ut.
  const [slow, setSlow] = useState(false)
  // Engangsmelding etter kontosletting (brukeren logges ut samtidig, så meldingen må leve i App).
  const [notice, setNotice] = useState(null)
  useEffect(() => onSlowRequest(setSlow), [])

  function go(id) {
    setTab(id)
  }
  const [theme, setTheme] = useState(() => {
    try { return localStorage.getItem('terrain-theme') === 'light' ? 'light' : 'dark' }
    catch { return 'dark' }
  })
  // Sted valgt i værsøket som brukeren vil planlegge tur til. Ligger her (ikke
  // i planleggeren) fordi tabbene demonteres ved bytte - staten må overleve.
  const [planTarget, setPlanTarget] = useState(null)

  function planTrip(place) {
    setPlanTarget(place)
    setTab('rute')
  }

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', theme === 'dark' ? '#101410' : '#f4f5f1')
    try { localStorage.setItem('terrain-theme', theme) } catch { /* privat modus o.l. */ }
  }, [theme])

  useEffect(() => {
    if (!supabase) return undefined
    supabase.auth.getSession().then(({ data }) => {
      setSession(data.session)
      if (!data.session) setTab('vaersok')
      setAuthReady(true)
    })
    // Tøm data-cachen når brukeren endres (inn/ut-logging), så ingen ser
    // forrige brukers cachede økter/kosthold.
    let lastUser = null
    const { data: sub } = supabase.auth.onAuthStateChange((_e, s) => {
      const uid = s?.user?.id ?? null
      if (uid !== lastUser) { switchCacheOwner(uid); lastUser = uid }
      setSession(s)
    })
    return () => sub.subscription.unsubscribe()
  }, [])

  const current = NAV.find((n) => n.id === tab)
  const locked = authReady && !session && current?.auth

  return (
    <AppShell tab={tab} onNavigate={go} session={session} theme={theme}
      onToggleTheme={() => setTheme(theme === 'light' ? 'dark' : 'light')}
      slow={slow} wide={tab === 'rute' && !locked}>
      {notice && <div className="wake-banner" role="status">{notice} <button className="link-btn" onClick={() => setNotice(null)}>{t('Lukk')}</button></div>}
        <Suspense fallback={<p className="lazy-fallback muted">{t('Laster …')}</p>}>
          {!authReady ? (
            <p className="lazy-fallback muted">{t('Laster …')}</p>
          ) : locked ? (
            <AuthView session={null} onOpenPrivacy={() => go('personvern')}
                      reason={t('Logg inn for å bruke {feature}. Værsøket er åpent for alle.', { feature: t(current.label) })} />
          ) : (<>
          {tab === 'hjem' && <Dashboard session={session} onNavigate={go} />}
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
          {tab === 'konto' && (
            <AuthView session={session} onOpenPrivacy={() => go('personvern')}
                      onDeleted={(r) => setNotice(r.loginRemoved
                        ? t('Kontoen din er slettet.')
                        : t('Dataene dine er slettet, men innloggingen (e-postadressen) ble ikke fjernet automatisk. Kontakt oss for å få den fjernet.'))} />
          )}
          {tab === 'personvern' && <PrivacyView onBack={() => go(session ? 'konto' : 'vaersok')} />}
          </>)}
        </Suspense>
        <AppFooter onOpenPrivacy={() => go('personvern')} />
    </AppShell>
  )
}
