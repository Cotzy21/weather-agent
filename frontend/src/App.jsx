import { useState, useRef, useEffect } from 'react'
import ResultMap from './ResultMap'
import './App.css'

// De fire faktorene brukeren kan vekte, og nivåene.
const FACTORS = [
  { key: 'temp', label: 'Temperatur', icon: '🌡️' },
  { key: 'rain', label: 'Regn', icon: '🌧️' },
  { key: 'wind', label: 'Vind', icon: '💨' },
  { key: 'elevation', label: 'Høyde', icon: '⛰️' },
]
const LEVELS = [
  { v: 'lav', t: 'Lav' },
  { v: 'middels', t: 'Middels' },
  { v: 'hoy', t: 'Høy' },
]

// Snitt over dagene for ett sted. Backend sender per-dag-tallene i `days`.
function summarize(place) {
  const days = place.days ?? []
  const n = days.length || 1
  const avg = (sel) => days.reduce((sum, d) => sum + sel(d), 0) / n
  return {
    name: place.location.name,
    lat: place.location.latitude,
    lon: place.location.longitude,
    elevation: days[0]?.elevationMeters ?? 0,
    temp: avg((d) => d.maxTempC),
    precip: avg((d) => d.totalPrecipMm),
    wind: avg((d) => d.avgWindMs),
    score: place.score,
  }
}

function Answer({ result }) {
  const { interpretation: t, ranking } = result
  const region = t.region ?? '(ukjent)'

  const interp = (
    <p className="interp">
      <strong>{region}</strong> · {t.when} ({t.dates.from} → {t.dates.to}) · {t.tripType}
    </p>
  )

  if (!ranking || ranking.length === 0) {
    return (
      <div className="answer">
        {interp}
        <p className="muted">
          {t.region
            ? 'Fant ingen værdata for perioden (kanskje for langt fram?).'
            : 'Jeg fant ingen region i spørsmålet – prøv å nevne et fylke, f.eks. «Rogaland».'}
        </p>
      </div>
    )
  }

  const places = ranking.slice(0, 10).map(summarize)
  const best = places[0]

  return (
    <div className="answer">
      {interp}
      <p className="winner">
        <span className="medal">🏔️</span> Finest vær: <strong>{best.name}</strong>{' '}
        <span className="muted">({best.elevation.toFixed(0)} moh)</span> – snitt{' '}
        {best.temp.toFixed(1)} °C, {best.precip.toFixed(1)} mm regn/dag
      </p>
      <ResultMap places={places} />
      <table className="ranking">
        <thead>
          <tr><th>Sted</th><th>moh</th><th>°C</th><th>mm</th><th>m/s</th><th>score</th></tr>
        </thead>
        <tbody>
          {places.map((p, i) => (
            <tr key={i} className={i === 0 ? 'top' : ''}>
              <td>{p.name}</td>
              <td>{p.elevation.toFixed(0)}</td>
              <td>{p.temp.toFixed(1)}</td>
              <td>{p.precip.toFixed(1)}</td>
              <td>{p.wind.toFixed(1)}</td>
              <td>{p.score.toFixed(1)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export default function App() {
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [weights, setWeights] = useState({
    temp: 'middels', rain: 'middels', wind: 'middels', elevation: 'middels',
  })
  const endRef = useRef(null)

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, loading])

  function setWeight(key, value) {
    setWeights((w) => ({ ...w, [key]: value }))
  }

  async function ask(e) {
    e.preventDefault()
    const query = input.trim()
    if (!query || loading) return

    setMessages((m) => [...m, { role: 'user', text: query }])
    setInput('')
    setLoading(true)

    try {
      const params = new URLSearchParams({ q: query, ...weights })
      const res = await fetch(`/api/turvaer?${params}`)
      if (!res.ok) throw new Error(`Tjeneren svarte ${res.status}`)
      const result = await res.json()
      setMessages((m) => [...m, { role: 'bot', result }])
    } catch (err) {
      setMessages((m) => [...m, { role: 'bot', error: err.message }])
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="app">
      <header>
        <h1>⛰️ Turvær‑assistent</h1>
        <p className="sub">Spør på vanlig norsk hvor i Norge det blir finest turvær.</p>
      </header>

      <section className="weights" aria-label="Vekting av faktorer">
        <span className="weights-title">Hva betyr mest for deg?</span>
        <div className="weights-grid">
          {FACTORS.map((f) => (
            <label key={f.key} className="weight">
              <span className="weight-label">{f.icon} {f.label}</span>
              <select value={weights[f.key]} onChange={(e) => setWeight(f.key, e.target.value)}>
                {LEVELS.map((l) => (
                  <option key={l.v} value={l.v}>{l.t}</option>
                ))}
              </select>
            </label>
          ))}
        </div>
      </section>

      <div className="chat">
        {messages.length === 0 && (
          <p className="hint">Prøv: «hvor er det finest fjellvær i Møre og Romsdal i helga»</p>
        )}
        {messages.map((m, i) =>
          m.role === 'user' ? (
            <div key={i} className="msg user">{m.text}</div>
          ) : (
            <div key={i} className="msg bot">
              {m.error ? <p className="error">Beklager – {m.error}</p> : <Answer result={m.result} />}
            </div>
          )
        )}
        {loading && (
          <div className="msg bot">
            <p className="muted typing">Henter vær <span>·</span><span>·</span><span>·</span></p>
          </div>
        )}
        <div ref={endRef} />
      </div>

      <form className="composer" onSubmit={ask}>
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="Skriv et spørsmål …"
          autoFocus
        />
        <button type="submit" disabled={loading || !input.trim()}>Send</button>
      </form>
    </div>
  )
}
