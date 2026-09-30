import { describe, it, expect } from 'vitest'
import { createQueue, memoryStore } from './offlineQueue.js'

const item = (id) => ({ clientId: id, body: { title: id } })

function setup(responses) {
  const store = memoryStore()
  const calls = []
  const counts = []
  const queue = createQueue({
    store,
    onChange: (n) => counts.push(n),
    send: async (i) => {
      calls.push(i.clientId)
      const r = responses.shift()
      if (r === 'offline') throw new TypeError('Failed to fetch')
      return r
    },
  })
  return { store, queue, calls, counts }
}

describe('offline queue', () => {
  it('sends straight away and leaves nothing behind when the server answers', async () => {
    const { queue, store } = setup([{ ok: true, status: 200 }])
    expect(await queue.submit(item('a'))).toEqual({ queued: false })
    expect(await store.all()).toEqual([])
  })

  it('keeps the workout on the device when the network is down', async () => {
    const { queue, store } = setup(['offline'])
    expect(await queue.submit(item('a'))).toEqual({ queued: true })
    expect((await store.all()).map((x) => x.clientId)).toEqual(['a'])
  })

  it('keeps it on 5xx too, but drops it and throws on 4xx (retrying cannot help)', async () => {
    const a = setup([{ ok: false, status: 503 }])
    expect(await a.queue.submit(item('a'))).toEqual({ queued: true })

    const b = setup([{ ok: false, status: 400, message: 'Ugyldig økt' }])
    await expect(b.queue.submit(item('b'))).rejects.toThrow('Ugyldig økt')
    expect(await b.store.all()).toEqual([])
  })

  it('flushes oldest first once the network is back, and reports how many were sent', async () => {
    const { queue, calls, store } = setup(['offline', 'offline', { ok: true, status: 200 }, { ok: true, status: 200 }])
    await queue.submit(item('a'))
    await queue.submit(item('b'))
    expect(await queue.flush()).toBe(2)
    expect(calls.slice(2)).toEqual(['a', 'b'])
    expect(await store.all()).toEqual([])
  })

  it('stops flushing at the first network failure and keeps the rest', async () => {
    const { queue, store } = setup(['offline', 'offline', 'offline'])
    await queue.submit(item('a'))
    await queue.submit(item('b'))
    expect(await queue.flush()).toBe(0)
    expect((await store.all()).length).toBe(2)
  })

  it('shares one flush job between simultaneous callers', async () => {
    const { queue, calls } = setup(['offline', { ok: true, status: 200 }])
    await queue.submit(item('a'))
    const [x, y] = await Promise.all([queue.flush(), queue.flush()])
    expect(x).toBe(1)
    expect(y).toBe(1)
    expect(calls.filter((c) => c === 'a').length).toBe(2) // ett forsøk ved submit + ett i felles flush
  })

  it('reports the number of pending workouts', async () => {
    const { queue, counts } = setup(['offline', { ok: true, status: 200 }])
    await queue.submit(item('a'))
    await queue.flush()
    expect(counts).toEqual([1, 0])
  })
})
