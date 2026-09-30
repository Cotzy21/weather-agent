import { describe, it, expect } from 'vitest'
import {
  categoryOf, guessStrengthCategory, groupTemplates, matchesQuery, lastDoneDate, plannedToday, exerciseNames,
  CATEGORY_ORDER, CATEGORY_LABELS, STRENGTH_CATEGORIES,
} from './workoutCategories.js'

const ex = (name) => ({ kind: 'exercise', name, sets: [{ reps: 8, weightKg: 20 }] })
const plan = (title, names = [], extra = {}) => ({
  id: title, title, type: 'STYRKE', content: { blocks: names.map(ex), ...(extra.content ?? {}) }, ...extra,
})
const cat = (title, names) => categoryOf(plan(title, names))

describe('gjetting fra tittel', () => {
  it('kjenner Push, Pull, Bein, Overkropp, Underkropp, Hele kroppen og Kjerne (norsk og engelsk)', () => {
    expect(cat('Push A')).toBe('push')
    expect(cat('Pull day')).toBe('pull')
    expect(cat('Legs')).toBe('legs')
    expect(cat('Bein og rumpe')).toBe('legs')
    expect(cat('Overkropp')).toBe('upper')
    expect(cat('Upper body')).toBe('upper')
    expect(cat('Underkropp B')).toBe('lower')
    expect(cat('Lower')).toBe('lower')
    expect(cat('Fullkropp 3x')).toBe('full')
    expect(cat('Full body')).toBe('full')
    expect(cat('Hele kroppen')).toBe('full')
    expect(cat('Kjerne og mage')).toBe('core')
  })

  it('«ben» treffer ikke «benkpress», og store/små bokstaver og aksenter spiller ingen rolle', () => {
    expect(cat('Benkpress-dag')).not.toBe('legs')
    expect(cat('BEIN')).toBe('legs')
    expect(cat('Knebøy fokus')).toBe('legs')
  })

  it('Overkropp slår Push når begge står i tittelen (mest spesifikke først)', () => {
    expect(cat('Overkropp push')).toBe('upper')
    expect(cat('Push/Pull/Legs')).toBe('push')
  })
})

describe('gjetting fra øvelser (uten treffende tittel)', () => {
  it('bare brystøvelser er push', () => {
    expect(cat('Mandagsøkt', ['Benkpress', 'Skråbenkpress', 'Flies'])).toBe('push')
  })
  it('bryst og skuldre er push, også med litt triceps', () => {
    expect(cat('X', ['Benkpress', 'Skulderpress', 'Sidehev', 'Triceps pushdown'])).toBe('push')
  })
  it('ryggøvelser er pull', () => {
    expect(cat('X', ['Nedtrekk', 'Sittende roing', 'Face pulls', 'Bicepscurl'])).toBe('pull')
  })
  it('bare beinøvelser er bein', () => {
    expect(cat('X', ['Knebøy', 'Leg press', 'Utfall'])).toBe('legs')
  })
  it('bryst og rygg uten bein er overkropp', () => {
    expect(cat('X', ['Benkpress', 'Nedtrekk', 'Skulderpress', 'Sittende roing'])).toBe('upper')
  })
  it('bare armer regnes som overkropp', () => {
    expect(cat('X', ['Bicepscurl', 'Hammercurl', 'Skullcrushers'])).toBe('upper')
  })
  it('bein sammen med overkropp er hele kroppen', () => {
    expect(cat('X', ['Knebøy', 'Benkpress', 'Nedtrekk'])).toBe('full')
  })
  it('helkroppsøvelser er hele kroppen', () => {
    expect(cat('X', ['Kettlebell swing', 'Burpees', 'Thruster'])).toBe('full')
  })
  it('mageøvelser er kjerne', () => {
    expect(cat('X', ['Planke', 'Sit-ups', 'Russian twists'])).toBe('core')
  })
  it('ukjente øvelser og tomme økter havner i «annen styrke»', () => {
    expect(cat('X', ['Min egen øvelse'])).toBe('other')
    expect(cat('X', [])).toBe('other')
    expect(guessStrengthCategory(undefined, null)).toBe('other')
  })
  it('øvelser i supersett teller med', () => {
    const p = { id: 's', title: 'X', type: 'STYRKE', content: { blocks: [{ kind: 'superset', rounds: 3, exercises: [{ name: 'Knebøy', sets: [] }, { name: 'Leg press', sets: [] }] }] } }
    expect(categoryOf(p)).toBe('legs')
    expect(exerciseNames(p.content)).toEqual(['Knebøy', 'Leg press'])
  })
})

