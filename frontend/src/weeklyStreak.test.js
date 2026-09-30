import { describe, it, expect } from 'vitest'
import {
  addDays, weekStart, clampGoal, normalizeSettings, computeStreak, togglePause, isIsoDate,
  DEFAULT_GOAL, MAX_CARDS, MAX_PAUSES,
} from './weeklyStreak.js'

// 2026-09-28 er en mandag; 2026-09-30 er en onsdag.
const MON = (n) => addDays('2026-09-28', 7 * n) // n uker etter 28. september (negativ = før)

/** `n` økter på n ulike dager (man, tir, ons …) i uka som starter på mandagen `start`. */
const week = (start, n) => Array.from({ length: n }, (_, i) => ({ date: addDays(start, i), type: 'STYRKE' }))
const weeks = (from, counts) => counts.flatMap((n, i) => week(addDays(from, 7 * i), n))
const statuses = (r) => r.weeks.map((w) => w.status)

describe('datoregning', () => {
  it('finner mandagen i uka, også søndag og månedsskifte', () => {
    expect(weekStart('2026-09-30')).toBe('2026-09-28') // onsdag
    expect(weekStart('2026-09-28')).toBe('2026-09-28') // mandag
    expect(weekStart('2026-10-04')).toBe('2026-09-28') // søndag
    expect(weekStart('2026-10-01')).toBe('2026-09-28') // over månedsskifte
    expect(weekStart('2027-01-01')).toBe('2026-12-28') // over årsskifte
  })

  it('addDays går over måneder og skuddår', () => {
    expect(addDays('2026-09-30', 1)).toBe('2026-10-01')
    expect(addDays('2028-02-28', 1)).toBe('2028-02-29')
    expect(addDays('2026-01-01', -1)).toBe('2025-12-31')
  })

  it('isIsoDate avviser tull og ugyldige datoer', () => {
    expect(isIsoDate('2026-09-30')).toBe(true)
    expect(isIsoDate('2026-02-30')).toBe(false)
    expect(isIsoDate('30.09.2026')).toBe(false)
    expect(isIsoDate(null)).toBe(false)
  })
})

describe('innstillinger', () => {
  it('holder målet mellom 1 og 7 og faller tilbake til standard', () => {
    expect(clampGoal(0)).toBe(1)
    expect(clampGoal(9)).toBe(7)
    expect(clampGoal(4.4)).toBe(4)
    expect(clampGoal('abc')).toBe(DEFAULT_GOAL)
  })

  it('normaliserer pauser til mandager, uten duplikater og uten ugyldige datoer', () => {
    const s = normalizeSettings({ goal: 5, pauses: ['2026-09-30', '2026-09-29', 'tull', '2026-09-21'] })
    expect(s).toEqual({ goal: 5, pauses: ['2026-09-21', '2026-09-28'] })
    expect(normalizeSettings(null)).toEqual({ goal: DEFAULT_GOAL, pauses: [] })
  })

  it('togglePause slår en uke av og på, og begrenser antall', () => {
    let p = togglePause([], '2026-09-30')
    expect(p).toEqual(['2026-09-28'])
    p = togglePause(p, '2026-09-29')
    expect(p).toEqual([])
    let many = []
    for (let i = 0; i < MAX_PAUSES + 5; i++) many = togglePause(many, addDays('2026-01-05', 7 * i))
    expect(many).toHaveLength(MAX_PAUSES)
    expect(many.at(-1)).toBe(addDays('2026-01-05', 7 * (MAX_PAUSES + 4))) // nyeste beholdes
  })
})

