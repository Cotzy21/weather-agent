import { useEffect, useState } from 'react'
import { apiUrl, readError } from './api.js'
import { accountHeadersFor } from './accountSession.js'
import { eraseLocalUserData } from './accountDeletion.js'
import { authHeaders, supabase } from './supabase.js'
import { setQueueOwner, workoutQueue } from './offlineQueue.js'
import { useI18n } from './i18n.jsx'

const consentKey = userId => `health-consent:${userId}`

function saveOfflineDecision(userId, decision) {
  try {
    const key = consentKey(userId)
    const value = JSON.stringify(decision)
    if (localStorage.getItem(key) !== value) localStorage.setItem(key, value)
  } catch { /* Storage may be disabled on this device. */ }
}

function offlineDecision(userId) {
  if (navigator.onLine !== false) return null
  try { return JSON.parse(localStorage.getItem(consentKey(userId))) }
  catch { return null }
}

async function requestDecision(userId, signal, method = 'GET', version) {
  const headers = await accountHeadersFor(userId, () => supabase.auth.getSession())
  signal?.throwIfAborted()
  const response = await fetch(apiUrl('/api/konto/helselagring'), {
    method, signal, cache: 'no-store',
    headers: method === 'PUT' ? { ...headers, 'Content-Type': 'application/json' } : headers,
    body: method === 'PUT' ? JSON.stringify({ version, allow: true }) : undefined,
  })
  if (!response.ok) throw new Error(await readError(response))
  return response.json()
}

async function clearWithdrawnData(userId, lastWithdrawal) {
  if (!lastWithdrawal) return
  const key = `health-cleaned:${userId}`
  if (localStorage.getItem(key) === lastWithdrawal) return
  await accountHeadersFor(userId, () => supabase.auth.getSession())
  eraseLocalUserData(userId)
  await workoutQueue(authHeaders).discardForOwner(userId)
  localStorage.setItem(key, lastWithdrawal)
}

export default function HealthStoragePermission({ userId, account = false, onOpenPrivacy, children }) {
  const { lang } = useI18n()
  const nb = lang === 'nb'
  const [decision, setDecision] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(true)
  const [confirm, setConfirm] = useState(false)
  const [reload, setReload] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    requestDecision(userId, controller.signal).then(async (result) => {
      if (controller.signal.aborted) return
      await clearWithdrawnData(userId, result.lastWithdrawal)
      if (!controller.signal.aborted) {
        saveOfflineDecision(userId, result)
        setDecision(result)
      }
    }).catch(() => {
      if (controller.signal.aborted) return
      const cached = !account && offlineDecision(userId)
      if (cached?.allowed) setDecision(cached)
      else setError('load')
    }).finally(() => {
      if (!controller.signal.aborted) setBusy(false)
    })
    return () => controller.abort()
  }, [userId, reload, account])

  useEffect(() => {
    const refresh = () => {
      setQueueOwner(null)
      setBusy(true)
      setError('')
      setDecision(null)
      setReload((n) => n + 1)
    }
    const sync = event => { if (event.key === consentKey(userId)) refresh() }
    window.addEventListener('online', refresh)
    window.addEventListener('storage', sync)
    return () => {
      window.removeEventListener('online', refresh)
      window.removeEventListener('storage', sync)
    }
  }, [userId])

  async function update(method) {
    if (busy || !decision) return
    setBusy(true)
    setError('')
    try {
      const next = await requestDecision(userId, undefined, method, decision.version)
      setDecision(next)
      setConfirm(false)
      if (next.withdrawn) {
        setQueueOwner(null)
        try { await clearWithdrawnData(userId, next.lastWithdrawal) } catch { setError('cleanup') }
      }
      saveOfflineDecision(userId, next)
    } catch {
      setError('save')
    } finally {
      setBusy(false)
    }
  }

  if (decision?.allowed && !account) return children
  return <section className="ai-intro" aria-busy={busy}>
    <h2>{nb ? 'Lagring av helsedata' : 'Health data storage'}</h2>
    <p>{nb
      ? 'Weather Agent, ved Oskar Oliwier Kotyla, ber om samtykke til å lagre treningsøkter, helseprofil, måltider, vekt, søvn og vaner på kontoen din. Dataene brukes til logg, progresjon og restitusjon. Du kan trekke samtykket tilbake under Konto.'
      : 'Weather Agent, run by Oskar Oliwier Kotyla, asks to store workouts, health profile, meals, weight, sleep and habits in your account. This powers your diary, progress and recovery. You can withdraw under Account.'}</p>
    <p className="muted">{nb
      ? 'Vær, ruter og konto fungerer uten dette samtykket. Deling med trenings-AI har et eget valg.'
      : 'Weather, routes and your account work without this permission. Training AI sharing has a separate choice.'}</p>
    {onOpenPrivacy && <button type="button" className="link-btn" onClick={onOpenPrivacy}>
      {nb ? 'Les personvernerklæringen' : 'Read the privacy notice'}
    </button>}
    {confirm ? <>
        <p>{nb ? 'Dette sletter økter, treningsplaner, kostholdsdata, vekt, vaner og tidligere Garmin-målinger. Konto og lagrede ruter beholdes. Last ned en kopi først hvis du vil beholde dataene.'
          : 'This removes workouts, plans, nutrition, weight, habits and earlier Garmin readings. Your account and saved routes stay. Download a copy first if you want to keep your data.'}</p>
        <button className="danger" type="button" disabled={busy} onClick={() => update('DELETE')}>
          {nb ? 'Trekk tilbake og slett helsedata' : 'Withdraw and erase health data'}
        </button>
        <button type="button" disabled={busy} onClick={() => setConfirm(false)}>{nb ? 'Avbryt' : 'Cancel'}</button>
    </> : decision?.allowed ? <>
      <p role="status">{nb ? 'Helselagring er på.' : 'Health data storage is on.'}</p>
      <button type="button" disabled={busy} onClick={() => setConfirm(true)}>
        {nb ? 'Trekk tilbake samtykket' : 'Withdraw consent'}
      </button>
    </> : decision ? <>
      {decision.withdrawn && <p role="status">{nb ? 'Helselagring er slått av.' : 'Health data storage is off.'}</p>}
      <button type="button" disabled={busy} onClick={() => update('PUT')}>
        {nb ? 'Jeg samtykker til helselagring' : 'I consent to health data storage'}
      </button>
      {account && !decision.withdrawn && <button type="button" disabled={busy} onClick={() => setConfirm(true)}>
        {nb ? 'Slett tidligere helsedata' : 'Delete previous health data'}
      </button>}
    </> : busy ? <p role="status">{nb ? 'Henter samtykke …' : 'Checking consent …'}</p>
      : <button type="button" onClick={() => { setError(''); setBusy(true); setReload((n) => n + 1) }}>{nb ? 'Prøv igjen' : 'Try again'}</button>}
    {error && <p className="error" role="alert">{error === 'cleanup'
      ? (nb ? 'Serverdataene er slettet, men lokal opprydding feilet. Åpne Konto på nytt for å prøve igjen.' : 'Server data was erased, but local cleanup failed. Open Account again to retry.')
      : (nb ? 'Kunne ikke oppdatere personvernvalget. Prøv igjen.' : 'Could not update the privacy choice. Try again.')}</p>}
    {account && decision?.allowed && !confirm && children}
  </section>
}
