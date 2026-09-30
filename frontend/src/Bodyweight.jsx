import { useState } from 'react'
import { useI18n } from './i18n.jsx'
import ChoiceChips from './ChoiceChips.jsx'
import { localIso } from './trainingStats.js'

const RANGES = [
  { v: 'all', days: null, t: 'Alle' },
  { v: '1w', days: 7, t: '1U' },
  { v: '1m', days: 30, t: '1M' },
  { v: '3m', days: 91, t: '3M' },
  { v: '6m', days: 182, t: '6M' },
  { v: '1y', days: 365, t: '1Å' },
]

const daysAgo = (n) => { const d = new Date(); d.setDate(d.getDate() - n); return localIso(d) }

function useFormat() {
  const { lang } = useI18n()
  const locale = lang === 'en' ? 'en-US' : 'nb-NO'
  return {
    kg: (v) => v.toLocaleString(locale, { minimumFractionDigits: 1, maximumFractionDigits: 1 }),
    date: (iso, opts = { day: 'numeric', month: 'short' }) => new Date(`${iso}T12:00`).toLocaleDateString(locale, opts),
  }
}

function Delta({ value, suffix }) {
  const f = useFormat()
  if (value == null || Math.abs(value) < 0.05) return <span className="muted">± 0 kg {suffix}</span>
  return (
    <span className="bw-delta">
      <strong>{value < 0 ? '↓' : '↑'} {f.kg(Math.abs(value))} kg</strong> <span className="muted">{suffix}</span>
    </span>
  )
}

// Ark for å legge inn en veiing (i dag som standard).
function WeighInSheet({ last, onSave, onClose }) {
  const { t } = useI18n()
  const [kg, setKg] = useState(last ? String(last.weightKg) : '')
  const [date, setDate] = useState(localIso(new Date()))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)

  async function save() {
    const value = Number(String(kg).replace(',', '.'))
    if (!value) { setError(t('Skriv inn vekten din.')); return }
    setBusy(true)
    setError(null)
    try {
      await onSave({ date, weightKg: value })
      onClose()
    } catch (e) {
      setError(e.message)
      setBusy(false)
    }
  }

  return (
    <div className="sheet-backdrop" onClick={onClose}>
      <div className="sheet confirm-sheet" role="dialog" aria-label={t('Vei deg')} onClick={(e) => e.stopPropagation()}>
        <span className="sheet-handle" aria-hidden="true" />
        <p className="confirm-text">{t('Vei deg')}</p>
        <div className="bw-input-row">
          <input className="bw-input" inputMode="decimal" autoFocus value={kg} aria-label={t('Vekt (kg)')}
                 onChange={(e) => setKg(e.target.value)} onKeyDown={(e) => { if (e.key === 'Enter') save() }} />
          <span className="bw-unit">kg</span>
        </div>
        <input type="date" value={date} max={localIso(new Date())} onChange={(e) => setDate(e.target.value)} aria-label={t('Dato')} />
        {error && <p className="error">{error}</p>}
        <button className="primary" disabled={busy} onClick={save}>{busy ? t('Lagrer …') : t('Lagre')}</button>
        <button className="confirm-keep" onClick={onClose}>{t('Avbryt')}</button>
      </div>
    </div>
  )
}

