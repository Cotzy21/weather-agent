import { describe, it, expect } from 'vitest'
import {
  buildSteps, currentIndex, nextUndone, restAfter, restFor, overview, makeSuperset, unlinkSuperset, removeExercise, stepValue,
  adjustRestTimer, progress, clampRest, DEFAULT_REST_SEC, MIN_REST_SEC, MAX_REST_SEC,
} from './liveFlow.js'
import { fromPlan, toContent } from './liveSession.js'

const set = (reps = '8', weightKg = '50', done = false) => ({ reps, weightKg, done })
const ex = (name, n = 3, extra = {}) => ({ kind: 'exercise', name, sets: Array.from({ length: n }, () => set()), ...extra })
const marks = (exercises, steps) => steps.map((s) => `${exercises[s.ei].name[0]}${s.si + 1}`).join(' ')

describe('buildSteps', () => {
  it('vanlige øvelser gjøres sett for sett, én øvelse om gangen', () => {
    const e = [ex('A', 2), ex('B', 3)]
    expect(marks(e, buildSteps(e))).toBe('A1 A2 B1 B2 B3')
  })

  it('supersett går vekselvis i runder: A1 B1 A2 B2 …', () => {
    const e = [ex('A', 3, { group: 'g' }), ex('B', 3, { group: 'g' }), ex('C', 2)]
    expect(marks(e, buildSteps(e))).toBe('A1 B1 A2 B2 A3 B3 C1 C2')
  })

  it('supersett der øvelsene har ulikt antall sett: den lengste fortsetter alene', () => {
    const e = [ex('A', 3, { group: 'g' }), ex('B', 1, { group: 'g' })]
    expect(marks(e, buildSteps(e))).toBe('A1 B1 A2 A3')
  })

  it('supersett kan ha tre øvelser, og to supersett holdes hver for seg', () => {
    const e = [ex('A', 2, { group: 'g' }), ex('B', 2, { group: 'g' }), ex('C', 2, { group: 'g' }), ex('D', 1, { group: 'h' }), ex('E', 1, { group: 'h' })]
    expect(marks(e, buildSteps(e))).toBe('A1 B1 C1 A2 B2 C2 D1 E1')
  })

  it('en øvelse med tomme sett og en tom økt gir ingen steg', () => {
    expect(buildSteps([])).toEqual([])
    expect(buildSteps([ex('A', 0)])).toEqual([])
  })

  it('medlemmer som ikke ligger ved siden av hverandre samles der det første medlemmet står', () => {
    const e = [ex('A', 1, { group: 'g' }), ex('X', 1), ex('B', 1, { group: 'g' })]
    expect(marks(e, buildSteps(e))).toBe('A1 B1 X1')
  })
})

describe('current/next', () => {
  const e = [ex('A', 2), ex('B', 2)]
  const steps = () => buildSteps(e)

  it('første ikke-gjorte sett er det som gjøres nå, og -1 når alt er gjort', () => {
    const ex1 = [ex('A', 2), ex('B', 1)]
    expect(currentIndex(ex1, buildSteps(ex1), null)).toBe(0)
    ex1[0].sets[0].done = true
    expect(currentIndex(ex1, buildSteps(ex1), null)).toBe(1)
    ex1.forEach((x) => x.sets.forEach((s) => { s.done = true }))
    expect(currentIndex(ex1, buildSteps(ex1), null)).toBe(-1)
  })

  it('en cursor (hoppet til) går foran, men bare hvis settet ikke er gjort og finnes', () => {
    const fresh = [ex('A', 2), ex('B', 2)]
    const s = buildSteps(fresh)
    expect(currentIndex(fresh, s, { ei: 1, si: 0 })).toBe(2)
    fresh[1].sets[0].done = true
    expect(currentIndex(fresh, s, { ei: 1, si: 0 })).toBe(0) // gjort: tilbake til første ugjorte
    expect(currentIndex(fresh, s, { ei: 9, si: 9 })).toBe(0)
  })

  it('nextUndone går videre etter steget, og rundt til starten om ingenting er igjen etter', () => {
    const f = [ex('A', 2), ex('B', 2)]
    const s = buildSteps(f)
    f[0].sets[0].done = true
    expect(nextUndone(f, s, 0)).toBe(1)
    f[1].sets[1].done = true
    expect(nextUndone(f, s, 3)).toBe(1) // rundt: A2 er ikke gjort
    f.forEach((x) => x.sets.forEach((q) => { q.done = true }))
    expect(nextUndone(f, s, 0)).toBe(-1)
    expect(steps().length).toBe(4)
    expect(e.length).toBe(2)
  })

  it('hopper en øvelse i det siste og fortsetter i samme øvelse etter hoppet', () => {
    const f = [ex('A', 2), ex('B', 3)]
    const s = buildSteps(f)
    f[1].sets[0].done = true // brukeren hoppet til B og gjorde sett 1
    expect(nextUndone(f, s, 2)).toBe(3) // B2, ikke A1
  })
})

