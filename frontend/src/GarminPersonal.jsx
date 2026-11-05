import { useId, useRef, useState } from 'react'
import { apiUrl, readError } from './api'
import { useI18n } from './i18n.jsx'
import { authHeaders } from './supabase'
import { importGarminActivities } from './garminPersonalImport.js'
import { postWithRetry } from './useGarminImport.js'

const JSON_TYPE = 'application/json'

async function garminRequest(path, body) {
  const response = await fetch(apiUrl(path), {
    method: 'POST',
    cache: 'no-store',
    headers: { 'Content-Type': JSON_TYPE, ...await authHeaders() },
    body: JSON.stringify(body),
  })
  if (!response.ok) throw new Error(await readError(response))
  return response.json()
}

export default function GarminPersonal({ onSaved }) {
  const { t, lang } = useI18n()
  const id = useId()
  const input = useRef(null)
  const key = useRef('')
  const pending = useRef(false)
  const [showKey, setShowKey] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [activities, setActivities] = useState(null)
  const [selected, setSelected] = useState([])
  const [saved, setSaved] = useState([])
  const [result, setResult] = useState(null)
  const [saving, setSaving] = useState(false)
  const [processed, setProcessed] = useState(0)

  async function fetchActivities(event) {
    event.preventDefault()
    if (pending.current) return
    const apiKey = input.current.value.trim()
    if (!apiKey) {
      setError('Skriv inn Garmin-nøkkelen din.')
      input.current.focus()
      return
    }
    input.current.value = ''
    setShowKey(false)
    setError('')
    key.current = ''
    setActivities(null)
    setSelected([])
    setSaved([])
    setResult(null)
    setProcessed(0)
    pending.current = true
    setBusy(true)
    try {
      const data = await garminRequest('/api/trening/garmin/aktiviteter', { apiKey })
      setActivities(data)
      if (data.length) key.current = apiKey
    } catch (e) {
      setError(e instanceof TypeError ? 'Kunne ikke hente aktiviteter fra Garmin. Prøv igjen om litt.' : e.message)
    } finally {
      pending.current = false
      setBusy(false)
    }
  }

  async function saveSelected() {
    if (pending.current || !selected.length || !key.current) return
    pending.current = true
    setSaving(true)
    setError('')
    const chosen = activities.filter((entry) => selected.includes(entry.id))
    try {
      await importGarminActivities(chosen, {
        download: (ids) => garminRequest('/api/trening/garmin/filer', { apiKey: key.current, ids: ids.map(Number) }),
        save: (entries) => postWithRetry('/api/trening/import/garmin-api', JSON.stringify({ activities: entries }), JSON_TYPE),
        onBatch: (ids, batchResult) => {
          setSaved((old) => [...old, ...ids])
          setSelected((old) => old.filter((item) => !ids.includes(item)))
          setProcessed((old) => old + ids.length)
          setResult((old) => ({
            imported: (old?.imported ?? 0) + batchResult.imported,
            merged: (old?.merged ?? 0) + batchResult.merged,
            skipped: (old?.skipped ?? 0) + batchResult.skipped,
            summaryOnly: (old?.summaryOnly ?? 0) + batchResult.summaryOnly,
          }))
        },
      })
    } catch (e) {
      setError(e instanceof TypeError ? 'Garmin-importen ble avbrutt. Prøv de gjenværende øktene på nytt.' : e.message)
    } finally {
      try { await onSaved?.() } catch { /* Saving has already completed for any reported batch. */ }
      pending.current = false
      setSaving(false)
    }
  }

  function clearResults() {
    key.current = ''
    setActivities(null)
    setSelected([])
    setSaved([])
    setResult(null)
    setError('')
    setProcessed(0)
  }

  const number = new Intl.NumberFormat(lang === 'en' ? 'en' : 'nb', { maximumFractionDigits: 1 })

  return (
    <section className="gi-personal" aria-labelledby={`${id}-title`}>
      <h3 id={`${id}-title`}>{t('Hent fra Garmin Connect')}</h3>
      <p className="muted gi-note" id={`${id}-hint`}>
        {t('Hent de siste aktivitetene og velg hva som skal inn i treningsloggen. Nøkkelen holdes i minnet til du lukker Garmin-listen eller forlater Trening.')}
      </p>
      <form autoComplete="off" onSubmit={fetchActivities} aria-busy={busy}>
        <label htmlFor={id}>{t('Personlig Garmin API-nøkkel')}</label>
        <div className="gi-key">
          <input id={id} ref={input} type={showKey ? 'text' : 'password'} autoComplete="off"
                 autoCapitalize="none" spellCheck={false} maxLength={1024} disabled={busy || saving}
                 aria-describedby={`${id}-hint${error ? ` ${id}-error` : ''}`} />
          <button type="button" className="train-action" onClick={() => setShowKey((show) => !show)}
                  disabled={busy || saving}>{t(showKey ? 'Skjul nøkkel' : 'Vis nøkkel')}</button>
        </div>
        {error && <p className="error gi-note" role="alert" id={`${id}-error`}>{t(error)}</p>}
        <div className="gi-buttons gi-personal-actions">
          <button type="submit" className="train-action" disabled={busy || saving}>
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
              ? t('{n} aktiviteter fra Garmin Connect. Velg hvilke du vil lagre.', { n: activities.length })
              : t('Fant ingen aktiviteter i Garmin Connect.')}
          </p>
          {activities.length > 0 && (
            <>
              <div className="gi-buttons gi-personal-actions">
                <button type="button" className="train-action go" disabled={saving || selected.length === 0} onClick={saveSelected}>
                  {saving ? t('Lagrer Garmin-økter …') : t('Lagre valgte i treningsloggen ({n})', { n: selected.length })}
                </button>
                <button type="button" className="train-action" disabled={saving}
                        onClick={() => setSelected(activities.filter((entry) => entry.startLocal && !saved.includes(entry.id)).map((entry) => entry.id))}>
                  {t('Velg alle')}
                </button>
                <button type="button" className="train-action" disabled={saving} onClick={clearResults}>{t('Lukk Garmin-listen')}</button>
              </div>
              {processed > 0 && result && (
                <p className="gi-note" role="status">
                  {t('{imported} lagt til · {merged} oppdatert · {skipped} fantes fra før', result)}
                  {result.summaryOnly > 0 && ` · ${t('{n} styrkeøkter uten sett fra Garmin', { n: result.summaryOnly })}`}
                </p>
              )}
              <ul className="gi-activities">
                {activities.map((activity) => {
                  const done = saved.includes(activity.id)
                  return (
                    <li key={activity.id}>
                      <label className="gi-activity-choice">
                        <input type="checkbox" checked={done || selected.includes(activity.id)} disabled={saving || done || !activity.startLocal}
                               onChange={(e) => setSelected((old) => e.target.checked
                                 ? [...old, activity.id] : old.filter((item) => item !== activity.id))} />
                        <span>
                          <strong>{activity.name || t('Garmin-aktivitet')}</strong>
                          <small className="muted">
                            {[
                              activity.startLocal?.slice(0, 16).replace('T', ' '),
                              activity.durationSeconds != null && `${number.format(activity.durationSeconds / 60)} min`,
                              activity.distanceMeters > 0 && `${number.format(activity.distanceMeters / 1000)} km`,
                              done && t('I treningsloggen'),
                            ].filter(Boolean).join(' · ')}
                          </small>
                        </span>
                      </label>
                    </li>
                  )
                })}
              </ul>
            </>
          )}
        </div>
      )}
    </section>
  )
}