// Kort på Hjem: siste veiing, endring denne måneden, veiinger siste 30 dager og siste 7 målinger.
export function BodyweightCard({ entries, onOpen, onLog }) {
  const { t } = useI18n()
  const f = useFormat()
  const [weighing, setWeighing] = useState(false)
  const last = entries[entries.length - 1]
  const monthStart = localIso(new Date()).slice(0, 8) + '01'
  const beforeMonth = [...entries].reverse().find((e) => e.date < monthStart)
  const firstThisMonth = entries.find((e) => e.date >= monthStart)
  const base = beforeMonth ?? firstThisMonth
  const logged = new Set(entries.map((e) => e.date))
  const grid = Array.from({ length: 30 }, (_, i) => daysAgo(29 - i))
  const weekStart = (() => { const d = new Date(); d.setDate(d.getDate() - ((d.getDay() + 6) % 7)); return localIso(d) })()
  const thisWeek = entries.filter((e) => e.date >= weekStart).length
  const last7 = entries.slice(-7)

  return (
    <section className="bw-card" data-reveal>
      <div className="bw-head">
        <h3 className="detail-h3">{t('Kroppsvekt')}</h3>
        {entries.length > 0 && <button className="linklike" onClick={onOpen}>{t('Se alt')} ›</button>}
      </div>

      {last ? (
        <div className="bw-main">
          <div>
            <span className="bw-big">{f.kg(last.weightKg)} <small>kg</small></span>
            <span className="muted bw-sub">{t('Sist veid')}: {f.date(last.date, { day: 'numeric', month: 'long' })}</span>
          </div>
          <button className="bw-weigh" onClick={() => setWeighing(true)}>＋ {t('Vei deg')}</button>
        </div>
      ) : (
        <div className="bw-main">
          <span className="muted">{t('Legg inn første veiing for å se trenden.')}</span>
          <button className="bw-weigh" onClick={() => setWeighing(true)}>＋ {t('Vei deg')}</button>
        </div>
      )}
      {last && base && base !== last && <Delta value={last.weightKg - base.weightKg} suffix={t('denne måneden')} />}

      {entries.length > 0 && (
        <div className="bw-minis">
          <button className="bw-mini" onClick={onOpen}>
            <span className="bw-mini-title">{t('Veiinger')}<small className="muted">{t('Siste 30 dager')}</small></span>
            <span className="bw-dots" aria-hidden="true">
              {grid.map((d) => <span key={d} className={logged.has(d) ? 'on' : ''} />)}
            </span>
            <span className="bw-mini-foot">{t('{n}/7 denne uka', { n: thisWeek })} ›</span>
          </button>
          <button className="bw-mini" onClick={onOpen}>
            <span className="bw-mini-title">{t('Vekt')}<small className="muted">{t('Siste {n} målinger', { n: last7.length })}</small></span>
            <Sparkline points={last7.map((e) => e.weightKg)} />
            <span className="bw-mini-foot">{f.kg(last.weightKg)} kg ›</span>
          </button>
        </div>
      )}

      {weighing && <WeighInSheet last={last} onSave={onLog} onClose={() => setWeighing(false)} />}
    </section>
  )
}

function Sparkline({ points }) {
  if (points.length < 2) return <span className="bw-spark" />
  const w = 120
  const h = 36
  const min = Math.min(...points)
  const max = Math.max(...points)
  const span = max - min || 1
  const xy = points.map((p, i) => [(i / (points.length - 1)) * (w - 8) + 4, h - 4 - ((p - min) / span) * (h - 8)])
  return (
    <svg className="bw-spark" viewBox={`0 0 ${w} ${h}`} aria-hidden="true">
      <polyline points={xy.map((p) => p.join(',')).join(' ')} fill="none" stroke="var(--accent)" strokeWidth="2" strokeLinejoin="round" strokeLinecap="round" />
      {xy.map(([x, y], i) => <circle key={i} cx={x} cy={y} r={i === xy.length - 1 ? 3.5 : 2.5} fill="var(--card-solid)" stroke="var(--accent)" strokeWidth="1.5" />)}
    </svg>
  )
}

