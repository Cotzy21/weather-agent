import { it, expect, vi } from 'vitest'
import { fetchAccountExport } from './accountExport.js'

it('downloads privately without using the API cache', async () => {
  const fetchFn = vi.fn().mockResolvedValue(new Response('{"formatVersion":1}', { headers: { 'Content-Type': 'application/json' } }))
  const blob = await fetchAccountExport({ headers: { Authorization: 'Bearer test' }, fetchFn })
  expect(await blob.text()).toContain('formatVersion')
  expect(fetchFn.mock.calls[0][1].cache).toBe('no-store')
})

it('does not save HTML returned by an expired login or proxy', async () => {
  await expect(fetchAccountExport({ fetchFn: async () => new Response('<html>Login</html>') })).rejects.toThrow('eksportformat')
})

it('rejects a late response after the account changed', async () => {
  const controller = new AbortController()
  const response = new Response('{}', { headers: { 'Content-Type': 'application/json' } })
  controller.abort()
  await expect(fetchAccountExport({ signal: controller.signal, fetchFn: async () => response })).rejects.toThrow()
})
