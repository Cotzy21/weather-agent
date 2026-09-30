import { useState } from 'react'
import { authHeaders } from './supabase'
import { useI18n } from './i18n.jsx'
import { REPORT_REASONS, customFoodUuid, reportFood } from './foodReports.js'

// «Rapporter»-knapp ved en offentlig delt matvare: velg grunn, skriv eventuelt en kommentar, send.
// En administrator ser rapportene og bestemmer om varen fjernes (se FoodModeration).
export default function ReportFood({ foodId }) {
  const { t } = useI18n()
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState('WRONG_VALUES')
  const [note, setNote] = useState('')
  const [state, setState] = useState('idle') // idle | sending | done
  const [error, setError] = useState(null)

  if (!customFoodUuid(foodId)) return null

  async function send() {
    setState('sending')
    setError(null)
    try {
      await reportFood({ foodId, reason, note, headers: await authHeaders() })
      setState('done')
    } catch (e) {
      setError(e.message)
      setState('idle')
    }
  }

  if (state === 'done') return <span className="report-food muted">{t('Takk – rapporten er sendt.')}</span>

  return (
    <span className="report-food">
      <button className="report-toggle" aria-expanded={open} title={t('Rapporter matvaren')}
              aria-label={t('Rapporter matvaren')} onClick={() => setOpen(!open)}>⚑</button>
      {open && (
        <span className="report-form">
          <select value={reason} onChange={(e) => setReason(e.target.value)} aria-label={t('Grunn')}>
            {REPORT_REASONS.map((r) => <option key={r.v} value={r.v}>{t(r.label)}</option>)}
          </select>
          <input value={note} maxLength={300} placeholder={t('Kommentar (valgfritt)')}
                 onChange={(e) => setNote(e.target.value)} />
          <button className="mini" disabled={state === 'sending'} onClick={send}>{t('Send rapport')}</button>
          {error && <span className="error" role="alert">{error}</span>}
        </span>
      )}
    </span>
  )
}
