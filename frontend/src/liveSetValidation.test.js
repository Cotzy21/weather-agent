import { describe, expect, it } from 'vitest'
import { liveSetError, toContent } from './liveSession.js'

describe('settlogging på mobil', () => {
  it.each(['', ' ', '0', '-1', '2.5', '3,5', 'abc', 'Infinity', '1e2'])(
    'avviser ugyldige reps %j', (reps) => {
      expect(liveSetError({ reps, weightKg: '20' })).toBe('reps')
    },
  )

  it.each(['-1', 'abc', 'NaN', 'Infinity', '20,', '20.', '1,2,3', '1e2'])(
    'avviser ugyldig vekt %j', (weightKg) => {
      expect(liveSetError({ reps: '8', weightKg })).toBe('weightKg')
    },
  )

  it.each(['', ' ', '0', '20', '20,5', '20.5', ',5', '.5'])(
    'godtar kroppsvekt og desimalvekt %j', (weightKg) => {
      expect(liveSetError({ reps: '8', weightKg })).toBeNull()
    },
  )

  it('sender samme desimalverdi som ble validert, og beholder kroppsvekt', () => {
    const sets = [
      { reps: '8', weightKg: '20,5', done: true },
      { reps: '12', weightKg: '', done: true },
      { reps: '10', weightKg: '30', done: false },
    ]
    const content = toContent([{ name: 'Bicepscurl', kind: 'exercise', sets }], 0, 60000)
    expect(content.blocks[0].sets).toEqual([
      { reps: 8, weightKg: 20.5 }, { reps: 12, weightKg: 0 },
    ])
  })
})
