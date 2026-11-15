const OPEN_METEO_LOCATIONS = {
  alesund: [62.4722, 6.1495], bergen: [60.3913, 5.3221], oslo: [59.9139, 10.7522], tromso: [69.6492, 18.9553],
}

export async function fetchOpenMeteoDirect(place, signal, fetchFn = fetch) {
  if (!Object.hasOwn(OPEN_METEO_LOCATIONS, place)) throw new Error('Unknown forecast location')
  const [latitude, longitude] = OPEN_METEO_LOCATIONS[place]
  const params = new URLSearchParams({ latitude, longitude, hourly: 'temperature_2m,wind_speed_10m',
    wind_speed_unit: 'ms', forecast_hours: 48, timezone: 'UTC', timeformat: 'unixtime' })
  const response = await fetchFn(`https://api.open-meteo.com/v1/forecast?${params}`, {
    credentials: 'omit', referrerPolicy: 'no-referrer', redirect: 'error', signal,
  })
  if (!response.ok) throw new Error('Open-Meteo unavailable')
  const { hourly } = await response.json()
  if (!Array.isArray(hourly?.time) || hourly.time.length > 48) throw new Error('Invalid Open-Meteo forecast')
  const points = hourly.time.flatMap((timestamp, i) => {
    const temperatureC = hourly.temperature_2m?.[i]
    const windSpeedMs = hourly.wind_speed_10m?.[i]
    const time = new Date(timestamp * 1000)
    if (!Number.isInteger(timestamp) || !Number.isFinite(time.valueOf()) || !Number.isFinite(temperatureC) || !Number.isFinite(windSpeedMs)) return []
    return [{ time: time.toISOString().replace('.000Z', 'Z'), temperatureC, windSpeedMs }]
  })
  if (!points.length) throw new Error('No Open-Meteo readings')
  return { name: 'Open-Meteo', available: true, points }
}

export function alignForecasts(data) {
  if (!data?.met?.available || !data?.openMeteo?.available) return []
  const other = new Map(data.openMeteo.points.map(point => [point.time, point]))
  return data.met.points.filter(point => other.has(point.time)).map(point => ({
    time: point.time, met: point, openMeteo: other.get(point.time),
  }))
}
