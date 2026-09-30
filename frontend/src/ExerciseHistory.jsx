import { useState } from 'react'
import { useI18n } from './i18n.jsx'
import ChoiceChips from './ChoiceChips.jsx'
import { exerciseHistory } from './liveSession.js'

// Tidligere økter for én øvelse: i denne planen (samme økt-tittel) eller alle.
export default function ExerciseHistory({ name, planTitle, workouts, fmtKg, onClose }) {
  const { t } = useI18n()
  const all = exerciseHistory(workouts, name)
  const inPlan = planTitle ? all.filter((h) => h.title === planTitle) : []
  const [scope, setScope] = useState(inPlan.length ? 'plan' : 'all')
  const rows = scope === 'plan' ? inPlan : all

  return (
    <div className="sheet-backdrop" onClick={onClose}>
      <div className="sheet picker" role="dialog" aria-label={name} onClick={(e) => e.stopPropagation()}>
        <span className="sheet-handle" aria-hidden="true" />
        <div className="picker-head">
          <strong>{name}</strong>
          <button className="del" onClick={onClose} aria-label={t('Lukk')}>✕</button>
        </div>
        {planTitle && (
          <ChoiceChips label={t('Historikk')} value={scope} onChange={setScope}
                       options={[{ v: 'plan', t: t('I denne planen') }, { v: 'all', t: t('Alle økter') }]} />
        )}
        <div className="picker-list">
          {rows.length === 0 && <p className="muted">{t('Ingen tidligere sett for denne øvelsen.')}</p>}
          {rows.map((h, i) => (
            <div className="hist-entry" key={`${h.id}-${i}`}>
              <div className="hist-date"><strong>{h.date}</strong><span className="muted">{h.title}</span></div>
              <div className="hist-sets">
                {h.sets.map((s, si) => (
                  <span key={si}>{si + 1}. {s.reps} × {s.weightKg > 0 ? `${fmtKg(s.weightKg)} kg` : t('kroppsvekt')}</span>
                ))}
              </div>
              {h.note && <p className="muted hist-note">✎ {h.note}</p>}
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
