import { useState, useEffect } from 'react'
import HabitTracker from './HabitTracker'
import { authHeaders } from './supabase'
import { apiUrl } from './api'
import { useReveal } from './anim'

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
  const [report, setReport] = useState(null)
  const revealRef = useReveal([session, report])

  useEffect(() => {
    if (!session) { setReport(null); return }
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session])

  async function load() {
    try {
      const res = await fetch(apiUrl('/api/recovery'), { headers: await authHeaders() })
      if (res.ok) setReport(await res.json())
    } catch { /* viser bare tom tilstand */ }
  }

  if (!session) {
    return (
      <div className="recovery" ref={revealRef}>
        <h2 className="detail-title" data-reveal>🧘 Restitusjon</h2>
        <p className="muted" data-reveal>
          Logg inn og før treningsdagbok – så får du restitusjonsråd og beskjed
          når treningsmengden øker raskere enn kroppen liker.
        </p>
      </div>
    )
  }

  const empty = report && report.advice.length === 0 && report.warnings.length === 0
    && !report.restDaySuggested

  return (
    <div className="recovery" ref={revealRef}>
      <h2 className="detail-title" data-reveal>🧘 Restitusjon</h2>

      {!report && <p className="muted" data-reveal>Henter rapporten …</p>}

      {empty && (
        <p className="muted" data-reveal>
          Ingenting å melde – logg økter under «Trening», så dukker råd og
          varsler opp her.
        </p>
      )}

      {report?.warnings.map((w) => (
        <div key={w.type} className={`warn-card ${w.level === 'HOY' ? 'high' : ''}`} data-reveal>
          <p className="warn-head">
            ⚠️ <strong>{label(w.type)}</strong>
            {w.level === 'NY_AKTIVITET'
              ? ' – ny aktivitet for deg'
              : ` – ~${w.percentAboveNormal} % over ditt vanlige nivå`}
          </p>
          <p className="warn-msg">{w.message}</p>
        </div>
      ))}

      {report?.restDaySuggested && (
        <div className="rest-card" data-reveal>
          🛌 Du har trent <strong>{report.streakDays} dager på rad</strong> – kroppen
          bygger seg sterkere på hviledagen. Vurder en rolig dag i morgen.
        </div>
      )}

      {report?.advice.length > 0 && (
        <>
          <h3 className="detail-h3" data-reveal>Etter de siste øktene</h3>
          {report.advice.map((a) => (
            <div key={a.type} className="advice-card" data-reveal>
              <p className="advice-head">
                <strong>{label(a.type)}</strong> <span className="muted">· {a.when}</span>
              </p>
              <ul>{a.steps.map((s, i) => <li key={i}>{s}</li>)}</ul>
            </div>
          ))}
        </>
      )}

      <HabitTracker />

      <p className="muted source-note" data-reveal>
        Varslene sammenligner siste 7 dager med snittet av de 4 ukene før
        (ACWR-metoden fra idrettsforskningen). Generelle tommelfingerregler –
        ikke medisinske råd.
      </p>
    </div>
  )
}
