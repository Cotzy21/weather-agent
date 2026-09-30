import { useState, useEffect } from 'react'
import { useI18n } from './i18n.jsx'
import { saveActivity, defaultForm, toActivityBody, usesDistance, usesAscent } from './activitySession.js'

const fmtClock = (sec) => {
  const h = Math.floor(sec / 3600)
  const m = Math.floor((sec % 3600) / 60)
  const s = String(sec % 60).padStart(2, '0')
  return h ? `${h}:${String(m).padStart(2, '0')}:${s}` : `${m}:${s}`
}

/**
 * Pågående økt som ikke er styrke: en klokke som går, målene fra malen, og «Fullfør» der distanse og varighet bekreftes før økta
 * lagres som gjennomført. Styrkeøkter har egen live-økt (LiveSession).
 */
export default function ActivitySession({ userId, initial, typeLabel, onFinish, onCancel }) {
  const { t } = useI18n()
  const [s, setS] = useState(initial)
  const [now, setNow] = useState(() => Date.now())
  const [finishing, setFinishing] = useState(null) // null | skjemaet (durationMin, distanceKm, ascentM)
  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => { saveActivity(userId, s) }, [userId, s])
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(id)
  }, [])

  // Hold skjermen på under økta (iOS slipper låsen når appen skjules, så be igjen ved retur).
  useEffect(() => {
    let lock = null
    const request = async () => {
      try { lock = await navigator.wakeLock?.request('screen') } catch { /* ikke støttet */ }
    }
    const onVisible = () => { if (document.visibilityState === 'visible') request() }
    request()
    document.addEventListener('visibilitychange', onVisible)
    return () => {
      document.removeEventListener('visibilitychange', onVisible)
      lock?.release().catch(() => {})
    }
  }, [])

  const targets = []
  if (s.targets?.distanceKm) targets.push(`${s.targets.distanceKm} km`)
  if (s.targets?.durationMin) targets.push(`${s.targets.durationMin} min`)
  if (s.targets?.ascentM) targets.push(`${s.targets.ascentM} m ↑`)

  async function save() {
    const r = toActivityBody(s, finishing, s.title)
    if (r.error) { setError(t(r.error)); return }
    setBusy(true)
    setError(null)
    try {
      await onFinish(r.body)
    } catch (e) {
      setError(e.message)
      setBusy(false)
    }
  }

  return (
    <div className="live activity-session">
      <div className="live-bar">
        <div className="live-bar-row">
          <span className="live-clock">{fmtClock(Math.max(0, Math.floor((now - s.startedAt) / 1000)))}</span>
          <span className="live-bar-sep" aria-hidden="true" />
          <span className="live-count">{typeLabel}</span>
          <button className="live-end" disabled={busy} onClick={() => setFinishing(defaultForm(s, Date.now()))}>{t('Fullfør økt')}</button>
        </div>
      </div>

      <input className="live-title" value={s.title} placeholder={typeLabel} maxLength={120}
             onChange={(e) => { const v = e.target.value; setS((p) => ({ ...p, title: v })) }} />
      {targets.length > 0 && <p className="muted">{t('Mål')}: {targets.join(' · ')}</p>}
      <p className="muted activity-hint">{t('Klokka går til du fullfører. Økta lagres som gjennomført først da.')}</p>

      <button className="live-cancel" onClick={() => setConfirming(true)} disabled={busy}>{t('Avbryt økt uten å lagre')}</button>

      {finishing && (
        <div className="sheet-backdrop" onClick={() => !busy && setFinishing(null)}>
          <div className="sheet" role="dialog" aria-label={t('Fullfør økt')} onClick={(e) => e.stopPropagation()}>
            <span className="sheet-handle" aria-hidden="true" />
            <div className="cardio-inputs">
              <label>{t('Varighet (min)')}
                <input type="number" inputMode="numeric" min="1" value={finishing.durationMin}
                       onChange={(e) => setFinishing({ ...finishing, durationMin: e.target.value })} />
              </label>
              {usesDistance(s.type) && (
                <label>{t('Distanse (km)')}
                  <input type="number" inputMode="decimal" min="0" step="0.1" value={finishing.distanceKm}
                         onChange={(e) => setFinishing({ ...finishing, distanceKm: e.target.value })} />
                </label>
              )}
              {usesAscent(s.type) && (
                <label>{t('Stigning (m)')}
                  <input type="number" inputMode="numeric" min="0" value={finishing.ascentM}
                         onChange={(e) => setFinishing({ ...finishing, ascentM: e.target.value })} />
                </label>
              )}
            </div>
            {error && <p className="error">{error}</p>}
            <div className="confirm-sheet activity-finish">
              <button className="primary" onClick={save} disabled={busy}>{busy ? t('Lagrer …') : t('Lagre som gjennomført')}</button>
              <button className="confirm-keep" onClick={() => { setFinishing(null); setError(null) }} disabled={busy}>{t('Fortsett økta')}</button>
            </div>
          </div>
        </div>
      )}

      {confirming && (
        <div className="sheet-backdrop" onClick={() => setConfirming(false)}>
          <div className="sheet confirm-sheet" role="alertdialog" onClick={(e) => e.stopPropagation()}>
            <span className="sheet-handle" aria-hidden="true" />
            <p className="confirm-text">{t('Avbryte økta? Den blir ikke lagret.')}</p>
            <button className="confirm-danger" onClick={() => { setConfirming(false); onCancel() }}>{t('Avbryt økt')}</button>
            <button className="confirm-keep" onClick={() => setConfirming(false)}>{t('Fortsett økta')}</button>
          </div>
        </div>
      )}
    </div>
  )
}
