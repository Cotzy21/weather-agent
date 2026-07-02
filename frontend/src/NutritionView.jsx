import { useLayoutEffect, useRef, useState } from 'react'
import gsap from 'gsap'
import MealDiary from './MealDiary'
import { useReveal, pop } from './anim'

/**
 * Kosthold: utforsk matfamiliene, merk favoritter, og sett sammen egne måltider
 * av favoritt-ingrediensene. Hver matvare har makrofordeling (protein/karbo/fett
 * per 100 g, ca-verdier fra Matvaretabellen) - ikke bare én merkelapp, siden
 * f.eks. kyllingfilet har både protein OG fett. Favoritter + egne måltider
 * lagres lokalt (localStorage).
 */

/* m = gram per 100 g: { p: protein, k: karbo, f: fett } */
const FAMILIES = [
  {
    id: 'kjott', name: 'Kjøtt', icon: '🥩', hue: '#fb7185',
    foods: [
      { n: 'Kyllingfilet', e: '🍗', m: { p: 23, k: 0, f: 2 } },
      { n: 'Karbonadedeig', e: '🥩', m: { p: 18, k: 0, f: 14 } },
      { n: 'Biff', e: '🥩', m: { p: 21, k: 0, f: 9 } },
      { n: 'Svinekotelett', e: '🍖', m: { p: 20, k: 0, f: 11 } },
      { n: 'Kalkun', e: '🦃', m: { p: 23, k: 0, f: 2 } },
      { n: 'Lammelår', e: '🍖', m: { p: 18, k: 0, f: 17 } },
      { n: 'Egg', e: '🥚', m: { p: 13, k: 1, f: 10 } },
      { n: 'Bacon', e: '🥓', m: { p: 14, k: 1, f: 32 } },
    ],
  },
  {
    id: 'fisk', name: 'Fisk & sjømat', icon: '🐟', hue: '#38bdf8',
    foods: [
      { n: 'Laks', e: '🐟', m: { p: 20, k: 0, f: 13 } },
      { n: 'Torsk', e: '🐟', m: { p: 18, k: 0, f: 1 } },
      { n: 'Makrell', e: '🐟', m: { p: 19, k: 0, f: 18 } },
      { n: 'Reker', e: '🦐', m: { p: 20, k: 0, f: 1 } },
      { n: 'Tunfisk', e: '🐟', m: { p: 25, k: 0, f: 1 } },
      { n: 'Sei', e: '🐟', m: { p: 19, k: 0, f: 1 } },
      { n: 'Sild', e: '🐟', m: { p: 17, k: 0, f: 14 } },
      { n: 'Blåskjell', e: '🦪', m: { p: 12, k: 3, f: 2 } },
    ],
  },
  {
    id: 'gronnsaker', name: 'Grønnsaker', icon: '🥦', hue: '#4ade80',
    foods: [
      { n: 'Brokkoli', e: '🥦', m: { p: 3, k: 4, f: 0 } },
      { n: 'Spinat', e: '🥬', m: { p: 3, k: 1, f: 0 } },
      { n: 'Gulrot', e: '🥕', m: { p: 1, k: 8, f: 0 } },
      { n: 'Paprika', e: '🫑', m: { p: 1, k: 5, f: 0 } },
      { n: 'Tomat', e: '🍅', m: { p: 1, k: 3, f: 0 } },
      { n: 'Agurk', e: '🥒', m: { p: 1, k: 2, f: 0 } },
      { n: 'Søtpotet', e: '🍠', m: { p: 2, k: 20, f: 0 } },
      { n: 'Blomkål', e: '🥦', m: { p: 2, k: 4, f: 0 } },
      { n: 'Løk', e: '🧅', m: { p: 1, k: 8, f: 0 } },
      { n: 'Sopp', e: '🍄', m: { p: 3, k: 1, f: 0 } },
    ],
  },
  {
    id: 'frukt', name: 'Frukt & bær', icon: '🍎', hue: '#facc15',
    foods: [
      { n: 'Eple', e: '🍎', m: { p: 0, k: 12, f: 0 } },
      { n: 'Banan', e: '🍌', m: { p: 1, k: 20, f: 0 } },
      { n: 'Appelsin', e: '🍊', m: { p: 1, k: 9, f: 0 } },
      { n: 'Blåbær', e: '🫐', m: { p: 1, k: 8, f: 1 } },
      { n: 'Jordbær', e: '🍓', m: { p: 1, k: 6, f: 0 } },
      { n: 'Druer', e: '🍇', m: { p: 1, k: 16, f: 0 } },
      { n: 'Mango', e: '🥭', m: { p: 1, k: 13, f: 0 } },
      { n: 'Kiwi', e: '🥝', m: { p: 1, k: 9, f: 1 } },
    ],
  },
  {
    id: 'korn', name: 'Korn & karbo', icon: '🌾', hue: '#fb923c',
    foods: [
      { n: 'Havregryn', e: '🥣', m: { p: 13, k: 58, f: 7 } },
      { n: 'Ris (kokt)', e: '🍚', m: { p: 3, k: 28, f: 0 } },
      { n: 'Fullkornspasta', e: '🍝', m: { p: 13, k: 62, f: 3 } },
      { n: 'Grovbrød', e: '🍞', m: { p: 9, k: 41, f: 4 } },
      { n: 'Quinoa', e: '🌾', m: { p: 14, k: 57, f: 6 } },
      { n: 'Poteter', e: '🥔', m: { p: 2, k: 17, f: 0 } },
      { n: 'Knekkebrød', e: '🫓', m: { p: 10, k: 60, f: 5 } },
      { n: 'Couscous', e: '🌾', m: { p: 13, k: 72, f: 1 } },
    ],
  },
  {
    id: 'meieri', name: 'Meieri', icon: '🥛', hue: '#c4b5fd',
    foods: [
      { n: 'Melk', e: '🥛', m: { p: 3, k: 5, f: 1 } },
      { n: 'Gresk yoghurt', e: '🥛', m: { p: 9, k: 4, f: 8 } },
      { n: 'Cottage cheese', e: '🧀', m: { p: 13, k: 2, f: 4 } },
      { n: 'Ost', e: '🧀', m: { p: 27, k: 0, f: 27 } },
      { n: 'Skyr', e: '🥛', m: { p: 11, k: 4, f: 0 } },
      { n: 'Kesam', e: '🥛', m: { p: 10, k: 4, f: 3 } },
    ],
  },
  {
    id: 'notter', name: 'Nøtter & frø', icon: '🥜', hue: '#a3e635',
    foods: [
      { n: 'Mandler', e: '🌰', m: { p: 21, k: 7, f: 50 } },
      { n: 'Valnøtter', e: '🌰', m: { p: 15, k: 7, f: 65 } },
      { n: 'Peanøttsmør', e: '🥜', m: { p: 25, k: 12, f: 50 } },
      { n: 'Chiafrø', e: '🌱', m: { p: 17, k: 8, f: 31 } },
      { n: 'Cashew', e: '🥜', m: { p: 18, k: 27, f: 44 } },
      { n: 'Solsikkefrø', e: '🌻', m: { p: 21, k: 12, f: 51 } },
    ],
  },
]