describe('computeStreak: grunnregler', () => {
  it('uten økter er tilstanden «empty» og serien 0', () => {
    const r = computeStreak([], { today: '2026-09-30' })
    expect(r.state).toBe('empty')
    expect(r.streak).toBe(0)
    expect(r.repair).toBeNull()
    expect(r.current.remaining).toBe(3)
  })

  it('teller avsluttede uker som nådde målet, og uka som pågår er «open» til den er nådd', () => {
    const r = computeStreak(weeks(MON(-3), [3, 3, 3]), { today: '2026-09-30' })
    expect(r.streak).toBe(3)
    expect(r.state).toBe('open')
    expect(r.current).toMatchObject({ days: 0, remaining: 3, daysLeft: 5, done: false })
    expect(statuses(r)).toEqual(['good', 'good', 'good', 'current'])
  })

  it('uka som pågår gir +1 med en gang målet er nådd', () => {
    const r = computeStreak([...weeks(MON(-3), [3, 3, 3]), ...week(MON(0), 3)], { today: '2026-09-30' })
    expect(r.streak).toBe(4)
    expect(r.state).toBe('done')
    expect(r.goodWeeks).toBe(4)
  })

  it('tre økter samme dag er én treningsdag', () => {
    const one = Array.from({ length: 3 }, () => ({ date: '2026-09-29' }))
    const r = computeStreak(one, { today: '2026-09-30' })
    expect(r.current.days).toBe(1)
  })

  it('en hviledag er aldri et brudd: 3 dager per uke på ulike dager holder serien', () => {
    const w = [{ date: '2026-09-07' }, { date: '2026-09-09' }, { date: '2026-09-12' }]
    const r = computeStreak([...w, ...weeks('2026-09-14', [3, 3])], { today: '2026-09-30' })
    expect(r.streak).toBe(3)
  })

  it('uka med første økt vurderes ikke som glipp', () => {
    const r = computeStreak([{ date: '2026-09-10' }, { date: '2026-09-11' }, ...weeks('2026-09-14', [3, 3])], { today: '2026-09-30' })
    expect(statuses(r).slice(0, 3)).toEqual(['start', 'good', 'good'])
    expect(r.streak).toBe(2)
  })

  it('målet kan endres: 2 dager er nok ved mål 2', () => {
    const r = computeStreak(weeks(MON(-2), [2, 2]), { goal: 2, today: '2026-09-30' })
    expect(r.streak).toBe(2)
  })

  it('daysLeft er 7 på mandag og 1 på søndag', () => {
    expect(computeStreak(week(MON(-1), 3), { today: '2026-09-28' }).current.daysLeft).toBe(7)
    expect(computeStreak(week(MON(-1), 3), { today: '2026-10-04' }).current.daysLeft).toBe(1)
  })
})

describe('computeStreak: glipp, hvilekort og comeback', () => {
  const threeGood = weeks('2026-09-07', [3, 3, 3]) // 7., 14., 21. september
  // I uke 28. september gjør vi ingenting; «i dag» er onsdag 7. oktober.
  const TODAY = '2026-10-07'

  it('en glipp uten hvilekort bryter serien, men lengste serie huskes og reparasjon tilbys', () => {
    const r = computeStreak(threeGood, { today: TODAY })
    expect(statuses(r).slice(-3)).toEqual(['good', 'missed', 'current'])
    expect(r.streak).toBe(0)
    expect(r.longest).toBe(3)
    expect(r.repair).toEqual({ broken: 3, target: 4 })
    expect(r.state).toBe('open')
  })

  it('reparasjon med handling: mål + 1 uka etter glippen redder den gamle serien', () => {
    const r = computeStreak([...threeGood, ...week('2026-10-05', 4)], { today: '2026-10-11' })
    expect(r.current.status).toBe('repaired')
    expect(r.streak).toBe(4) // 3 + uka
    expect(r.repair).toBeNull()
  })

  it('bare målet uka etter glippen er en comeback: ny serie på 1', () => {
    const r = computeStreak([...threeGood, ...week('2026-10-05', 3)], { today: '2026-10-11' })
    expect(r.current.status).toBe('comeback')
    expect(r.streak).toBe(1)
    expect(r.longest).toBe(3)
  })

  it('en pause mellom glippen og comebacket bruker ikke opp reparasjonsvinduet', () => {
    const r = computeStreak([...threeGood, ...week('2026-10-12', 4)], { today: '2026-10-18', pauses: ['2026-10-05'] })
    expect(statuses(r).slice(-3)).toEqual(['missed', 'pause', 'repaired'])
    expect(r.streak).toBe(4)
  })

  it('hvert 4. gode uke gir ett hvilekort som dekker en glipp', () => {
    const four = weeks('2026-09-07', [3, 3, 3, 3]) // 7., 14., 21., 28. september
    const r = computeStreak(four, { today: '2026-10-14' }) // uka 5. oktober er glippen
    expect(statuses(r).slice(-3)).toEqual(['good', 'covered', 'current'])
    expect(r.streak).toBe(4) // serien fortsetter, men uka gir ikke +1
    expect(r.cards).toBe(0)
    expect(r.repair).toBeNull()
  })

  it('kortet vises på lager før det brukes', () => {
    const four = weeks('2026-09-07', [3, 3, 3, 3])
    const r = computeStreak(four, { today: '2026-10-07' }) // 5. oktober pågår ennå
    expect(r.cards).toBe(1)
    expect(r.cardProgress).toBe(0)
    expect(r.earnedCardWeeks).toEqual(['2026-09-28'])
  })

  it('to glipper på rad: kortet dekker den første, den andre bryter', () => {
    const four = weeks('2026-09-07', [3, 3, 3, 3])
    const r = computeStreak(four, { today: '2026-10-21' })
    expect(statuses(r).slice(-4)).toEqual(['good', 'covered', 'missed', 'current'])
    expect(r.streak).toBe(0)
    expect(r.repair).toEqual({ broken: 4, target: 4 })
  })

  it('hvilekort har tak på 2 uansett hvor lenge serien varer', () => {
    const r = computeStreak(weeks(addDays('2026-09-28', -7 * 15), Array(16).fill(3)), { today: '2026-09-30' })
    expect(r.streak).toBe(16)
    expect(r.cards).toBe(MAX_CARDS)
  })

  it('fremdrift mot neste kort telles i gode uker', () => {
    const r = computeStreak(weeks('2026-09-07', [3, 3]), { today: '2026-09-23' }) // uka 21. september pågår
    expect(r.cardProgress).toBe(2)
    expect(r.cardEvery).toBe(4)
  })

  it('en glipp nullstiller opptjeningen mot neste kort', () => {
    const r = computeStreak(weeks('2026-09-07', [3, 3]), { today: '2026-10-07' }) // 21. og 28. september uten økter
    expect(r.cardProgress).toBe(0)
  })
})

