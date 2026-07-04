import { useEffect, useState } from 'react'
import { apiUrl, readError } from './api'
import { authHeaders } from './supabase'
import { useI18n } from './i18n.jsx'

/**
 * Kostholdsdagboka: søk i Matvaretabellen, velg porsjon og mengde, og logg på
 * dagens måltider. Viser dagens totaler (kcal/makroer) og ukas vitamin-/
 * mineralstatus med råd der inntaket er lavt.
 *
 * Data: Matvaretabellen (Mattilsynet) via backend – kilden vises nederst.
 */

const MEALS = [
  { v: 'FROKOST', t: 'Frokost' },
  { v: 'LUNSJ', t: 'Lunsj' },
  { v: 'MIDDAG', t: 'Middag' },
  { v: 'KVELDS', t: 'Kvelds' },
  { v: 'MELLOM', t: 'Mellommåltid' },
]

const ACTIVITY = [
  { v: 'ROLIG', t: 'Rolig (stillesittende)' },
  { v: 'LETT', t: 'Lett aktiv' },
  { v: 'MODERAT', t: 'Moderat aktiv' },
  { v: 'HØY', t: 'Svært aktiv' },
]

const PACE = [
  { v: -1, t: 'Ned 1 kg/uke' },
  { v: -0.5, t: 'Ned 0,5 kg/uke' },
  { v: -0.25, t: 'Ned 0,25 kg/uke' },
  { v: 0, t: 'Holde vekta' },
  { v: 0.25, t: 'Opp 0,25 kg/uke' },
  { v: 0.5, t: 'Opp 0,5 kg/uke' },
]

const today = () => new Date().toISOString().slice(0, 10)

