import { afterEach, expect, it, vi } from 'vitest'
import { cachedGet, getCached, switchCacheOwner } from './api.js'

afterEach(() => { switchCacheOwner(null); vi.unstubAllGlobals() })

it('discards old account responses and starts a new request after an account switch', async () => {
  const resolvers = []
  vi.stubGlobal('fetch', vi.fn(() => new Promise(resolve => resolvers.push(resolve))))
  switchCacheOwner('first')
  const old = cachedGet('/api/treningsokter')
  const rejected = expect(old).rejects.toThrow('Innloggingen ble endret')
  switchCacheOwner('second')
  const current = cachedGet('/api/treningsokter')
  resolvers[0]({ ok: true, json: async () => [{ private: 'first' }] })
  await rejected
  expect(getCached('/api/treningsokter')).toBeUndefined()
  resolvers[1]({ ok: true, json: async () => [{ private: 'second' }] })
  await current
  expect(getCached('/api/treningsokter')).toEqual([{ private: 'second' }])
  expect(fetch).toHaveBeenCalledTimes(2)
})
