import { useEffect, useMemo, useState } from 'react'
import { useI18n } from './i18n.jsx'
import { liveExercise, findSuggestion, toNum } from './liveSession.js'
import {
  buildSteps, currentIndex, nextUndone, overview, progress, makeSuperset, unlinkSuperset, removeExercise, stepValue, restFor,
  DEFAULT_REST_SEC, MIN_REST_SEC, MAX_REST_SEC, clampRest,
} from './liveFlow.js'

const fmtClock = (sec) => {
  const h = Math.floor(sec / 3600)
  const m = Math.floor((sec % 3600) / 60)
  const s = String(sec % 60).padStart(2, '0')
  return h ? `${h}:${String(m).padStart(2, '0')}:${s}` : `${m}:${s}`
}
const fmtRest = (sec) => {
  if (sec < 60) return `${sec} s`
  return sec % 60 === 0 ? `${sec / 60} min` : `${Math.floor(sec / 60)}:${String(sec % 60).padStart(2, '0')}`
}

const REST_PRESETS = [30, 45, 60, 90, 120, 180, 300]
const RING_R = 92
const RING_C = 2 * Math.PI * RING_R

/**
 * Fokusmodus for live-økta: hele skjermen er ÉN øvelse og ÉTT sett om gangen (kg og reps med store knapper, «Fullfør sett»), så en
 * hvileskjerm med nedtelling mellom settene. Du kan se hva som kommer, hoppe til en annen øvelse, lage et supersett midt i økta
 * (to øvelser vekselvis, hvile først etter runden), og endre hvilen underveis. Selve økta (tilstanden) eies av LiveSession.
 */
