const MIN_PAIRS = 10

function shiftDate(date, offset) {
  const value = new Date(`${date}T12:00:00Z`)
  value.setUTCDate(value.getUTCDate() + offset)
  return value.toISOString().slice(0, 10)
}

function clockGap(a, b) {
  const difference = Math.abs(a - b)
  return Math.min(difference, 1440 - difference)
}

export function pairDays(days, xKey, yKey, lag) {
  const lookup = new Map(days.map((day) => [day.date, day.metrics]))
  function measure(date, key) {
    const current = lookup.get(date)
    if (key !== 'bedtimeShift') return current?.[key]
    const previous = lookup.get(shiftDate(date, -1))
    return Number.isFinite(current?.bedtime) && Number.isFinite(previous?.bedtime)
      ? clockGap(current.bedtime, previous.bedtime) : null
  }
  const pairs = []
  for (const { date } of days) {
    const target = shiftDate(date, lag)
    const x = xKey === 'loggedWorkout' && lookup.has(target)
      ? Number((lookup.get(date)?.workouts ?? 0) > 0) : measure(date, xKey)
    const y = measure(target, yKey)
    if (Number.isFinite(x) && Number.isFinite(y)) pairs.push({ date, target, x, y })
  }
  return pairs.sort((a, b) => a.date.localeCompare(b.date))
}

function ranks(values) {
  const order = values.map((value, index) => ({ value, index })).sort((a, b) => a.value - b.value)
  const result = Array(values.length)
  for (let first = 0; first < order.length;) {
    let end = first + 1
    while (end < order.length && order[first].value === order[end].value) end++
    for (let i = first; i < end; i++) result[order[i].index] = (first + end - 1) / 2
    first = end
  }
  return result
}

export function spearman(pairs) {
  if (pairs.length < MIN_PAIRS) return { reason: 'few', count: pairs.length }
  const x = ranks(pairs.map((pair) => pair.x))
  const y = ranks(pairs.map((pair) => pair.y))
  const mean = (pairs.length - 1) / 2
  let xy = 0, xx = 0, yy = 0
  for (let i = 0; i < pairs.length; i++) {
    const dx = x[i] - mean
    const dy = y[i] - mean
    xy += dx * dy
    xx += dx * dx
    yy += dy * dy
  }
  if (xx === 0 || yy === 0) return { reason: 'constant', count: pairs.length }
  return { rho: Math.max(-1, Math.min(1, xy / Math.sqrt(xx * yy))), count: pairs.length }
}
