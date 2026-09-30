import { useState } from 'react'
import { useI18n } from './i18n.jsx'
import { EXERCISE_GROUPS } from './exercises'

// Ark nederfra med søk + øvelsesliste. Erstatter <input list="exercises">.
// `mine` = brukerens egne øvelser (fra historikk/forslag), vises øverst.
export default function ExercisePicker({ title, mine = [], onPick, onClose }) {
  const { t } = useI18n()
  const [q, setQ] = useState('')
  const query = q.trim().toLowerCase()
  const match = (name) => !query || name.toLowerCase().includes(query)

  const mineUnique = [...new Set(mine.filter(Boolean))]
  const mineSet = new Set(mineUnique.map((n) => n.toLowerCase()))
  const sections = [
    { group: 'Dine øvelser', items: mineUnique },
    ...EXERCISE_GROUPS.map((g) => ({ ...g, items: g.items.filter((n) => !mineSet.has(n.toLowerCase())) })),
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
                <button key={name} className="picker-item" onClick={() => onPick(name)}>{name}</button>
              ))}
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
