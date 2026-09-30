import { useI18n } from './i18n.jsx'
import { dayIndex, strengthVolume, countSets, localIso } from './trainingStats.js'

const SHORT = ['Man', 'Tir', 'Ons', 'Tor', 'Fre', 'Lør', 'Søn']

// Denne uka med planene dine: avhuket når det er logget en økt den dagen,
// dagens økt kan startes med ett trykk, og totaler for all styrketrening.
export default function WeekProgram({ plans, workouts, onStart }) {
  const { t, lang } = useI18n()
  const now = new Date()
  const todayIdx = (now.getDay() + 6) % 7 // mandag = 0
  const monday = new Date(now.getFullYear(), now.getMonth(), now.getDate() - todayIdx)

  const days = Array.from({ length: 7 }, (_, i) => {
    const day = new Date(monday.getFullYear(), monday.getMonth(), monday.getDate() + i)
    const date = localIso(day)
    const planned = plans.filter((p) => dayIndex(p.content?.day) === i)
    const logged = workouts.filter((w) => w.date === date)
    let status = 'rest'
    if (logged.length) status = 'done'
    else if (i === todayIdx && planned.length) status = 'today'
    else if (planned.length) status = i < todayIdx ? 'missed' : 'planned'
    return { i, date, dayNum: day.getDate(), planned, logged, status }
  })

  const today = days[todayIdx]
  const todayPlan = today.status === 'today' ? today.planned[0] : null
  const strength = workouts.filter((w) => w.type === 'STYRKE')
  const totalKg = Math.round(strength.reduce((sum, w) => sum + strengthVolume(w.content), 0))
  const totalSets = strength.reduce((sum, w) => sum + countSets(w.content), 0)
  const fmt = (n) => n.toLocaleString(lang === 'en' ? 'en-US' : 'nb-NO')
  const hasSchedule = plans.some((p) => dayIndex(p.content?.day) >= 0)

  if (!hasSchedule && !strength.length) return null

  return (
    <div className="week-program" data-reveal>
      {hasSchedule && (
        <>
          <span className="week-program-label">{t('Denne uka')}</span>
          <div className="week-dots">
            {days.map((d) => (
              <div className="week-dot-col" key={d.i}>
                <span className={`week-dot ${d.status} ${d.i === todayIdx ? 'is-today' : ''}`}
                      title={[...d.planned.map((p) => p.title), ...d.logged.map((w) => w.title)].join(', ')}>
                  {d.status === 'done' ? '✓' : d.status === 'rest' ? '·' : d.status === 'missed' ? '–' : d.dayNum}
                </span>
                <span className="week-dot-day">{t(SHORT[d.i])}</span>
              </div>
            ))}
          </div>
          {todayPlan && (
            <button className="week-start" onClick={() => onStart(todayPlan)}>
              ▶ {t('Start dagens økt')}: <strong>{todayPlan.title}</strong>
            </button>
          )}
        </>
      )}
      {strength.length > 0 && (
        <div className="week-totals">
          <div><strong>{fmt(totalKg)} kg</strong><span className="muted">{t('Totalt løftet')}</span></div>
          <div><strong>{fmt(totalSets)}</strong><span className="muted">{t('Sett logget')}</span></div>
        </div>
      )}
    </div>
  )
}
