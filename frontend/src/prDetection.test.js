import { describe, it, expect } from 'vitest'
import { detectPRs, priorBests, livePRSets, setStrength, MAX_REPS_FOR_E1RM } from './prDetection.js'

const workout = (date, blocks, type = 'STYRKE') => ({ id: date + blocks.length, date, type, title: 'Økt', content: { blocks } })
const ex = (name, ...sets) => ({ kind: 'exercise', name, sets: sets.map(([weightKg, reps]) => ({ weightKg, reps })) })
const content = (...blocks) => ({ blocks })

describe('setStrength', () => {
  it('regner estimert 1RM med Epley for sett med vekt', () => {
    expect(setStrength({ weightKg: 100, reps: 5 }).e1rm).toBeCloseTo(116.67, 1)
    expect(setStrength({ weightKg: 100, reps: 1 }).e1rm).toBe(100)
  })
  it('kroppsvekt (0 kg) og lange sett gir ingen e1rm', () => {
    expect(setStrength({ weightKg: 0, reps: 10 }).e1rm).toBe(0)
    expect(setStrength({ weightKg: 20, reps: MAX_REPS_FOR_E1RM + 1 }).e1rm).toBe(0)
  })
  it('tåler komma som desimaltegn og tomme verdier (live-økta lagrer tekst)', () => {
    expect(setStrength({ weightKg: '82,5', reps: '5' })).toMatchObject({ kg: 82.5, reps: 5 })
    expect(setStrength({ weightKg: '', reps: '' })).toMatchObject({ kg: 0, reps: 0, e1rm: 0 })
    expect(setStrength(null).e1rm).toBe(0)
  })
})

describe('priorBests', () => {
  const history = [
    workout('2026-09-01', [ex('Benkpress', [80, 5], [80, 5])]),
    workout('2026-09-08', [ex('Benkpress', [85, 5])]),
    workout('2026-09-10', [ex('Pull-ups', [0, 8])]),
    workout('2026-09-12', [{ kind: 'exercise', name: 'Løping', sets: [] }]),
    { id: 'x', date: '2026-09-11', type: 'LØPING', content: { distanceKm: 5 } },
  ]

  it('finner beste e1rm, tyngste vekt og flest kroppsvekt-reps per øvelse, og teller økter', () => {
    const b = priorBests(history)
    const bench = [...b.values()].find((v) => v.name === 'Benkpress')
    expect(bench.sessions).toBe(2)
    expect(bench.weight).toBe(85)
    expect(bench.e1rm).toBeCloseTo(85 * (1 + 5 / 30), 1)
    const pull = [...b.values()].find((v) => v.name === 'Pull-ups')
    expect(pull).toMatchObject({ sessions: 1, reps: 8, e1rm: 0 })
  })

  it('øvelser uten loggede sett og andre økttyper teller ikke som økter', () => {
    const b = priorBests(history)
    const run = [...b.values()].find((v) => v.name === 'Løping')
    expect(run?.sessions ?? 0).toBe(0)
  })

  it('kan begrenses til økter til og med en dato', () => {
    const b = priorBests(history, '2026-09-05')
    const bench = [...b.values()].find((v) => v.name === 'Benkpress')
    expect(bench.sessions).toBe(1)
    expect(bench.weight).toBe(80)
  })
})

