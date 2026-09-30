// Offline-kø for fullførte økter: en økt lagres på enheten FØR den sendes, og fjernes først når
// serveren har bekreftet. Hver økt har en clientId, og backend er idempotent på den, så et nytt
// forsøk aldri lager duplikater (f.eks. når svaret gikk tapt selv om økta kom fram).
import { apiUrl } from './api.js'

const DB = 'offline-queue'
const STORE = 'workouts'

/** Lagring i IndexedDB (localStorage er for lite og synkront). Byttes ut i tester. */
export function idbStore() {
  const open = () => new Promise((resolve, reject) => {
    const req = indexedDB.open(DB, 1)
    req.onupgradeneeded = () => req.result.createObjectStore(STORE, { keyPath: 'clientId' })
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(req.error)
  })
  const run = async (mode, fn) => {
    const db = await open()
    return new Promise((resolve, reject) => {
      const tx = db.transaction(STORE, mode)
      const result = fn(tx.objectStore(STORE))
      tx.oncomplete = () => { db.close(); resolve(result.result) }
      tx.onerror = () => { db.close(); reject(tx.error) }
    })
  }
  return {
    put: (item) => run('readwrite', (s) => s.put(item)),
    remove: (clientId) => run('readwrite', (s) => s.delete(clientId)),
    all: () => run('readonly', (s) => s.getAll()),
  }
}

/** Minnelager med samme grensesnitt (tester, og reserve når IndexedDB ikke finnes). */
export function memoryStore() {
  const m = new Map()
  return {
    put: async (item) => { m.set(item.clientId, item) },
    remove: async (id) => { m.delete(id) },
    all: async () => [...m.values()],
  }
}

/** Kan feilen skyldes at vi ikke når serveren (dårlig dekning), i motsetning til at serveren sa nei? */
export function isNetworkError(err) {
  return err instanceof TypeError || err?.name === 'NetworkError'
}

/**
 * Køen. `send(item)` gjør selve POST-en og returnerer { ok, status } (kaster ved nettverksfeil).
 * Tjenesten er ren logikk over `store` og `send`, så den kan testes uten nettleser.
 */
export function createQueue({ store, send, onChange = () => {} }) {
  let flushing = null

  async function pending() {
    return (await store.all()).sort((a, b) => a.queuedAt - b.queuedAt)
  }

  async function notify() {
    onChange((await store.all()).length)
  }

  /**
   * Prøv å sende økta nå; ved nettverksfeil eller 5xx legges den i køen.
   * Returnerer { queued: false } når serveren har den, { queued: true } når den venter.
   * 4xx (ugyldig økt) kastes videre, for et nytt forsøk hjelper ikke.
   */
  async function submit(item) {
    const entry = { ...item, queuedAt: Date.now() }
    await store.put(entry) // først på enheten, så ingenting går tapt hvis appen lukkes midt i
    try {
      const res = await send(entry)
      if (res.ok) {
        await store.remove(entry.clientId)
        await notify()
        return { queued: false }
      }
      if (res.status >= 400 && res.status < 500 && res.status !== 408 && res.status !== 429) {
        await store.remove(entry.clientId)
        await notify()
        const err = new Error(res.message || `Noe gikk galt (${res.status})`)
        err.rejected = true
        throw err
      }
    } catch (e) {
      if (e.rejected) throw e
      if (!isNetworkError(e)) throw e
    }
    await notify()
    return { queued: true }
  }

  /** Send alt som venter, eldst først. Stopper ved første nettverksfeil. Samtidige kall deler jobben. */
  function flush() {
    if (flushing) return flushing
    flushing = (async () => {
      let sent = 0
      for (const item of await pending()) {
        try {
          const res = await send(item)
          const permanent = !res.ok && res.status >= 400 && res.status < 500 && res.status !== 408 && res.status !== 429
          if (res.ok || permanent) {
            await store.remove(item.clientId)
            if (res.ok) sent++
          } else {
            break // 5xx/429: prøv igjen senere
          }
        } catch {
          break // ingen nett
        }
      }
      await notify()
      return sent
    })().finally(() => { flushing = null })
    return flushing
  }

  return { submit, flush, pending, count: async () => (await store.all()).length }
}

// --- Bruk i appen ---

let shared = null
const listeners = new Set()

/** Antall økter som venter (for banneret). Returnerer en funksjon som avmelder. */
export function onPendingChange(fn) {
  listeners.add(fn)
  return () => listeners.delete(fn)
}

export function workoutQueue(getHeaders) {
  if (!shared) {
    let store
    try { store = typeof indexedDB === 'undefined' ? memoryStore() : idbStore() } catch { store = memoryStore() }
    shared = createQueue({
      store,
      onChange: (n) => listeners.forEach((fn) => fn(n)),
      send: async (item) => {
        const res = await fetch(apiUrl('/api/treningsokter'), {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', ...(await getHeaders()) },
          body: JSON.stringify(item.body),
        })
        let message
        if (!res.ok) {
          try { message = (await res.json())?.error } catch { /* ikke JSON */ }
        }
        return { ok: res.ok, status: res.status, message }
      },
    })
  }
  return shared
}
