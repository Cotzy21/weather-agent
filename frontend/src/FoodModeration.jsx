import { useCallback, useEffect, useState } from 'react'
import { authHeaders } from './supabase'
import { useI18n } from './i18n.jsx'
import { REPORT_REASONS, deleteReportedFood, dismissReports, fetchReported } from './foodReports.js'

// Enkel moderering av rapporterte delte matvarer. Vises bare for administratorer: backend svarer 403 til alle andre,
// og da rendrer komponenten ingenting. Notater fra brukere er upålitelig tekst og vises som ren tekst (React escaper).
export default function FoodModeration() {
  const { t } = useI18n()
  const [items, setItems] = useState(null) // null: ikke administrator (eller ikke lastet ennå)
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const load = useCallback(async () => {
    try {
      setItems(await fetchReported({ headers: await authHeaders() }))
    } catch (e) {
      setError(e.message)
    }
  }, [])

  useEffect(() => { load() }, [load])

  async function act(fn, id) {
    setBusy(true)
    setError(null)
    try {
      await fn({ id, headers: await authHeaders() })
      await load()
    } catch (e) {
      setError(e.message)
    }
    setBusy(false)
  }

  if (items === null) return null

  const label = (v) => t(REPORT_REASONS.find((r) => r.v === v)?.label ?? v)

  return (
    <section className="danger-zone moderation">
      <h3>{t('Moderering')}</h3>
      {items.length === 0 && <p className="muted">{t('Ingen rapporterte matvarer.')}</p>}
      <ul className="moderation-list">
        {items.map((f) => (
          <li key={f.id}>
            <strong>{f.name}</strong>{f.brand ? ` (${f.brand})` : ''}
            <div className="muted">
              {f.kcalPer100g.toFixed(0)} kcal · P {f.proteinPer100g.toFixed(1)} · F {f.fatPer100g.toFixed(1)} · K {f.carbPer100g.toFixed(1)} {t('per 100 g')}
            </div>
            <div className="muted">
              {t('{n} rapporter', { n: f.reports })}: {Object.entries(f.reasons).map(([r, n]) => `${label(r)} ×${n}`).join(', ')}
            </div>
            {f.notes.map((n, i) => <div key={i} className="muted">“{n}”</div>)}
            <div className="moderation-actions">
              <button className="danger" disabled={busy} onClick={() => act(deleteReportedFood, f.id)}>{t('Slett matvaren')}</button>
              <button disabled={busy} onClick={() => act(dismissReports, f.id)}>{t('Avvis rapportene')}</button>
            </div>
          </li>
        ))}
      </ul>
      {error && <p className="error" role="alert">{error}</p>}
    </section>
  )
}
