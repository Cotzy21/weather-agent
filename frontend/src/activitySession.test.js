import { describe, it, expect } from 'vitest'
import {
  newActivity, elapsedMinutes, defaultForm, toActivityBody, loadActivity, saveActivity, clearActivity, usesDistance, usesAscent,
} from './activitySession.js'

const memory = () => {
  const d = {}
  return { getItem: (k) => (k in d ? d[k] : null), setItem: (k, v) => { d[k] = String(v) }, removeItem: (k) => { delete d[k] } }
}
const T0 = new Date(2026, 8, 30, 10, 0, 0).getTime() // 30. september 2026 kl. 10.00 lokal tid

describe('newActivity', () => {
  it('tar mål og id fra malen, og lager en clientId', () => {
    const a = newActivity('LØPING', { id: 'p1', title: 'Intervall', content: { distanceKm: 5, durationMin: 30 } }, T0)
    expect(a).toMatchObject({ type: 'LØPING', title: 'Intervall', plannedId: 'p1', startedAt: T0,
      targets: { distanceKm: 5, durationMin: 30, ascentM: null } })
    expect(a.clientId).toBeTruthy()
  })
  it('en tom økt av en type har ingen mal og ingen mål', () => {
    const a = newActivity('HIKING', null, T0)
    expect(a).toMatchObject({ type: 'HIKING', title: '', plannedId: null, targets: { distanceKm: null, durationMin: null, ascentM: null } })
  })
  it('hver økt får sin egen clientId', () => {
    expect(newActivity('LØPING', null, T0).clientId).not.toBe(newActivity('LØPING', null, T0).clientId)
  })
})

describe('klokke og skjema', () => {
  it('minutter siden start, minst 1', () => {
    const a = newActivity('LØPING', null, T0)
    expect(elapsedMinutes(a, T0 + 5_000)).toBe(1)
    expect(elapsedMinutes(a, T0 + 45 * 60_000)).toBe(45)
    expect(elapsedMinutes(a, T0 + 45.4 * 60_000)).toBe(45)
  })
  it('skjemaet foreslår varighet fra klokka og distanse/stigning fra malen', () => {
    const run = newActivity('LØPING', { id: 'p', title: 'x', content: { distanceKm: 8.5, ascentM: 200 } }, T0)
    expect(defaultForm(run, T0 + 30 * 60_000)).toEqual({ durationMin: '30', distanceKm: '8.5', ascentM: '' }) // stigning gjelder bare tur
    const hike = newActivity('HIKING', { id: 'p', title: 'x', content: { distanceKm: 12, ascentM: 700 } }, T0)
    expect(defaultForm(hike, T0 + 90 * 60_000)).toEqual({ durationMin: '90', distanceKm: '12', ascentM: '700' })
    expect(defaultForm(newActivity('BULDRING', null, T0), T0 + 60 * 60_000)).toEqual({ durationMin: '60', distanceKm: '', ascentM: '' })
  })
  it('hvilke typer bruker distanse og stigning', () => {
    expect(['LØPING', 'SYKKEL', 'SVØMMING', 'HIKING'].every(usesDistance)).toBe(true)
    expect(usesDistance('BULDRING')).toBe(false)
    expect(usesAscent('HIKING')).toBe(true)
    expect(usesAscent('LØPING')).toBe(false)
  })
})

describe('toActivityBody', () => {
  const run = newActivity('LØPING', { id: 'p1', title: 'Intervall', content: {} }, T0)

  it('bygger kroppen med dato fra starten, type, innhold, plan-id og clientId', () => {
    const r = toActivityBody(run, { durationMin: '32', distanceKm: '5,4', ascentM: '' }, '')
    expect(r.error).toBeUndefined()
    expect(r.body).toEqual({
      date: '2026-09-30', title: 'Intervall', type: 'LØPING', content: { durationMin: 32, distanceKm: 5.4 },
      notes: null, clientId: run.clientId, plannedId: 'p1',
    })
  })
  it('bruker tittelen fra skjemaet, ellers malens, ellers typen', () => {
    expect(toActivityBody(run, { durationMin: '10' }, '  Morgentur ').body.title).toBe('Morgentur')
    expect(toActivityBody(newActivity('SYKKEL', null, T0), { durationMin: '10' }, '').body.title).toBe('SYKKEL')
  })
  it('en økt over midnatt hører til dagen den startet', () => {
    const late = newActivity('LØPING', null, new Date(2026, 8, 30, 23, 50).getTime())
    expect(toActivityBody(late, { durationMin: '40' }, '').body.date).toBe('2026-09-30')
  })
  it('distanse og stigning sendes bare for typer som bruker dem, og tomme felt utelates', () => {
    const boulder = newActivity('BULDRING', null, T0)
    expect(toActivityBody(boulder, { durationMin: '60', distanceKm: '5', ascentM: '9' }, '').body.content).toEqual({ durationMin: 60 })
    expect(toActivityBody(run, { durationMin: '20', distanceKm: '' }, '').body.content).toEqual({ durationMin: 20 })
    const hike = newActivity('HIKING', null, T0)
    expect(toActivityBody(hike, { durationMin: '120', distanceKm: '10', ascentM: '650' }, '').body.content)
      .toEqual({ durationMin: 120, distanceKm: 10, ascentM: 650 })
  })
  it('avviser ugyldig varighet, distanse og stigning med en norsk melding', () => {
    for (const d of ['', '0', '-5', 'abc', '1441']) expect(toActivityBody(run, { durationMin: d }, '').error).toMatch(/Varigheten/)
    expect(toActivityBody(run, { durationMin: '20', distanceKm: '-1' }, '').error).toMatch(/Distansen/)
    expect(toActivityBody(run, { durationMin: '20', distanceKm: '1001' }, '').error).toMatch(/Distansen/)
    expect(toActivityBody(newActivity('HIKING', null, T0), { durationMin: '20', ascentM: '20000' }, '').error).toMatch(/Stigningen/)
  })
  it('runder varigheten til hele minutter', () => {
    expect(toActivityBody(run, { durationMin: '20.6' }, '').body.content.durationMin).toBe(21)
  })
})

describe('lagring', () => {
  it('lagrer og leser tilbake per bruker, og ryddes ved fullføring', () => {
    const s = memory()
    const a = newActivity('LØPING', null, T0)
    expect(loadActivity('u1', s)).toBeNull()
    saveActivity('u1', a, s)
    expect(loadActivity('u1', s)).toEqual(a)
    expect(loadActivity('u2', s)).toBeNull()
    clearActivity('u1', s)
    expect(loadActivity('u1', s)).toBeNull()
  })
  it('ødelagt eller ugyldig lagring gir null, og lagring som kaster stopper ikke appen', () => {
    const s = memory()
    s.setItem('activity-session:u1', '{ikke json')
    expect(loadActivity('u1', s)).toBeNull()
    s.setItem('activity-session:u1', JSON.stringify({ hei: 1 }))
    expect(loadActivity('u1', s)).toBeNull()
    const broken = { getItem: () => { throw new Error('x') }, setItem: () => { throw new Error('x') }, removeItem: () => { throw new Error('x') } }
    expect(() => saveActivity('u1', {}, broken)).not.toThrow()
    expect(() => clearActivity('u1', broken)).not.toThrow()
    expect(loadActivity('u1', broken)).toBeNull()
  })
})
