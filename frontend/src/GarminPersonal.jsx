import { useId, useRef, useState } from 'react'
import { apiUrl, readError } from './api'
import { useI18n } from './i18n.jsx'
import { authHeaders } from './supabase'

export default function GarminPersonal() {
  const { t, lang } = useI18n()
  const id = useId()
  const input = useRef(null)
  const [showKey, setShowKey] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [activities, setActivities] = useState(null)

  async function fetchActivities(event) {
    event.preventDefault()
    if (busy) return
    const apiKey = input.current.value.trim()
    if (!apiKey) {
      setError('Skriv inn Garmin-nøkkelen din.')
      input.current.focus()
      return
    }
    input.current.value = ''
    setShowKey(false)
    setError('')
    setActivities(null)
    setBusy(true)
    try {
      const response = await fetch(apiUrl('/api/trening/garmin/aktiviteter'), {
        method: 'POST',
        cache: 'no-store',
        headers: { 'Content-Type': 'application/json', ...await authHeaders() },
        body: JSON.stringify({ apiKey }),
      })
      if (!response.ok) {
        setError(await readError(response))
        return
      }
      setActivities(await response.json())
    } catch {
      setError('Kunne ikke hente aktiviteter fra Garmin. Prøv igjen om litt.')
    } finally {
      setBusy(false)
    }
  }

  const number = new Intl.NumberFormat(lang === 'en' ? 'en' : 'nb', { maximumFractionDigits: 1 })

  return (
    <section className="gi-personal" aria-labelledby={`${id}-title`}>
      <h3 id={`${id}-title`}>{t('Hent fra Garmin Connect')}</h3>
      <p className="muted gi-note" id={`${id}-hint`}>
        {t('Se opptil 20 av dine siste aktiviteter. Nøkkelen brukes til dette oppslaget og lagres ikke.')}
      </p>
      <form autoComplete="off" onSubmit={fetchActivities} aria-busy={busy}>
        <label htmlFor={id}>{t('Personlig Garmin API-nøkkel')}</label>
        <div className="gi-key">
          <input id={id} ref={input} type={showKey ? 'text' : 'password'} autoComplete="off"
                 autoCapitalize="none" spellCheck={false} maxLength={1024} disabled={busy}
                 aria-describedby={`${id}-hint${error ? ` ${id}-error` : ''}`} />
          <button type="button" className="train-action" onClick={() => setShowKey((show) => !show)}
                  disabled={busy}>{t(showKey ? 'Skjul nøkkel' : 'Vis nøkkel')}</button>
        </div>
        {error && <p className="error gi-note" role="alert" id={`${id}-error`}>{t(error)}</p>}
        <div className="gi-buttons gi-personal-actions">
          <button type="submit" className="train-action" disabled={busy}>
            {t(busy ? 'Henter fra Garmin …' : 'Hent aktiviteter')}
          </button>
          <a href="https://connect.garmin.com/app/settings/personalApiToken" target="_blank" rel="noreferrer">
            {t('Finn nøkkelen i Garmin Connect')}
          </a>
        </div>
      </form>
      {activities && (
        <div className="gi-personal-results">
          <p className="muted gi-note" role="status">
            {activities.length
              ? t('{n} aktiviteter fra Garmin Connect. Visning uten lagring.', { n: activities.length })
              : t('Fant ingen aktiviteter i Garmin Connect.')}
          </p>
          {activities.length > 0 && (
            <ul className="gi-activities">
              {activities.map((activity) => (
                <li key={activity.id}>
                  <strong>{activity.name || t('Garmin-aktivitet')}</strong>
                  <small className="muted">
                    {[
                      activity.startLocal?.slice(0, 16).replace('T', ' '),
                      activity.durationSeconds != null && `${number.format(activity.durationSeconds / 60)} min`,
                      activity.distanceMeters > 0 && `${number.format(activity.distanceMeters / 1000)} km`,
                      activity.deviceName,
                    ].filter(Boolean).join(' · ')}
                  </small>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </section>
  )
}
