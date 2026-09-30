import { useEffect } from 'react'
import { useI18n } from './i18n.jsx'

const PIECES = ['🎉', '✨', '🏆', '💪', '🎊', '⭐']

/**
 * Feiring når en økt slo en personlig rekord. Viser bare ekte tall fra brukerens egen historikk (se prDetection.js), med
 * forrige rekord ved siden av, så gevinsten er konkret. Konfettien er ren CSS og skrus av med «reduser bevegelse».
 */
export default function PrCelebration({ prs, fmtKg, onClose }) {
  const { t } = useI18n()
  useEffect(() => { navigator.vibrate?.([30, 60, 30]) }, [])

  const shown = prs.slice(0, 5)
  return (
    <div className="sheet-backdrop" onClick={onClose}>
      <div className="sheet pr-sheet" role="dialog" aria-label={t('Ny rekord')} onClick={(e) => e.stopPropagation()}>
        <span className="sheet-handle" aria-hidden="true" />
        <div className="pr-confetti" aria-hidden="true">
          {Array.from({ length: 14 }, (_, i) => (
            <span key={i} style={{ left: `${(i * 7 + 4) % 96}%`, animationDelay: `${(i % 7) * 0.12}s` }}>{PIECES[i % PIECES.length]}</span>
          ))}
        </div>
        <h3 className="pr-title">🏆 {prs.length === 1 ? t('Ny rekord!') : t('{n} nye rekorder!', { n: prs.length })}</h3>
        <ul className="pr-list">
          {shown.map((p) => (
            <li key={p.key}>
              <strong>{p.name}</strong>
              {p.kind === 'e1rm' ? (
                <span className="muted">
                  {t('Estimert 1RM {value} kg', { value: fmtKg(p.value) })} (+{fmtKg(p.gain)}) · {fmtKg(p.kg)} kg × {p.reps}
                  {p.heaviest ? ` · ${t('tyngste vekt noensinne')}` : ''}
                </span>
              ) : (
                <span className="muted">{t('{value} reps (forrige rekord: {previous})', { value: p.value, previous: p.previous })}</span>
              )}
            </li>
          ))}
        </ul>
        {prs.length > shown.length && <p className="muted">{t('+ {n} til', { n: prs.length - shown.length })}</p>}
        <button className="primary" onClick={onClose}>{t('Kjempebra!')}</button>
      </div>
    </div>
  )
}
