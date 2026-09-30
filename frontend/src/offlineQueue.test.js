import { describe, it, expect } from 'vitest'
import { createQueue, memoryStore } from './offlineQueue.js'

const item = (id) => ({ clientId: id, body: { title: id } })

function setup(responses, owner = { id: 'u1' }) {
  const store = memoryStore()
  const calls = []
  const counts = []
  const queue = createQueue({
    store,
    owner: () => owner.id,
    onChange: (n) => counts.push(n),
    send: async (i) => {
      calls.push(i.clientId)
      const r = responses.shift()
      if (r === 'offline') throw new TypeError('Failed to fetch')
      return r
    },
  })
  return { store, queue, calls, counts, owner }
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

  it("never sends or counts another user's workouts, and keeps them for the right user", async () => {
    const { queue, store, calls, owner } = setup(['offline', { ok: true, status: 200 }])
    await queue.submit(item('a')) // u1 er innlogget, ingen nett
    owner.id = 'u2' // en annen bruker logger inn på samme enhet
    expect(await queue.count()).toBe(0)
    expect(await queue.flush()).toBe(0)
    expect(calls).toEqual(['a']) // bare det første forsøket, ingenting sendt under u2
    expect((await store.all()).length).toBe(1) // ligger fortsatt der

    owner.id = 'u1'
    expect(await queue.count()).toBe(1)
    expect(await queue.flush()).toBe(1)
  })

  it('refuses to queue when nobody is logged in', async () => {
    const { queue } = setup([], { id: null })
    await expect(queue.submit(item('a'))).rejects.toThrow('innlogget')
  })

  it('drops entries that have no owner instead of sending them under someone else', async () => {
    const { queue, store, calls } = setup([])
    await store.put({ clientId: 'gammel', body: {}, queuedAt: 1 })
    expect(await queue.flush()).toBe(0)
    expect(calls).toEqual([])
    expect(await store.all()).toEqual([])
  })

  it("discards the logged-in user's queued workouts on account deletion, and only theirs", async () => {
    const { queue, store, owner, counts } = setup(['offline', 'offline'])
    await queue.submit(item('a')) // u1
    await queue.submit(item('b')) // u1
    owner.id = 'u2'
    await queue.submit(item('c')) // u2
    owner.id = 'u1'

    expect(await queue.discardMine()).toBe(2)
    expect((await store.all()).map((i) => i.clientId)).toEqual(['c'])
    expect(await queue.count()).toBe(0)
    expect(counts.at(-1)).toBe(0) // banneret «økter venter» oppdateres
  })

  it('discards nothing when nobody is logged in', async () => {
    const { queue, store } = setup(['offline'])
    await queue.submit(item('a'))
    const loggedOut = createQueue({ store, send: async () => ({ ok: true, status: 200 }), owner: () => null })
    expect(await loggedOut.discardMine()).toBe(0)
    expect((await store.all()).length).toBe(1)
  })
})
