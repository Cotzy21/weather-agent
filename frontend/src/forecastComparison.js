export function alignForecasts(data) {
  if (!data?.met?.available || !data?.openMeteo?.available) return []
  const other = new Map(data.openMeteo.points.map(point => [point.time, point]))
  return data.met.points.filter(point => other.has(point.time)).map(point => ({
    time: point.time, met: point, openMeteo: other.get(point.time),
  }))
}
