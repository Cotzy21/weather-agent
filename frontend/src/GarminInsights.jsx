import { useEffect, useId, useState } from 'react'
import { authHeaders } from './supabase'
import { apiUrl, readError } from './api'
import { useI18n } from './i18n.jsx'
import { localIso } from './trainingStats.js'
import { pairDays, spearman } from './garminAnalysis.js'
import './garminInsights.css'

const PATH = '/api/recovery/garmin'
const GROUPS = {
  sleep: ['Søvn og døgnrytme', {
    sleepMin: ['Søvn', 'min'], sleepScore: ['Søvnscore', '/100'], bedtime: ['Leggetid', 'clock'],
    wakeTime: ['Oppvåkning', 'clock'], deepSleepMin: ['Dyp søvn', 'min'], lightSleepMin: ['Lett søvn', 'min'],
    remSleepMin: ['REM-søvn', 'min'], awakeMin: ['Våken om natta', 'min'], napMin: ['Lur', 'min'],
    sleepHr: ['Nattpuls', 'bpm'], sleepRespiration: ['Nattlig respirasjon', '/min'],
    sleepSpo2: ['Nattlig SpO₂', '%'], awakenings: ['Oppvåkninger', ''],
  }],
  recovery: ['Puls og restitusjon', {
    hrv: ['HRV sist natt', 'ms'], hrvPeak: ['Høyeste nattlige HRV', 'ms'], weeklyHrv: ['HRV ukesnitt', 'ms'], restingHr: ['Hvilepuls', 'bpm'],
    dailyMinHr: ['Laveste puls', 'bpm'], dailyMaxHr: ['Høyeste puls', 'bpm'], weeklyRestingHr: ['Hvilepuls ukesnitt', 'bpm'],
    stress: ['Stress', '/100'], stressMax: ['Høyeste stress', '/100'],
    bodyBattery: ['Body Battery', '/100'], bodyBatteryHigh: ['Body Battery høy', '/100'],
    bodyBatteryLow: ['Body Battery lav', '/100'], bodyBatteryCharged: ['Body Battery ladet', ''],
    bodyBatteryDrained: ['Body Battery brukt', ''], respiration: ['Respirasjon', '/min'], spo2: ['SpO₂', '%'],
    spo2Low: ['Laveste SpO₂', '%'], weeklySpo2: ['SpO₂ ukesnitt', '%'],
  }],
  movement: ['Bevegelse og energi', {
    steps: ['Skritt', ''], stepGoal: ['Skrittmål', ''], floors: ['Etasjer', ''],
    moderateMin: ['Moderat aktivitet', 'min'], vigorousMin: ['Hard aktivitet', 'min'],
    activeKcal: ['Aktiv energi', 'kcal'], restingKcal: ['Hvileenergi', 'kcal'],
    workouts: ['Loggede økter', ''], workoutMin: ['Logget treningstid', 'min'],
  }],
  body: ['Kropp og væske', {
    weightKg: ['Vekt', 'kg'], bmi: ['BMI', ''], bodyFat: ['Kroppsfett', '%'], bodyWater: ['Kroppsvann', '%'],
    muscleMass: ['Muskelmasse', ''], boneMass: ['Benmasse', ''], visceralFat: ['Visceralt fett', ''],
    metabolicAge: ['Metabolsk alder', ''], fitnessAge: ['Kondisjonsalder', ''], fitnessAgeGoal: ['Oppnåelig kondisjonsalder', ''], waterMl: ['Vann', 'ml'], waterGoalMl: ['Vannmål', 'ml'],
    sweatLossMl: ['Estimert væsketap', 'ml'],
  }],
  food: ['Mat og næring', {
    appFoodKcal: ['Energi i Turvær', 'kcal'], appProteinG: ['Protein i Turvær', 'g'],
    appCarbG: ['Karbohydrater i Turvær', 'g'], appFatG: ['Fett i Turvær', 'g'], appEveningKcal: ['Kveldsmat i Turvær', 'kcal'],
    garminFoodKcal: ['Energi i Garmin', 'kcal'], garminProteinG: ['Protein i Garmin', 'g'],
    garminCarbG: ['Karbohydrater i Garmin', 'g'], garminFatG: ['Fett i Garmin', 'g'],
  }],
}

