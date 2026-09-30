import { useCallback, useRef, useState } from 'react'
import { authHeaders } from './supabase'
import { apiUrl, readError } from './api'
import { importCsv, importFit } from './garminImport.js'
import { matchExerciseNames } from './exerciseMatch.js'

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

/**
 * Sender én bit til API-et. Prøver på nytt ved dårlig dekning (nettverksfeil) og venter når serveren ber om
 * det (429 med Retry-After), så en lang import ikke dør av ett hikke.
 */
export async function postWithRetry(path, body, contentType, { fetchImpl = fetch, headers = authHeaders, wait = sleep, tries = 4 } = {}) {
  let lastError
  for (let attempt = 0; attempt < tries; attempt++) {
    try {
      const res = await fetchImpl(apiUrl(path), {
        method: 'POST',
        headers: { 'Content-Type': contentType, ...(await headers()) },
        body,
      })
      if (res.status === 429) {
        const seconds = Math.min(60, Number(res.headers?.get?.('Retry-After')) || 10)
        lastError = new Error(await readError(res))
        await wait(seconds * 1000)
        continue
      }
      if (res.status >= 500 && attempt < tries - 1) {
        lastError = new Error(await readError(res))
        await wait(2000 * (attempt + 1))
        continue
      }
      if (!res.ok) throw new Error(await readError(res))
      return await res.json()
    } catch (e) {
      if (!(e instanceof TypeError) || attempt === tries - 1) throw e // bare nettverksfeil prøves på nytt
      lastError = e
      await wait(2000 * (attempt + 1))
    }
  }
  throw lastError ?? new Error('Importen feilet')
}

/** Tilstanden til en Garmin-import (CSV eller FIT/ZIP), og funksjonene som starter dem. */
export function useGarminImport({ onDone, lang = 'en' }) {
  const [state, setState] = useState({ busy: false, kind: null, progress: null, result: null, error: null })
  const running = useRef(false)

  const run = useCallback(async (kind, job) => {
    if (running.current) return
    running.current = true
    setState({ busy: true, kind, progress: null, result: null, error: null })
    try {
      const result = await job((progress) => setState((s) => ({ ...s, progress })))
      setState({ busy: false, kind, progress: null, result, error: null })
      await onDone?.()
    } catch (e) {
      setState({ busy: false, kind, progress: null, result: null, error: e.message || 'Importen feilet' })
      await onDone?.() // det som rakk å bli importert skal vises
    } finally {
      running.current = false
    }
  }, [onDone])

  const runCsv = useCallback((file) => file && run('csv', (onProgress) => importCsv(file, { post: postWithRetry, onProgress })), [run])
  const runFit = useCallback((files) => {
    const list = Array.from(files ?? [])
    return list.length && run('fit', (onProgress) => importFit(list, {
      post: postWithRetry,
      onProgress,
      matchNames: (names) => matchExerciseNames(names, { lang, post: postWithRetry }),
    }))
  }, [run, lang])

  return { ...state, runCsv, runFit }
}
