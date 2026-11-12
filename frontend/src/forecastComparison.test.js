import { expect, it } from 'vitest'
import { alignForecasts } from './forecastComparison.js'

it('compares matching instants only and never replaces a missing source', () => {
  const data = {
    met: { available: true, points: [{ time: 'one', temperatureC: 0 }, { time: 'two', temperatureC: 4 }] },
    openMeteo: { available: true, points: [{ time: 'two', temperatureC: 5 }, { time: 'three', temperatureC: 6 }] },
  }
  expect(alignForecasts(data)).toEqual([{ time: 'two', met: data.met.points[1], openMeteo: data.openMeteo.points[0] }])
  expect(alignForecasts({ ...data, openMeteo: { available: false, points: [] } })).toEqual([])
})
