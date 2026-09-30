import { useState } from 'react'
import { useI18n } from './i18n.jsx'
import ExercisePicker from './ExercisePicker.jsx'

const WEEKDAYS = ['Mandag', 'Tirsdag', 'Onsdag', 'Torsdag', 'Fredag', 'Lørdag', 'Søndag']
const same = (a, b) => a.toLowerCase() === b.toLowerCase()

// Det appen husker om treningen din, og som AI-forslagene bruker.
const LEVEL_LABELS = { BEGINNER: 'Helt ny', NOVICE: 'Litt erfaren', INTERMEDIATE: 'Middels erfaren', ADVANCED: 'Avansert' }

export default function TrainingMemoryCard({ memory, mine, onSave, profile, onEditProfile }) {
  const { t, lang } = useI18n()
  const [notes, setNotes] = useState(memory.notes)
  const [adding, setAdding] = useState(null) // 'liked' | 'disliked'
  const [error, setError] = useState(null)

  async function save(patch) {
    setError(null)
    try {
      await onSave({ liked: memory.liked, disliked: memory.disliked, notes: memory.notes, ...patch })
    } catch (e) {
      setError(e.message)
    }
  }

  function add(list, name) {
    const other = list === 'liked' ? 'disliked' : 'liked'
    if (memory[list].some((x) => same(x, name))) return
    save({ [list]: [...memory[list], name], [other]: memory[other].filter((x) => !same(x, name)) })
  }

  const avg = lang === 'en' ? String(memory.avgPerWeek) : String(memory.avgPerWeek).replace('.', ',')
  const days = memory.usualDays.map((d) => t(WEEKDAYS[d - 1]).toLowerCase())

  return (
    <div className="memory-card">
      <div className="memory-head">
        <strong>🧠 {t('Treningsminne')}</strong>
        <span className="muted">{t('Brukes til anbefalingene dine')}</span>
      </div>

      {profile !== undefined && (
        <p className="memory-habits">
          🎯 {profile ? `${t('Nivå')}: ${t(LEVEL_LABELS[profile.experienceLevel])}` : t('Ikke fylt ut ennå')}
          {' · '}<button className="mini" onClick={onEditProfile}>{t(profile ? 'Rediger treningsprofil' : 'Fyll ut treningsprofil')}</button>
        </p>
      )}

      {memory.workoutsLast28Days > 0 ? (
        <p className="memory-habits">
          📅 {t('{n} økter/uke', { n: avg })}{days.length > 0 && ` · ${t('oftest')} ${days.join(', ')}`}
        </p>
      ) : (
        <p className="muted memory-habits">{t('Logg noen økter, så lærer appen vanene dine.')}</p>
      )}

      {[['liked', '♥', 'Liker'], ['disliked', '👎', 'Liker ikke']].map(([list, icon, label]) => (
        <div className="memory-row" key={list}>
          <span className="memory-label">{icon} {t(label)}</span>
          <div className="memory-chips">
            {memory[list].map((name) => (
              <span className={`memory-chip ${list}`} key={name}>
                {name}
                <button aria-label={t('Fjern {name}', { name })}
                        onClick={() => save({ [list]: memory[list].filter((x) => x !== name) })}>✕</button>
              </span>
            ))}
            <button className="memory-add" onClick={() => setAdding(list)}>＋ {t('Legg til')}</button>
          </div>
        </div>
      ))}

      <div className="field memory-notes">
        {t('Notater (utstyr, skader, mål)')}
        <textarea rows="2" maxLength={500} value={notes} placeholder={t('F.eks. «trener hjemme med manualer, vondt i venstre kne»')}
                  onChange={(e) => setNotes(e.target.value)} />
        {notes !== memory.notes && (
          <button className="mini" onClick={() => save({ notes })}>{t('Lagre notater')}</button>
        )}
      </div>

      {error && <p className="error">{error}</p>}

      {adding && (
        <ExercisePicker
          title={t(adding === 'liked' ? 'Øvelse du liker' : 'Øvelse du ikke liker')}
          mine={mine}
          liked={memory.liked}
          disliked={memory.disliked}
          onClose={() => setAdding(null)}
          onPick={(name) => { add(adding, name); setAdding(null) }}
        />
      )}
    </div>
  )
}
