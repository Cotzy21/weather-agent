import { useEffect, useRef, useState } from 'react'
import '@fontsource/poiret-one/latin-400.css'
import { apiUrl } from './api.js'
import { alignForecasts, fetchOpenMeteoDirect } from './forecastComparison.js'
import './observatory.css'
import Mark from './ObservatoryMark.jsx'

const PLACES = { alesund: 'Ålesund', bergen: 'Bergen', oslo: 'Oslo', tromso: 'Tromsø' }
const clock = time => new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit', timeZone: 'Europe/Oslo' }).format(new Date(time))
const number = value => Number.isFinite(value) ? value.toFixed(1) : '—'

function Arrow({ down = false }) {
  return <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.4" aria-hidden="true" style={down ? { transform: 'rotate(90deg)' } : undefined}><path d="M4 12h15M13 5l7 7-7 7" /></svg>
}

function ForecastPlot({ rows, index, metric }) {
  const values = rows.flatMap(row => [row.met[metric], row.openMeteo[metric]])
  const min = Math.floor(Math.min(...values) - 1)
  const max = Math.ceil(Math.max(...values) + 1)
  const x = i => 42 + i / Math.max(1, rows.length - 1) * 716
  const y = value => 176 - (value - min) / Math.max(1, max - min) * 144
  const path = source => rows.map((row, i) => `${i ? 'L' : 'M'} ${x(i)} ${y(row[source][metric])}`).join(' ')
  return (
    <svg className="obs-plot" viewBox="0 0 800 220" role="img" aria-label={`${metric === 'temperatureC' ? 'Temperature' : 'Wind'} forecasts from MET Norway and Open-Meteo. Exact values are shown below.`}>
      {[0, 0.5, 1].map(ratio => <g key={ratio}><line x1="42" x2="758" y1={y(min + (max - min) * ratio)} y2={y(min + (max - min) * ratio)} className="obs-grid" /><text x="26" y={y(min + (max - min) * ratio) + 4} textAnchor="end">{number(min + (max - min) * ratio)}</text></g>)}
      <path d={path('met')} className="obs-line obs-met" />
      <path d={path('openMeteo')} className="obs-line obs-open" />
      <line x1={x(index)} x2={x(index)} y1="20" y2="186" className="obs-cursor" />
      <circle cx={x(index)} cy={y(rows[index].met[metric])} r="4" className="obs-met-dot" />
      <circle cx={x(index)} cy={y(rows[index].openMeteo[metric])} r="4" className="obs-open-dot" />
      {[0, Math.floor((rows.length - 1) / 2), rows.length - 1].filter((v, i, all) => all.indexOf(v) === i).map(i => <text key={i} x={x(i)} y="211" textAnchor="middle">{clock(rows[i].time)}</text>)}
    </svg>
  )
}

