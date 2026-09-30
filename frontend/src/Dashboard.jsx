import { useState, useEffect } from 'react'
import { BodyweightCard, BodyweightSheet } from './Bodyweight.jsx'
import LifetimeStats from './LifetimeStats.jsx'
import DashboardCustomizer from './DashboardCustomizer.jsx'
import StreakCard from './StreakCard.jsx'
import { loadStreakSettings, storeStreakSettings, hasStoredStreakSettings, pullStreakSettings, pushStreakSettings } from './streakSettings.js'
import { WIDGET_BY_ID, loadLayout, storeLayout, mergeLayout, hasStoredLayout } from './dashboardLayout.js'
import { localIso } from './trainingStats.js'
import { authHeaders } from './supabase'
import { cachedGet, getCached, apiUrl, readError } from './api'
import { useReveal, useCountUp } from './anim'
import { useI18n } from './i18n.jsx'

const ICONS = { STYRKE: '🏋️', LØPING: '🏃', SVØMMING: '🏊', SYKKEL: '🚴', BULDRING: '🧗', HIKING: '🥾', KAMPSPORT: '🥋', FRISTIL: '✨' }
const DAY_LETTERS = { nb: ['M', 'T', 'O', 'T', 'F', 'L', 'S'], en: ['M', 'T', 'W', 'T', 'F', 'S', 'S'] } // mandag først

function startOfWeek() {
  const d = new Date()
  const day = (d.getDay() + 6) % 7 // mandag = 0
  d.setHours(0, 0, 0, 0)
  d.setDate(d.getDate() - day)
  return d
}

const isoToday = () => localIso(new Date())

/**
 * Hjem-dashboard i Garmin Connect-stil: «I fokus»-kort i et grid til venstre
 * (ukas trening med stolper per dag, kosthold, siste økt) og en «I dag»-kolonne
 * til høyre med dagens aktivitet og snarveier.
 */
