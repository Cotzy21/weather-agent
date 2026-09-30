import { useI18n } from './i18n.jsx'
import { dayIndex, strengthVolume, countSets, localIso, matchesPlan } from './trainingStats.js'
import ProgramBar from './ProgramBar.jsx'

const SHORT = ['Man', 'Tir', 'Ons', 'Tor', 'Fre', 'Lør', 'Søn']

// Morgen før ettermiddag før kveld; uten tidspunkt havner økta i midten.
function timeRank(time) {
  const t = (time || '').toLowerCase()
  if (/morg|morn|\bam\b/.test(t)) return 0
  if (/kveld|even|night|\bpm\b/.test(t)) return 2
  return 1
}

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
      .sort((a, b) => timeRank(a.content?.time) - timeRank(b.content?.time))
    const logged = workouts.filter((w) => w.date === date)
    // En dag kan ha flere økter (f.eks. styrke om morgenen, BJJ om kvelden).
    let status = 'rest'
    if (logged.length && logged.length >= planned.length) status = 'done'
    else if (logged.length) status = 'partial'
    else if (i === todayIdx && planned.length) status = 'today'
    else if (planned.length) status = i < todayIdx ? 'missed' : 'planned'
    const remaining = planned.filter((p) => !logged.some((w) => matchesPlan(w, p)))
    return { i, date, dayNum: day.getDate(), planned, logged, remaining, status }
  })

  const today = days[todayIdx]
  // Samme tittel som en logget økt = gjort; de andre kan fortsatt startes i dag.
  const todayPlans = today.logged.length >= today.planned.length && today.logged.length ? [] : today.remaining
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
          <ProgramBar />
          <div className="week-dots">
            {days.map((d) => (
              <div className="week-dot-col" key={d.i}>
                <span className={`week-dot ${d.status} ${d.i === todayIdx ? 'is-today' : ''}`}
                      title={[...d.planned.map((p) => p.title), ...d.logged.map((w) => w.title)].join(', ')}>
                  {d.status === 'done' ? '✓' : d.status === 'partial' ? `${d.logged.length}/${d.planned.length}` : d.status === 'rest' ? '·' : d.status === 'missed' ? '–' : d.dayNum}
                </span>
                <span className="week-dot-day">{t(SHORT[d.i])}</span>
              </div>
            ))}
          </div>
          {todayPlans.map((p) => (
            <button className="week-start" key={p.id} onClick={() => onStart(p)}>
              ▶ {t('Start dagens økt')}: <strong>{p.title}</strong>{p.content?.time ? ` · ${p.content.time}` : ''}
            </button>
          ))}
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
