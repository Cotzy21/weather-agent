import { describe, it, expect } from 'vitest'
import {
  categoryOf, groupTemplates, matchesQuery, lastDoneDate, plannedToday, exerciseNames, CATEGORY_ORDER, CATEGORY_LABELS,
} from './workoutCategories.js'

const ex = (name) => ({ kind: 'exercise', name, sets: [{ reps: 8, weightKg: 20 }] })
const strength = (title, names = [], extra = {}) => ({ id: title, title, type: 'STYRKE', content: { blocks: names.map(ex) }, ...extra })
const other = (id, title, type) => ({ id, title, type, content: { distanceKm: 5 } })

describe('categoryOf', () => {
  it('kategorien er økttypen', () => {
    expect(categoryOf(strength('Push A'))).toBe('STYRKE')
    expect(categoryOf(other('r', 'Intervall', 'LØPING'))).toBe('LØPING')
    expect(categoryOf(other('h', 'Langtur', 'HIKING'))).toBe('HIKING')
    expect(categoryOf(other('s', 'Crawl', 'SVØMMING'))).toBe('SVØMMING')
  })
  it('ukjent eller manglende type havner i fristil', () => {
    expect(categoryOf({ type: 'YOGA' })).toBe('FRISTIL')
    expect(categoryOf(null)).toBe('FRISTIL')
    expect(categoryOf({})).toBe('FRISTIL')
  })
  it('alle kategorier har et navn, og Styrke kommer først', () => {
    for (const id of CATEGORY_ORDER) expect(CATEGORY_LABELS[id]).toBeTruthy()
    expect(CATEGORY_ORDER[0]).toBe('STYRKE')
    expect(CATEGORY_ORDER[1]).toBe('LØPING')
  })
})

describe('gruppering etter økttype', () => {
  const plans = [
    other('r', 'Intervall', 'LØPING'),
    strength('Pull B', ['Nedtrekk']),
    strength('Push A', ['Benkpress']),
    other('h', 'Langtur', 'HIKING'),
    strength('Bein', ['Knebøy']),
    other('r2', 'Rolig tur', 'LØPING'),
  ]

  it('gir typene i fast rekkefølge (Styrke, Løping, … Hiking) og utelater tomme', () => {
    expect(groupTemplates(plans, []).map((g) => g.id)).toEqual(['STYRKE', 'LØPING', 'HIKING'])
    expect(groupTemplates([plans[0]], []).map((g) => g.id)).toEqual(['LØPING'])
  })

  it('samler alle styrkeøktene under Styrke, alfabetisk uten historikk', () => {
    const g = groupTemplates(plans, []).find((x) => x.id === 'STYRKE')
    expect(g.items.map((i) => i.plan.title)).toEqual(['Bein', 'Pull B', 'Push A'])
  })

  it('sorterer innen en type: sist brukt først, så alfabetisk', () => {
    const workouts = [
      { id: 'a', date: '2026-09-20', title: 'Push A', plannedId: 'Push A' },
      { id: 'b', date: '2026-09-25', title: 'Pull B', plannedId: 'Pull B' },
    ]
    const g = groupTemplates(plans, workouts).find((x) => x.id === 'STYRKE')
    expect(g.items.map((i) => i.plan.title)).toEqual(['Pull B', 'Push A', 'Bein'])
    expect(g.items[0].lastDone).toBe('2026-09-25')
    expect(g.items[2].lastDone).toBeNull()
  })

  it('tomt utvalg gir ingen grupper', () => {
    expect(groupTemplates([], [])).toEqual([])
    expect(groupTemplates(null, null)).toEqual([])
  })

  it('søk filtrerer på tittel, økttype og øvelse (uten hensyn til store/små bokstaver og aksenter)', () => {
    const titles = (q) => groupTemplates(plans, [], { query: q }).flatMap((g) => g.items.map((i) => i.plan.title))
    expect(titles('push')).toEqual(['Push A'])
    expect(titles('KNEBØY')).toEqual(['Bein'])
    expect(titles('løping')).toEqual(['Intervall', 'Rolig tur'])
    expect(titles('hiking')).toEqual(['Langtur'])
    expect(titles('finnesikke')).toEqual([])
    expect(matchesQuery(plans[0], '   ')).toBe(true)
  })
})

describe('øvelsesnavn', () => {
  it('samler navn også fra supersett og hopper over tomme', () => {
    const content = { blocks: [ex('Benkpress'), { kind: 'superset', rounds: 3, exercises: [{ name: 'Knebøy' }, { name: '  ' }] }, { kind: 'dropset', name: '' }] }
    expect(exerciseNames(content)).toEqual(['Benkpress', 'Knebøy'])
    expect(exerciseNames(null)).toEqual([])
  })
})

describe('sist gjort og planlagt i dag', () => {
  const p = { id: 'p1', title: 'Push A', type: 'STYRKE', content: { day: 'Tirsdag', blocks: [] } }

  it('finner siste dato, kobler på plan-id og ellers tittel', () => {
    const w = [
      { date: '2026-09-01', plannedId: 'p1' },
      { date: '2026-09-15', plannedId: 'p1' },
      { date: '2026-09-20', plannedId: 'annen' },
      { date: '2026-09-25', title: 'push a' },
    ]
    expect(lastDoneDate(p, w)).toBe('2026-09-25') // eldre økt uten plan-id kobles på tittel
    expect(lastDoneDate(p, [{ date: '2026-09-20', plannedId: 'annen' }])).toBeNull()
    expect(lastDoneDate(p, null)).toBeNull()
  })

  it('planlagt i dag: riktig ukedag og ikke allerede gjort i dag', () => {
    expect(plannedToday([p], [], '2026-09-29', 1)).toEqual([p]) // tirsdag = 1
    expect(plannedToday([p], [], '2026-09-30', 2)).toEqual([])
    expect(plannedToday([p], [{ date: '2026-09-29', plannedId: 'p1' }], '2026-09-29', 1)).toEqual([])
    expect(plannedToday(null, null, '2026-09-29', 1)).toEqual([])
  })
})