export default function MealDiary({ session }) {
  const { t } = useI18n()
  const [date, setDate] = useState(today)
  const [meal, setMeal] = useState('FROKOST')

  const [query, setQuery] = useState('')
  const [results, setResults] = useState([])
  const [picked, setPicked] = useState(null)   // valgt matvare fra søket
  const [portion, setPortion] = useState('g')  // 'g' eller index i picked.portions
  const [amount, setAmount] = useState(100)

  const [day, setDay] = useState(null)
  const [week, setWeek] = useState([])
  const [showWeek, setShowWeek] = useState(false)
  const [error, setError] = useState(null)

  const [goal, setGoal] = useState(null)       // lagret profil + utregnet mål
  const [showGoal, setShowGoal] = useState(false)
  const [goalForm, setGoalForm] = useState({
    weightKg: '', heightCm: '', age: '', sex: 'M', activityLevel: 'MODERAT', goalKgPerWeek: 0,
  })

  // Debounced søk – venter til brukeren slutter å skrive.
  useEffect(() => {
    if (query.trim().length < 2) { setResults([]); return undefined }
    const t = setTimeout(async () => {
      try {
        const params = new URLSearchParams({ sok: query.trim() })
        const res = await fetch(apiUrl(`/api/kosthold/matvarer?${params}`), { headers: await authHeaders() })
        if (res.ok) {
          setError(null)
          setResults(await res.json())
        } else {
          // Ikke svelg feilen - et søk som «bare ikke virker» er umulig å forstå.
          setResults([])
          setError(await readError(res))
        }
      } catch { /* nettverksglipp – behold forrige liste */ }
    }, 300)
    return () => clearTimeout(t)
  }, [query])

  useEffect(() => { loadDay() }, [date]) // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { loadWeek(); loadGoal() }, []) // eslint-disable-line react-hooks/exhaustive-deps

  async function loadDay() {
    try {
      const res = await fetch(apiUrl(`/api/kosthold/dag?dato=${date}`), { headers: await authHeaders() })
      if (res.ok) setDay(await res.json())
    } catch { /* vis bare tom dag */ }
  }

  async function loadWeek() {
    try {
      const res = await fetch(apiUrl(`/api/kosthold/uke?til=${today()}`), { headers: await authHeaders() })
      if (res.ok) setWeek(await res.json())
    } catch { /* panelet er sekundært */ }
  }

  async function loadGoal() {
    try {
      const res = await fetch(apiUrl('/api/kosthold/maal'), { headers: await authHeaders() })
      if (res.ok) {
        const g = await res.json()
        setGoal(g)
        setGoalForm({ weightKg: g.weightKg, heightCm: g.heightCm, age: g.age, sex: g.sex, activityLevel: g.activityLevel, goalKgPerWeek: g.goalKgPerWeek })
      }
      // 404 = ikke satt opp ennå - da viser vi bare «sett opp mål»-knappen.
    } catch { /* sekundært */ }
  }

  async function saveGoal() {
    setError(null)
    try {
      const res = await fetch(apiUrl('/api/kosthold/maal'), {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json', ...(await authHeaders()) },
        body: JSON.stringify({
          weightKg: Number(goalForm.weightKg),
          heightCm: Number(goalForm.heightCm),
          age: Number(goalForm.age),
          sex: goalForm.sex,
          activityLevel: goalForm.activityLevel,
          goalKgPerWeek: Number(goalForm.goalKgPerWeek),
        }),
      })
      if (!res.ok) throw new Error(await readError(res))
      setGoal(await res.json())
      setShowGoal(false)
      loadDay() // dagsbalansen avhenger av målet
    } catch (e) {
      setError(e.message)
    }
  }

  function pick(food) {
    setPicked(food)
    // Har varen porsjoner (glass, skive …), foreslå den første – ellers gram.
    if (food.portions.length > 0) { setPortion(0); setAmount(1) }
    else { setPortion('g'); setAmount(100) }
  }

  // Mengde -> gram: enten direkte, eller antall porsjoner * porsjonsvekt.
  const grams = picked == null ? 0
    : portion === 'g' ? Number(amount) || 0
    : (Number(amount) || 0) * (picked.portions[portion]?.grams ?? 0)

  const previewKcal = picked ? (picked.kcalPer100g * grams) / 100 : 0

  async function add() {
    if (!picked || grams <= 0) return
    setError(null)
    try {
      const res = await fetch(apiUrl('/api/kosthold/logg'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...(await authHeaders()) },
        body: JSON.stringify({ date, meal, foodId: picked.foodId, grams }),
      })
      if (!res.ok) throw new Error(await readError(res))
      setPicked(null)
      setQuery('')
      setResults([])
      loadDay()
      loadWeek()
    } catch (e) {
      setError(e.message)
    }
  }

  async function remove(id) {
    try {
      await fetch(apiUrl(`/api/kosthold/logg/${id}`), { method: 'DELETE', headers: await authHeaders() })
      loadDay()
      loadWeek()
    } catch { /* ignorer */ }
  }

  if (!session) {
    return <p className="muted" data-reveal>{t('Logg inn for å føre kostholdsdagbok.')}</p>
  }

  const lows = week.filter((n) => n.advice)

  return (
    <div className="diary" data-reveal>
      <div className="diary-controls">
        <label>{t('Dag')}
          <input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
        </label>
        <label>{t('Måltid')}
          <select value={meal} onChange={(e) => setMeal(e.target.value)}>
            {MEALS.map((m) => <option key={m.v} value={m.v}>{t(m.t)}</option>)}
          </select>
        </label>
        <button className="linklike goal-toggle" onClick={() => setShowGoal(!showGoal)}>
          🎯 {goal ? t('Mål: {n} kcal/dag', { n: goal.dailyTargetKcal.toFixed(0) }) : t('Sett opp kalorimål')} {showGoal ? '▾' : '▸'}
        </button>
      </div>

      {showGoal && (
        <div className="goal-form">
          <label>{t('Vekt (kg)')}
            <input type="number" min="30" value={goalForm.weightKg}
                   onChange={(e) => setGoalForm({ ...goalForm, weightKg: e.target.value })} />
          </label>
          <label>{t('Høyde (cm)')}
            <input type="number" min="120" value={goalForm.heightCm}
                   onChange={(e) => setGoalForm({ ...goalForm, heightCm: e.target.value })} />
          </label>
          <label>{t('Alder')}
            <input type="number" min="15" value={goalForm.age}
                   onChange={(e) => setGoalForm({ ...goalForm, age: e.target.value })} />
          </label>
          <label>{t('Kjønn')}
            <select value={goalForm.sex} onChange={(e) => setGoalForm({ ...goalForm, sex: e.target.value })}>
              <option value="M">{t('Mann')}</option>
              <option value="K">{t('Kvinne')}</option>
            </select>
          </label>
          <label>{t('Hverdagsaktivitet')}
            <select value={goalForm.activityLevel}
                    onChange={(e) => setGoalForm({ ...goalForm, activityLevel: e.target.value })}>
              {ACTIVITY.map((a) => <option key={a.v} value={a.v}>{t(a.t)}</option>)}
            </select>
          </label>
          <label>{t('Mål')}
            <select value={goalForm.goalKgPerWeek}
                    onChange={(e) => setGoalForm({ ...goalForm, goalKgPerWeek: e.target.value })}>
              {PACE.map((p) => <option key={p.v} value={p.v}>{t(p.t)}</option>)}
            </select>
          </label>
          <button className="primary" onClick={saveGoal}
                  disabled={!goalForm.weightKg || !goalForm.heightCm || !goalForm.age}>
            {t('Lagre mål')}
          </button>
        </div>
      )}

      <input
        className="diary-search"
        placeholder={t('Søk i Matvaretabellen … (f.eks. havregryn)')}
        value={query}
        onChange={(e) => { setQuery(e.target.value); setPicked(null) }}
      />

      {!picked && results.length > 0 && (
        <ul className="food-results">
          {results.map((f) => (
            <li key={f.foodId}>
              <button className="linklike" onClick={() => pick(f)}>
                {f.name} <span className="muted">{f.kcalPer100g.toFixed(0)} kcal/100 g</span>
              </button>
            </li>
          ))}
        </ul>
      )}

      {picked && (
        <div className="diary-add">
          <p className="diary-picked"><strong>{picked.name}</strong></p>
          <div className="diary-amount">
            <label>{t('Mengde')}
              <input type="number" min="0" step="any" value={amount}
                     onChange={(e) => setAmount(e.target.value)} />
            </label>
            <label>{t('Enhet')}
              <select value={portion}
                      onChange={(e) => setPortion(e.target.value === 'g' ? 'g' : Number(e.target.value))}>
                <option value="g">{t('gram')}</option>
                {picked.portions.map((p, i) => (
                  <option key={i} value={i}>{p.name} ({p.grams.toFixed(0)} g)</option>
                ))}
              </select>
            </label>
            <span className="muted">= {grams.toFixed(0)} g · ~{previewKcal.toFixed(0)} kcal</span>
            <button className="primary" onClick={add} disabled={grams <= 0}>{t('Legg til')}</button>
          </div>
        </div>
      )}

      {error && <p className="error">{t('Beklager –')} {error}</p>}

      {day && (
        <>
          <p className="diary-totals">
            <strong>{day.kcal.toFixed(0)} kcal</strong>
            {' '}· {day.proteinG.toFixed(0)} g protein · {day.carbG.toFixed(0)} g {t('karbo')} · {day.fatG.toFixed(0)} g {t('fett')}
            {day.burnedKcal > 0 && <> · 🏋️ {t('trening')} −{day.burnedKcal} kcal</>}
          </p>
          {day.targetKcal != null && (
            <p className="diary-balance">
              {t('Mål')} {day.targetKcal.toFixed(0)} − {t('spist')} {day.kcal.toFixed(0)}
              {day.burnedKcal > 0 && <> + {t('trening')} {day.burnedKcal}</>}
              {' '}= <strong className={day.remainingKcal < 0 ? 'over-budget' : ''}>
                {day.remainingKcal.toFixed(0)} {t('kcal igjen')}
              </strong>
            </p>
          )}
          {day.recoveryTip && <p className="recovery-tip">💧 {day.recoveryTip}</p>}
          {MEALS.filter((m) => day.entries.some((e) => e.meal === m.v)).map((m) => (
            <div className="diary-meal" key={m.v}>
              <p className="trails-title">{m.t}</p>
              <ul>
                {day.entries.filter((e) => e.meal === m.v).map((e) => (
                  <li key={e.id}>
                    {e.foodName} <span className="muted">
                      {e.grams.toFixed(0)} g · {e.kcal.toFixed(0)} kcal
                    </span>
                    <button className="del" onClick={() => remove(e.id)} aria-label={t('Slett')}>✕</button>
                  </li>
                ))}
              </ul>
            </div>
          ))}
          {day.entries.length === 0 && (
            <p className="muted">{t('Ingenting logget denne dagen ennå.')}</p>
          )}
        </>
      )}

      {week.length > 0 && (
        <div className="week-panel">
          <button className="linklike week-toggle" onClick={() => setShowWeek(!showWeek)}>
            {t('🧪 Ukas vitaminer og mineraler')} {lows.length > 0 ? t('– {n} å se på', { n: lows.length }) : t('– ser bra ut')} {showWeek ? '▾' : '▸'}
          </button>
          {showWeek && (
            <div className="macro-bars">
              {week.map((n) => (
                <div key={n.nutrientId}>
                  <div className="macro-row">
                    <span className="macro-label">{n.name}</span>
                    <span className="macro-bar">
                      <span style={{
                        width: `${Math.min(100, n.percent)}%`,
                        background: n.percent < 75 ? '#fbbf24' : '#34d399',
                      }} />
                    </span>
                    <span className="macro-count">
                      {n.avgPerDay.toFixed(n.avgPerDay < 10 ? 1 : 0)}/{n.dailyTarget} {n.unit}
                    </span>
                  </div>
                  {n.advice && <p className="nutrient-advice muted">{n.advice}</p>}
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      <p className="muted source-note">{t('Kilde: Matvaretabellen, Mattilsynet (matvaretabellen.no)')}</p>
    </div>
  )
}