const MACROS = [
  { key: 'p', label: 'Protein', color: '#34d399' },
  { key: 'k', label: 'Karbo', color: '#fbbf24' },
  { key: 'f', label: 'Fett', color: '#f472b6' },
]

/** Oppslag "familie:navn" -> matvare, bl.a. for å oppgradere gamle favoritter. */
const FOOD_BY_KEY = new Map(
  FAMILIES.flatMap((fam) => fam.foods.map((food) => [`${fam.id}:${food.n}`, food])),
)

const kcalOf = (m, grams = 100) => ((m.p * 4 + m.k * 4 + m.f * 9) * grams) / 100

/** Energiandeler i prosent (4/4/9 kcal per gram). */
function shares(m) {
  const kcal = m.p * 4 + m.k * 4 + m.f * 9
  if (kcal <= 0) return { p: 0, k: 0, f: 0 }
  return { p: (m.p * 4 * 100) / kcal, k: (m.k * 4 * 100) / kcal, f: (m.f * 9 * 100) / kcal }
}

/** Stablet makro-søyle, med valgfri prosent-tekst under. */
function MacroSplit({ m, withLegend = false }) {
  const s = shares(m)
  const title = MACROS.map((x) => `${x.label} ${s[x.key].toFixed(0)} %`).join(' · ')
  return (
    <span className="macro-split">
      <span className="macro-split-bar" title={title}>
        {MACROS.map((x) => (
          <span key={x.key} style={{ width: `${s[x.key]}%`, background: x.color }} />
        ))}
      </span>
      {withLegend && (
        <span className="macro-split-legend">
          {MACROS.map((x) => (
            <span key={x.key} style={{ color: x.color }}>{x.label} {s[x.key].toFixed(0)} %</span>
          ))}
        </span>
      )}
    </span>
  )
}

const FAVS_KEY = 'kosthold-favoritter'
const MEALS_KEY = 'kosthold-maaltider'

function loadFavs() {
  try {
    const raw = JSON.parse(localStorage.getItem(FAVS_KEY)) ?? []
    // Oppgrader gamle favoritter (som bare hadde tag) til makro-format.
    return raw
      .map((f) => (f.m ? f : { key: f.key, n: f.n, e: f.e, m: FOOD_BY_KEY.get(f.key)?.m }))
      .filter((f) => f.m)
  } catch { return [] }
}