describe('categoryOf', () => {
  it('brukerens eget valg går foran gjetting', () => {
    expect(categoryOf(plan('Push A', ['Benkpress'], { content: { category: 'lower' } }))).toBe('lower')
  })
  it('ugyldig lagret kategori ignoreres', () => {
    expect(categoryOf(plan('Push A', [], { content: { category: 'tull' } }))).toBe('push')
  })
  it('andre aktiviteter er sin egen kategori etter type, og ukjente typer havner i fristil', () => {
    expect(categoryOf({ type: 'LØPING', title: 'Intervall' })).toBe('LØPING')
    expect(categoryOf({ type: 'HIKING', title: 'Langtur' })).toBe('HIKING')
    expect(categoryOf({ type: 'YOGA', title: 'Yoga' })).toBe('FRISTIL')
  })
  it('alle kategorier har et navn og en plass i rekkefølgen', () => {
    for (const id of CATEGORY_ORDER) expect(CATEGORY_LABELS[id]).toBeTruthy()
    expect(STRENGTH_CATEGORIES.every((c) => CATEGORY_ORDER.includes(c))).toBe(true)
  })
})

describe('gruppering', () => {
  const plans = [
    plan('Pull B', ['Nedtrekk']),
    plan('Push A', ['Benkpress']),
    plan('Push C', ['Benkpress']),
    plan('Bein', ['Knebøy']),
    { id: 'run', title: 'Intervall', type: 'LØPING', content: { distanceKm: 5 } },
    plan('Fullkropp', ['Burpees']),
  ]

  it('gir kategoriene i fast rekkefølge og utelater tomme', () => {
    const g = groupTemplates(plans, [])
    expect(g.map((x) => x.id)).toEqual(['push', 'pull', 'legs', 'full', 'LØPING'])
  })

  it('sorterer innen en kategori: sist brukt først, så alfabetisk', () => {
    const workouts = [{ id: 'w', date: '2026-09-20', title: 'Push C', plannedId: 'Push C' }]
    const push = groupTemplates(plans, workouts).find((x) => x.id === 'push')
    expect(push.items.map((i) => i.plan.title)).toEqual(['Push C', 'Push A'])
    expect(push.items[0].lastDone).toBe('2026-09-20')
    expect(push.items[1].lastDone).toBeNull()
    const alpha = groupTemplates(plans, []).find((x) => x.id === 'push')
    expect(alpha.items.map((i) => i.plan.title)).toEqual(['Push A', 'Push C'])
  })

  it('tomt utvalg gir ingen grupper', () => {
    expect(groupTemplates([], [])).toEqual([])
    expect(groupTemplates(null, null)).toEqual([])
  })

  it('søk filtrerer på tittel, kategorinavn og øvelse (uten hensyn til store/små bokstaver og aksenter)', () => {
    expect(groupTemplates(plans, [], { query: 'push' }).flatMap((g) => g.items.map((i) => i.plan.title))).toEqual(['Push A', 'Push C'])
    expect(groupTemplates(plans, [], { query: 'KNEBØY' }).flatMap((g) => g.items.map((i) => i.plan.title))).toEqual(['Bein'])
    expect(groupTemplates(plans, [], { query: 'hele kroppen' }).flatMap((g) => g.items.map((i) => i.plan.title))).toEqual(['Fullkropp'])
    expect(groupTemplates(plans, [], { query: 'finnesikke' })).toEqual([])
    expect(matchesQuery(plans[0], '   ')).toBe(true)
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