// Trendgraf: én serie, 2px linje, lys flate, trådkors med verdiboks som følger pekeren.
function TrendChart({ data }) {
  const f = useFormat()
  const [hover, setHover] = useState(null)
  const W = 340
  const H = 180
  const pad = { l: 8, r: 8, t: 26, b: 22 }
  const ys = data.map((d) => d.weightKg)
  const min = Math.min(...ys)
  const max = Math.max(...ys)
  const spread = Math.max(max - min, 1)
  const lo = min - spread * 0.15
  const hi = max + spread * 0.15
  const t0 = new Date(`${data[0].date}T12:00`).getTime()
  const t1 = new Date(`${data[data.length - 1].date}T12:00`).getTime()
  const x = (iso) => pad.l + (t1 === t0 ? 0.5 : (new Date(`${iso}T12:00`).getTime() - t0) / (t1 - t0)) * (W - pad.l - pad.r)
  const y = (v) => pad.t + (1 - (v - lo) / (hi - lo)) * (H - pad.t - pad.b)
  const pts = data.map((d) => [x(d.date), y(d.weightKg)])
  const line = pts.map((p) => p.join(',')).join(' ')
  const area = `${pts[0][0]},${H - pad.b} ${line} ${pts[pts.length - 1][0]},${H - pad.b}`

  function move(e) {
    const rect = e.currentTarget.getBoundingClientRect()
    const px = ((e.clientX - rect.left) / rect.width) * W
    let best = 0
    pts.forEach((p, i) => { if (Math.abs(p[0] - px) < Math.abs(pts[best][0] - px)) best = i })
    setHover(best)
  }

  const h = hover != null ? data[hover] : null
  const hx = h ? pts[hover][0] : 0
  const first = data[0]
  const lastD = data[data.length - 1]

  return (
    <div className="bw-chart">
      <svg viewBox={`0 0 ${W} ${H}`} role="img"
           aria-label={`${f.kg(first.weightKg)} kg → ${f.kg(lastD.weightKg)} kg`}
           onPointerMove={move} onPointerDown={move} onPointerLeave={() => setHover(null)}>
        {[0.25, 0.5, 0.75].map((r) => (
          <line key={r} x1={pad.l} x2={W - pad.r} y1={pad.t + r * (H - pad.t - pad.b)} y2={pad.t + r * (H - pad.t - pad.b)}
                stroke="var(--border)" strokeWidth="1" />
        ))}
        <polygon points={area} fill="var(--accent-soft)" />
        <polyline points={line} fill="none" stroke="var(--accent)" strokeWidth="2" strokeLinejoin="round" strokeLinecap="round" />
        {data.length > 1 && <text x={pts[0][0]} y={pts[0][1] - 10} className="bw-label" textAnchor="start">{f.kg(first.weightKg)}</text>}
        <text x={pts[pts.length - 1][0]} y={pts[pts.length - 1][1] - 10} className="bw-label" textAnchor="end">{f.kg(lastD.weightKg)}</text>
        <circle cx={pts[pts.length - 1][0]} cy={pts[pts.length - 1][1]} r="4.5" fill="var(--card-solid)" stroke="var(--accent)" strokeWidth="2" />
        <text x={pad.l} y={H - 6} className="bw-axis">{f.date(first.date)}</text>
        <text x={W - pad.r} y={H - 6} className="bw-axis" textAnchor="end">{f.date(lastD.date)}</text>
        {h && (
          <g>
            <line x1={hx} x2={hx} y1={pad.t - 8} y2={H - pad.b} stroke="var(--border-strong)" strokeWidth="1" />
            <circle cx={hx} cy={pts[hover][1]} r="5" fill="var(--accent)" stroke="var(--card-solid)" strokeWidth="2" />
          </g>
        )}
      </svg>
      {h && (
        <div className="bw-tooltip" style={{ left: `${(hx / W) * 100}%` }}>
          <strong>{f.kg(h.weightKg)} kg</strong>
          <span className="muted">{f.date(h.date, { day: 'numeric', month: 'short', year: 'numeric' })}</span>
        </div>
      )}
    </div>
  )
}