function loadMeals() {
  try { return JSON.parse(localStorage.getItem(MEALS_KEY)) ?? [] } catch { return [] }
}

/** Sum-makroer for en ingrediensliste (gram-vektet). */
function totalMacros(ingredients) {
  return ingredients.reduce(
    (acc, i) => ({
      p: acc.p + (i.m.p * i.grams) / 100,
      k: acc.k + (i.m.k * i.grams) / 100,
      f: acc.f + (i.m.f * i.grams) / 100,
    }),
    { p: 0, k: 0, f: 0 },
  )
}

export default function NutritionView({ session }) {
  const [open, setOpen] = useState(null) // åpen familie-id
  const [favs, setFavs] = useState(loadFavs)
  const [meals, setMeals] = useState(loadMeals)
  const [building, setBuilding] = useState(false)
  const [mealName, setMealName] = useState('')
  const [ingredients, setIngredients] = useState([])
  const [mealSaved, setMealSaved] = useState(false) // kort «✓ Måltid lagret»-kvittering
  const rootRef = useReveal([])
  const foodsRef = useRef(null)

  // Matvarene "fanner ut" når en familie åpnes.
  useLayoutEffect(() => {
    const el = foodsRef.current
    if (!el) return undefined
    const ctx = gsap.context(() => {
      gsap.fromTo(el.children,
        { opacity: 0, y: 16, scale: 0.85 },
        { opacity: 1, y: 0, scale: 1, duration: 0.4, stagger: 0.045, ease: 'back.out(1.7)', clearProps: 'all' })
    }, el)
    return () => ctx.revert()
  }, [open])

  function saveFavs(next) {
    setFavs(next)
    try { localStorage.setItem(FAVS_KEY, JSON.stringify(next)) } catch { /* privat modus o.l. */ }
  }

  function saveMeals(next) {
    setMeals(next)
    try { localStorage.setItem(MEALS_KEY, JSON.stringify(next)) } catch { /* privat modus o.l. */ }
  }

  function toggleFav(food, familyId, el) {
    const key = `${familyId}:${food.n}`
    if (favs.some((f) => f.key === key)) {
      saveFavs(favs.filter((f) => f.key !== key))
    } else {
      saveFavs([...favs, { key, n: food.n, e: food.e, m: food.m }])
      pop(el)
    }
  }

  const isFav = (familyId, food) => favs.some((f) => f.key === `${familyId}:${food.n}`)

  /* --- måltidsbygger --- */

  function addIngredient(fav, el) {
    if (ingredients.some((i) => i.key === fav.key)) return
    setIngredients([...ingredients, { ...fav, grams: 100 }])
    pop(el)
  }

  function setGrams(key, grams) {
    setIngredients(ingredients.map((i) => (i.key === key ? { ...i, grams } : i)))
  }

  function saveMeal() {
    const clean = ingredients
      .map((i) => ({ ...i, grams: Number(i.grams) || 0 }))
      .filter((i) => i.grams > 0)
    if (!mealName.trim() || clean.length === 0) return
    saveMeals([...meals, { id: crypto.randomUUID(), name: mealName.trim(), ingredients: clean }])
    setMealName('')
    setIngredients([])
    setBuilding(false)
    setMealSaved(true)
    setTimeout(() => setMealSaved(false), 2500)
  }

  const buildTotals = totalMacros(ingredients.map((i) => ({ ...i, grams: Number(i.grams) || 0 })))
  const buildKcal = kcalOf(buildTotals)

  // Samlet profil av favorittene (likt vektet per 100 g).
  const favTotals = totalMacros(favs.map((f) => ({ ...f, grams: 100 })))
  const family = FAMILIES.find((f) => f.id === open)

  return (
    <div className="nutrition" ref={rootRef}>
      <h2 className="detail-title" data-reveal>🥗 Kosthold</h2>

      <MealDiary session={session} />

      <p className="muted" data-reveal>
        Utforsk matfamiliene, merk favorittene dine, og sett dem sammen til egne måltider.
      </p>

      {favs.length > 0 && (
        <div className="fav-shelf" data-reveal>
          <span className="ai-title">⭐ Favorittene dine</span>
          <div className="fav-chips">
            {favs.map((f) => (
              <button key={f.key} className="fav-chip" title="Fjern"
                      onClick={() => saveFavs(favs.filter((x) => x.key !== f.key))}>
                <span>{f.e}</span> {f.n} ✕
              </button>
            ))}
          </div>
          <div className="fav-profile">
            <span className="muted">Samlet energifordeling:</span>
            <MacroSplit m={favTotals} withLegend />
          </div>

          {!building ? (
            <span className="meal-start-row">
              <button className="primary meal-start" onClick={() => setBuilding(true)}>
                🍽️ Sett sammen måltid av favorittene
              </button>
              {mealSaved && <span className="success">✓ Måltid lagret</span>}
            </span>
          ) : (
            <div className="meal-builder">
              <p className="food-panel-title">Trykk på favoritter for å legge dem i måltidet:</p>
              <div className="fav-chips">
                {favs.filter((f) => !ingredients.some((i) => i.key === f.key)).map((f) => (
                  <button key={f.key} className="fav-chip add"
                          onClick={(e) => addIngredient(f, e.currentTarget)}>
                    <span>{f.e}</span> {f.n} +
                  </button>
                ))}
              </div>
              {ingredients.map((i) => (
                <div className="ingredient-row" key={i.key}>
                  <span className="ingredient-name">{i.e} {i.n}</span>
                  <input type="number" min="0" step="10" value={i.grams}
                         onChange={(e) => setGrams(i.key, e.target.value)} />
                  <span className="muted">g</span>
                  <MacroSplit m={i.m} />
                  <button className="del" aria-label="Fjern"
                          onClick={() => setIngredients(ingredients.filter((x) => x.key !== i.key))}>✕</button>
                </div>
              ))}
              {ingredients.length > 0 && (
                <p className="meal-total">
                  <strong>~{buildKcal.toFixed(0)} kcal</strong>
                  {' '}· {buildTotals.p.toFixed(0)} g protein · {buildTotals.k.toFixed(0)} g karbo · {buildTotals.f.toFixed(0)} g fett
                </p>
              )}
              <div className="meal-actions">
                <input placeholder="Navn på måltidet, f.eks. Treningsfrokost"
                       value={mealName} onChange={(e) => setMealName(e.target.value)} />
                <button className="primary" onClick={saveMeal}
                        disabled={!mealName.trim() || ingredients.length === 0}>Lagre måltid</button>
                <button className="mini" onClick={() => { setBuilding(false); setIngredients([]) }}>Avbryt</button>
              </div>
            </div>
          )}
        </div>
      )}

      {meals.length > 0 && (
        <div className="meal-list" data-reveal>
          <span className="ai-title">🍽️ Måltidene dine</span>
          {meals.map((meal) => {
            const t = totalMacros(meal.ingredients)
            return (
              <div className="meal-card" key={meal.id}>
                <div className="meal-head">
                  <strong>{meal.name}</strong>
                  <span className="muted">~{kcalOf(t).toFixed(0)} kcal</span>
                  <button className="del" aria-label="Slett"
                          onClick={() => saveMeals(meals.filter((x) => x.id !== meal.id))}>✕</button>
                </div>
                <p className="muted meal-ingredients">
                  {meal.ingredients.map((i) => `${i.n} ${i.grams} g`).join(' · ')}
                </p>
                <MacroSplit m={t} withLegend />
              </div>
            )
          })}
        </div>
      )}

      <div className="family-grid" data-reveal>
        {FAMILIES.map((f) => (
          <button key={f.id}
                  className={`family-card ${open === f.id ? 'open' : ''}`}
                  style={{ '--family-hue': f.hue }}
                  onClick={() => setOpen(open === f.id ? null : f.id)}>
            <span className="family-icon">{f.icon}</span>
            <span className="family-name">{f.name}</span>
            <span className="family-count muted">
              {f.foods.filter((x) => isFav(f.id, x)).length > 0
                ? `${f.foods.filter((x) => isFav(f.id, x)).length} ⭐`
                : `${f.foods.length} matvarer`}
            </span>
          </button>
        ))}
      </div>

      {family && (
        <div className="food-panel" style={{ '--family-hue': family.hue }}>
          <p className="food-panel-title">{family.icon} {family.name} – trykk for å merke favoritter</p>
          <div className="food-grid" ref={foodsRef}>
            {family.foods.map((food) => (
              <button key={food.n}
                      className={`food-card ${isFav(family.id, food) ? 'fav' : ''}`}
                      onClick={(e) => toggleFav(food, family.id, e.currentTarget)}>
                <span className="food-emoji">{food.e}</span>
                <span className="food-name">{food.n}</span>
                <MacroSplit m={food.m} />
                <span className="food-kcal muted">~{kcalOf(food.m).toFixed(0)} kcal/100 g</span>
                <span className="food-star">{isFav(family.id, food) ? '⭐' : '☆'}</span>
              </button>
            ))}
          </div>
          <p className="muted source-note">Makroverdier er ca-tall per 100 g (Matvaretabellen).</p>
        </div>
      )}
    </div>
  )
}
