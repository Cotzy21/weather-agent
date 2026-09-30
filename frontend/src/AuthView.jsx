import { useState } from 'react'
import { supabase, authHeaders } from './supabase'
import { useI18n } from './i18n.jsx'
import { deleteAccount, eraseLocalUserData, isDeleteConfirmation } from './accountDeletion.js'
import { workoutQueue } from './offlineQueue.js'

// Enkel konto-side: e-post/passord registrering + innlogging via Supabase,
// eller utlogging hvis man allerede er innlogget. Innlogget bruker kan også slette kontoen sin (GDPR).
export default function AuthView({ session, reason, onOpenPrivacy, onDeleted }) {
  const { t } = useI18n()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [msg, setMsg] = useState(null)
  const [busy, setBusy] = useState(false)
  // Samtykke kreves for å registrere seg: appen lagrer helseopplysninger (se personvernerklæringen).
  const [consent, setConsent] = useState(false)
  const [confirmWord, setConfirmWord] = useState('')
  const [deleteError, setDeleteError] = useState(null)

  if (!supabase) {
    return (
      <div className="account">
        <h2 className="detail-title">{t('Konto')}</h2>
        <p className="muted">
          {t('Supabase er ikke konfigurert ennå (mangler VITE_SUPABASE_URL / VITE_SUPABASE_ANON_KEY).')}
        </p>
      </div>
    )
  }

  async function removeAccount() {
    setBusy(true)
    setDeleteError(null)
    try {
      const result = await deleteAccount({ headers: await authHeaders() })
      // Serveren har slettet alt. Rydd også bort det som ligger igjen på enheten, og kast økter som venter på nett,
      // ellers ville de gjenopprettet dataene neste gang appen fikk nett.
      await workoutQueue(authHeaders).discardMine()
      eraseLocalUserData(session.user.id)
      onDeleted?.(result)
      try { await supabase.auth.signOut() } catch { /* kontoen er slettet uansett */ }
    } catch (e) {
      setDeleteError(e.message)
      setBusy(false)
    }
  }

  if (session) {
    return (
      <div className="account">
        <h2 className="detail-title">{t('Konto')}</h2>
        <p>{t('Innlogget som')} <strong>{session.user.email}</strong></p>
        <button className="primary" onClick={() => supabase.auth.signOut()}>{t('Logg ut')}</button>

        <section className="danger-zone">
          <h3>{t('Personvern og data')}</h3>
          <p className="muted">
            <button className="link-btn" onClick={onOpenPrivacy}>{t('Les personvernerklæringen')}</button>
          </p>
          <details>
            <summary>{t('Slett kontoen min')}</summary>
            <p>
              {t('Dette sletter alle dataene dine (økter, planer, måltider, vekt, vaner, ruter og innstillinger) og innloggingen din, for godt. Det kan ikke angres.')}
            </p>
            <label className="confirm-delete">
              {t('Skriv SLETT for å bekrefte')}
              <input value={confirmWord} onChange={(e) => setConfirmWord(e.target.value)} autoComplete="off" />
            </label>
            <button className="danger" disabled={busy || !isDeleteConfirmation(confirmWord)} onClick={removeAccount}>
              {busy ? t('Sletter …') : t('Slett kontoen min for godt')}
            </button>
            {deleteError && <p className="error" role="alert">{deleteError}</p>}
          </details>
        </section>
      </div>
    )
  }

  async function submit(mode) {
    setBusy(true)
    setMsg(null)
    const { error } = mode === 'inn'
      ? await supabase.auth.signInWithPassword({ email, password })
      : await supabase.auth.signUp({ email, password })
    if (error) setMsg(error.message)
    else if (mode === 'opp') setMsg(t('Konto opprettet. Bekreft e-posten din hvis bekreftelse er påslått.'))
    setBusy(false)
  }

  return (
    <div className="account">
      <h2 className="detail-title">{t('Logg inn')}</h2>
      {reason && <p className="muted auth-reason">{reason}</p>}
      <div className="auth-form">
        <input type="email" placeholder={t('E-post')} value={email} onChange={(e) => setEmail(e.target.value)} />
        <input type="password" placeholder={t('Passord')} value={password} onChange={(e) => setPassword(e.target.value)} />
        <label className="consent">
          <input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} />
          <span>
            {t('Jeg samtykker til at helse- og treningsdata jeg legger inn lagres.')}{' '}
            <button type="button" className="link-btn" onClick={onOpenPrivacy}>{t('Les personvernerklæringen')}</button>
          </span>
        </label>
        <div className="auth-actions">
          <button className="primary" disabled={busy} onClick={() => submit('inn')}>{t('Logg inn')}</button>
          <button disabled={busy || !consent} title={consent ? undefined : t('Kryss av samtykket for å registrere deg')}
                  onClick={() => submit('opp')}>{t('Registrer')}</button>
        </div>
        {msg && <p className="muted">{msg}</p>}
      </div>
    </div>
  )
}
