import { describe, expect, it } from 'vitest'
import { remainingPlansToday } from './todayPlans.js'
const tuesday = new Date(2026, 10, 3, 10)
const plan = (id, time = 'morgen') => ({ id, title: `Økt ${id}`, content: { day: 'Tirsdag', time } })
describe('remaining plans for the main training action', () => {
  it('keeps the next plan when an unrelated workout was logged on the same day', () => {
    expect(remainingPlansToday([plan(1)], [{ date: '2026-11-03', title: 'En tur' }], tuesday)).toHaveLength(1)
  })
  it('recognizes a completed plan by ID even when its title was edited', () => {
    expect(remainingPlansToday([plan(1)], [{ date: '2026-11-03', title: 'Nytt navn', plannedId: 1 }], tuesday)).toEqual([])
  })
  it('supports older logged workouts that only have a matching title', () => {
    expect(remainingPlansToday([plan(1)], [{ date: '2026-11-03', title: 'øKT 1' }], tuesday)).toEqual([])
  })
  it('does not complete a recurring plan using a previous week', () => {
    expect(remainingPlansToday([plan(1)], [{ date: '2026-10-27', title: 'Økt 1', plannedId: 1 }], tuesday)).toHaveLength(1)
  })
  it('orders multiple sessions by time and leaves the input array untouched', () => {
    const plans = [plan(1, 'kveld'), plan(2, 'morgen'), plan(3, '45 min')]
    expect(remainingPlansToday(plans, [], tuesday).map(p => p.id)).toEqual([2, 3, 1])
    expect(plans.map(p => p.id)).toEqual([1, 2, 3])
  })
  it('ignores templates without a weekday and other weekdays', () => {
    expect(remainingPlansToday([{ id: 1 }, { content: { day: 'Mandag' } }], [], tuesday)).toEqual([])
  })
})
