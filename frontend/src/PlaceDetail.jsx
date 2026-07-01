import { useState, useEffect } from 'react'
import ResultMap from './ResultMap'
import { apiUrl, readError } from './api'

// "2026-06-27" -> "Lørdag 27. jun"
function fmtDay(iso) {
  const d = new Date(iso + 'T12:00:00')
  const s = d.toLocaleDateString('nb-NO', { weekday: 'long', day: 'numeric', month: 'short' })
  return s.charAt(0).toUpperCase() + s.slice(1)
}

export default function PlaceDetail({ place, onBack }) {
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    let alive = true
    setData(null)
    setError(null)
    const params = new URLSearchParams({ name: place.name, lat: place.lat, lon: place.lon })
    fetch(apiUrl(`/api/sted?${params}`))
      .then(async (r) => {
        if (!r.ok) throw new Error(await readError(r))
        return r.json()
      })
      .then((d) => alive && setData(d))
      .catch((e) => alive && setError(e.message))
    return () => {
      alive = false
    }
  }, [place])

  return (
    <div className="detail">
      <button className="back" onClick={onBack}>← Tilbake til resultatene</button>

      {error && <p className="error">Beklager – {error}</p>}
      {!data && !error && (
        <p className="muted typing">Henter varsel <span>·</span><span>·</span><span>·</span></p>
      )}

      {data && (
        <>
          <h2 className="detail-title">
            {data.name} <span className="muted">{data.elevationM.toFixed(0)} moh</span>
          </h2>

          <ResultMap
            places={[{
              name: data.name,
              lat: place.lat,
              lon: place.lon,
              temp: data.days[0]?.maxTempC ?? 0,
              precip: data.days[0]?.totalPrecipMm ?? 0,
              elevation: data.elevationM,
            }]}
            trails={data.trails}
          />

          <h3 className="detail-h3">Vær de neste dagene</h3>
          {data.days.length === 0 ? (
            <p className="muted">Ingen værdata tilgjengelig for stedet.</p>
          ) : (
            <div className="days">
              {data.days.map((d, i) => (
                <div className="day" key={i}>
                  <span className="day-name">{fmtDay(d.date)}</span>
                  <span className="day-temp">{d.maxTempC.toFixed(1)}°</span>
                  <span className="day-precip">
                    {d.totalPrecipMm > 0 ? `💧 ${d.totalPrecipMm.toFixed(1)} mm` : 'tørt'}
                  </span>
                  <span className="day-wind">{d.avgWindMs.toFixed(1)} m/s</span>
                </div>
              ))}
            </div>
          )}

          {data.clothing?.length > 0 && (
            <div className="gear">
              <span className="gear-title">🎒 Klær &amp; utstyr</span>
              <ul>{data.clothing.map((c, i) => <li key={i}>{c}</li>)}</ul>
            </div>
          )}

          <div className="trails">
            <p className="trails-title">🥾 Merkede turer i nærheten</p>
            {data.trails?.length > 0 ? (
              <ul>
                {data.trails.map((t, i) => (
                  <li key={i}>
                    {t.name}
                    {t.operator && <span className="muted"> · {t.operator}</span>}
                  </li>
                ))}
              </ul>
            ) : (
              <p className="muted">Fant ingen merkede turer (OpenStreetMap) i nærheten.</p>
            )}
          </div>
        </>
      )}
    </div>
  )
}
