import { useEffect, useState } from 'react'
import { apiUrl, readError } from './api'
import { authHeaders } from './supabase'

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

const today = () => new Date().toISOString().slice(0, 10)

export default function MealDiary({ session }) {
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

  // Debounced søk – venter til brukeren slutter å skrive.
  useEffect(() => {
    if (query.trim().length < 2) { setResults([]); return undefined }
    const t = setTimeout(async () => {
      try {
        const params = new URLSearchParams({ sok: query.trim() })
        const res = await fetch(apiUrl(`/api/kosthold/matvarer?${params}`), { headers: await authHeaders() })
        if (res.ok) setResults(await res.json())
      } catch { /* nettverksglipp – behold forrige liste */ }
    }, 300)
    return () => clearTimeout(t)
  }, [query])

  useEffect(() => { loadDay() }, [date]) // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { loadWeek() }, [])    // eslint-disable-line react-hooks/exhaustive-deps

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
    return <p className="muted" data-reveal>Logg inn for å føre kostholdsdagbok.</p>
  }

  const lows = week.filter((n) => n.advice)

  return (
    <div className="diary" data-reveal>
      <div className="diary-controls">
        <label>Dag
          <input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
        </label>
        <label>Måltid
          <select value={meal} onChange={(e) => setMeal(e.target.value)}>
            {MEALS.map((m) => <option key={m.v} value={m.v}>{m.t}</option>)}
          </select>
        </label>
      </div>

      <input
        className="diary-search"
        placeholder="Søk i Matvaretabellen … (f.eks. havregryn)"
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
            <label>Mengde
              <input type="number" min="0" step="any" value={amount}
                     onChange={(e) => setAmount(e.target.value)} />
            </label>
            <label>Enhet
              <select value={portion}
                      onChange={(e) => setPortion(e.target.value === 'g' ? 'g' : Number(e.target.value))}>
                <option value="g">gram</option>
                {picked.portions.map((p, i) => (
                  <option key={i} value={i}>{p.name} ({p.grams.toFixed(0)} g)</option>
                ))}
              </select>
            </label>
            <span className="muted">= {grams.toFixed(0)} g · ~{previewKcal.toFixed(0)} kcal</span>
            <button className="primary" onClick={add} disabled={grams <= 0}>Legg til</button>
          </div>
        </div>
      )}

      {error && <p className="error">Beklager – {error}</p>}

      {day && (
        <>
          <p className="diary-totals">
            <strong>{day.kcal.toFixed(0)} kcal</strong>
            {' '}· {day.proteinG.toFixed(0)} g protein · {day.carbG.toFixed(0)} g karbo · {day.fatG.toFixed(0)} g fett
          </p>
          {MEALS.filter((m) => day.entries.some((e) => e.meal === m.v)).map((m) => (
            <div className="diary-meal" key={m.v}>
              <p className="trails-title">{m.t}</p>
              <ul>
                {day.entries.filter((e) => e.meal === m.v).map((e) => (
                  <li key={e.id}>
                    {e.foodName} <span className="muted">
                      {e.grams.toFixed(0)} g · {e.kcal.toFixed(0)} kcal
                    </span>
                    <button className="del" onClick={() => remove(e.id)} aria-label="Slett">✕</button>
                  </li>
                ))}
              </ul>
            </div>
          ))}
          {day.entries.length === 0 && (
            <p className="muted">Ingenting logget denne dagen ennå.</p>
          )}
        </>
      )}

      {week.length > 0 && (
        <div className="week-panel">
          <button className="linklike week-toggle" onClick={() => setShowWeek(!showWeek)}>
            🧪 Ukas vitaminer og mineraler {lows.length > 0 ? `– ${lows.length} å se på` : '– ser bra ut'} {showWeek ? '▾' : '▸'}
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

      <p className="muted source-note">Kilde: Matvaretabellen, Mattilsynet (matvaretabellen.no)</p>
    </div>
  )
}
