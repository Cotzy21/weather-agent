import { useLayoutEffect, useRef, useState } from 'react'
import gsap from 'gsap'
import MealDiary from './MealDiary'
import { useReveal, pop } from './anim'

/**
 * Kosthold: utforsk matfamiliene (kjøtt, fisk, grønnsaker …), åpne en familie
 * og trykk på favorittene dine. Favorittene lagres lokalt (localStorage) og
 * oppsummeres som en enkel makro-profil. Rent frontend – ingen backend ennå.
 */
const FAMILIES = [
  {
    id: 'kjott', name: 'Kjøtt', icon: '🥩', hue: '#fb7185',
    foods: [
      { n: 'Kyllingfilet', e: '🍗', tag: 'protein' }, { n: 'Karbonadedeig', e: '🥩', tag: 'protein' },
      { n: 'Biff', e: '🥩', tag: 'protein' }, { n: 'Svinekotelett', e: '🍖', tag: 'protein' },
      { n: 'Kalkun', e: '🦃', tag: 'protein' }, { n: 'Lammelår', e: '🍖', tag: 'protein' },
      { n: 'Egg', e: '🥚', tag: 'protein' }, { n: 'Bacon', e: '🥓', tag: 'fett' },
    ],
  },
  {
    id: 'fisk', name: 'Fisk & sjømat', icon: '🐟', hue: '#38bdf8',
    foods: [
      { n: 'Laks', e: '🐟', tag: 'fett' }, { n: 'Torsk', e: '🐟', tag: 'protein' },
      { n: 'Makrell', e: '🐟', tag: 'fett' }, { n: 'Reker', e: '🦐', tag: 'protein' },
      { n: 'Tunfisk', e: '🐟', tag: 'protein' }, { n: 'Sei', e: '🐟', tag: 'protein' },
      { n: 'Sild', e: '🐟', tag: 'fett' }, { n: 'Blåskjell', e: '🦪', tag: 'protein' },
    ],
  },
  {
    id: 'gronnsaker', name: 'Grønnsaker', icon: '🥦', hue: '#4ade80',
    foods: [
      { n: 'Brokkoli', e: '🥦', tag: 'vitaminer' }, { n: 'Spinat', e: '🥬', tag: 'vitaminer' },
      { n: 'Gulrot', e: '🥕', tag: 'vitaminer' }, { n: 'Paprika', e: '🫑', tag: 'vitaminer' },
      { n: 'Tomat', e: '🍅', tag: 'vitaminer' }, { n: 'Agurk', e: '🥒', tag: 'vitaminer' },
      { n: 'Søtpotet', e: '🍠', tag: 'karbo' }, { n: 'Blomkål', e: '🥦', tag: 'vitaminer' },
      { n: 'Løk', e: '🧅', tag: 'vitaminer' }, { n: 'Sopp', e: '🍄', tag: 'vitaminer' },
    ],
  },
  {
    id: 'frukt', name: 'Frukt & bær', icon: '🍎', hue: '#facc15',
    foods: [
      { n: 'Eple', e: '🍎', tag: 'vitaminer' }, { n: 'Banan', e: '🍌', tag: 'karbo' },
      { n: 'Appelsin', e: '🍊', tag: 'vitaminer' }, { n: 'Blåbær', e: '🫐', tag: 'vitaminer' },
      { n: 'Jordbær', e: '🍓', tag: 'vitaminer' }, { n: 'Druer', e: '🍇', tag: 'karbo' },
      { n: 'Mango', e: '🥭', tag: 'karbo' }, { n: 'Kiwi', e: '🥝', tag: 'vitaminer' },
    ],
  },
  {
    id: 'korn', name: 'Korn & karbo', icon: '🌾', hue: '#fb923c',
    foods: [
      { n: 'Havregryn', e: '🥣', tag: 'karbo' }, { n: 'Ris', e: '🍚', tag: 'karbo' },
      { n: 'Fullkornspasta', e: '🍝', tag: 'karbo' }, { n: 'Grovbrød', e: '🍞', tag: 'karbo' },
      { n: 'Quinoa', e: '🌾', tag: 'karbo' }, { n: 'Poteter', e: '🥔', tag: 'karbo' },
      { n: 'Knekkebrød', e: '🫓', tag: 'karbo' }, { n: 'Couscous', e: '🌾', tag: 'karbo' },
    ],
  },
  {
    id: 'meieri', name: 'Meieri', icon: '🥛', hue: '#c4b5fd',
    foods: [
      { n: 'Melk', e: '🥛', tag: 'protein' }, { n: 'Gresk yoghurt', e: '🥛', tag: 'protein' },
      { n: 'Cottage cheese', e: '🧀', tag: 'protein' }, { n: 'Ost', e: '🧀', tag: 'fett' },
      { n: 'Skyr', e: '🥛', tag: 'protein' }, { n: 'Kesam', e: '🥛', tag: 'protein' },
    ],
  },
  {
    id: 'notter', name: 'Nøtter & frø', icon: '🥜', hue: '#a3e635',
    foods: [
      { n: 'Mandler', e: '🌰', tag: 'fett' }, { n: 'Valnøtter', e: '🌰', tag: 'fett' },
      { n: 'Peanøttsmør', e: '🥜', tag: 'fett' }, { n: 'Chiafrø', e: '🌱', tag: 'fett' },
      { n: 'Cashew', e: '🥜', tag: 'fett' }, { n: 'Solsikkefrø', e: '🌻', tag: 'fett' },
    ],
  },
]

