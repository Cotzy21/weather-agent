import { describe, it, expect, beforeEach, vi } from 'vitest'

function fakeStorage() {
  const m = new Map()
  return {
    getItem: (k) => (m.has(k) ? m.get(k) : null),
    setItem: (k, v) => m.set(k, String(v)),
    removeItem: (k) => m.delete(k),
    key: (i) => [...m.keys()][i],
    get length() { return m.size },
    keys: () => [...m.keys()],
  }
}

let api
beforeEach(async () => {
  const storage = fakeStorage()
  // Object.keys(localStorage) i api.js: gi lagringen egne nøkler
  const proxy = new Proxy(storage, {
    ownKeys: () => storage.keys(),
    getOwnPropertyDescriptor: (t, k) => (t.keys().includes(k) ? { enumerable: true, configurable: true, value: t.getItem(k) } : Reflect.getOwnPropertyDescriptor(t, k)),
  })
  vi.stubGlobal('localStorage', proxy)
  vi.resetModules()
  api = await import('./api.js')
})

const respond = (data) => vi.stubGlobal('fetch', vi.fn(async () => ({ ok: true, status: 200, json: async () => data })))

describe('api cache', () => {
  it('keeps training data on the device so it is available offline after a restart', async () => {
    respond([{ id: 1 }])
    await api.cachedGet('/api/treningsokter')
    vi.resetModules()
    const fresh = await import('./api.js')
    expect(fresh.getCached('/api/treningsokter')).toEqual([{ id: 1 }])
  })

  it('does not persist other endpoints', async () => {
    respond({ secret: true })
    await api.cachedGet('/api/kosthold/dag')
    vi.resetModules()
    const fresh = await import('./api.js')
    expect(fresh.getCached('/api/kosthold/dag')).toBeUndefined()
  })

  it('keeps the stored copy for the same user but wipes it when another user logs in or out', async () => {
    api.switchCacheOwner('user-a')
    respond([1])
    await api.cachedGet('/api/treningsokter')

    vi.resetModules()
    let fresh = await import('./api.js')
    fresh.switchCacheOwner('user-a')
    expect(fresh.getCached('/api/treningsokter')).toEqual([1])

    fresh.switchCacheOwner('user-b')
    vi.resetModules()
    fresh = await import('./api.js')
    expect(fresh.getCached('/api/treningsokter')).toBeUndefined()
  })

  it('announces slow requests and clears the notice when they finish', async () => {
    vi.useFakeTimers()
    let release
    vi.stubGlobal('fetch', vi.fn(() => new Promise((r) => { release = () => r({ ok: true, status: 200, json: async () => ({}) }) })))
    const seen = []
    api.onSlowRequest((v) => seen.push(v))

    const p = api.cachedGet('/api/trening/minne')
    await vi.advanceTimersByTimeAsync(4100)
    expect(seen).toEqual([true])
    release()
    await p
    expect(seen).toEqual([true, false])
    vi.useRealTimers()
  })
})