describe('computeStreak: pause og «i fare»', () => {
  it('en pauset uke bryter ikke, øker ikke og bruker ikke kort', () => {
    const r = computeStreak(weeks('2026-09-07', [3, 3, 3]), { today: '2026-10-07', pauses: ['2026-09-28'] })
    expect(statuses(r).slice(-3)).toEqual(['good', 'pause', 'current'])
    expect(r.streak).toBe(3)
    expect(r.repair).toBeNull()
  })

  it('pauset uke nå: tilstanden er «paused» og serien er uendret', () => {
    const r = computeStreak(weeks('2026-09-07', [3, 3, 3]), { today: '2026-09-30', pauses: ['2026-09-28'] })
    expect(r.state).toBe('paused')
    expect(r.streak).toBe(3)
    expect(r.current.paused).toBe(true)
  })

  it('«atRisk» når målet ikke lenger kan nås og det ikke finnes hvilekort', () => {
    // søndag: 1 dag igjen, men 2 dager mangler
    const r = computeStreak([...weeks('2026-09-07', [3, 3, 3]), ...week('2026-09-28', 1)], { today: '2026-10-04' })
    expect(r.state).toBe('atRisk')
    expect(r.streak).toBe(3)
  })

  it('«covered» når målet ikke kan nås, men et hvilekort dekker uka', () => {
    const r = computeStreak([...weeks('2026-08-31', [3, 3, 3, 3]), ...week('2026-09-28', 1)], { today: '2026-10-04' })
    expect(r.state).toBe('covered')
    expect(r.cards).toBe(1)
  })

  it('første uka er aldri «i fare»', () => {
    const r = computeStreak([{ date: '2026-10-03' }], { today: '2026-10-04' })
    expect(r.state).toBe('open')
  })

  it('viser maks 8 uker, den siste er uka som pågår', () => {
    const r = computeStreak(weeks(addDays('2026-09-28', -7 * 11), Array(12).fill(3)), { today: '2026-09-30' })
    expect(r.weeks).toHaveLength(8)
    expect(r.weeks.at(-1)).toMatchObject({ current: true, start: '2026-09-28' })
  })

  it('fremtidige økter (klokkefeil) påvirker ikke avsluttede uker', () => {
    const r = computeStreak([{ date: '2027-05-01' }, ...weeks('2026-09-14', [3, 3])], { today: '2026-09-30' })
    expect(r.streak).toBe(2)
  })
})
