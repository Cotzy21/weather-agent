import { describe, it, expect } from 'vitest'
import {
  loadStreakSettings, storeStreakSettings, hasStoredStreakSettings, pullStreakSettings, pushStreakSettings,
} from './streakSettings.js'

const memoryStorage = (initial = {}) => {
  const data = { ...initial }
  return {
    getItem: (k) => (k in data ? data[k] : null),
    setItem: (k, v) => { data[k] = String(v) },
    data,
  }
}

const response = (status, body) => ({
  status,
  ok: status >= 200 && status < 300,
  json: async () => body,
  text: async () => JSON.stringify(body ?? {}),
  headers: { get: () => 'application/json' },
})

describe('lokal lagring', () => {
  it('gir standardverdier når ingenting er lagret eller lagringen er ødelagt', () => {
    expect(loadStreakSettings('u1', memoryStorage())).toEqual({ goal: 3, pauses: [] })
    expect(loadStreakSettings('u1', memoryStorage({ 'streak-settings:u1': '{ikke json' }))).toEqual({ goal: 3, pauses: [] })
    expect(loadStreakSettings('u1', undefined)).toEqual({ goal: 3, pauses: [] })
  })

  it('lagrer og leser tilbake per bruker, og renser verdiene', () => {
    const s = memoryStorage()
    expect(hasStoredStreakSettings('u1', s)).toBe(false)
    storeStreakSettings('u1', { goal: 99, pauses: ['2026-09-30'] }, s)
    expect(hasStoredStreakSettings('u1', s)).toBe(true)
    expect(loadStreakSettings('u1', s)).toEqual({ goal: 7, pauses: ['2026-09-28'] }) // mål begrenset, pause til mandag
    expect(hasStoredStreakSettings('u2', s)).toBe(false) // en annen bruker på samme enhet ser ikke dette
  })

  it('tåler at lagring kaster (privat modus)', () => {
    const broken = { getItem: () => { throw new Error('nei') }, setItem: () => { throw new Error('nei') } }
    expect(() => storeStreakSettings('u1', { goal: 3, pauses: [] }, broken)).not.toThrow()
    expect(loadStreakSettings('u1', broken)).toEqual({ goal: 3, pauses: [] })
    expect(hasStoredStreakSettings('u1', broken)).toBe(false)
  })
})

describe('konto', () => {
  it('pull: 204 betyr ikke lagret ennå', async () => {
    expect(await pullStreakSettings({}, async () => response(204))).toBeNull()
  })

  it('pull: 200 gir normaliserte innstillinger, andre svar kaster', async () => {
    const ok = await pullStreakSettings({ Authorization: 'x' }, async (url, init) => {
      expect(url).toContain('/api/innstillinger/streak')
      expect(init.headers.Authorization).toBe('x')
      return response(200, { goal: 4, pauses: ['2026-09-28'] })
    })
    expect(ok).toEqual({ goal: 4, pauses: ['2026-09-28'] })
    await expect(pullStreakSettings({}, async () => response(500, { error: 'feil' }))).rejects.toThrow()
  })

  it('push: sender PUT med JSON og returnerer det serveren lagret', async () => {
    let seen
    const out = await pushStreakSettings({ goal: 5, pauses: ['2026-09-30'] }, { Authorization: 'x' }, async (url, init) => {
      seen = { url, init }
      return response(200, { goal: 5, pauses: ['2026-09-28'] })
    })
    expect(seen.init.method).toBe('PUT')
    expect(seen.init.headers['Content-Type']).toBe('application/json')
    expect(JSON.parse(seen.init.body)).toEqual({ goal: 5, pauses: ['2026-09-28'] }) // sendes allerede renset
    expect(out).toEqual({ goal: 5, pauses: ['2026-09-28'] })
  })

  it('push: feil fra serveren kaster, så kalleren kan beholde den lokale kopien', async () => {
    await expect(pushStreakSettings({ goal: 3, pauses: [] }, {}, async () => response(400, { error: 'Ugyldig innstilling.' }))).rejects.toThrow()
  })
})
