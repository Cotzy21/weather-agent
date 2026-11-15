import { expect, it, vi } from 'vitest'
import { alignForecasts, fetchOpenMeteoDirect } from './forecastComparison.js'

it('compares matching instants only and never replaces a missing source', () => {
  const data = {
    met: { available: true, points: [{ time: 'one', temperatureC: 0 }, { time: 'two', temperatureC: 4 }] },
    openMeteo: { available: true, points: [{ time: 'two', temperatureC: 5 }, { time: 'three', temperatureC: 6 }] },
  }
  expect(alignForecasts(data)).toEqual([{ time: 'two', met: data.met.points[1], openMeteo: data.openMeteo.points[0] }])
  expect(alignForecasts({ ...data, openMeteo: { available: false, points: [] } })).toEqual([])
})

it('uses only a fixed public city, sends no credentials and aligns valid upstream timestamps', async () => {
  const start = Date.parse('2026-11-15T13:00:00Z') / 1000
  const fetchFn = vi.fn(async () => ({ ok: true, json: async () => ({ hourly: {
    time: [start, start + 3600, start + 7200], temperature_2m: [1, null, 5], wind_speed_10m: [0, 7],
  } }) }))
  const source = await fetchOpenMeteoDirect('alesund', undefined, fetchFn)
  const [url, options] = fetchFn.mock.calls[0]
  expect(new URL(url).origin).toBe('https://api.open-meteo.com')
  expect(new URL(url).searchParams.get('latitude')).toBe('62.4722')
  expect(options).toMatchObject({ credentials: 'omit', referrerPolicy: 'no-referrer', redirect: 'error' })
  expect(source.points).toEqual([{ time: '2026-11-15T13:00:00Z', temperatureC: 1, windSpeedMs: 0 }])
  expect(alignForecasts({ openMeteo: source, met: { available: true, points: [{ time: '2026-11-15T13:00:00Z' }] } })).toHaveLength(1)
  fetchFn.mockClear()
  await expect(fetchOpenMeteoDirect('https://attacker.example', undefined, fetchFn)).rejects.toThrow()
  expect(fetchFn).not.toHaveBeenCalled()
})