export default function LiveFocus({
  s, now, restLeft, nextSets, fmtKg, prSets, edit, onComplete, onUndo, onSkipRest, onAdjustRest, onOpenList, onEnd, onDiscard,
  onHistory, onSwap, onPickExercise, busy, error, children,
}) {
  const { t, lang } = useI18n()
  const [sheet, setSheet] = useState(null) // null | 'menu' | 'drawer' | 'rest' | 'super'
  const [tipOpen, setTipOpen] = useState(false)
  const [noteOpen, setNoteOpen] = useState(false)
  const [restScope, setRestScope] = useState('exercise')

  // Helskjerm: siden bak skal ikke rulle mens fokusvisningen er oppe.
  useEffect(() => {
    const prev = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = prev }
  }, [])

  const steps = useMemo(() => buildSteps(s.exercises), [s.exercises])
  const curIdx = currentIndex(s.exercises, steps, s.cursor)
  const cur = steps[curIdx]
  const ex = cur ? s.exercises[cur.ei] : null
  const set = ex ? ex.sets[cur.si] : null
  const prog = progress(s.exercises)
  const nextIdx = cur ? nextUndone(s.exercises, steps, curIdx) : -1
  const next = nextIdx >= 0 && nextIdx !== curIdx ? steps[nextIdx] : null
  const resting = Boolean(s.restEnd) && Boolean(cur)
  const items = useMemo(() => overview(s.exercises, steps, curIdx), [s.exercises, steps, curIdx])
  const members = ex?.group ? s.exercises.map((e, i) => ({ e, i })).filter((m) => m.e.group === ex.group) : []
  const sug = ex && ex.kind !== 'dropset' ? findSuggestion(ex.name, nextSets) : null
  const justDone = s.lastDone && prSets.has(`${s.lastDone.ei}-${s.lastDone.si}`)
  const restSecNow = ex ? restFor(ex, s.restSec ?? DEFAULT_REST_SEC) : s.restSec ?? DEFAULT_REST_SEC

  const show = (v) => (lang === 'en' ? String(v ?? '') : String(v ?? '').replace('.', ','))
  const setField = (ei, si, field, value) => edit((c) => { c.exercises[ei].sets[si][field] = value })
  const bump = (target, field, delta) => {
    const opts = field === 'weightKg' ? { min: 0, max: 1000, start: 0, blankAtZero: true } : { min: 1, max: 100, start: 8 }
    setField(target.ei, target.si, field, stepValue(s.exercises[target.ei].sets[target.si][field], delta, opts))
  }

  const jump = (ei) => {
    edit((c) => {
      const si = c.exercises[ei].sets.findIndex((x) => !x.done)
      if (si >= 0) c.cursor = { ei, si }
    })
    setSheet(null)
  }
  const moveToEnd = (ei) => edit((c) => { const [m] = c.exercises.splice(ei, 1); c.exercises.push(m); c.cursor = null })
  const remove = (ei) => edit((c) => { c.exercises = removeExercise(c.exercises, ei); c.cursor = null })
  const pairWith = (other) => { edit((c) => { c.exercises = makeSuperset(c.exercises, cur.ei, other); c.cursor = null }); setSheet(null) }
  const pairWithNew = () => {
    const curEi = cur.ei
    setSheet(null)
    onPickExercise((name) => edit((c) => {
      c.exercises.push(liveExercise(name, null, nextSets))
      c.exercises = makeSuperset(c.exercises, curEi, c.exercises.length - 1)
      c.cursor = null
    }))
  }
  const unlink = () => { edit((c) => { c.exercises = unlinkSuperset(c.exercises, cur.ei); c.cursor = null }); setSheet(null) }
  const extraSet = () => edit((c) => {
    const sets = c.exercises[cur.ei].sets
    const last = sets[sets.length - 1]
    sets.push({ reps: last?.reps ?? '', weightKg: last?.weightKg ?? '', done: false })
  })
  const applyRest = (sec) => edit((c) => {
    const v = clampRest(sec)
    if (restScope === 'all') {
      c.restSec = v
      c.exercises.forEach((e) => { delete e.restSec })
    } else {
      c.exercises[cur.ei].restSec = v
    }
  })

  const stepper = ({ label, target, field, kgStep }) => {
    const value = s.exercises[target.ei].sets[target.si][field]
    return (
      <div className="fl-stepper">
        <span className="fl-label">{label}</span>
        <div className="fl-stepper-row">
          <button className="fl-step" aria-label={`${label} −`} onClick={() => bump(target, field, -kgStep)}>−</button>
          <input className="fl-input" inputMode={field === 'weightKg' ? 'decimal' : 'numeric'} value={show(value)} placeholder="–"
                 aria-label={label} onChange={(e) => setField(target.ei, target.si, field, e.target.value)} />
          <button className="fl-step" aria-label={`${label} +`} onClick={() => bump(target, field, kgStep)}>+</button>
        </div>
      </div>
    )
  }

  const nextLine = (st) => {
    const e = s.exercises[st.ei]
    const q = e.sets[st.si]
    const w = toNum(q.weightKg) > 0 ? `${show(q.weightKg)} kg × ` : ''
    return `${e.name} · ${t('sett {n} av {total}', { n: st.si + 1, total: e.sets.length })}${q.reps !== '' ? ` · ${w}${q.reps}` : ''}`
  }

  // ---- Toppfelt (alltid synlig) ----
  const topBar = (
    <div className="fl-top">
      <button className="fl-icon" aria-label={t('Meny')} onClick={() => setSheet('menu')}>✕</button>
      <span className="fl-clock">{fmtClock(Math.max(0, Math.floor((now - s.startedAt) / 1000)))}</span>
      <button className="fl-icon fl-count" onClick={() => setSheet('drawer')} aria-label={t('Neste øvelser')}>
        {t('Sett {n}/{total}', { n: Math.min(prog.done + 1, prog.total), total: prog.total })} ☰
      </button>
      <div className="fl-progress" aria-hidden="true"><span style={{ width: `${prog.total ? (prog.done / prog.total) * 100 : 0}%` }} /></div>
    </div>
  )

  const addFirst = () => onPickExercise((name) => edit((c) => { c.exercises.push(liveExercise(name, null, nextSets)); c.cursor = null }))

  let body
  if (!cur && prog.total === 0) {
    // ---- Tom økt: legg til første øvelse ----
    body = (
      <div className="fl-main fl-center">
        <div className="fl-done-emoji" aria-hidden="true">🏋️</div>
        <h2 className="fl-title">{t('Tom økt')}</h2>
        <p className="muted">{t('Legg til din første øvelse for å begynne.')}</p>
        <button className="primary fl-big" onClick={addFirst}>＋ {t('Legg til øvelse')}</button>
      </div>
    )
  } else if (!cur) {
    // ---- Ferdig ----
    body = (
      <div className="fl-main fl-center">
        <div className="fl-done-emoji" aria-hidden="true">🎉</div>
        <h2 className="fl-title">{t('Økta er ferdig!')}</h2>
        <p className="muted">{t('{n} sett fullført', { n: prog.done })}</p>
        {error && <p className="error">{error}</p>}
        <button className="primary fl-big" disabled={busy} onClick={onEnd}>{busy ? t('Lagrer …') : t('Lagre økt')}</button>
        <button className="fl-secondary" onClick={addFirst}>＋ {t('Legg til øvelse')}</button>
        {s.lastDone && <button className="fl-link" onClick={onUndo}>↩ {t('Angre siste sett')}</button>}
      </div>
    )
  } else if (resting) {
    // ---- Hvile ----
    const total = Math.max(1, s.restTotal || restSecNow)
    const ready = restLeft === 0
    const pct = ready ? 0 : Math.min(1, (restLeft ?? 0) / total)
    body = (
      <div className="fl-main fl-center">
        {justDone && <p className="fl-pr">🏆 {t('Ny rekord!')}</p>}
        <p className="fl-label">{ready ? t('Klar!') : t('Hvile')}</p>
        <div className={`fl-ring ${ready ? 'ready' : ''}`} role="timer" aria-label={t('Hvile')}>
          <svg viewBox="0 0 200 200" aria-hidden="true">
            <circle className="fl-ring-bg" cx="100" cy="100" r={RING_R} />
            <circle className="fl-ring-fg" cx="100" cy="100" r={RING_R} strokeDasharray={RING_C} strokeDashoffset={RING_C * (1 - pct)} />
          </svg>
          <span className="fl-ring-time">{ready ? '0:00' : fmtClock(restLeft ?? 0)}</span>
        </div>
        <div className="fl-rest-btns">
          <button className="fl-secondary" onClick={() => onAdjustRest(-15)}>−15 s</button>
          <button className="fl-secondary" onClick={() => onAdjustRest(15)}>+15 s</button>
        </div>
        <button className="primary fl-big" onClick={onSkipRest}>{ready ? t('Start neste sett') : t('Hopp over hvile')} →</button>

        <div className="fl-next-card">
          <span className="fl-label">{t('Neste opp')}</span>
          <strong>{ex.name}</strong>
          <span className="muted">{t('Sett {n} av {total}', { n: cur.si + 1, total: ex.sets.length })}{members.length > 1 ? ` · ${t('Supersett')}` : ''}</span>
          <div className="fl-next-steppers">
            {stepper({ label: 'kg', target: cur, field: 'weightKg', kgStep: 2.5 })}
            {stepper({ label: t('Reps'), target: cur, field: 'reps', kgStep: 1 })}
          </div>
        </div>
        <div className="fl-rest-foot">
          <button className="fl-link" onClick={() => setSheet('rest')}>⏱ {t('Endre hvile')} ({fmtRest(restSecNow)})</button>
          {s.lastDone && <button className="fl-link" onClick={onUndo}>↩ {t('Angre forrige sett')}</button>}
        </div>
      </div>
    )
  } else {
    // ---- Sett ----
    const canComplete = toNum(set.reps) > 0
    body = (
      <div className="fl-main">
        {members.length > 1 && (
          <div className="fl-pair" aria-label={t('Supersett')}>
            <span className="fl-tag">⇄ {t('Supersett')}</span>
            {members.map((m) => (
              <span key={m.i} className={`fl-chip ${m.i === cur.ei ? 'on' : ''}`}>{m.e.name}</span>
            ))}
          </div>
        )}
        <h2 className="fl-title">{ex.name}{ex.kind === 'dropset' && <span className="muted"> · {t('dropsett')}</span>}</h2>
        <p className="fl-setno">{t('Sett {n} av {total}', { n: cur.si + 1, total: ex.sets.length })}</p>

        <div className="fl-actions">
          {sug && <button className={`fl-chipbtn ${tipOpen ? 'on' : ''}`} onClick={() => setTipOpen((v) => !v)}>💡 {t('Tips')}</button>}
          <button className="fl-chipbtn" onClick={() => onHistory(cur.ei)}>🕘 {t('Historikk')}</button>
          <button className="fl-chipbtn" onClick={() => onSwap(cur.ei)}>⇄ {t('Bytt')}</button>
          <button className={`fl-chipbtn ${noteOpen || ex.note ? 'on' : ''}`} onClick={() => setNoteOpen((v) => !v)}>✎ {t('Notat')}</button>
        </div>
        {sug && tipOpen && (
          <p className="live-hint muted">
            📈 {sug.lastWeightKg > 0 ? `${fmtKg(sug.lastWeightKg)} kg` : t('kroppsvekt')} × {sug.lastReps.join(', ')} → {sug.reason}
          </p>
        )}
        {noteOpen && (
          <textarea className="live-note" rows="2" maxLength={300} value={ex.note || ''} placeholder={t('F.eks. «setehøyde 4, smal grep»')}
                    onChange={(e) => { const v = e.target.value; edit((c) => { c.exercises[cur.ei].note = v }) }} />
        )}

        <div className="fl-steppers">
          {stepper({ label: 'kg', target: cur, field: 'weightKg', kgStep: 2.5 })}
          {stepper({ label: t('Reps'), target: cur, field: 'reps', kgStep: 1 })}
        </div>

        {error && <p className="error">{error}</p>}
        <button className="primary fl-big fl-complete" disabled={!canComplete} onClick={() => onComplete(cur.ei, cur.si)}>
          ✓ {t('Fullfør sett')}
        </button>
        {!canComplete && <p className="muted fl-hint">{t('Fyll inn reps for å fullføre settet.')}</p>}

        <div className="fl-toolrow">
          <button className="fl-link" onClick={() => setSheet('rest')}>⏱ {t('Hvile')} {fmtRest(restSecNow)}</button>
          <button className="fl-link" onClick={() => setSheet('super')}>⇄ {members.length > 1 ? t('Supersett …') : t('Lag supersett')}</button>
          <button className="fl-link" onClick={extraSet}>＋ {t('Ekstra sett')}</button>
        </div>

        {next && (
          <button className="fl-next" onClick={() => setSheet('drawer')}>
            <span className="fl-label">{t('Neste')}</span>
            <span>{nextLine(next)}</span>
            <span aria-hidden="true">›</span>
          </button>
        )}
      </div>
    )
  }

  // ---- Ark ----
  const close = () => setSheet(null)
  const sheets = (
    <>
      {sheet === 'menu' && (
        <div className="sheet-backdrop" onClick={close}>
          <div className="sheet" role="dialog" aria-label={t('Meny')} onClick={(e) => e.stopPropagation()}>
            <span className="sheet-handle" aria-hidden="true" />
            <button className="sheet-item" onClick={() => { close(); onOpenList() }}>
              <span className="sheet-icon">📋</span><span className="sheet-text">{t('Alle sett (liste)')}<small>{t('Se og rediger hele økta, og bruk resten av appen')}</small></span>
            </button>
            <button className="sheet-item" onClick={() => { close(); onEnd() }}>
              <span className="sheet-icon">💾</span><span className="sheet-text">{t('Avslutt og lagre')}<small>{t('Lagrer de avhukede settene')}</small></span>
            </button>
            <button className="sheet-item" onClick={() => { close(); onDiscard() }}>
              <span className="sheet-icon">🗑️</span><span className="sheet-text">{t('Avbryt økt uten å lagre')}</span>
            </button>
            <button className="sheet-item" onClick={close}>
              <span className="sheet-icon">▶</span><span className="sheet-text">{t('Fortsett økta')}</span>
            </button>
          </div>
        </div>
      )}

      {sheet === 'drawer' && (
        <div className="sheet-backdrop" onClick={close}>
          <div className="sheet picker" role="dialog" aria-label={t('Neste øvelser')} onClick={(e) => e.stopPropagation()}>
            <span className="sheet-handle" aria-hidden="true" />
            <div className="picker-head"><strong>{t('Neste øvelser')}</strong><button className="mini" onClick={close}>{t('Lukk')}</button></div>
            <ul className="fl-list">
              {items.map((it) => (
                <li key={it.ei} className={`fl-row ${it.state}`}>
                  <button className="fl-row-main" disabled={it.state === 'done'} onClick={() => jump(it.ei)}>
                    <span className="fl-row-state" aria-hidden="true">{it.state === 'done' ? '✓' : it.state === 'current' ? '▶' : '○'}</span>
                    <span className="fl-row-name">
                      <strong>{it.name}</strong>
                      <span className="muted">{t('{done} av {total} sett', { done: it.done, total: it.total })}{it.group ? ` · ⇄ ${t('Supersett')}` : ''}</span>
                    </span>
                  </button>
                  {it.state !== 'done' && (
                    <span className="fl-row-btns">
                      {cur && it.ei !== cur.ei && (!ex.group || ex.group !== it.group) && (
                        <button className="fl-mini" aria-label={t('Supersett med {name}', { name: ex.name })} title={t('Supersett med {name}', { name: ex.name })}
                                onClick={() => pairWith(it.ei)}>⇄</button>
                      )}
                      {it.state === 'upcoming' && <button className="fl-mini" aria-label={t('Flytt sist')} title={t('Flytt sist')} onClick={() => moveToEnd(it.ei)}>↓</button>}
                      <button className="fl-mini" aria-label={t('Fjern')} title={t('Fjern')} onClick={() => remove(it.ei)}>✕</button>
                    </span>
                  )}
                </li>
              ))}
            </ul>
            <button className="live-add-btn" onClick={() => { close(); addFirst() }}>
              ＋ {t('Legg til øvelse')}
            </button>
          </div>
        </div>
      )}

      {sheet === 'super' && cur && (
        <div className="sheet-backdrop" onClick={close}>
          <div className="sheet picker" role="dialog" aria-label={t('Lag supersett')} onClick={(e) => e.stopPropagation()}>
            <span className="sheet-handle" aria-hidden="true" />
            <div className="picker-head"><strong>{t('Supersett med {name}', { name: ex.name })}</strong><button className="mini" onClick={close}>{t('Lukk')}</button></div>
            <p className="muted fl-super-help">{t('Øvelsene gjøres vekselvis (A, B, A, B …) og hvilen kommer først etter hver runde.')}</p>
            <ul className="fl-list">
              {items.filter((it) => it.state !== 'done' && it.ei !== cur.ei && (!ex.group || ex.group !== it.group)).map((it) => (
                <li key={it.ei} className="fl-row">
                  <button className="fl-row-main" onClick={() => pairWith(it.ei)}>
                    <span className="fl-row-state" aria-hidden="true">⇄</span>
                    <span className="fl-row-name"><strong>{it.name}</strong><span className="muted">{t('{done} av {total} sett', { done: it.done, total: it.total })}</span></span>
                  </button>
                </li>
              ))}
            </ul>
            <button className="live-add-btn" onClick={pairWithNew}>＋ {t('Velg en ny øvelse')}</button>
            {members.length > 1 && <button className="live-cancel" onClick={unlink}>{t('Løs opp supersettet')}</button>}
          </div>
        </div>
      )}

      {sheet === 'rest' && ex && (
        <div className="sheet-backdrop" onClick={close}>
          <div className="sheet" role="dialog" aria-label={t('Hvile mellom sett')} onClick={(e) => e.stopPropagation()}>
            <span className="sheet-handle" aria-hidden="true" />
            <div className="picker-head"><strong>{t('Hvile mellom sett')}</strong><button className="mini" onClick={close}>{t('Lukk')}</button></div>
            <div className="type-select" role="group" aria-label={t('Gjelder for')}>
              <button className={`type-chip ${restScope === 'exercise' ? 'active' : ''}`} onClick={() => setRestScope('exercise')}>{t('Denne øvelsen')}</button>
              <button className={`type-chip ${restScope === 'all' ? 'active' : ''}`} onClick={() => setRestScope('all')}>{t('Alle øvelser')}</button>
            </div>
            <div className="fl-rest-now">
              <button className="fl-step" aria-label={t('15 sekunder kortere')} onClick={() => applyRest(restSecNow - 15)} disabled={restSecNow <= MIN_REST_SEC}>−</button>
              <strong>{fmtRest(restSecNow)}</strong>
              <button className="fl-step" aria-label={t('15 sekunder lengre')} onClick={() => applyRest(restSecNow + 15)} disabled={restSecNow >= MAX_REST_SEC}>+</button>
            </div>
            <div className="type-select">
              {REST_PRESETS.map((sec) => (
                <button key={sec} className={`type-chip ${restSecNow === sec ? 'active' : ''}`} onClick={() => applyRest(sec)}>{fmtRest(sec)}</button>
              ))}
            </div>
            <p className="muted fl-super-help">{t('Du kan alltid hoppe over eller justere hvilen mens den går.')}</p>
          </div>
        </div>
      )}
    </>
  )

  return (
    <div className="focus-live">
      {topBar}
      {body}
      {sheets}
      {children}
    </div>
  )
}
