import { useEffect, useState } from 'react'
import { apiUrl, readError } from './api'
import ChoiceChips from './ChoiceChips.jsx'
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

// Generiske enheter i tillegg til varens egne porsjoner (glass, skive …).
// Volum regnes som 1 ml ≈ 1 g - stemmer for vann/melk/shake, grovt for pulver.
const GENERIC_UNITS = [
  { v: 'kg', t: 'kg', g: 1000 },
  { v: 'dl', t: 'dl', g: 100, volume: true },
  { v: 'ml', t: 'ml', g: 1, volume: true },
  { v: 'ss', t: 'spiseskje (ss)', g: 15, volume: true },
  { v: 'ts', t: 'teskje (ts)', g: 5, volume: true },
]
const unitByKey = Object.fromEntries(GENERIC_UNITS.map((u) => [u.v, u]))

// De 14 allergenene EU krever merking av (kodene matcher backendens FoodFlags).
const ALLERGENS = [
  ['GLUTEN', 'gluten'], ['MELK', 'melk'], ['EGG', 'egg'], ['NOTTER', 'nøtter'],
  ['PEANOTTER', 'peanøtter'], ['FISK', 'fisk'], ['SKALLDYR', 'skalldyr'], ['BLOTDYR', 'bløtdyr'],
  ['SOYA', 'soya'], ['SELLERI', 'selleri'], ['SENNEP', 'sennep'], ['SESAM', 'sesam'],
  ['LUPIN', 'lupin'], ['SULFITT', 'sulfitt'],
]
const allergenName = Object.fromEntries(ALLERGENS)

const DIETS = [
  { v: 'ALT', t: 'Spiser alt' },
  { v: 'VEGETAR', t: 'Vegetar' },
  { v: 'PESCETAR', t: 'Pescetar (fisk, ikke kjøtt)' },
  { v: 'VEGAN', t: 'Vegan' },
]

const EMPTY_FOOD = {
  name: '', brand: '', barcode: '', kcal: '', protein: '', carb: '', fat: '',
  portionName: '', portionGrams: '', isPublic: false,
}

const today = () => new Date().toISOString().slice(0, 10)