describe('detectPRs', () => {
  const history = [
    workout('2026-09-01', [ex('Benkpress', [80, 5])]),
    workout('2026-09-08', [ex('Benkpress', [82.5, 5]), ex('Pull-ups', [0, 8])]),
    workout('2026-09-09', [ex('Markløft', [140, 3])]),
  ]

  it('finner en e1rm-rekord og forteller forrige rekord og gevinst', () => {
    const prs = detectPRs(history, content(ex('Benkpress', [85, 5])))
    expect(prs).toHaveLength(1)
    expect(prs[0]).toMatchObject({ kind: 'e1rm', name: 'Benkpress', kg: 85, reps: 5, heaviest: true })
    expect(prs[0].value).toBeGreaterThan(prs[0].previous)
    expect(prs[0].gain).toBeCloseTo(prs[0].value - prs[0].previous, 1)
  })

  it('ingen rekord når økta ikke slår den gamle (heller ikke ved lik verdi)', () => {
    expect(detectPRs(history, content(ex('Benkpress', [82.5, 5])))).toEqual([])
    expect(detectPRs(history, content(ex('Benkpress', [80, 5])))).toEqual([])
  })

  it('første gang en øvelse logges er ikke en rekord', () => {
    expect(detectPRs(history, content(ex('Skulderpress', [50, 8])))).toEqual([])
    expect(detectPRs([], content(ex('Benkpress', [100, 5])))).toEqual([]) // uten historikk (ikke lastet ennå) feires ingenting
  })

  it('samme rep-tall med mer vekt er rekord, færre reps med samme vekt er det ikke', () => {
    expect(detectPRs(history, content(ex('Benkpress', [82.5, 6])))).toHaveLength(1)
    expect(detectPRs(history, content(ex('Benkpress', [82.5, 4])))).toEqual([])
  })

  it('en tyngre enkelt (1 rep) kan slå e1rm fra flere reps', () => {
    const prs = detectPRs(history, content(ex('Benkpress', [100, 1])))
    expect(prs[0]).toMatchObject({ kind: 'e1rm', kg: 100, reps: 1 })
  })

  it('kroppsvektøvelser: flere reps er rekord', () => {
    const prs = detectPRs(history, content(ex('Pull-ups', [0, 9])))
    expect(prs).toEqual([expect.objectContaining({ kind: 'reps', value: 9, previous: 8, gain: 1 })])
    expect(detectPRs(history, content(ex('Pull-ups', [0, 8])))).toEqual([])
  })

  it('går man fra kroppsvekt til vekt første gang er det ikke en rekord', () => {
    expect(detectPRs(history, content(ex('Pull-ups', [10, 5])))).toEqual([])
  })

  it('lange sett (over 12 reps) gir ikke en falsk e1rm-rekord', () => {
    expect(detectPRs(history, content(ex('Benkpress', [40, 40])))).toEqual([])
  })

  it('samme øvelse i flere blokker teller det beste settet, og navnet på ulike språk er samme øvelse', () => {
    const prs = detectPRs(history, content(ex('Bench press', [70, 5]), ex('Benkpress', [85, 5])))
    expect(prs).toHaveLength(1)
    expect(prs[0].kg).toBe(85)
  })

  it('historikk på engelsk og ny økt på norsk er samme øvelse', () => {
    const en = [workout('2026-09-01', [ex('Bench press', [80, 5])])]
    expect(detectPRs(en, content(ex('Benkpress', [85, 5])))).toHaveLength(1)
  })

  it('finner rekord i supersett og bruker toppsettet i dropsett', () => {
    const superset = { kind: 'superset', rounds: 1, exercises: [{ name: 'Benkpress', sets: [{ weightKg: 90, reps: 5 }] }] }
    expect(detectPRs(history, content(superset))).toHaveLength(1)
    const dropset = { kind: 'dropset', name: 'Benkpress', drops: [{ weightKg: 90, reps: 5 }, { weightKg: 60, reps: 8 }] }
    expect(detectPRs(history, content(dropset))).toHaveLength(1)
  })

  it('flere rekorder i samme økt gir én per øvelse', () => {
    const prs = detectPRs(history, content(ex('Benkpress', [90, 5]), ex('Markløft', [150, 3]), ex('Pull-ups', [0, 10])))
    expect(prs.map((p) => p.name).sort()).toEqual(['Benkpress', 'Markløft', 'Pull-ups'])
  })

  it('en økt logget i ettertid regnes bare mot økter til og med den datoen', () => {
    const late = [...history, workout('2026-09-20', [ex('Benkpress', [120, 5])])]
    expect(detectPRs(late, content(ex('Benkpress', [85, 5])), { date: '2026-09-15' })).toHaveLength(1)
    expect(detectPRs(late, content(ex('Benkpress', [85, 5])))).toEqual([]) // uten dato: mot alt
  })

  it('tomt innhold og ugyldige verdier gir ingen rekorder', () => {
    expect(detectPRs(history, null)).toEqual([])
    expect(detectPRs(history, content(ex('Benkpress', ['x', 'y'])))).toEqual([])
    expect(detectPRs(null, content(ex('Benkpress', [90, 5])))).toEqual([])
  })
})

describe('livePRSets', () => {
  const bests = priorBests([workout('2026-09-01', [ex('Benkpress', [80, 5])]), workout('2026-09-02', [ex('Pull-ups', [0, 8])])])
  const live = (name, sets, kind = 'exercise') => ({ name, kind, sets })
  const set = (weightKg, reps, done = true) => ({ weightKg: String(weightKg), reps: String(reps), done })

  it('merker bare det beste avhukede settet som slår rekorden', () => {
    const marked = livePRSets([live('Benkpress', [set(80, 5), set(85, 5), set(87.5, 5)])], bests)
    expect([...marked]).toEqual(['0-2'])
  })

  it('sett som ikke er huket av teller ikke', () => {
    expect(livePRSets([live('Benkpress', [set(90, 5, false)])], bests).size).toBe(0)
  })

  it('kroppsvekt: flest reps over forrige rekord', () => {
    expect([...livePRSets([live('Pull-ups', [set(0, 9), set(0, 10), set(0, 8)])], bests)]).toEqual(['0-1'])
  })

  it('øvelser uten historikk og dropsett merkes ikke', () => {
    expect(livePRSets([live('Skulderpress', [set(50, 8)])], bests).size).toBe(0)
    expect(livePRSets([live('Benkpress', [set(90, 5)], 'dropset')], bests).size).toBe(0)
  })

  it('indeksen følger øvelsen og settet', () => {
    const marked = livePRSets([live('Skulderpress', [set(50, 8)]), live('Benkpress', [set(70, 5), set(90, 5)])], bests)
    expect([...marked]).toEqual(['1-1'])
  })
})