describe('hvile', () => {
  it('vanlige sett: øvelsens hvile, ellers økas standard', () => {
    const e = [ex('A', 2, { restSec: 120 }), ex('B', 2)]
    const s = buildSteps(e)
    expect(restAfter(e, s, 0, { restSec: 90 })).toBe(120)
    expect(restAfter(e, s, 2, { restSec: 75 })).toBe(75)
    expect(restAfter(e, s, 2)).toBe(DEFAULT_REST_SEC)
  })

  it('supersett: ingen hvile mellom øvelsene, full hvile etter runden (den lengste)', () => {
    const e = [ex('A', 2, { group: 'g', restSec: 60 }), ex('B', 2, { group: 'g', restSec: 120 })]
    const s = buildSteps(e) // A1 B1 A2 B2
    expect(restAfter(e, s, 0, { restSec: 90 })).toBe(0) // A1 → B1 med en gang
    expect(restAfter(e, s, 1, { restSec: 90 })).toBe(120) // B1 avslutter runden: den lengste hvilen
    expect(restAfter(e, s, 2, { restSec: 90 })).toBe(0)
    expect(restAfter(e, s, 3, { restSec: 90 })).toBe(120)
  })

  it('supersett kan ha en kort overgang i stedet for ingen', () => {
    const e = [ex('A', 1, { group: 'g' }), ex('B', 1, { group: 'g' })]
    expect(restAfter(e, buildSteps(e), 0, { transitionSec: 10 })).toBe(10)
  })

  it('ukjent steg gir ingen hvile, og hvilen holdes mellom minimum og maksimum', () => {
    expect(restAfter([], [], 0)).toBe(0)
    expect(clampRest(1)).toBe(MIN_REST_SEC)
    expect(clampRest(9999)).toBe(MAX_REST_SEC)
    expect(clampRest('abc')).toBe(DEFAULT_REST_SEC)
    expect(restFor({ restSec: 5 }, 90)).toBe(MIN_REST_SEC)
  })
})

describe('adjustRestTimer', () => {
  const NOW = 1_000_000
  it('+15 forlenger en pågående hvile, −15 forkorter den (aldri bak i tid)', () => {
    expect(adjustRestTimer({ restEnd: NOW + 40_000, restTotal: 90 }, 15, NOW)).toEqual({ restEnd: NOW + 55_000, restTotal: 105 })
    expect(adjustRestTimer({ restEnd: NOW + 40_000, restTotal: 90 }, -15, NOW)).toEqual({ restEnd: NOW + 25_000, restTotal: 75 })
    expect(adjustRestTimer({ restEnd: NOW + 10_000, restTotal: 90 }, -15, NOW).restEnd).toBe(NOW)
  })
  it('+15 etter at hvilen har gått ut starter en ny hvile på 15 s', () => {
    expect(adjustRestTimer({ restEnd: NOW - 5_000, restTotal: 90 }, 15, NOW)).toEqual({ restEnd: NOW + 15_000, restTotal: 15 })
  })
  it('uten pågående hvile skjer ingenting', () => {
    expect(adjustRestTimer({ restEnd: null, restTotal: null }, 15, NOW)).toEqual({ restEnd: null, restTotal: null })
  })
})