const METRICS = {
  ...Object.assign({}, ...Object.values(GROUPS).map(([, metrics]) => metrics)),
  loggedWorkout: ['Økt logget', ''],
  bedtimeShift: ['Endring i leggetid', 'min'],
}

const PAIRS = [
  ['Mat og søvn · Turvær', 'appFoodKcal', 'sleepMin', 1],
  ['Mat og søvn · Garmin', 'garminFoodKcal', 'sleepMin', 1],
  ['Treningsdag og søvn', 'loggedWorkout', 'sleepMin', 1],
  ['Treningstid og søvnscore', 'workoutMin', 'sleepScore', 1],
  ['Trening og døgnrytme', 'loggedWorkout', 'bedtimeShift', 1],
  ['Skritt og HRV', 'steps', 'hrv', 1],
  ['Stress og søvn', 'stress', 'sleepMin', 1],
  ['Kveldsmat og søvn', 'appEveningKcal', 'sleepMin', 1],
]

async function request(path = PATH, options = {}) {
  const response = await fetch(apiUrl(path), {
    ...options, cache: 'no-store', headers: { ...await authHeaders(), ...options.headers },
  })
  if (!response.ok) throw new Error(await readError(response))
  return response.status === 204 ? null : response.json()
}

function number(value, lang) {
  return new Intl.NumberFormat(lang === 'en' ? 'en-GB' : 'nb-NO', { maximumFractionDigits: 2 }).format(value)
}

function format(value, unit, lang) {
  if (unit === 'clock') {
    const minute = Math.floor(value) % 1440
    return `${String(Math.floor(minute / 60)).padStart(2, '0')}:${String(minute % 60).padStart(2, '0')}`
  }
  return `${number(value, lang)}${unit.startsWith('/') ? '' : unit ? ' ' : ''}${unit}`
}

function formatDate(date, lang) {
  return new Intl.DateTimeFormat(lang === 'en' ? 'en-GB' : 'nb-NO', {
    day: 'numeric', month: 'short', timeZone: 'UTC',
  }).format(new Date(`${date}T12:00:00Z`))
}

function useDays(days) {
  const [snapshot, setSnapshot] = useState(null)
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)
  useEffect(() => {
    let cancelled = false
    request(`${PATH}?days=${days}`).then((result) => {
      if (!cancelled) { setSnapshot({ days, result }); setError('') }
    }).catch(() => {
      if (!cancelled) setError('Kunne ikke hente Garmin-data. Prøv igjen om litt.')
    })
    return () => { cancelled = true }
  }, [days, revision])
  return { data: snapshot?.days === days ? snapshot.result : null, error, refresh: () => setRevision((value) => value + 1) }
}

export function GarminHomeSummary({ onOpen }) {
  const { t, lang } = useI18n()
  const { data } = useDays(7)
  const latest = data?.findLast(({ metrics }) => ['sleepMin', 'hrv', 'restingHr', 'steps'].some((key) => Number.isFinite(metrics[key])))
  if (!latest) return null
  return (
    <section className="garmin-insights garmin-insights-home">
      <div className="garmin-heading">
        <h2>{t('Garmin · siste dag med målinger')}</h2>
        <button type="button" className="train-action" onClick={onOpen}>{t('Åpne innsikt')}</button>
      </div>
      <p className="muted">{formatDate(latest.date, lang)}</p>
      <dl className="garmin-metrics">
        {['sleepMin', 'hrv', 'restingHr', 'steps'].filter((key) => Number.isFinite(latest.metrics[key])).map((key) => (
          <div key={key}><dt>{t(METRICS[key][0])}</dt><dd>{format(latest.metrics[key], METRICS[key][1], lang)}</dd></div>
        ))}
      </dl>
    </section>
  )
}

export function GarminNutritionSummary() {
  const { t, lang } = useI18n()
  const { data } = useDays(14)
  const keys = ['garminFoodKcal', 'garminProteinG', 'garminCarbG', 'garminFatG', 'waterMl']
  const latest = data?.findLast(({ metrics }) => keys.some((key) => Number.isFinite(metrics[key])))
  if (!latest) return null
  return (
    <section className="garmin-insights">
      <h2>{t('Mat og væske fra Garmin')}</h2>
      <p className="muted">{formatDate(latest.date, lang)} · {t('Vises separat fra matdagboken.')}</p>
      <dl className="garmin-metrics">
        {keys.filter((key) => Number.isFinite(latest.metrics[key])).map((key) => (
          <div key={key}><dt>{t(METRICS[key][0])}</dt><dd>{format(latest.metrics[key], METRICS[key][1], lang)}</dd></div>
        ))}
      </dl>
    </section>
  )
}

