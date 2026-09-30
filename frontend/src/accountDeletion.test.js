import { describe, it, expect } from 'vitest'
import { deleteAccount, eraseLocalUserData, isDeleteConfirmation } from './accountDeletion.js'

// Et lagringsobjekt som ligner localStorage: nøklene er enumererbare, removeItem er det ikke.
function fakeStorage(entries) {
  const s = { ...entries }
  Object.defineProperty(s, 'removeItem', { value: (k) => { delete s[k] } })
  return s
}

describe('isDeleteConfirmation', () => {
  it('accepts the confirmation word in Norwegian or English, in any case', () => {
    for (const ok of ['slett', 'SLETT', ' Slett ', 'delete', 'DELETE']) expect(isDeleteConfirmation(ok)).toBe(true)
  })

  it('rejects everything else, including empty input', () => {
    for (const no of ['', '  ', 'sletter', 'yes', 'ok', null, undefined]) expect(isDeleteConfirmation(no)).toBe(false)
  })
})

describe('eraseLocalUserData', () => {
  it("removes this user's live session and dashboard layout, and the local nutrition data", () => {
    const storage = fakeStorage({
      'live-session:u1': '{}',
      'dash-layout:u1': '[]',
      'kosthold-favoritter': '[]',
      'kosthold-maaltider': '[]',
    })
    eraseLocalUserData('u1', storage)
    expect(Object.keys(storage)).toEqual([])
  })

  it('keeps language, theme and other users\' entries', () => {
    const storage = fakeStorage({ lang: 'nb', theme: 'dark', 'live-session:u2': '{}', 'live-session:u1': '{}' })
    eraseLocalUserData('u1', storage)
    expect(Object.keys(storage).sort()).toEqual(['lang', 'live-session:u2', 'theme'])
  })

  it('never throws when storage is unavailable (private mode)', () => {
    expect(() => eraseLocalUserData('u1', null)).not.toThrow()
  })
})

describe('deleteAccount', () => {
  it('sends DELETE to /api/konto with the auth headers and returns the result', async () => {
    let seen
    const fetchFn = async (url, init) => {
      seen = { url, init }
      return { ok: true, json: async () => ({ deletedRows: 7, loginRemoved: true }) }
    }
    const result = await deleteAccount({ headers: { Authorization: 'Bearer x' }, fetchFn })
    expect(result).toEqual({ deletedRows: 7, loginRemoved: true })
    expect(seen.url).toMatch(/\/api\/konto$/)
    expect(seen.init).toEqual({ method: 'DELETE', headers: { Authorization: 'Bearer x' } })
  })

  it('throws a readable message when the server refuses', async () => {
    const fetchFn = async () => ({ ok: false, status: 401, json: async () => ({}) })
    await expect(deleteAccount({ headers: {}, fetchFn })).rejects.toThrow('innlogget')
  })
})