describe('supersett midt i økta', () => {
  it('lager supersett av to øvelser og setter dem etter hverandre', () => {
    const e = [ex('Leg extension', 3), ex('Skulderpress', 3), ex('Planke', 3)]
    const out = makeSuperset(e, 0, 2, 'g1')
    expect(out.map((x) => x.name)).toEqual(['Leg extension', 'Planke', 'Skulderpress'])
    expect(out.map((x) => x.group)).toEqual(['g1', 'g1', undefined])
    expect(marks(out, buildSteps(out))).toBe('L1 P1 L2 P2 L3 P3 S1 S2 S3')
    expect(e[0].group).toBeUndefined() // muterer ikke inputen
  })

  it('midt i økta: gjorde man A1 først, er B1 neste (A er allerede gjort)', () => {
    const e = [ex('Leg extension', 3), ex('Planke', 3)]
    e[0].sets[0].done = true
    const out = makeSuperset(e, 0, 1, 'g')
    const steps = buildSteps(out)
    expect(marks(out, steps)).toBe('L1 P1 L2 P2 L3 P3')
    expect(currentIndex(out, steps, null)).toBe(1) // P1
  })

  it('en øvelse som allerede er i et supersett slår gruppene sammen', () => {
    const e = [ex('A', 2, { group: 'g' }), ex('B', 2, { group: 'g' }), ex('C', 2)]
    const out = makeSuperset(e, 1, 2, 'ny')
    expect(out.map((x) => x.group)).toEqual(['g', 'g', 'g'])
    expect(marks(out, buildSteps(out))).toBe('A1 B1 C1 A2 B2 C2')
  })

  it('en ikke-nabo flyttes inntil, og dropsett og samme øvelse to ganger avvises', () => {
    const e = [ex('A', 1), ex('X', 1), ex('B', 1)]
    expect(makeSuperset(e, 0, 2, 'g').map((x) => x.name)).toEqual(['A', 'B', 'X'])
    const drop = [ex('A', 1), { kind: 'dropset', name: 'D', sets: [set()] }]
    expect(makeSuperset(drop, 0, 1)).toBe(drop)
    expect(makeSuperset(e, 1, 1)).toBe(e)
    expect(makeSuperset(e, 0, 9)).toBe(e)
  })

  it('løs opp gjør alle medlemmene til vanlige øvelser igjen', () => {
    const e = [ex('A', 1, { group: 'g' }), ex('B', 1, { group: 'g' }), ex('C', 1)]
    const out = unlinkSuperset(e, 0)
    expect(out.every((x) => x.group === undefined)).toBe(true)
    expect(unlinkSuperset(e, 2)).toBe(e)
  })

  it('å fjerne en øvelse oppløser et supersett som blir på én, men beholder et på tre', () => {
    const two = [ex('A', 1, { group: 'g' }), ex('B', 1, { group: 'g' })]
    expect(removeExercise(two, 0).map((x) => x.group)).toEqual([undefined])
    const three = [ex('A', 1, { group: 'g' }), ex('B', 1, { group: 'g' }), ex('C', 1, { group: 'g' })]
    expect(removeExercise(three, 1).map((x) => x.group)).toEqual(['g', 'g'])
    expect(removeExercise([ex('A', 1), ex('B', 1)], 0).map((x) => x.name)).toEqual(['B'])
  })
})

describe('overview og progress', () => {
  it('øvelsene i utførelsesrekkefølge med fremdrift og status; supersett merkes', () => {
    const e = [ex('A', 2), ex('B', 2, { group: 'g' }), ex('C', 2, { group: 'g' })]
    e[0].sets.forEach((s) => { s.done = true })
    const steps = buildSteps(e)
    const o = overview(e, steps, currentIndex(e, steps, null))
    expect(o.map((x) => [x.name, x.state, x.done, x.total, x.group])).toEqual([
      ['A', 'done', 2, 2, null], ['B', 'current', 0, 2, 'g'], ['C', 'current', 0, 2, 'g'],
    ])
    expect(progress(e)).toEqual({ done: 2, total: 6 })
  })
  it('alt gjort: ingen er «current»', () => {
    const e = [ex('A', 1)]
    e[0].sets[0].done = true
    const steps = buildSteps(e)
    expect(overview(e, steps, -1)[0].state).toBe('done')
  })
})