function Comparison() {
  const [place, setPlace] = useState('alesund')
  const [state, setState] = useState({ loading: true })
  const [metric, setMetric] = useState('temperatureC')
  const [index, setIndex] = useState(0)
  const [revision, setRevision] = useState(0)
  const directRequest = useRef(null)
  useEffect(() => {
    const controller = new AbortController()
    fetch(apiUrl(`/api/vaer/sammenlign?place=${place}`), { signal: controller.signal })
      .then(async response => { if (!response.ok) throw new Error(); return response.json() })
      .then(data => { if (!controller.signal.aborted) setState({ data, loading: false }) })
      .catch(() => { if (!controller.signal.aborted) setState({ error: true, loading: false }) })
    return () => {
      controller.abort()
      const direct = directRequest.current
      directRequest.current = null
      direct?.abort()
    }
  }, [place, revision])
  const rows = alignForecasts(state.data)
  const selected = rows[Math.min(index, Math.max(0, rows.length - 1))]
  const unit = metric === 'temperatureC' ? '°C' : 'm/s'
  const difference = selected ? Math.abs(selected.met[metric] - selected.openMeteo[metric]) : null
  const retry = () => { setState({ loading: true }); setRevision(value => value + 1) }

  async function tryDirect() {
    directRequest.current?.abort()
    const request = new AbortController()
    directRequest.current = request
    const data = state.data
    setState({ data, loading: true })
    const timeout = setTimeout(() => request.abort(), 10000)
    try {
      const openMeteo = await fetchOpenMeteoDirect(place, request.signal)
      if (directRequest.current === request && !request.signal.aborted) {
        setState({ data: { ...data, openMeteo }, direct: true })
      }
    } catch {
      if (directRequest.current === request) setState({ data, directError: true })
    } finally {
      clearTimeout(timeout)
    }
  }

  return <div className="obs-instrument" data-ui>
    <div className="obs-instrument-top">
      <div><span className="obs-caption">YOUR FORECAST OBSERVATORY</span><h3>{PLACES[place]}<span>, Norway</span></h3></div>
      <label className="obs-place"><span>Explore a location</span><select value={place} onChange={event => { setPlace(event.target.value); setIndex(0); setState({ loading: true }) }}>{Object.entries(PLACES).map(([id, name]) => <option key={id} value={id}>{name}</option>)}</select></label>
    </div>
    <div className="obs-chart-controls">
      <div className="obs-segment" aria-label="Forecast measurement">
        <button type="button" aria-pressed={metric === 'temperatureC'} onClick={() => setMetric('temperatureC')}>Temperature</button>
        <button type="button" aria-pressed={metric === 'windSpeedMs'} onClick={() => setMetric('windSpeedMs')}>Wind</button>
      </div>
      <p className="obs-legend"><span className="obs-legend-met">MET Norway</span><span className="obs-legend-open">Open-Meteo</span></p>
    </div>
    {state.loading ? <div className="obs-chart-empty" role="status"><div className="obs-loader" /><p>Reading the sky from two sources…</p><small>The first request may take a moment.</small></div>
      : state.error ? <div className="obs-chart-empty" role="alert"><p>The forecast is taking a little longer.</p><button type="button" className="obs-text-button" onClick={retry}>Try again <Arrow /></button></div>
        : !rows.length ? <div className="obs-chart-empty" role="status"><p>Both sources are needed for this comparison.</p>
          {state.data?.met?.available && !state.data?.openMeteo?.available ? <>
            <small>{state.directError ? 'Open-Meteo did not return forecast readings. You can try again.' : 'Open-Meteo is unavailable through our server. If you try it directly, Open-Meteo receives the selected city coordinates and your IP address.'}</small>
            <button type="button" className="obs-text-button" onClick={tryDirect}>Try Open-Meteo directly <Arrow /></button>
          </> : <>
            <small>{!state.data?.met?.available ? 'MET Norway is currently unavailable.' : 'The sources did not return matching forecast hours.'}</small>
            <button type="button" className="obs-text-button" onClick={retry}>Try again <Arrow /></button>
          </>}
        </div>
          : <>
            <ForecastPlot rows={rows} index={Math.min(index, rows.length - 1)} metric={metric} />
            <label className="obs-timeline"><span>Explore the hours <span>{clock(selected.time)} · Norway time</span></span><input type="range" min="0" max={rows.length - 1} value={Math.min(index, rows.length - 1)} onChange={event => setIndex(Number(event.target.value))} aria-label="Forecast hour" aria-valuetext={clock(selected.time)} /></label>
            <div className="obs-readings">
              <div><span className="obs-legend-met">MET Norway</span><strong>{number(selected.met[metric])}<small>{unit}</small></strong></div>
              <div><span className="obs-legend-open">Open-Meteo</span><strong>{number(selected.openMeteo[metric])}<small>{unit}</small></strong></div>
              <div className="obs-difference"><span>Difference at {clock(selected.time)}</span><strong>{number(difference)}<small>{unit}</small></strong></div>
            </div>
            <p className="obs-data-note">{state.direct ? 'Open-Meteo fetched directly · MET fetched ' : 'Fetched '}{clock(state.data.fetchedAt)} · Forecast values at matching times</p>
            <details className="obs-values"><summary>See the hourly values</summary><div className="obs-table-wrap"><table><caption>{metric === 'temperatureC' ? 'Temperature in °C' : 'Wind speed in m/s'} · Europe/Oslo</caption><thead><tr><th>Time</th><th>MET Norway</th><th>Open-Meteo</th><th>Difference</th></tr></thead><tbody>{rows.map(row => <tr key={row.time}><th>{new Intl.DateTimeFormat('en-GB', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', timeZone: 'Europe/Oslo' }).format(new Date(row.time))}</th><td>{number(row.met[metric])}</td><td>{number(row.openMeteo[metric])}</td><td>{number(Math.abs(row.met[metric] - row.openMeteo[metric]))}</td></tr>)}</tbody></table></div></details>
          </>}
  </div>
}

export default function Observatory() {
  const page = useRef(null)
  useEffect(() => {
    document.title = 'Weather Agent — A clearer view'
    const nodes = page.current.querySelectorAll('[data-obs-reveal]')
    if (matchMedia('(prefers-reduced-motion: reduce)').matches) return
    const observer = new IntersectionObserver(entries => entries.forEach(entry => {
      if (entry.isIntersecting) { entry.target.classList.add('obs-visible'); observer.unobserve(entry.target) }
    }), { threshold: 0.12 })
    nodes.forEach(node => observer.observe(node))
    return () => observer.disconnect()
  }, [])

  return <div className="observatory" ref={page}>
    <a className="obs-skip" href="#forecast">Skip to forecast comparison</a>
    <header className="obs-header">
      <a href="#" className="obs-brand" aria-label="Weather Agent home"><Mark /><span>WEATHER<span>AGENT</span></span></a>
      <nav aria-label="Main navigation"><a href="#forecast">The forecast</a><a href="#perspective">Our perspective</a><a href="#questions">Questions</a></nav>
      <a className="obs-app-link" href="/?app=1">Open the app <Arrow /></a>
    </header>
    <main>
      <section className="obs-hero" aria-labelledby="obs-title">
        <img className="obs-sky" src="/observatory/sky.png" alt="" fetchPriority="high" />
        <div className="obs-hero-shade" />
        <div className="obs-orbit" aria-hidden="true"><span /><span /><span /></div>
        <div className="obs-hero-content">
          <p className="obs-caption obs-entrance">A DIFFERENT PERSPECTIVE ON WEATHER</p>
          <h1 id="obs-title" className="obs-entrance">A clearer view<br /><em>of what’s ahead.</em></h1>
          <p className="obs-hero-copy obs-entrance">Two forecasts. One thoughtful view.<br />Explore where MET Norway and Open-Meteo agree, and where the sky leaves room for possibility.</p>
          <a className="obs-primary obs-entrance" href="#forecast">Compare the forecasts <Arrow down /></a>
        </div>
        <div className="obs-hero-foot"><span>OBSERVE. COMPARE. UNDERSTAND.</span><span>Made for a world that changes.</span></div>
      </section>

      <section className="obs-comparison obs-width" id="forecast" aria-labelledby="forecast-title">
        <div className="obs-section-heading" data-obs-reveal><div><p className="obs-caption">TWO SOURCES. A WIDER VIEW.</p><h2 id="forecast-title">The forecast,<br /><em>in perspective.</em></h2></div><p>A small difference can change your plans. Look at both forecasts, hour by hour, and make the call with more context.</p></div>
        <Comparison />
        <div className="obs-source-strip" data-obs-reveal><span>Open weather data.<br />Clearly attributed.</span><a href="https://www.met.no/en" target="_blank" rel="noreferrer">MET NORWAY <Arrow /></a><a href="https://open-meteo.com/" target="_blank" rel="noreferrer">OPEN-METEO <Arrow /></a></div>
      </section>

      <section className="obs-perspective" id="perspective" aria-labelledby="perspective-title">
        <div className="obs-width obs-perspective-grid">
          <div className="obs-atlas" data-obs-reveal>
            <img src="/observatory/sky.png" alt="An atmospheric illustration of silver clouds meeting the dawn" loading="lazy" />
            <div className="obs-atlas-geometry" aria-hidden="true"><span /><span /></div>
            <p>THE SKY IS ALWAYS<br />A WORK IN PROGRESS.</p>
          </div>
          <div className="obs-perspective-copy">
            <p className="obs-caption" data-obs-reveal>A LITTLE MORE CONTEXT</p>
            <h2 id="perspective-title" data-obs-reveal>Nature has<br /><em>more than one answer.</em></h2>
            <p data-obs-reveal>A forecast is a model of a changing atmosphere. Sources can differ in their models, terrain, update times and how they represent your location.</p>
            <p data-obs-reveal>Weather Agent places the predictions beside each other. You can see the difference for yourself, whether you’re heading into the hills or simply planning the day.</p>
            <a href="#method" className="obs-text-button" data-obs-reveal>How to read the comparison <Arrow down /></a>
          </div>
        </div>
      </section>

      <section id="method" className="obs-method obs-width" aria-labelledby="method-title">
        <div className="obs-section-heading" data-obs-reveal><div><p className="obs-caption">FROM OBSERVATION TO UNDERSTANDING</p><h2 id="method-title">Read between<br /><em>the forecasts.</em></h2></div><p>Both sources stay visible. Every difference has a time, a place and a unit.</p></div>
        <div className="obs-method-rows">
          <article data-obs-reveal><svg viewBox="0 0 90 62" fill="none" aria-hidden="true"><path d="M10 40 Q45 -10 80 40M20 40 Q45 5 70 40M30 40 Q45 20 60 40M10 50h70" /></svg><h3>Start with a place.</h3><p>Choose a Norwegian city. Both sources are requested for the same coordinates.</p></article>
          <article data-obs-reveal><svg viewBox="0 0 90 62" fill="none" aria-hidden="true"><path d="M10 42l18-16 18 8 17-20 17 6M10 49l18-14 18 6 17-15 17-8" /></svg><h3>Follow the difference.</h3><p>Switch between temperature and wind. Move through matching forecast hours to see where the lines separate.</p></article>
          <article data-obs-reveal><svg viewBox="0 0 90 62" fill="none" aria-hidden="true"><circle cx="45" cy="31" r="23" /><path d="M45 2v58M16 31h58M45 15l7 16-7 16-7-16Z" /></svg><h3>Keep the bigger picture.</h3><p>Agreement gives context. It cannot guarantee accuracy. For exposed trips, check local conditions and official warnings too.</p></article>
        </div>
      </section>

      <section id="questions" className="obs-questions obs-width" aria-labelledby="questions-title">
        <div data-obs-reveal><p className="obs-caption">GOOD QUESTIONS</p><h2 id="questions-title">A little clarity.</h2></div>
        <div className="obs-faq-list">
          <details data-obs-reveal><summary>Are these live forecasts?<span>+</span></summary><p>Yes. We request forecasts from MET Norway and Open-Meteo and cache them for up to 15 minutes. The comparison shows when we fetched them. These are predictions for the hours ahead.</p></details>
          <details data-obs-reveal><summary>Which source should I trust?<span>+</span></summary><p>We show both sources without declaring a winner. Their performance depends on place, conditions and forecast horizon. Open-Meteo may also use models from MET Norway, so the sources are not necessarily independent.</p></details>
          <details data-obs-reveal><summary>What if a source is unavailable?<span>+</span></summary><p>We tell you. The comparison waits until both sources provide matching times. Missing data is never presented as a measured zero.</p></details>
          <details data-obs-reveal><summary>Do I need an account?<span>+</span></summary><p>The forecast comparison is open to everyone. You can explore Ålesund, Bergen, Oslo and Tromsø without signing in or sharing your device location.</p></details>
        </div>
      </section>

      <section className="obs-closing" aria-labelledby="closing-title">
        <div className="obs-width"><Mark /><h2 id="closing-title" data-obs-reveal>Look ahead.<br /><em>With perspective.</em></h2><a className="obs-primary" href="#forecast">Find your forecast <Arrow /></a></div>
      </section>
    </main>
    <footer className="obs-footer obs-width"><a href="#" className="obs-brand"><Mark /><span>WEATHER<span>AGENT</span></span></a><p>Weather data from <a href="https://www.met.no/en" target="_blank" rel="noreferrer">MET Norway</a> and <a href="https://open-meteo.com/" target="_blank" rel="noreferrer">Open-Meteo</a>.<br /><a href="https://creativecommons.org/licenses/by/4.0/" target="_blank" rel="noreferrer">CC BY 4.0</a> · Values aligned and rounded by Weather Agent.<br />Cloud artwork generated for Weather Agent.</p><a className="obs-text-button" href="/?app=1">Explore the app <Arrow /></a></footer>
  </div>
}
