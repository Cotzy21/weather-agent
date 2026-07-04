import { useState, useEffect } from 'react'
import { authHeaders } from './supabase'
import { apiUrl, readError } from './api'
import { useI18n } from './i18n.jsx'

/**
 * Habit tracker: vaner (koffein, søvn, lesing …) med 7-dagers rutenett og
 * streak. Ja/nei-vaner togglas med ett klikk; mengde-vaner (med enhet) har
 * et lite tallfelt per dag. Bor i Restitusjon-fanen – søvn og koffein ER
 * restitusjonsdata, og skal senere sammenlignes med HRV fra klokke-import.
 */

const SUGGESTIONS = [
  { name: 'Koffein', emoji: '☕', unit: 'kopper' },
  { name: 'Søvn', emoji: '😴', unit: 'timer' },
  { name: 'Lesing', emoji: '📖', unit: 'min' },
  { name: 'Meditasjon', emoji: '🧘', unit: 'min' },
  { name: 'Vann', emoji: '💧', unit: 'glass' },
]

const toIso = (d) => d.toISOString().slice(0, 10)

/** De siste 7 dagene, eldst først, som { iso, label } (label = "M", "T" …). */
function lastDays(n, locale) {
  const days = []
  for (let i = n - 1; i >= 0; i--) {
    const d = new Date()
    d.setDate(d.getDate() - i)
    days.push({
      iso: toIso(d),
      label: d.toLocaleDateString(locale, { weekday: 'short' }).slice(0, 2),
    })
  }
  return days
}

export default function HabitTracker() {
  const { t, lang } = useI18n()
  const [habits, setHabits] = useState(null)
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState({ name: '', emoji: '', unit: '' })
  const [error, setError] = useState(null)

  const days = lastDays(7, lang === 'en' ? 'en-GB' : 'nb-NO')
  const today = days[days.length - 1].iso

  useEffect(() => { load() }, [])

  async function load() {
    try {
      const res = await fetch(apiUrl('/api/vaner'), { headers: await authHeaders() })
      if (res.ok) setHabits(await res.json())
    } catch { /* seksjonen er sekundær */ }
  }

  async function create(habit) {
    setError(null)
    try {
      const res = await fetch(apiUrl('/api/vaner'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...(await authHeaders()) },
        body: JSON.stringify(habit),
      })
      if (!res.ok) throw new Error(await readError(res))
      setForm({ name: '', emoji: '', unit: '' })
      setShowForm(false)
      load()
    } catch (e) {
      setError(e.message)
    }
  }

  async function remove(id) {
    try {
      await fetch(apiUrl(`/api/vaner/${id}`), { method: 'DELETE', headers: await authHeaders() })
      load()
    } catch { /* ignorer */ }
  }

  async function log(habitId, date, value) {
    try {
      await fetch(apiUrl(`/api/vaner/${habitId}/logg`), {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json', ...(await authHeaders()) },
        body: JSON.stringify({ date, value }),
      })
      load()
    } catch { /* ignorer – cellen beholder gammel verdi ved reload */ }
  }

  const valueFor = (habit, iso) => habit.logs.find((l) => l.date === iso)?.value

  // Vaner brukeren ikke har fra før, som hurtigvalg i skjemaet.
  const unusedSuggestions = SUGGESTIONS.filter(
    (s) => !(habits ?? []).some((h) => h.name.toLowerCase() === s.name.toLowerCase()),
  )

  return (
    <div className="habits" data-reveal>
      <div className="habits-head">
        <h3 className="detail-h3">{t('📅 Vaner')}</h3>
        <button className="mini" onClick={() => setShowForm(!showForm)}>
          {showForm ? t('Lukk') : t('+ Ny vane')}
        </button>
      </div>

      {showForm && (
        <div className="habit-form">
          {unusedSuggestions.length > 0 && (
            <div className="habit-suggestions">
              {unusedSuggestions.map((s) => (
                <button key={s.name} className="pill" onClick={() => create(s)}>
                  {s.emoji} {t(s.name)}
                </button>
              ))}
            </div>
          )}
          <div className="habit-form-fields">
            <input placeholder={t('Navn (f.eks. Tøying)')} value={form.name}
                   onChange={(e) => setForm({ ...form, name: e.target.value })} />
            <input className="short" placeholder={t('Emoji')} value={form.emoji}
                   onChange={(e) => setForm({ ...form, emoji: e.target.value })} />
            <input className="short" placeholder={t('Enhet (tom = ja/nei)')} value={form.unit}
                   onChange={(e) => setForm({ ...form, unit: e.target.value })} />
            <button className="primary" disabled={!form.name.trim()} onClick={() => create(form)}>
              {t('Legg til')}
            </button>
          </div>
        </div>
      )}
      {error && <p className="error">{t('Beklager –')} {error}</p>}

      {habits && habits.length === 0 && !showForm && (
        <p className="muted">{t('Ingen vaner ennå – legg til koffein, søvn eller noe helt eget.')}</p>
      )}

      {habits && habits.length > 0 && (
        <div className="habit-grid">
          <div className="habit-row habit-header">
            <span className="habit-name" />
            {days.map((d) => (
              <span key={d.iso} className={`habit-day ${d.iso === today ? 'today' : ''}`}>{d.label}</span>
            ))}
            <span className="habit-streak" title={t('Dager på rad')}>🔥</span>
          </div>

          {habits.map((h) => (
            <div className="habit-row" key={h.id}>
              <span className="habit-name" title={h.unit ? `${h.name} (${h.unit})` : h.name}>
                {h.emoji} {t(h.name)}
                {h.unit && <span className="muted habit-unit"> {t(h.unit)}</span>}
              </span>

              {days.map((d) => {
                const value = valueFor(h, d.iso)
                return h.unit ? (
                  <input
                    key={d.iso}
                    className={`habit-cell ${d.iso === today ? 'today' : ''}`}
                    type="number"
                    min="0"
                    step="any"
                    defaultValue={value ?? ''}
                    onBlur={(e) => {
                      const v = e.target.value === '' ? 0 : Number(e.target.value)
                      if (v !== (value ?? 0)) log(h.id, d.iso, v)
                    }}
                  />
                ) : (
                  <button
                    key={d.iso}
                    className={`habit-cell toggle ${value ? 'done' : ''} ${d.iso === today ? 'today' : ''}`}
                    aria-label={`${h.name} ${d.iso}`}
                    onClick={() => log(h.id, d.iso, value ? 0 : 1)}
                  >
                    {value ? '✓' : ''}
                  </button>
                )
              })}

              <span className="habit-streak">{h.streakDays > 0 ? h.streakDays : ''}</span>
              <button className="del" onClick={() => remove(h.id)} aria-label={t('Slett vane')}>✕</button>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