describe('stepValue', () => {
  it('kg i steg på 2,5 og reps i steg på 1, tom verdi starter på startverdien', () => {
    expect(stepValue('60', 2.5)).toBe('62.5')
    expect(stepValue('62,5', -2.5)).toBe('60') // komma som desimaltegn
    expect(stepValue('', 1, { start: 8, min: 1 })).toBe('8') // første trykk gir startverdien
    expect(stepValue('', -1, { start: 8, min: 1 })).toBe('8')
    expect(stepValue('', 2.5)).toBe('2.5') // kg uten startverdi: ett steg opp
    expect(stepValue('8', -1, { min: 1 })).toBe('7')
  })
  it('holder seg innenfor grensene og kan bli tom (kroppsvekt)', () => {
    expect(stepValue('1', -1, { min: 1 })).toBe('1')
    expect(stepValue('2.5', -2.5, { blankAtZero: true })).toBe('')
    expect(stepValue('999', 5, { max: 1000 })).toBe('1000')
    expect(stepValue('abc', 1, { start: 8 })).toBe('8')
  })
})

describe('fromPlan og toContent med supersett', () => {
  const plan = {
    blocks: [
      { kind: 'exercise', name: 'Benkpress', sets: [{ reps: 8, weightKg: 60 }, { reps: 8, weightKg: 60 }] },
      { kind: 'superset', rounds: 3, exercises: [{ name: 'Leg extension', sets: [{ reps: 12, weightKg: 40 }] }, { name: 'Planke', sets: [{ reps: 1, weightKg: 0 }] }] },
    ],
  }

  it('fromPlan gir supersett-øvelsene en felles gruppe og runder × sett hver', () => {
    const live = fromPlan(plan, [])
    expect(live.map((x) => [x.name, x.sets.length, Boolean(x.group)])).toEqual([['Benkpress', 2, false], ['Leg extension', 3, true], ['Planke', 3, true]])
    expect(live[1].group).toBe(live[2].group)
    expect(marks(live, buildSteps(live))).toBe('B1 B2 L1 P1 L2 P2 L3 P3')
  })

  it('toContent lagrer et supersett som én supersett-blokk med bare avhukede sett', () => {
    const live = fromPlan(plan, [])
    live[1].sets[0].done = true; live[1].sets[1].done = true
    live[2].sets[0].done = true
    live[0].sets[0].done = true
    const c = toContent(live, 0, 30 * 60_000)
    expect(c.durationMin).toBe(30)
    expect(c.blocks[0]).toMatchObject({ kind: 'exercise', name: 'Benkpress', sets: [{ reps: 8, weightKg: 60 }] })
    expect(c.blocks[1]).toEqual({
      kind: 'superset', rounds: 1,
      exercises: [
        { name: 'Leg extension', sets: [{ reps: 12, weightKg: 40 }, { reps: 12, weightKg: 40 }] },
        { name: 'Planke', sets: [{ reps: 1, weightKg: 0 }] },
      ],
    })
  })

  it('et supersett der bare én øvelse ble gjort lagres som en vanlig øvelse; ingen sett gir ingen blokk', () => {
    const live = fromPlan(plan, [])
    live[2].sets[0].done = true
    const c = toContent(live, 0, 60_000)
    expect(c.blocks).toEqual([{ kind: 'exercise', name: 'Planke', sets: [{ reps: 1, weightKg: 0 }] }])
  })

  it('vanlige øvelser og notater lagres som før', () => {
    const live = [{ kind: 'exercise', name: 'Knebøy', note: ' dypt ', sets: [set('5', '100', true), set('', '', true), set('5', '100', false)] }]
    expect(toContent(live, 0, 120_000).blocks).toEqual([{ kind: 'exercise', name: 'Knebøy', sets: [{ reps: 5, weightKg: 100 }], note: 'dypt' }])
  })
})
