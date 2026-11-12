import { useEffect, useRef, useState } from 'react'
import { apiUrl, readError } from './api.js'
import { accountHeadersFor } from './accountSession.js'
import { useI18n } from './i18n.jsx'
import { supabase } from './supabase.js'

export default function TrainingAiPermission({ userId, account = false, children }) {
  const { lang } = useI18n()
  const norwegian = lang === 'nb'
  const [decision, setDecision] = useState(null)
  const [busy, setBusy] = useState(true)
  const [error, setError] = useState(null)
  const [reload, setReload] = useState(0)
  const pending = useRef(null)

  useEffect(() => {
    const request = new AbortController()
    pending.current = request
    fetchDecision(userId, request.signal).then(setDecision).catch((err) => {
      if (!request.signal.aborted) setError(err.message)
    }).finally(() => {
      if (!request.signal.aborted) setBusy(false)
    })
    return () => pending.current?.abort()
  }, [userId, reload])

  async function change(allowed) {
    if (!decision || busy) return
    const request = new AbortController()
    pending.current = request
    setBusy(true)
    setError(null)
    try {
      setDecision(await fetchDecision(userId, request.signal, { version: decision.version, allowed }))
    } catch (err) {
      if (!request.signal.aborted) setError(err.message)
    } finally {
      if (!request.signal.aborted) setBusy(false)
    }
  }

  if (decision?.allowed && !account) return children

  return <section className="ai-intro" aria-busy={busy}>
    <h3>{norwegian ? 'Trenings-AI og personvern' : 'Training AI and privacy'}</h3>
    <p>{norwegian
      ? 'Hvis du slår på trenings-AI, sendes forespørselen din, relevant treningshistorikk, profil, treningsnotater og logget søvn til en ekstern AI-tjeneste for å lage forslaget. Profilen kan inneholde helseopplysninger. Du kan slå av delingen under Konto.'
      : 'If you enable Training AI, your request, relevant workout history, profile, training notes and logged sleep are sent to an external AI service to make the suggestion. Your profile may contain health information. You can turn off sharing under Account.'}</p>
    {decision ? <button type="button" disabled={busy} onClick={() => change(!decision.allowed)}>
      {decision.allowed ? (norwegian ? 'Slå av trenings-AI' : 'Turn off Training AI')
        : (norwegian ? 'Samtykk og slå på trenings-AI' : 'Consent and enable Training AI')}
    </button> : busy ? <p role="status">{norwegian ? 'Henter personvernvalg …' : 'Loading privacy choices …'}</p>
      : <button type="button" onClick={() => { setBusy(true); setError(null); setReload((n) => n + 1) }}>
        {norwegian ? 'Prøv igjen' : 'Try again'}
      </button>}
    {error && <p className="error" role="alert">{error}</p>}
  </section>
}

async function fetchDecision(userId, signal, update) {
  const headers = await accountHeadersFor(userId, () => supabase.auth.getSession())
  signal.throwIfAborted()
  const response = await fetch(apiUrl('/api/konto/trenings-ai'), {
    headers: update ? { ...headers, 'Content-Type': 'application/json' } : headers,
    method: update ? 'PUT' : 'GET',
    body: update ? JSON.stringify(update) : undefined,
    cache: 'no-store', signal,
  })
  if (!response.ok) throw new Error(await readError(response))
  const result = await response.json()
  signal.throwIfAborted()
  return result
}