export default function GarminInsights() {
  const { t } = useI18n()
  const [range, setRange] = useState(30)
  const { data, error, refresh } = useDays(range)
  const day = data?.findLast(({ metrics }) => Object.keys(metrics).some((key) => !key.startsWith('app') && key !== 'workouts' && key !== 'workoutMin'))
  return (
    <section className="garmin-insights">
      <div className="garmin-heading"><h2>{t('Garmin og sammenhenger')}</h2>
        <label>{t('Periode')} <select value={range} onChange={(event) => setRange(Number(event.target.value))}>
          {[14, 30, 60, 90].map((days) => <option key={days} value={days}>{t('{n} dager', { n: days })}</option>)}
        </select></label>
      </div>
      <GarminSync onChange={refresh} />
      {error && <p role="alert">{t(error)}</p>}
      {!data && !error && <p className="muted" role="status">{t('Laster …')}</p>}
      {data && !day && <p className="muted">{t('Importer Garmin-data for å se målinger og sammenhenger over tid.')}</p>}
      {day && <GarminTrends days={data} />}
      {data && <GarminCorrelations days={data} />}
    </section>
  )
}

function GarminSync({ onChange }) {
  const { t } = useI18n()
  const keyId = useId()
  const today = localIso(new Date())
  const [group, setGroup] = useState('WELLNESS')
  const [to, setTo] = useState(today)
  const [length, setLength] = useState(14)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [saved, setSaved] = useState(null)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [deleted, setDeleted] = useState(false)

  async function sync(event) {
    event.preventDefault()
    const input = event.currentTarget.elements.namedItem('key')
    const apiKey = input.value.trim()
    if (!apiKey || busy) return
    input.value = ''
    setBusy(true)
    setError('')
    setSaved(null)
    setDeleted(false)
    try {
      const start = new Date(`${to}T12:00:00Z`)
      start.setUTCDate(start.getUTCDate() - length + 1)
      const result = await request(PATH, { method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ apiKey, group, from: start.toISOString().slice(0, 10), to }),
      })
      setSaved(result.days)
      onChange()
    } catch (e) {
      setError(e instanceof TypeError ? 'Kunne ikke hente Garmin-data. Prøv igjen om litt.' : e.message)
    } finally {
      setBusy(false)
    }
  }

  async function erase() {
    setBusy(true)
    setError('')
    try {
      await request(PATH, { method: 'DELETE' })
      setConfirmDelete(false)
      setSaved(null)
      setDeleted(true)
      onChange()
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <details className="garmin-sync">
      <summary>{t('Importer eller slett Garmin-målinger')}</summary>
      <p className="muted">{t('Velg hvilke data du vil lagre. Nøkkelen lagres ikke. Du kan slette de importerte målingene her.')}</p>
      <form method="post" onSubmit={sync} autoComplete="off" aria-busy={busy}>
        <fieldset disabled={busy} className="garmin-sync-fields">
          <label>{t('Datasett')}<select value={group} onChange={(event) => { setGroup(event.target.value); setLength(event.target.value === 'FOOD' ? 7 : event.target.value === 'DETAIL' ? 1 : 14) }}>
            <option value="WELLNESS">{t('Søvn, HRV og daglig velvære')}</option>
            <option value="BODY">{t('Vekt og væske')}</option>
            <option value="FOOD">{t('Mat og makroer fra Garmin')}</option>
            <option value="DETAIL">{t('Puls, SpO₂ og kondisjonsalder · én dag')}</option>
          </select></label>
          <label>{t('Til og med')}<input type="date" value={to} max={today} required onChange={(event) => setTo(event.target.value)} /></label>
          <label>{t('Antall dager')}<select value={length} onChange={(event) => setLength(Number(event.target.value))}>
            {(group === 'FOOD' ? [1, 3, 7] : group === 'DETAIL' ? [1] : [1, 7, 14, 28]).map((days) => <option key={days}>{days}</option>)}
          </select></label>
          <label htmlFor={keyId} className="garmin-sync-key">{t('Personlig Garmin API-nøkkel')}
            <input id={keyId} name="key" type="password" required maxLength={1024} autoComplete="off" autoCapitalize="none" spellCheck={false} />
          </label>
          <button type="submit" className="primary">{t(busy ? 'Henter fra Garmin …' : 'Importer målinger')}</button>
        </fieldset>
      </form>
      {saved !== null && <p role="status">{t(saved ? '{n} dager lagret eller oppdatert.' : 'Garmin hadde ingen målinger i perioden.', { n: saved })}</p>}
      {deleted && <p role="status">{t('Garmin-målingene er slettet.')}</p>}
      {error && <p className="error" role="alert">{t(error)}</p>}
      {!confirmDelete && <button type="button" className="train-action" disabled={busy} onClick={() => setConfirmDelete(true)}>{t('Slett importerte målinger')}</button>}
      {confirmDelete && (
        <div className="garmin-delete">
          <p>{t('Slette alle Garmin-målinger fra Hjem og Restitusjon? Treningsloggen beholdes.')}</p>
          <button type="button" className="train-action" disabled={busy} onClick={erase}>{t('Ja, slett målingene')}</button>
          <button type="button" className="train-action" disabled={busy} onClick={() => setConfirmDelete(false)}>{t('Avbryt')}</button>
        </div>
      )}
    </details>
  )
}

