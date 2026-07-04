import { useState } from 'react'
import { supabase } from './supabase'
import { useI18n } from './i18n.jsx'

// Enkel konto-side: e-post/passord registrering + innlogging via Supabase,
// eller utlogging hvis man allerede er innlogget.
export default function AuthView({ session }) {
  const { t } = useI18n()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [msg, setMsg] = useState(null)
  const [busy, setBusy] = useState(false)

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

  if (session) {
    return (
      <div className="account">
        <h2 className="detail-title">{t('Konto')}</h2>
        <p>{t('Innlogget som')} <strong>{session.user.email}</strong></p>
        <button className="primary" onClick={() => supabase.auth.signOut()}>{t('Logg ut')}</button>
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
      <div className="auth-form">
        <input type="email" placeholder={t('E-post')} value={email} onChange={(e) => setEmail(e.target.value)} />
        <input type="password" placeholder={t('Passord')} value={password} onChange={(e) => setPassword(e.target.value)} />
        <div className="auth-actions">
          <button className="primary" disabled={busy} onClick={() => submit('inn')}>{t('Logg inn')}</button>
          <button disabled={busy} onClick={() => submit('opp')}>{t('Registrer')}</button>
        </div>
        {msg && <p className="muted">{msg}</p>}
      </div>
    </div>
  )
}
