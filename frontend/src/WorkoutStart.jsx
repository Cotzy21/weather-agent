import { useState, useMemo } from 'react'
import { useI18n } from './i18n.jsx'
import { groupTemplates, plannedToday, CATEGORY_LABELS, exerciseNames } from './workoutCategories.js'
import { localIso } from './trainingStats.js'

/** Antall maler der kategoriene starter sammenfoldet (færre: alt er synlig med en gang). */
const COLLAPSE_ABOVE = 12

/**
 * Start-skjermen: her velger du hvilken økt du vil starte. Malene (lagrede økter) er sortert etter kategori (Push, Pull, Bein …
 * og aktivitetstypene), med søk og «sist gjort». En økt lagres først som gjennomført når den er startet herfra og fullført.
 */
export default function WorkoutStart({ plans, workouts, activityTypes, onStartPlan, onStartEmpty, onStartActivity }) {
  const { t } = useI18n()
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState({})

  const now = new Date()
  const todayIdx = (now.getDay() + 6) % 7
  const groups = useMemo(() => groupTemplates(plans, workouts, { query }), [plans, workouts, query])
  const dueToday = useMemo(
    () => (query.trim() ? [] : plannedToday(plans, workouts, localIso(now), todayIdx)),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [plans, workouts, query, todayIdx],
  )

  const many = (plans?.length ?? 0) > COLLAPSE_ABOVE
  const isOpen = (id) => (query.trim() ? true : open[id] ?? !many)
  const fmtDate = (iso) => new Date(`${iso}T00:00:00`).toLocaleDateString(undefined, { day: 'numeric', month: 'short' })

  const subtitle = ({ plan, lastDone }) => {
    const parts = []
    if (plan.type === 'STYRKE') {
      const n = exerciseNames(plan.content).length
      parts.push(t(n === 1 ? '1 øvelse' : '{n} øvelser', { n }))
    } else if (plan.content?.distanceKm || plan.content?.durationMin) {
      if (plan.content.distanceKm) parts.push(`${plan.content.distanceKm} km`)
      if (plan.content.durationMin) parts.push(`${plan.content.durationMin} min`)
    }
    if (lastDone) parts.push(t('sist {date}', { date: fmtDate(lastDone) }))
    return parts.join(' · ')
  }

  const row = (item) => (
    <li key={item.plan.id} className="plan-card">
      <button className="plan-card-main" onClick={() => onStartPlan(item.plan)}>
        <strong>{item.plan.title}</strong>
        <span className="muted">{subtitle(item)}</span>
      </button>
      <button className="plan-play" aria-label={t('Start {name}', { name: item.plan.title })} onClick={() => onStartPlan(item.plan)}>▶</button>
    </li>
  )

  return (
    <div className="start-screen">
      <h2 className="detail-title" data-reveal>{t('Start økt')}</h2>
      <p className="muted start-intro">{t('Velg hvilken økt du vil starte. Den regnes som gjennomført først når du fullfører den.')}</p>

      <div className="start-quick">
        <button className="primary" onClick={onStartEmpty}>▶ {t('Tom styrkeøkt')}</button>
      </div>
      <p className="sheet-label">{t('Annen aktivitet')}</p>
      <div className="type-select">
        {activityTypes.map((tp) => (
          <button key={tp.v} className="type-chip" onClick={() => onStartActivity(tp.v)}>{t(tp.t)}</button>
        ))}
      </div>

      <h3 className="detail-h3">{t('Mine økter')}</h3>
      {(plans?.length ?? 0) === 0 ? (
        <p className="muted">{t('Du har ingen lagrede økter ennå. Lag en under «Ny» (bygg selv, velg muskler eller AI), så dukker den opp her.')}</p>
      ) : (
        <>
          {plans.length > 4 && (
            <input className="start-search" type="search" value={query} placeholder={t('Søk i øktene dine')}
                   aria-label={t('Søk i øktene dine')} onChange={(e) => setQuery(e.target.value)} />
          )}

          {dueToday.length > 0 && (
            <section className="start-group">
              <h4 className="start-group-head static">📅 {t('Planlagt i dag')}</h4>
              <ul className="plan-cards">{dueToday.map((p) => row({ plan: p, lastDone: null }))}</ul>
            </section>
          )}

          {groups.length === 0 && <p className="muted">{t('Ingen økter passer med søket.')}</p>}
          {groups.map((g) => (
            <section className="start-group" key={g.id}>
              <button className="start-group-head" aria-expanded={isOpen(g.id)}
                      onClick={() => setOpen((o) => ({ ...o, [g.id]: !isOpen(g.id) }))}>
                <span>{t(CATEGORY_LABELS[g.id])}</span>
                <span className="muted">{g.items.length}</span>
                <span className="start-chevron" aria-hidden="true">{isOpen(g.id) ? '▾' : '▸'}</span>
              </button>
              {isOpen(g.id) && <ul className="plan-cards">{g.items.map(row)}</ul>}
            </section>
          ))}
        </>
      )}
    </div>
  )
}