function GarminTrends({ days }) {
  const { t, lang } = useI18n()
  const [metric, setMetric] = useState('')
  const available = Object.entries(GROUPS).map(([id, [title, values]]) =>
    [id, title, Object.keys(values).filter((key) => days.some(({ metrics }) => Number.isFinite(metrics[key])))])
    .filter(([, , keys]) => keys.length)
  const selected = available.some(([, , keys]) => keys.includes(metric)) ? metric : available[0]?.[2][0]
  if (!selected) return null
  const values = days.filter(({ metrics }) => Number.isFinite(metrics[selected]))
  const [title, unit] = METRICS[selected]
  const last = values.at(-1)
  return (
    <div className="garmin-trends">
      <div className="garmin-heading"><h3>{t('Målinger over tid')}</h3>
        <label>{t('Måling')} <select value={selected} onChange={(event) => setMetric(event.target.value)}>
          {available.map(([id, label, keys]) => <optgroup key={id} label={t(label)}>
            {keys.map((key) => <option key={key} value={key}>{t(METRICS[key][0])}</option>)}
          </optgroup>)}
        </select></label>
      </div>
      <p className="garmin-latest"><strong>{format(last.metrics[selected], unit, lang)}</strong> <span className="muted">{t(title)} · {formatDate(last.date, lang)}</span></p>
      {values.length > 1 && unit !== 'clock' && <Plot points={values.map(({ date, metrics }) => ({
        x: Date.parse(date) / 86400000, y: metrics[selected], date,
      }))} timeline label={t(title)} unit={unit} />}
      <details><summary>{t('Se målingene per dato')}</summary>
        <ul className="garmin-day-list">{values.toReversed().map(({ date, metrics }) => (
          <li key={date}><time dateTime={date}>{formatDate(date, lang)}</time><strong>{format(metrics[selected], unit, lang)}</strong></li>
        ))}</ul>
      </details>
    </div>
  )
}

