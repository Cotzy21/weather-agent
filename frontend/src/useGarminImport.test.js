import { describe, it, expect, vi } from 'vitest'
import { postWithRetry } from './useGarminImport.js'

const ok = (data) => ({ ok: true, status: 200, json: async () => data, headers: { get: () => null } })
const fail = (status, error = 'x', retryAfter) => ({
  ok: false, status, json: async () => ({ error }), headers: { get: (h) => (h === 'Retry-After' ? retryAfter : null) },
})
const opts = (fetchImpl, wait = vi.fn(async () => {})) => ({ fetchImpl, headers: async () => ({ Authorization: 'Bearer t' }), wait })

describe('postWithRetry', () => {
  it('sends the body with the content type and the auth header', async () => {
    const fetchImpl = vi.fn(async () => ok({ imported: 3 }))
    const res = await postWithRetry('/api/x', 'abc', 'text/plain', opts(fetchImpl))
    expect(res).toEqual({ imported: 3 })
    const [, init] = fetchImpl.mock.calls[0]
    expect(init.headers).toMatchObject({ 'Content-Type': 'text/plain', Authorization: 'Bearer t' })
    expect(init.body).toBe('abc')
  })

  it('retries after a network error (bad coverage) and then succeeds', async () => {
    const fetchImpl = vi.fn().mockRejectedValueOnce(new TypeError('Failed to fetch')).mockResolvedValueOnce(ok({ imported: 1 }))
    const wait = vi.fn(async () => {})
    expect(await postWithRetry('/api/x', '{}', 'application/json', opts(fetchImpl, wait))).toEqual({ imported: 1 })
    expect(fetchImpl).toHaveBeenCalledTimes(2)
    expect(wait).toHaveBeenCalledTimes(1)
  })

  it('waits for Retry-After when rate limited, then continues', async () => {
    const fetchImpl = vi.fn().mockResolvedValueOnce(fail(429, 'For mange', '7')).mockResolvedValueOnce(ok({ ok: 1 }))
    const wait = vi.fn(async () => {})
    await postWithRetry('/api/x', '{}', 'application/json', opts(fetchImpl, wait))
    expect(wait).toHaveBeenCalledWith(7000)
  })

  it('retries server errors a few times but gives up with the server message', async () => {
    const fetchImpl = vi.fn(async () => fail(503, 'Utilgjengelig'))
    await expect(postWithRetry('/api/x', '{}', 'application/json', opts(fetchImpl))).rejects.toThrow('Utilgjengelig')
    expect(fetchImpl).toHaveBeenCalledTimes(4)
  })

  it('does not retry a rejected request (4xx): a new attempt cannot help', async () => {
    const fetchImpl = vi.fn(async () => fail(400, 'Ugyldig økt'))
    await expect(postWithRetry('/api/x', '{}', 'application/json', opts(fetchImpl))).rejects.toThrow('Ugyldig økt')
    expect(fetchImpl).toHaveBeenCalledTimes(1)
  })

  it('gives up after repeated network failures', async () => {
    const fetchImpl = vi.fn(async () => { throw new TypeError('Failed to fetch') })
    await expect(postWithRetry('/api/x', '{}', 'application/json', opts(fetchImpl))).rejects.toThrow('Failed to fetch')
    expect(fetchImpl).toHaveBeenCalledTimes(4)
  })
})
