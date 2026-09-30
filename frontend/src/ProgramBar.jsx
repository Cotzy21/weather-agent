import { useEffect, useState } from 'react'
import { authHeaders } from './supabase'
import { apiUrl, readError } from './api'
import { useI18n } from './i18n.jsx'
import ChoiceChips from './ChoiceChips.jsx'
import { localIso } from './trainingStats.js'

const WEEK_OPTIONS = [4, 6, 8, 12]

// «Dag 12/56»: hvor langt du er i programmet. Uten program kan du starte ett (startdato i dag + lengde).
export default function ProgramBar() {
  const { t } = useI18n()
  const [program, setProgram] = useState(undefined) // undefined = laster, null = ikke startet
  const [weeks, setWeeks] = useState(8)
  const [starting, setStarting] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    let alive = true
    ;(async () => {
      try {
        const res = await fetch(apiUrl('/api/trening/program'), { headers: await authHeaders() })
        if (alive && (res.status === 200 || res.status === 204)) setProgram(res.status === 200 ? await res.json() : null)
      } catch { /* sekundært */ }
    })()
    return () => { alive = false }
  }, [])

  async function start() {
    setError(null)
    try {
      const res = await fetch(apiUrl('/api/trening/program'), {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json', ...(await authHeaders()) },
        body: JSON.stringify({ startDate: localIso(new Date()), weeks }),
      })
      if (!res.ok) throw new Error(await readError(res))
      setProgram(await res.json())
      setStarting(false)
    } catch (e) { setError(e.message) }
  }

  async function end() {
    try {
      await fetch(apiUrl('/api/trening/program'), { method: 'DELETE', headers: await authHeaders() })
      setProgram(null)
    } catch { /* ignorer */ }
  }

  if (program === undefined) return null

  if (!program) {
    return (
      <div className="program-bar">
        {!starting ? (
          <button className="mini" onClick={() => setStarting(true)}>📆 {t('Start et program')}</button>
        ) : (
          <>
            <span className="muted">{t('Hvor mange uker?')}</span>
            <ChoiceChips options={WEEK_OPTIONS.map((w) => ({ v: w, t: `${w}` }))} value={weeks} onChange={setWeeks} label={t('Uker')} />
            <button className="mini" onClick={start}>{t('Start i dag')}</button>
            {error && <span className="error">{error}</span>}
          </>
        )}
      </div>
    )
  }

  const pct = Math.round((program.dayNumber / program.totalDays) * 100)
  const label = program.status === 'NOT_STARTED'
    ? t('Programmet starter {date}', { date: program.startDate })
    : program.status === 'FINISHED'
      ? t('Programmet er ferdig 🎉')
      : t('Dag {d}/{n} · uke {w} av {weeks}', { d: program.dayNumber, n: program.totalDays, w: program.weekNumber, weeks: program.weeks })
  return (
    <div className="program-bar">
      <strong>{label}</strong>
      <div className="program-progress" role="progressbar" aria-valuenow={pct} aria-valuemin="0" aria-valuemax="100">
        <span style={{ width: `${pct}%` }} />
      </div>
      <button className="mini" onClick={end}>{t('Avslutt')}</button>
    </div>
  )
}