// År-rutenett: én rute per dag (uker som kolonner), fylt når det finnes en veiing.
function YearGrid({ year, logged }) {
  const { lang } = useI18n()
  const f = useFormat()
  const jan1 = new Date(year, 0, 1)
  const offset = (jan1.getDay() + 6) % 7
  const days = []
  for (let d = new Date(year, 0, 1); d.getFullYear() === year; d.setDate(d.getDate() + 1)) days.push(localIso(d))
  const months = Array.from({ length: 12 }, (_, m) => new Date(year, m, 1))
  const monthCol = (m) => Math.floor((offset + Math.round((months[m] - jan1) / 86400000)) / 7)
  return (
    <div className="bw-year">
      <strong>{year}</strong>
      <div className="bw-year-grid" style={{ gridTemplateColumns: 'repeat(53, 1fr)' }}>
        {days.map((iso, i) => (
          <span key={iso} className={logged.has(iso) ? 'on' : ''}
                style={{ gridRow: ((offset + i) % 7) + 1, gridColumn: Math.floor((offset + i) / 7) + 1 }}
                title={logged.has(iso) ? `${f.date(iso)}: ${logged.get(iso)} kg` : undefined} />
        ))}
      </div>
      <div className="bw-months" aria-hidden="true">
        {months.map((m, i) => (
          <span key={i} style={{ left: `${(monthCol(i) / 53) * 100}%` }}>{m.toLocaleDateString(lang === 'en' ? 'en-US' : 'nb-NO', { month: 'short' }).slice(0, 3)}</span>
        ))}
      </div>
    </div>
  )
}

// Hele kroppsvekt-siden: periodefilter, trendgraf, år-rutenett og alle veiinger.
export function BodyweightSheet({ entries, onLog, onDelete, onClose }) {
  const { t } = useI18n()
  const f = useFormat()
  const [range, setRange] = useState('3m')
  const [weighing, setWeighing] = useState(false)
  const days = RANGES.find((r) => r.v === range).days
  const shown = days ? entries.filter((e) => e.date >= daysAgo(days)) : entries
  const change = shown.length > 1 ? shown[shown.length - 1].weightKg - shown[0].weightKg : null
  const logged = new Map(entries.map((e) => [e.date, f.kg(e.weightKg)]))
  const years = [...new Set(entries.map((e) => Number(e.date.slice(0, 4))))].sort((a, b) => b - a)

  return (
    <div className="sheet-backdrop" onClick={onClose}>
      <div className="sheet picker bw-sheet" role="dialog" aria-label={t('Kroppsvekt')} onClick={(e) => e.stopPropagation()}>
        <span className="sheet-handle" aria-hidden="true" />
        <div className="picker-head">
          <strong>{t('Kroppsvekt')}</strong>
          <button className="del" onClick={onClose} aria-label={t('Lukk')}>✕</button>
        </div>
        <div className="picker-list bw-scroll">
          <ChoiceChips label={t('Periode')} value={range} onChange={setRange}
                       options={RANGES.map((r) => ({ v: r.v, t: t(r.t) }))} />
          {shown.length > 0 ? (
            <>
              <div className="bw-range-head">
                <span className="muted">{t('Siden {date}', { date: f.date(shown[0].date, { day: 'numeric', month: 'short', year: 'numeric' }) })}</span>
                {change != null && <Delta value={change} suffix="" />}
              </div>
              <TrendChart data={shown} />
            </>
          ) : (
            <p className="muted">{t('Ingen veiinger i denne perioden.')}</p>
          )}

          <button className="bw-weigh wide" onClick={() => setWeighing(true)}>＋ {t('Vei deg')}</button>

          <h3 className="detail-h3">{t('Veiinger')}</h3>
          {years.map((y) => <YearGrid key={y} year={y} logged={logged} />)}

          <ul className="bw-entries">
            {[...entries].reverse().map((e, i, arr) => {
              const prev = arr[i + 1]
              const diff = prev ? e.weightKg - prev.weightKg : 0
              return (
                <li key={e.id}>
                  <span>{f.date(e.date, { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' })}</span>
                  <span className="bw-entry-kg">
                    {prev && Math.abs(diff) >= 0.05 && <span className="muted">{diff < 0 ? '↓' : '↑'}</span>} {f.kg(e.weightKg)} kg
                  </span>
                  <button className="del" aria-label={t('Slett veiing')} onClick={() => onDelete(e.id)}>✕</button>
                </li>
              )
            })}
          </ul>
        </div>
        {weighing && <WeighInSheet last={entries[entries.length - 1]} onSave={onLog} onClose={() => setWeighing(false)} />}
      </div>
    </div>
  )
}
