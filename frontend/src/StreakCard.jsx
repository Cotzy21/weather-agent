import { useMemo } from 'react'
import { computeStreak, togglePause, clampGoal, MIN_GOAL, MAX_GOAL } from './weeklyStreak.js'
import { useI18n } from './i18n.jsx'

// Tegn og forklaring per ukestatus i stripa over de siste ukene.
const DOT = {
  good: { glyph: '●', label: 'Mål nådd' },
  comeback: { glyph: '★', label: 'Comeback' },
  repaired: { glyph: '★', label: 'Serien reddet' },
  covered: { glyph: '🛡', label: 'Dekket av hvilekort' },
  pause: { glyph: '⏸', label: 'Pauset uke' },
  missed: { glyph: '○', label: 'Målet ble ikke nådd' },
  start: { glyph: '·', label: 'Første uke' },
  current: { glyph: '◔', label: 'Denne uka' },
}

/**
 * Ukeserie-kortet på Hjem: hvor mange uker på rad målet er nådd, ukas fremdrift, hvilekort og pause.
 * Tonen er bevisst tilgivende (se ENGAGEMENT.md): en glipp er ikke skam, og serien kan dekkes, pauses eller reddes med handling.
 */
export default function StreakCard({ workouts, settings, today, onChange }) {
  const { t } = useI18n()
  const r = useMemo(
    () => computeStreak(workouts, { goal: settings.goal, pauses: settings.pauses, today }),
    [workouts, settings, today],
  )
  const days = (n) => (n === 1 ? t('1 dag') : t('{n} dager', { n }))
  const cur = r.current

  let message
  if (r.state === 'empty') message = t('Logg din første økt for å starte serien. Målet er {goal} treningsdager i uka.', { goal: r.goal })
  else if (r.state === 'paused') message = t('⏸ Uka er pauset – serien er trygg.')
  else if (r.state === 'done') {
    message = cur.status === 'repaired' ? t('Serien er reddet! 🔥')
      : cur.status === 'comeback' ? t('Comeback! Du er i gang igjen – en ny serie er startet. 💪')
        : t('Målet for uka er nådd! 🎉')
  } else if (r.state === 'covered') message = t('Målet blir vanskelig å nå, men uka dekkes av et hvilekort 🛡️ – serien fortsetter.')
  else if (r.state === 'atRisk') message = t('Målet blir vanskelig å nå denne uka. Er du syk, skadet eller på ferie? Ta en pause, så bryter ikke serien.')
  else if (cur.days === 0 && cur.daysLeft === 7) message = t('Ny uke, ny start 🌱 Målet er {goal} treningsdager.', { goal: r.goal })
  else message = t('{left} igjen til målet. {daysLeft} igjen av uka.', { left: days(cur.remaining), daysLeft: days(cur.daysLeft) })

  const showRepair = r.repair && r.state !== 'paused'
  const setGoal = (g) => onChange({ ...settings, goal: clampGoal(g) })

  return (
    <div className="focus-card streak-card" data-reveal>
      <span className="focus-head">{t('🔥 Ukeserie')}</span>
      <div className="focus-main">
        <span className="focus-big">{r.streak}</span>
        <span className="focus-sub muted">{r.streak === 1 ? t('uke på rad') : t('uker på rad')}</span>
      </div>

      <div className="streak-progress" role="img"
           aria-label={t('{days} av {goal} treningsdager denne uka', { days: cur.days, goal: r.goal })}>
        {Array.from({ length: r.goal }, (_, i) => (
          <span key={i} className={`streak-pip ${i < cur.days ? 'on' : ''}`} />
        ))}
        <span className="streak-count muted">{cur.days}/{r.goal}</span>
      </div>

      <p className="focus-line streak-message">{message}</p>
      {showRepair && (
        <p className="focus-line streak-repair">
          {t('Fullfør {target} treningsdager denne uka for å redde serien på {broken} uker.', { target: r.repair.target, broken: r.repair.broken })}
        </p>
      )}

      {r.state !== 'empty' && (
        <div className="streak-weeks" aria-label={t('De siste ukene')}>
          {r.weeks.map((w) => {
            const d = DOT[w.status] ?? DOT.missed
            return (
              <span key={w.start} className={`streak-dot ${w.status}`} title={`${w.start}: ${t(d.label)}`}>
                <span aria-hidden="true">{d.glyph}</span>
                <span className="sr-only">{`${w.start}: ${t(d.label)}`}</span>
              </span>
            )
          })}
        </div>
      )}

      <p className="focus-line muted streak-stats">
        🛡 {t('Hvilekort: {n} av {max}', { n: r.cards, max: r.maxCards })} · {t('{n} av {every} gode uker til neste', { n: r.cardProgress, every: r.cardEvery })}
      </p>
      <p className="focus-line muted streak-stats">
        {t('Lengste serie: {n}', { n: r.longest })} · {t('Gode uker totalt: {n}', { n: r.goodWeeks })}
      </p>

      <div className="streak-controls">
        <span className="streak-goal">
          <button className="mini" aria-label={t('Færre dager per uke')} disabled={r.goal <= MIN_GOAL} onClick={() => setGoal(r.goal - 1)}>−</button>
          <span>{r.goal} {t('dager per uke')}</span>
          <button className="mini" aria-label={t('Flere dager per uke')} disabled={r.goal >= MAX_GOAL} onClick={() => setGoal(r.goal + 1)}>+</button>
        </span>
        <button className="mini" title={t('Pause uka når du er syk, skadet eller på ferie. Serien bryter ikke.')}
                onClick={() => onChange({ ...settings, pauses: togglePause(settings.pauses, cur.start) })}>
          {cur.paused ? t('▶ Opphev pause') : t('⏸ Pause uka')}
        </button>
      </div>
    </div>
  )
}