function GarminCorrelations({ days }) {
  const { t, lang } = useI18n()
  const [preset, setPreset] = useState(2)
  const [custom, setCustom] = useState({ x: 'appFoodKcal', y: 'sleepMin', lag: 1 })
  const [, presetX, presetY, presetLag] = PAIRS[preset] ?? []
  const { x, y, lag } = preset < 0 ? custom : { x: presetX, y: presetY, lag: presetLag }
  const pairs = pairDays(days, x, y, lag)
  const result = spearman(pairs)
  return (
    <div className="garmin-correlations">
      <h3>{t('Sammenhenger i din logg')}</h3>
      <div className="garmin-comparisons">
        <label>{t('Sammenligning')}<select value={preset} onChange={(event) => setPreset(Number(event.target.value))}>
          {PAIRS.map(([label], i) => <option key={label} value={i}>{t(label)}</option>)}
          <option value={-1}>{t('Velg selv')}</option>
        </select></label>
        {preset < 0 && <>
          <label>{t('Første måling')}<select value={x} onChange={(event) => setCustom({ ...custom, x: event.target.value })}>
            {Object.entries(METRICS).filter(([, [, unit]]) => unit !== 'clock').map(([key, [label]]) => <option key={key} value={key}>{t(label)}</option>)}
          </select></label>
          <label>{t('Andre måling')}<select value={y} onChange={(event) => setCustom({ ...custom, y: event.target.value })}>
            {Object.entries(METRICS).filter(([key, [, unit]]) => key !== 'loggedWorkout' && unit !== 'clock').map(([key, [label]]) =>
              <option key={key} value={key}>{t(label)}</option>)}
          </select></label>
          <label>{t('Koble datoer')}<select value={lag} onChange={(event) => setCustom({ ...custom, lag: Number(event.target.value) })}>
            <option value={0}>{t('Samme dato')}</option><option value={1}>{t('Neste dato')}</option>
          </select></label>
        </>}
      </div>
      <p className="muted">{t(lag ? 'Første måling kobles til neste dato. Søvn står på datoen du våknet.' : 'Målingene kobles på samme dato.')}</p>
      {x === y && lag === 0 ? <p role="status">{t('Velg to ulike målinger på samme dato.')}</p>
        : result.reason === 'few' ? <p role="status">{t('{n} datapar. Minst 10 trengs før vi viser en korrelasjon.', { n: result.count })}</p>
        : result.reason === 'constant' ? <p role="status">{t('Målingene varierer for lite til å beregne korrelasjon.')}</p>
        : <>
          <p className="garmin-latest"><strong>ρ {number(result.rho, lang)}</strong> <span>{t('{n} datapar · Spearman', { n: result.count })}</span></p>
          <Plot points={pairs} label={`${t(METRICS[x][0])} → ${t(METRICS[y][0])}`} unit={METRICS[y][1]} xUnit={METRICS[x][1]} />
          <p className="muted">{t('Positiv verdi betyr at målingene gjerne øker sammen. Negativ verdi betyr at de ofte går hver sin vei. Korrelasjon viser ikke årsak.')}</p>
        </>}
      {x === 'loggedWorkout' && <p className="muted">{t('En dag uten logget økt kan fortsatt ha vært en aktiv dag.')}</p>}
    </div>
  )
}

function Plot({ points, timeline, label, unit, xUnit = '' }) {
  const { lang } = useI18n()
  const xs = points.map((point) => point.x)
  const ys = points.map((point) => point.y)
  const x0 = Math.min(...xs), x1 = Math.max(...xs)
  const y0 = Math.min(...ys), y1 = Math.max(...ys)
  const x = (n) => x1 === x0 ? 174 : 12 + (n - x0) / (x1 - x0) * 324
  const y = (n) => y1 === y0 ? 66 : 120 - (n - y0) / (y1 - y0) * 108
  const lines = timeline ? points.slice(1).map((point, index) => [points[index], point])
    .filter(([previous, point]) => point.x - previous.x === 1) : []
  return (
    <figure className="garmin-plot">
      <div className="garmin-axis"><span>{format(y1, unit, lang)}</span><span>{format(y0, unit, lang)}</span></div>
      <svg viewBox="0 0 348 132" role="img" aria-label={label}>
        {[12, 66, 120].map((level) => <line key={level} className="garmin-gridline" x1="12" x2="336" y1={level} y2={level} />)}
        {lines.map(([previous, point]) => <line className="garmin-plot-line" key={point.date} x1={x(previous.x)} y1={y(previous.y)} x2={x(point.x)} y2={y(point.y)} />)}
        {points.map((point) => <circle key={point.date} className="garmin-dot" cx={x(point.x)} cy={y(point.y)} r={timeline ? 3 : 4}>
          <title>{formatDate(point.date, lang)} · {format(point.y, unit, lang)}</title>
        </circle>)}
      </svg>
      <figcaption>
        <span>{timeline ? formatDate(points[0].date, lang) : format(x0, xUnit, lang)}</span>
        <span>{timeline ? formatDate(points.at(-1).date, lang) : format(x1, xUnit, lang)}</span>
      </figcaption>
    </figure>
  )
}
