import { useMemo, useState } from 'react'
import { useI18n } from './i18n.jsx'
import { exerciseTrends, sessionTrends } from './exerciseProgress.js'
import Sparkline from './Sparkline.jsx'

const INITIAL = 12

// Progresjon per øvelse over hele historikken (regnet ut her i nettleseren), og økter over tid per økttype
// (f.eks. Push/Pull/Legs) for økter som bare har totaler fra Garmin-CSV. `renderChart(name)` viser detaljgrafen.
export default function ExerciseTrends({ workouts, onOpen, openName, chart }) {
  const { t, lang } = useI18n()
  const [showAll, setShowAll] = useState(false)
  const trends = useMemo(() => exerciseTrends(workouts), [workouts])
  const sessions = useMemo(() => sessionTrends(workouts), [workouts])
  const fmt = (n) => (Math.round(n * 10) / 10).toLocaleString(lang === 'en' ? 'en-US' : 'nb-NO')
  const month = (iso) => new Date(`${iso}T00:00:00`).toLocaleDateString(lang === 'en' ? 'en-US' : 'nb-NO', { month: 'short', year: 'numeric' })
  const arrow = (pct) => (pct == null ? '' : pct > 0 ? '▲' : pct < 0 ? '▼' : '–')
  const cls = (pct) => (pct == null ? '' : pct > 0 ? 'up' : pct < 0 ? 'down' : '')

  if (!trends.length && !sessions.length) return null
  const shown = showAll ? trends : trends.slice(0, INITIAL)
  const metricLabel = { volume: t('volum'), reps: t('reps'), durationMin: t('minutter') }

  return (
    <>
      {trends.length > 0 && (
        <>
          <h3 className="detail-h3">{t('Utvikling per øvelse')}</h3>
          <p className="muted source-note">{t('Fra første til siste økt, for alle øktene dine (også de importerte).')}</p>
          <ul className="trend-list">
            {shown.map((tr) => (
              <li key={tr.key}>
                <button className={`trend-row ${openName === tr.name ? 'open' : ''}`} onClick={() => onOpen(tr.name)} aria-expanded={openName === tr.name}>
                  <span className="trend-main">
                    <strong>{tr.name}</strong>
                    <span className="muted">
                      {tr.weighted ? `${fmt(tr.first)} → ${fmt(tr.last)} kg` : `${tr.first} → ${tr.last} ${t('reps')}`}
                      {' · '}{t('{n} økter', { n: tr.sessions })}{' · '}{month(tr.firstDate)}–{month(tr.lastDate)}
                    </span>
                  </span>
                  <Sparkline values={tr.values} label={t('Trend for {name}', { name: tr.name })} />
                  <span className={`trend-pct ${cls(tr.changePct)}`}>{tr.changePct == null ? '' : `${arrow(tr.changePct)} ${Math.abs(tr.changePct)}%`}</span>
                </button>
                {openName === tr.name && chart}
              </li>
            ))}
          </ul>
          {trends.length > INITIAL && (
            <button className="mini" onClick={() => setShowAll(!showAll)}>
              {showAll ? t('Vis færre') : t('Vis alle {n} øvelser', { n: trends.length })}
            </button>
          )}
        </>
      )}

      {sessions.length > 0 && (
        <>
          <h3 className="detail-h3">{t('Økter over tid')}</h3>
          <p className="muted source-note">
            {t('Per økttype. Fra Garmin-CSV finnes bare totaler (sett og reps); last opp FIT-filer for vekter per øvelse.')}
          </p>
          <ul className="trend-list">
            {sessions.map((s) => (
              <li key={s.key} className="trend-row static">
                <span className="trend-main">
                  <strong>{s.title}</strong>
                  <span className="muted">
                    {t('{n} økter', { n: s.sessions })} · {month(s.firstDate)}–{month(s.lastDate)} · {metricLabel[s.metric]}
                    {s.latest.sets ? ` · ${t('sist {n} sett', { n: s.latest.sets })}` : ''}
                  </span>
                </span>
                <Sparkline values={s.values} label={t('Trend for {name}', { name: s.title })} />
                <span className={`trend-pct ${cls(s.changePct)}`}>{s.changePct == null ? '' : `${arrow(s.changePct)} ${Math.abs(s.changePct)}%`}</span>
              </li>
            ))}
          </ul>
        </>
      )}
    </>
  )
}