export default function MealDiary({ session }) {
  const { t } = useI18n()
  const [date, setDate] = useState(today)
  const [meal, setMeal] = useState('FROKOST')

  const [query, setQuery] = useState('')
  const [results, setResults] = useState([])
  const [picked, setPicked] = useState(null)   // valgt matvare fra søket
  const [portion, setPortion] = useState('g')  // 'g', generisk enhet ('dl' …) eller index i picked.portions

  // Egne matvarer (for varer Matvaretabellen ikke har, f.eks. en bestemt shake).
  const [showFoodForm, setShowFoodForm] = useState(false)
  const [foodForm, setFoodForm] = useState(EMPTY_FOOD)
  const [myFoods, setMyFoods] = useState([])
  const [amount, setAmount] = useState(100)

  const [day, setDay] = useState(null)
  const [week, setWeek] = useState([])
  const [showWeek, setShowWeek] = useState(false)
  const [error, setError] = useState(null)

  const [goal, setGoal] = useState(null)       // lagret profil + utregnet mål
  const [showGoal, setShowGoal] = useState(false)

  // Kostholdspreferanser: diett, allergier og «liker ikke». Backend bruker dem
  // til å merke søketreff og tilpasse kildene i næringsrådene.
  const [prefs, setPrefs] = useState({ diet: 'ALT', allergies: [], dislikes: [] })
  const [showPrefs, setShowPrefs] = useState(false)
  const [dislikeText, setDislikeText] = useState('')
  const [prefsVersion, setPrefsVersion] = useState(0) // bumpes ved lagring -> nytt søk
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
  }, [query, prefsVersion])

  useEffect(() => { loadDay() }, [date]) // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { loadWeek(); loadGoal(); loadPrefs() }, []) // eslint-disable-line react-hooks/exhaustive-deps

  async function loadPrefs() {
    try {
      const res = await fetch(apiUrl('/api/kosthold/preferanser'), { headers: await authHeaders() })
      if (res.ok) {
        const p = await res.json()
        setPrefs(p)
        setDislikeText(p.dislikes.join(', '))
      }
    } catch { /* sekundært */ }
  }

  function toggleAllergen(code) {
    setPrefs((p) => ({
      ...p,
      allergies: p.allergies.includes(code) ? p.allergies.filter((a) => a !== code) : [...p.allergies, code],
    }))
  }

  async function savePrefs() {
    setError(null)
    try {
      const res = await fetch(apiUrl('/api/kosthold/preferanser'), {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json', ...(await authHeaders()) },
        body: JSON.stringify({
          diet: prefs.diet,
          allergies: prefs.allergies,
          dislikes: dislikeText.split(',').map((d) => d.trim()).filter(Boolean),
        }),
      })
      if (!res.ok) throw new Error(await readError(res))
      const saved = await res.json()
      setPrefs(saved)
      setDislikeText(saved.dislikes.join(', '))
      setShowPrefs(false)
      setPrefsVersion((v) => v + 1) // merk søketreffene på nytt
      loadWeek()                   // næringsrådene tilpasses
    } catch (e) {
      setError(e.message)
    }
  }

  // Advarselskode fra backend -> kort merkelapp.
  function warningLabel(code) {
    if (code.startsWith('ALLERGEN:')) return `⚠️ ${t(allergenName[code.slice(9)] ?? code.slice(9))}`
    if (code === 'DIETT') return `🚫 ${t('passer ikke dietten')}`
    if (code === 'MISLIKER') return `👎 ${t('liker ikke')}`
    return code
  }

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

  async function loadMyFoods() {
    try {
      const res = await fetch(apiUrl('/api/kosthold/egne-matvarer'), { headers: await authHeaders() })
      if (res.ok) setMyFoods(await res.json())
    } catch { /* sekundært */ }
  }

  function toggleFoodForm() {
    if (!showFoodForm) loadMyFoods()
    setShowFoodForm(!showFoodForm)
  }

  // En egen vare fra API-et i samme form som et søketreff, så den kan velges direkte.
  function fromCustom(f) {
    return {
      foodId: f.foodId,
      name: f.brand ? `${f.name} (${f.brand})` : f.name,
      kcalPer100g: f.kcalPer100g,
      proteinPer100g: f.proteinPer100g,
      fatPer100g: f.fatPer100g,
      carbPer100g: f.carbPer100g,
      portions: f.portionName && f.portionGrams ? [{ name: f.portionName, grams: f.portionGrams }] : [],
      source: 'EGEN',
    }
  }

  async function createFood() {
    setError(null)
    const num = (x) => (x === '' ? 0 : Number(x))
    try {
      const res = await fetch(apiUrl('/api/kosthold/egne-matvarer'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...(await authHeaders()) },
        body: JSON.stringify({
          name: foodForm.name.trim(),
          brand: foodForm.brand.trim() || null,
          barcode: foodForm.barcode.trim() || null,
          kcalPer100g: num(foodForm.kcal),
          proteinPer100g: num(foodForm.protein),
          fatPer100g: num(foodForm.fat),
          carbPer100g: num(foodForm.carb),
          portionName: foodForm.portionName.trim() || null,
          portionGrams: foodForm.portionGrams === '' ? null : Number(foodForm.portionGrams),
          isPublic: foodForm.isPublic,
        }),
      })
      if (!res.ok) throw new Error(await readError(res))
      const created = await res.json()
      setFoodForm(EMPTY_FOOD)
      setShowFoodForm(false)
      pick(fromCustom(created)) // rett til mengde-steget med den nye varen
    } catch (e) {
      setError(e.message)
    }
  }

  async function deleteFood(id) {
    try {
      await fetch(apiUrl(`/api/kosthold/egne-matvarer/${id}`), { method: 'DELETE', headers: await authHeaders() })
      loadMyFoods()
    } catch { /* ignorer */ }
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
    : unitByKey[portion] ? (Number(amount) || 0) * unitByKey[portion].g
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
        <div className="field">{t('Måltid')}
          <ChoiceChips label={t('Måltid')} value={meal} onChange={setMeal}
                       options={MEALS.map((m) => ({ v: m.v, t: t(m.t) }))} />
        </div>
        <button className="linklike goal-toggle" onClick={() => setShowPrefs(!showPrefs)}>
          ⚙️ {t('Preferanser & allergier')}
          {(prefs.allergies.length > 0 || prefs.diet !== 'ALT') && (
            <span className="prefs-count"> ({prefs.allergies.length + (prefs.diet !== 'ALT' ? 1 : 0)})</span>
          )} {showPrefs ? '▾' : '▸'}
        </button>
        <button className="linklike goal-toggle" onClick={() => setShowGoal(!showGoal)}>
          🎯 {goal ? t('Mål: {n} kcal/dag', { n: goal.dailyTargetKcal.toFixed(0) }) : t('Sett opp kalorimål')} {showGoal ? '▾' : '▸'}
        </button>
      </div>

      {showPrefs && (
        <div className="prefs-form">
          <div className="field">{t('Diett')}
            <ChoiceChips label={t('Diett')} value={prefs.diet} onChange={(v) => setPrefs({ ...prefs, diet: v })}
                         options={DIETS.map((d) => ({ v: d.v, t: t(d.t) }))} />
          </div>
          <div className="prefs-allergens">
            <span className="prefs-label">{t('Allergier')}</span>
            <div className="pill-row">
              {ALLERGENS.map(([code, name]) => (
                <button key={code}
                        className={`pill ${prefs.allergies.includes(code) ? 'on' : ''}`}
                        aria-pressed={prefs.allergies.includes(code)}
                        onClick={() => toggleAllergen(code)}>
                  {t(name)}
                </button>
              ))}
            </div>
          </div>
          <label>{t('Liker ikke (skill med komma)')}
            <input placeholder={t('f.eks. sopp, rosenkål, lever')} value={dislikeText}
                   onChange={(e) => setDislikeText(e.target.value)} />
          </label>
          <p className="muted prefs-note">
            {t('Søket merker og legger varer som ikke passer bakerst, og næringsrådene foreslår bare kilder som passer deg. Allergivarsler er basert på navnet – sjekk alltid pakningen.')}
          </p>
          <button className="primary" onClick={savePrefs}>{t('Lagre preferanser')}</button>
        </div>
      )}

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
          <div className="field">{t('Kjønn')}
            <ChoiceChips label={t('Kjønn')} value={goalForm.sex} onChange={(v) => setGoalForm({ ...goalForm, sex: v })}
                         options={[{ v: 'M', t: t('Mann') }, { v: 'K', t: t('Kvinne') }]} />
          </div>
          <div className="field">{t('Hverdagsaktivitet')}
            <ChoiceChips label={t('Hverdagsaktivitet')} value={goalForm.activityLevel}
                         onChange={(v) => setGoalForm({ ...goalForm, activityLevel: v })}
                         options={ACTIVITY.map((a) => ({ v: a.v, t: t(a.t) }))} />
          </div>
          <div className="field">{t('Mål')}
            <ChoiceChips label={t('Mål')} value={goalForm.goalKgPerWeek}
                         onChange={(v) => setGoalForm({ ...goalForm, goalKgPerWeek: v })}
                         options={PACE.map((p) => ({ v: p.v, t: t(p.t) }))} />
          </div>
          <button className="primary" onClick={saveGoal}
                  disabled={!goalForm.weightKg || !goalForm.heightCm || !goalForm.age}>
            {t('Lagre mål')}
          </button>
        </div>
      )}

      <div className="diary-search-row">
        <input
          className="diary-search"
          placeholder={t('Søk etter matvare … (f.eks. havregryn)')}
          value={query}
          onChange={(e) => { setQuery(e.target.value); setPicked(null) }}
        />
        <button className="mini" onClick={toggleFoodForm}>
          {showFoodForm ? t('Lukk') : t('+ Egen matvare')}
        </button>
      </div>

      {showFoodForm && (
        <div className="food-form">
          <p className="muted food-form-hint">
            {t('Mangler en vare? Legg den inn selv – næring står på pakningen (per 100 g).')}
          </p>
          <div className="food-form-grid">
            <input placeholder={t('Navn (f.eks. Proteinshake sjokolade)')} value={foodForm.name}
                   onChange={(e) => setFoodForm({ ...foodForm, name: e.target.value })} />
            <input placeholder={t('Merke (valgfritt)')} value={foodForm.brand}
                   onChange={(e) => setFoodForm({ ...foodForm, brand: e.target.value })} />
            <input placeholder={t('Strekkode (valgfritt)')} inputMode="numeric" value={foodForm.barcode}
                   onChange={(e) => setFoodForm({ ...foodForm, barcode: e.target.value })} />
          </div>
          <div className="food-form-grid numbers">
            {[['kcal', 'kcal / 100 g'], ['protein', 'Protein (g)'], ['carb', 'Karbo (g)'], ['fat', 'Fett (g)']].map(([k, label]) => (
              <label key={k}>{t(label)}
                <input type="number" min="0" step="any" value={foodForm[k]}
                       onChange={(e) => setFoodForm({ ...foodForm, [k]: e.target.value })} />
              </label>
            ))}
          </div>
          <div className="food-form-grid">
            <input placeholder={t('Porsjon (valgfritt, f.eks. scoop)')} value={foodForm.portionName}
                   onChange={(e) => setFoodForm({ ...foodForm, portionName: e.target.value })} />
            <input type="number" min="0" step="any" placeholder={t('Gram per porsjon')} value={foodForm.portionGrams}
                   onChange={(e) => setFoodForm({ ...foodForm, portionGrams: e.target.value })} />
          </div>
          <label className="food-public">
            <input type="checkbox" checked={foodForm.isPublic}
                   onChange={(e) => setFoodForm({ ...foodForm, isPublic: e.target.checked })} />
            {t('Del offentlig – alle brukere kan søke den opp')}
          </label>
          <button className="primary" onClick={createFood}
                  disabled={!foodForm.name.trim() || foodForm.kcal === ''}>
            {t('Lagre matvare')}
          </button>

          {myFoods.length > 0 && (
            <div className="my-foods">
              <p className="trails-title">{t('Mine matvarer')}</p>
              <ul>
                {myFoods.map((f) => (
                  <li key={f.id}>
                    <button className="linklike" onClick={() => { pick(fromCustom(f)); setShowFoodForm(false) }}>
                      {f.name}{f.brand ? ` (${f.brand})` : ''}
                    </button>
                    <span className="muted"> · {f.kcalPer100g.toFixed(0)} kcal/100 g{f.isPublic ? ` · ${t('delt')}` : ''}</span>
                    <button className="del" onClick={() => deleteFood(f.id)} aria-label={t('Slett')}>✕</button>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}

      {!picked && results.length > 0 && (
        <ul className="food-results">
          {results.map((f) => (
            <li key={f.foodId}>
              <button className="linklike" onClick={() => pick(f)}>
                {f.name} <span className="muted">{f.kcalPer100g.toFixed(0)} kcal/100 g</span>
                {f.source === 'EGEN' && <span className="food-source own">{t('egen')}</span>}
                {f.source === 'OFFENTLIG' && <span className="food-source shared">{t('delt')}</span>}
                {f.warnings?.map((w) => (
                  <span key={w} className={`food-warning ${w.startsWith('ALLERGEN') ? 'allergen' : ''}`}>
                    {warningLabel(w)}
                  </span>
                ))}
              </button>
            </li>
          ))}
        </ul>
      )}

      {picked && (
        <div className="diary-add">
          <p className="diary-picked"><strong>{picked.name}</strong>
            {picked.warnings?.map((w) => (
              <span key={w} className={`food-warning ${w.startsWith('ALLERGEN') ? 'allergen' : ''}`}>
                {warningLabel(w)}
              </span>
            ))}
          </p>
          <div className="diary-amount">
            <label>{t('Mengde')}
              <input type="number" min="0" step="any" value={amount}
                     onChange={(e) => setAmount(e.target.value)} />
            </label>
            <div className="field">{t('Enhet')}
              <ChoiceChips label={t('Enhet')} value={portion} onChange={setPortion} options={[
                { v: 'g', t: t('gram') },
                ...picked.portions.map((p, i) => ({ v: i, t: `${p.name} (${p.grams.toFixed(0)} g)` })),
                ...GENERIC_UNITS.map((u) => ({ v: u.v, t: t(u.t) })),
              ]} />
            </div>
            <span className="muted">= {grams.toFixed(0)} g · ~{previewKcal.toFixed(0)} kcal</span>
            {unitByKey[portion]?.volume && (
              <span className="muted unit-hint">{t('(1 ml ≈ 1 g – stemmer for drikke, omtrentlig for pulver)')}</span>
            )}
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