export default function Dashboard({ session, onNavigate }) {
  const { t, lang } = useI18n()
  const weekAgo = localIso(new Date(Date.now() - 7 * 86400000))
  // Hydrer fra cachen så fanen tegnes med forrige data straks (ingen tomt glimt).
  // Foretrekk full liste hvis den finnes, ellers siste-uke-slicen (forhåndshentet).
  const [workouts, setWorkouts] = useState(() =>
    getCached('/api/treningsokter') ?? getCached(`/api/treningsokter?siden=${weekAgo}`) ?? [])
  const [day, setDay] = useState(() => getCached(`/api/kosthold/dag?dato=${isoToday()}&lang=${lang}`) ?? null)
  const [lows, setLows] = useState(() => (getCached(`/api/kosthold/uke?til=${isoToday()}&lang=${lang}`) ?? []).filter((n) => n.advice))
  const [weighIns, setWeighIns] = useState(() => getCached('/api/kropp/vekt') ?? [])
  const [showWeight, setShowWeight] = useState(false)
  const [layout, setLayout] = useState(() => loadLayout(session?.user?.id ?? 'anon'))
  const [customizing, setCustomizing] = useState(false)
  const [streakSettings, setStreakSettings] = useState(() => loadStreakSettings(session?.user?.id ?? 'anon'))

  // Ukeseriens mål og pauser følger kontoen (samme mønster som dashboard-oppsettet): lokal kopi først, server er fasit.
  async function saveStreakSettings(next) {
    const uid = session?.user?.id ?? 'anon'
    setStreakSettings(next)
    storeStreakSettings(uid, next)
    if (!session) return
    try {
      const saved = await pushStreakSettings(next, await authHeaders())
      setStreakSettings(saved)
      storeStreakSettings(uid, saved)
    } catch { /* offline: lokal kopi er lagret, neste endring prøver igjen */ }
  }

  async function syncStreakSettings() {
    try {
      const uid = session.user.id
      const remote = await pullStreakSettings(await authHeaders())
      if (remote) {
        setStreakSettings(remote)
        storeStreakSettings(uid, remote)
      } else if (hasStoredStreakSettings(uid)) {
        pushStreakSettings(loadStreakSettings(uid), await authHeaders()).catch(() => {}) // engangs: løft lokalt oppsett opp til kontoen
      }
    } catch { /* sekundært */ }
  }

  function saveLayout(next) {
    setLayout(next)
    storeLayout(session?.user?.id ?? 'anon', next)
    pushLayout(next)
  }

  // Oppsettet følger kontoen: server er fasit, lokal kopi gir rask oppstart og fungerer offline.
  async function pushLayout(next) {
    if (!session) return
    try {
      await fetch(apiUrl('/api/innstillinger/dashboard'), {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json', ...(await authHeaders()) },
        body: JSON.stringify(next),
      })
    } catch { /* offline: lokal kopi er lagret, neste lagring prøver igjen */ }
  }

  async function syncLayout() {
    try {
      const uid = session.user.id
      const res = await fetch(apiUrl('/api/innstillinger/dashboard'), { headers: await authHeaders() })
      if (res.status === 200) {
        const merged = mergeLayout(await res.json())
        setLayout(merged)
        storeLayout(uid, merged)
      } else if (res.status === 204 && hasStoredLayout(uid)) {
        pushLayout(loadLayout(uid)) // engangs: løft eksisterende lokalt oppsett opp til kontoen
      }
    } catch { /* sekundært */ }
  }

  useEffect(() => {
    if (!session) { setWorkouts([]); setDay(null); setLows([]); setWeighIns([]); return }
    loadWorkouts()
    loadNutrition()
    loadWeighIns()
    syncLayout()
    syncStreakSettings()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session, lang]) // lang: rådene fra backend kommer på valgt språk, så de hentes på nytt ved språkbytte

  async function loadWorkouts() {
    const headers = await authHeaders()
    // Rask: siste ukes økter først (ukesoppsummering + siste økt tegnes straks),
    // hopp over hvis full liste alt er hentet (prefetch/tidligere besøk).
    if (!getCached('/api/treningsokter')) {
      try { setWorkouts(await cachedGet(`/api/treningsokter?siden=${weekAgo}`, headers)) } catch { /* behold cachet */ }
    }
    // Så hele historikken (fyller feed + totalen).
    try { setWorkouts(await cachedGet('/api/treningsokter', headers)) } catch { /* behold cachet */ }
  }

  async function loadWeighIns() {
    try { setWeighIns(await cachedGet('/api/kropp/vekt', await authHeaders())) } catch { /* behold cachet */ }
  }

  async function logWeighIn(entry) {
    const res = await fetch(apiUrl('/api/kropp/vekt'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...(await authHeaders()) },
      body: JSON.stringify(entry),
    })
    if (!res.ok) throw new Error(await readError(res))
    await loadWeighIns()
  }

  async function deleteWeighIn(id) {
    try {
      await fetch(apiUrl(`/api/kropp/vekt/${id}`), { method: 'DELETE', headers: await authHeaders() })
      await loadWeighIns()
    } catch { /* ignorer */ }
  }

  async function loadNutrition() {
    const iso = isoToday()
    const headers = await authHeaders()
    try {
      const [dag, uke] = await Promise.all([
        cachedGet(`/api/kosthold/dag?dato=${iso}&lang=${lang}`, headers),
        cachedGet(`/api/kosthold/uke?til=${iso}&lang=${lang}`, headers),
      ])
      setDay(dag)
      setLows(uke.filter((n) => n.advice))
    } catch { /* hjem er sekundært – behold cachet */ }
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
        <h2 className="hero-title" data-reveal>{t('Finn eventyret.')}<br /><span className="grad">{t('Følg formen.')}</span></h2>
        <p className="muted hero-sub" data-reveal>
          {t('Finn finest turvær, planlegg ruter, og før treningsdagbok – på ett sted.')}
        </p>
        <div className="dash-actions" data-reveal>
          <button className="primary" onClick={() => onNavigate('konto')}>{t('Logg inn')}</button>
          <button onClick={() => onNavigate('vaersok')}>{t('Finn turvær')}</button>
        </div>
      </div>
    )
  }

  const widgets = {
    week: (
      <div className="focus-card" data-reveal>
        <span className="focus-head">{t('🏋️ Trening denne uka')}</span>
        <div className="focus-main">
          <span className="focus-big" ref={weekRef}>{weekCount}</span>
          <span className="focus-sub muted">{t('økter')} · {workouts.length} {t('totalt')}</span>
        </div>
        <div className="week-bars" aria-label="Økter per ukedag">
          {dayCounts.map((n, i) => (
            <span className={`week-day ${i === todayIdx ? 'today' : ''}`} key={i}>
              <span className="week-bar">
                <span style={{ height: `${(n / maxCount) * 100}%` }} />
              </span>
              <span className="week-letter">{(DAY_LETTERS[lang] ?? DAY_LETTERS.en)[i]}</span>
            </span>
          ))}
        </div>
      </div>
    ),
    streak: <StreakCard workouts={workouts} settings={streakSettings} today={isoToday()} onChange={saveStreakSettings} />,
    nutrition: (
      <button className="focus-card clickable" data-reveal onClick={() => onNavigate('kosthold')}>
        <span className="focus-head">{t('🥗 Kosthold i dag')}</span>
        {day == null || (day.entries.length === 0 && lows.length === 0) ? (
          <p className="muted">{t('Ingenting logget ennå – begynn kostholdsdagboka her →')}</p>
        ) : (
          <>
            <div className="focus-main">
              <span className="focus-big">{day.kcal.toFixed(0)}</span>
              <span className="focus-sub muted">
                kcal
                {day.burnedKcal > 0 && <> · {t('trening')} −{day.burnedKcal}</>}
              </span>
            </div>
            {day.remainingKcal != null && (
              <p className={`focus-line ${day.remainingKcal < 0 ? 'over-budget' : ''}`}>
                {day.remainingKcal.toFixed(0)} {t('kcal igjen av målet')}
              </p>
            )}
            {lows.length > 0 && (
              <p className="focus-line muted">
                {t('🧪 Lavt denne uka:')} {lows.map((n) => n.name).slice(0, 3).join(', ')}
                {lows.length > 3 ? ` +${lows.length - 3}` : ''} →
              </p>
            )}
          </>
        )}
      </button>
    ),
    latest: (
      <div className="focus-card" data-reveal>
        <span className="focus-head">{t('⏱️ Siste økt')}</span>
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
          <p className="muted">{t('Ingen økter ennå – logg din første under «Trening».')}</p>
        )}
      </div>
    ),
    weather: (
      <div className="focus-card" data-reveal>
        <span className="focus-head">{t('🌤️ Turvær')}</span>
        <p className="focus-line">{t('Hvor er det finest i helga?')}</p>
        <p className="focus-line muted">{t('Spør værsøket – rangerer topper og turruter etter vær.')}</p>
        <button className="mini" onClick={() => onNavigate('vaersok')}>{t('Finn turvær →')}</button>
      </div>
    ),
    today: (
      <aside className="today-col">
        <h3 className="detail-h3">{t('I dag')}</h3>
        {todays.length === 0 ? (
          <div className="today-card muted">{t('Ingen aktivitet logget i dag ennå.')}</div>
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
          <button className="primary" onClick={() => onNavigate('trening')}>{t('Logg økt')}</button>
          <button onClick={() => onNavigate('rute')}>{t('Planlegg rute')}</button>
          <button onClick={() => onNavigate('vaersok')}>{t('Finn turvær')}</button>
        </div>
      </aside>
    ),
    bodyweight: <BodyweightCard entries={weighIns} onOpen={() => setShowWeight(true)} onLog={logWeighIn} />,
    lifetime: <LifetimeStats workouts={workouts} />,
    recent: (
      <>
      <h3 className="detail-h3" data-reveal>{t('Nylig aktivitet')}</h3>
      {workouts.length === 0 ? (
        <p className="muted" data-reveal>{t('Ingen økter ennå – logg din første under «Trening».')}</p>
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
      </>
    ),
  }

  return (
    <div className="dash garmin" ref={revealRef}>
      <div className="dash-head">
        <h2 className="detail-title" data-reveal>{t('I fokus')}</h2>
        <button className="dash-customize" onClick={() => setCustomizing(true)}>✎ {t('Tilpass')}</button>
      </div>

      {layout.every((w) => !w.on) && (
        <button className="plan-empty" onClick={() => setCustomizing(true)}>{t('Dashboardet er tomt – velg hva du vil se')}</button>
      )}

      <div className="dash-widgets">
        {layout.filter((w) => w.on).map((w) => (
          <div key={w.id} className={`dash-widget ${WIDGET_BY_ID[w.id].wide ? 'wide' : ''}`}>{widgets[w.id]}</div>
        ))}
      </div>

      {showWeight && (
        <BodyweightSheet entries={weighIns} onLog={logWeighIn} onDelete={deleteWeighIn} onClose={() => setShowWeight(false)} />
      )}
      {customizing && <DashboardCustomizer layout={layout} onChange={saveLayout} onClose={() => setCustomizing(false)} />}
    </div>
  )
}