const TAGS = {
  protein: { t: 'Protein', c: '#34d399' },
  karbo: { t: 'Karbo', c: '#fbbf24' },
  fett: { t: 'Fett', c: '#f472b6' },
  vitaminer: { t: 'Vitaminer', c: '#60a5fa' },
}

const STORAGE_KEY = 'kosthold-favoritter'

function loadFavs() {
  try { return JSON.parse(localStorage.getItem(STORAGE_KEY)) ?? [] } catch { return [] }
}

export default function NutritionView({ session }) {
  const [open, setOpen] = useState(null) // åpen familie-id
  const [favs, setFavs] = useState(loadFavs)
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
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify(next)) } catch { /* privat modus o.l. */ }
  }

  function toggleFav(food, familyId, el) {
    const key = `${familyId}:${food.n}`
    if (favs.some((f) => f.key === key)) {
      saveFavs(favs.filter((f) => f.key !== key))
    } else {
      saveFavs([...favs, { key, n: food.n, e: food.e, tag: food.tag }])
      pop(el)
    }
  }

  const isFav = (familyId, food) => favs.some((f) => f.key === `${familyId}:${food.n}`)

  // Enkel makro-profil av favorittene.
  const tagCounts = Object.keys(TAGS).map((tag) => ({
    tag, count: favs.filter((f) => f.tag === tag).length,
  }))
  const maxTag = Math.max(1, ...tagCounts.map((t) => t.count))
  const family = FAMILIES.find((f) => f.id === open)

  return (
    <div className="nutrition" ref={rootRef}>
      <h2 className="detail-title" data-reveal>🥗 Kosthold</h2>

      <MealDiary session={session} />

      <p className="muted" data-reveal>
        Utforsk matfamiliene og merk favorittene dine – så ser du hva tallerkenen din er bygget av.
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
          <div className="macro-bars">
            {tagCounts.filter((t) => t.count > 0).map(({ tag, count }) => (
              <div className="macro-row" key={tag}>
                <span className="macro-label">{TAGS[tag].t}</span>
                <span className="macro-bar">
                  <span style={{ width: `${(count / maxTag) * 100}%`, background: TAGS[tag].c }} />
                </span>
                <span className="macro-count">{count}</span>
              </div>
            ))}
          </div>
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
                <span className="food-tag" style={{ color: TAGS[food.tag].c }}>{TAGS[food.tag].t}</span>
                <span className="food-star">{isFav(family.id, food) ? '⭐' : '☆'}</span>
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
