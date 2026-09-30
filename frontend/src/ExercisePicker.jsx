import { useState } from 'react'
import { useI18n } from './i18n.jsx'
import { groupsFor } from './exercises'

// Ark nederfra med søk + øvelsesliste. Erstatter <input list="exercises">.
// `mine` = brukerens egne øvelser (fra historikk/forslag), vises øverst.
export default function ExercisePicker({ title, mine = [], liked = [], disliked = [], recommended = [], onPick, onClose }) {
  const { t, lang } = useI18n()
  const [q, setQ] = useState('')
  const query = q.trim().toLowerCase()
  const match = (name) => !query || name.toLowerCase().includes(query)

  // Favoritter øverst, så egne øvelser; det brukeren ikke liker vises bare i gruppene, merket 👎.
  const lower = (list) => new Set(list.map((n) => n.toLowerCase()))
  const likedSet = lower(liked)
  const dislikedSet = lower(disliked)
  const rec = recommended.filter((n) => !dislikedSet.has(n.toLowerCase()))
  const recSet = lower(rec)
  const mineUnique = [...new Set(mine.filter(Boolean))]
    .filter((n) => !likedSet.has(n.toLowerCase()) && !dislikedSet.has(n.toLowerCase()) && !recSet.has(n.toLowerCase()))
  const shown = new Set([...recSet, ...likedSet, ...lower(mineUnique)])
  const sections = [
    { group: 'Anbefalt (samme muskler)', items: rec },
    { group: 'Favoritter', items: liked.filter((n) => !recSet.has(n.toLowerCase())) },
    { group: 'Dine øvelser', items: mineUnique },
    ...groupsFor(lang).map((g) => ({ ...g, items: g.items.filter((n) => !shown.has(n.toLowerCase())) })),
  ]
    .map((s) => ({ ...s, items: s.items.filter(match) }))
    .filter((s) => s.items.length)

  const exact = sections.some((s) => s.items.some((n) => n.toLowerCase() === query))

  return (
    <div className="sheet-backdrop" onClick={onClose}>
      <div className="sheet picker" role="dialog" aria-label={title} onClick={(e) => e.stopPropagation()}>
        <span className="sheet-handle" aria-hidden="true" />
        <div className="picker-head">
          <strong>{title}</strong>
          <button className="del" onClick={onClose} aria-label={t('Lukk')}>✕</button>
        </div>
        <input
          className="picker-search"
          type="search"
          placeholder={t('Søk eller skriv en ny øvelse')}
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
        <div className="picker-list">
          {query && !exact && (
            <button className="sheet-item" onClick={() => onPick(q.trim())}>
              <span className="sheet-icon">＋</span>
              <span className="sheet-text">{t('Legg til «{name}»', { name: q.trim() })}</span>
            </button>
          )}
          {sections.map((s) => (
            <div key={s.group}>
              <p className="sheet-label">{t(s.group)}</p>
              {s.items.map((name) => (
                <button key={name} className={`picker-item ${dislikedSet.has(name.toLowerCase()) ? 'disliked' : ''}`}
                        onClick={() => onPick(name)}>
                  {s.group === 'Favoritter' && '♥ '}{name}{dislikedSet.has(name.toLowerCase()) && ' 👎'}
                </button>
              ))}
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
