import { useState, useEffect } from 'react'
import HabitTracker from './HabitTracker'
import { authHeaders } from './supabase'
import { cachedGet, getCached } from './api'
import { useReveal } from './anim'
import { useI18n } from './i18n.jsx'

/**
 * Restitusjon og skadeforebygging: skadevarsler (volumhopp per aktivitet),
 * hviledag-forslag og konkrete restitusjonssteg etter de siste øktene.
 * Alt beregnes i backend fra treningsloggen – tommelfingerregler, ikke
 * medisinske råd (og det sier vi tydelig nederst).
 */

const TYPE_LABELS = {
  STYRKE: '🏋️ Styrke', LØPING: '🏃 Løping', SVØMMING: '🏊 Svømming',
  SYKKEL: '🚴 Sykkel', BULDRING: '🧗 Buldring', HIKING: '🥾 Hiking', FRISTIL: '✨ Fristil',
}
const label = (type) => TYPE_LABELS[type] || type

export default function RecoveryView({ session }) {
  const { t } = useI18n()
  // Hydrer fra cachen (forhåndshentet ved innlogging) så fanen tegnes straks.
  const [report, setReport] = useState(() => getCached('/api/recovery') ?? null)
  const revealRef = useReveal([session, report])

  useEffect(() => {
    if (!session) { setReport(null); return }
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session])

  async function load() {
    try {
      setReport(await cachedGet('/api/recovery', await authHeaders()))
    } catch { /* behold cachet / tom tilstand */ }
  }

  if (!session) {
    return (
      <div className="recovery" ref={revealRef}>
        <h2 className="detail-title" data-reveal>{t('🧘 Restitusjon')}</h2>
        <p className="muted" data-reveal>
          {t('Logg inn og før treningsdagbok – så får du restitusjonsråd og beskjed når treningsmengden øker raskere enn kroppen liker.')}
        </p>
      </div>
    )
  }

  const empty = report && report.advice.length === 0 && report.warnings.length === 0
    && !report.restDaySuggested

  return (
    <div className="recovery" ref={revealRef}>
      <h2 className="detail-title" data-reveal>{t('🧘 Restitusjon')}</h2>

      {!report && <p className="muted" data-reveal>{t('Henter rapporten …')}</p>}

      {empty && (
        <p className="muted" data-reveal>
          {t('Ingenting å melde – logg økter under «Trening», så dukker råd og varsler opp her.')}
        </p>
      )}

      {report?.warnings.map((w) => (
        <div key={w.type} className={`warn-card ${w.level === 'HOY' ? 'high' : ''}`} data-reveal>
          <p className="warn-head">
            ⚠️ <strong>{t(label(w.type))}</strong>
            {w.level === 'NY_AKTIVITET'
              ? ` ${t('– ny aktivitet for deg')}`
              : ` ${t('– ~{p} % over ditt vanlige nivå', { p: w.percentAboveNormal })}`}
          </p>
          <p className="warn-msg">{w.message}</p>
        </div>
      ))}

      {report?.restDaySuggested && (
        <div className="rest-card" data-reveal>
          🛌 <strong>{t('Du har trent {n} dager på rad', { n: report.streakDays })}</strong>{' '}
          {t('– kroppen bygger seg sterkere på hviledagen. Vurder en rolig dag i morgen.')}
        </div>
      )}

      {report?.advice.length > 0 && (
        <>
          <h3 className="detail-h3" data-reveal>{t('Etter de siste øktene')}</h3>
          {report.advice.map((a) => (
            <div key={a.type} className="advice-card" data-reveal>
              <p className="advice-head">
                <strong>{t(label(a.type))}</strong> <span className="muted">· {t(a.when)}</span>
              </p>
              <ul>{a.steps.map((s, i) => <li key={i}>{s}</li>)}</ul>
            </div>
          ))}
        </>
      )}

      <HabitTracker />

      <p className="muted source-note" data-reveal>
        {t('Varslene sammenligner siste 7 dager med snittet av de 4 ukene før (ACWR-metoden fra idrettsforskningen). Generelle tommelfingerregler – ikke medisinske råd.')}
      </p>
    </div>
  )
}
