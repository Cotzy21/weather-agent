import { describe, it, expect } from 'vitest'
import { customFoodUuid, deleteReportedFood, dismissReports, fetchReported, reportFood } from './foodReports.js'

const UUID = '11111111-2222-3333-4444-555555555555'

function recorder(response) {
  const calls = []
  const fetchFn = async (url, init) => { calls.push({ url, init }); return response }
  return { calls, fetchFn }
}

describe('customFoodUuid', () => {
  it('extracts the uuid from a shared food id and ignores everything else', () => {
    expect(customFoodUuid(`egen:${UUID}`)).toBe(UUID)
    expect(customFoodUuid('05.049')).toBeNull() // Matvaretabellen
    expect(customFoodUuid(undefined)).toBeNull()
    expect(customFoodUuid(42)).toBeNull()
  })
})

describe('reportFood', () => {
  it('posts the reason and a trimmed note to the food-specific report endpoint', async () => {
    const { calls, fetchFn } = recorder({ ok: true, status: 204 })
    await reportFood({ foodId: `egen:${UUID}`, reason: 'SPAM', note: '  suspekt  ', headers: { Authorization: 'Bearer x' }, fetchFn })
    expect(calls[0].url).toMatch(new RegExp(`/api/kosthold/egne-matvarer/${UUID}/rapport$`))
    expect(calls[0].init.method).toBe('POST')
    expect(calls[0].init.headers).toEqual({ 'Content-Type': 'application/json', Authorization: 'Bearer x' })
    expect(JSON.parse(calls[0].init.body)).toEqual({ reason: 'SPAM', note: 'suspekt' })
  })

  it('sends null instead of an empty note', async () => {
    const { calls, fetchFn } = recorder({ ok: true, status: 204 })
    await reportFood({ foodId: `egen:${UUID}`, reason: 'OTHER', note: '   ', headers: {}, fetchFn })
    expect(JSON.parse(calls[0].init.body).note).toBeNull()
  })

  it('refuses foods from Matvaretabellen without calling the server', async () => {
    const { calls, fetchFn } = recorder({ ok: true })
    await expect(reportFood({ foodId: '05.049', reason: 'SPAM', headers: {}, fetchFn })).rejects.toThrow('delte')
    expect(calls).toEqual([])
  })

  it("shows the server's message when it refuses", async () => {
    const { fetchFn } = recorder({ ok: false, status: 400, json: async () => ({ error: 'Du kan ikke rapportere din egen matvare.' }) })
    await expect(reportFood({ foodId: `egen:${UUID}`, reason: 'SPAM', headers: {}, fetchFn })).rejects.toThrow('egen matvare')
  })
})

describe('moderation calls', () => {
  it('returns null for non-admins (403) so the panel stays hidden', async () => {
    const { fetchFn } = recorder({ ok: false, status: 403 })
    expect(await fetchReported({ headers: {}, fetchFn })).toBeNull()
  })

  it('returns the reported foods for an admin', async () => {
    const list = [{ id: UUID, name: 'Bar', reports: 2 }]
    const { calls, fetchFn } = recorder({ ok: true, status: 200, json: async () => list })
    expect(await fetchReported({ headers: {}, fetchFn })).toEqual(list)
    expect(calls[0].url).toMatch(/\/api\/admin\/rapporter$/)
  })

  it('deletes a food and dismisses reports on the right endpoints', async () => {
    const del = recorder({ ok: true, status: 204 })
    await deleteReportedFood({ id: UUID, headers: {}, fetchFn: del.fetchFn })
    expect(del.calls[0].url).toMatch(new RegExp(`/api/admin/matvarer/${UUID}$`))
    expect(del.calls[0].init.method).toBe('DELETE')

    const dismiss = recorder({ ok: true, status: 204 })
    await dismissReports({ id: UUID, headers: {}, fetchFn: dismiss.fetchFn })
    expect(dismiss.calls[0].url).toMatch(new RegExp(`/api/admin/rapporter/${UUID}$`))
    expect(dismiss.calls[0].init.method).toBe('DELETE')
  })

  it('throws on unexpected server errors', async () => {
    const { fetchFn } = recorder({ ok: false, status: 500, json: async () => ({}) })
    await expect(fetchReported({ headers: {}, fetchFn })).rejects.toThrow('500')
  })
})
