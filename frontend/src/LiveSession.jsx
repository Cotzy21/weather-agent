import { useState, useEffect, useRef, useMemo } from 'react'
import { useI18n } from './i18n.jsx'
import { saveLive, liveExercise, findSuggestion, toContent } from './liveSession.js'
import ExercisePicker from './ExercisePicker.jsx'
import ExerciseHistory from './ExerciseHistory.jsx'
import { sameGroup, exerciseKey } from './exercises'
import { priorBests, livePRSets } from './prDetection.js'

const fmtClock = (sec) => {
  const h = Math.floor(sec / 3600)
  const m = Math.floor((sec % 3600) / 60)
  const s = String(sec % 60).padStart(2, '0')
  return h ? `${h}:${String(m).padStart(2, '0')}:${s}` : `${m}:${s}`
}

function beep(ctx) {
  if (!ctx) return
  try {
    const osc = ctx.createOscillator()
    const gain = ctx.createGain()
    osc.frequency.value = 880
    gain.gain.value = 0.2
    osc.connect(gain).connect(ctx.destination)
    osc.start()
    osc.stop(ctx.currentTime + 0.4)
  } catch { /* lyd er bare en bonus */ }
}

export default function LiveSession({ userId, initial, nextSets, memory, workouts, fmtKg, onFinish, onCancel }) {
  const { t, lang } = useI18n()
  const [s, setS] = useState(initial)
  const [now, setNow] = useState(() => Date.now())
  const [picking, setPicking] = useState(false)
  // Egen bekreftelse i appen: window.confirm vises ikke alltid i hjemskjerm-apper på iPhone.
  const [confirming, setConfirming] = useState(null) // null | 'discard' | 'end'
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  // Handlingslinjen per øvelse: tips/notat folder ut i kortet, historikk/bytt åpner et ark.
  const [panel, setPanel] = useState(null) // { ei, kind: 'tip' | 'note' }
  const [sheet, setSheet] = useState(null) // { ei, kind: 'history' | 'swap' }
  const [scrollTick, setScrollTick] = useState(0)
  const audio = useRef(null)
  const alerted = useRef(null)
  // Rekorder mens økta pågår: sammenlignet med historikken (uten denne økta) får det beste avhukede settet en 🏆.
  const bests = useMemo(() => priorBests(workouts), [workouts])
  const prSets = useMemo(() => livePRSets(s.exercises, bests), [s.exercises, bests])
  const seenPrs = useRef(prSets)

  useEffect(() => { saveLive(userId, s) }, [userId, s])

  // Kort vibrasjon når et sett blir en NY rekord (ikke ved hvert tegn man skriver, og ikke for rekorder som allerede var markert).
  useEffect(() => {
    if ([...prSets].some((k) => !seenPrs.current.has(k))) navigator.vibrate?.([30, 50, 30])
    seenPrs.current = prSets
  }, [prSets])

  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(id)
  }, [])

  // Hold skjermen på under økta. iOS slipper låsen når appen skjules, så be igjen ved retur.
  useEffect(() => {
    let lock = null
    const request = async () => {
      try { lock = await navigator.wakeLock?.request('screen') } catch { /* ikke støttet */ }
    }
    const onVisible = () => { if (document.visibilityState === 'visible') request() }
    request()
    document.addEventListener('visibilitychange', onVisible)
    return () => {
      document.removeEventListener('visibilitychange', onVisible)
      lock?.release().catch(() => {})
    }
  }, [])

  // Pausen regnes mot et fast sluttidspunkt, så den stemmer selv om telefonen har vært låst.
  const restLeft = s.restEnd ? Math.max(0, Math.ceil((s.restEnd - now) / 1000)) : null

  useEffect(() => {
    if (restLeft !== 0 || alerted.current === s.restEnd) return
    alerted.current = s.restEnd
    navigator.vibrate?.([200, 100, 200])
    beep(audio.current)
  }, [restLeft, s.restEnd])

  function edit(fn) {
    setS((prev) => { const c = structuredClone(prev); fn(c); return c })
  }

  function toggleSet(ei, si) {
    // Lyd må låses opp av et trykk (iOS), så AudioContext lages her.
    if (!audio.current) {
      try { audio.current = new (window.AudioContext || window.webkitAudioContext)() } catch { /* ingen lyd */ }
    }
    audio.current?.resume?.()
    const start = Date.now()
    if (!s.exercises[ei].sets[si].done) setScrollTick((n) => n + 1)
    edit((c) => {
      const set = c.exercises[ei].sets[si]
      set.done = !set.done
      if (set.done) c.restEnd = start + c.restSec * 1000
    })
  }

  // Etter avhuking: rull neste sett inn i midten, så tommelen alltid treffer riktig rad.
  useEffect(() => {
    if (!scrollTick) return
    document.querySelector('.live-set.current')?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }, [scrollTick])

  function adjustRest(delta) {
    edit((c) => {
      c.restSec = Math.max(15, c.restSec + delta)
      if (c.restEnd) c.restEnd = Math.max(Date.now(), c.restEnd + delta * 1000)
    })
  }

  function addExercise(name) {
    const n = name.trim()
    if (!n) return
    edit((c) => { c.exercises.push(liveExercise(n, null, nextSets)) })
  }

  // Bytt øvelse: avhukede sett beholdes som de er. Resten får forslaget for den nye
  // øvelsen hvis det finnes; ellers beholdes reps og vekten tømmes (annen øvelse, annen vekt).
  function swapExercise(ei, name) {
    const sug = findSuggestion(name, nextSets)
    edit((c) => {
      const ex = c.exercises[ei]
      ex.name = name
      ex.sets = ex.sets.map((set) => {
        if (set.done) return set
        if (sug) return { ...set, reps: String(sug.nextReps), weightKg: sug.nextWeightKg > 0 ? String(sug.nextWeightKg) : '' }
        return { ...set, weightKg: '' }
      })
    })
  }

  async function finish() {
    const content = toContent(s.exercises, s.startedAt)
    if (!content.blocks.length) { setError(t('Huk av minst ett sett før du fullfører.')); return }
    setBusy(true)
    setError(null)
    try {
      await onFinish({ title: s.title.trim() || t('Styrkeøkt'), content })
    } catch (e) {
      setError(e.message)
      setBusy(false)
    }
  }



  const allSets = s.exercises.flatMap((e) => e.sets)
  const doneCount = allSets.filter((x) => x.done).length
  const inSession = new Set(s.exercises.map((e) => exerciseKey(e.name)))
  // Første sett som ikke er huket av = «nå»-settet som utheves.
  let current = null
  s.exercises.some((ex, ei) => {
    const si = ex.sets.findIndex((x) => !x.done)
    if (si >= 0) current = `${ei}-${si}`
    return si >= 0
  })
  const currentNo = Math.min(doneCount + 1, allSets.length)

  function endWorkout() {
    if (doneCount > 0 && allSets.length - doneCount > 0) setConfirming('end')
    else finish()
  }


  return (
    <div className="live">
      <div className="live-bar">
        <div className="live-bar-row">
          <span className="live-clock">{fmtClock(Math.max(0, Math.floor((now - s.startedAt) / 1000)))}</span>
          <span className="live-bar-sep" aria-hidden="true" />
          <span className="live-count">{t('Sett {n}/{total}', { n: currentNo, total: allSets.length })}</span>
          <button className="live-end" disabled={busy} onClick={endWorkout}>
            {busy ? t('Lagrer …') : t('Avslutt økt')}
          </button>
        </div>
        <div className="live-progress" aria-hidden="true">
          <span style={{ width: `${allSets.length ? (doneCount / allSets.length) * 100 : 0}%` }} />
        </div>
        {error && <p className="error live-error">{error}</p>}
      </div>

      <input
        className="live-title"
        value={s.title}
        placeholder={t('Styrkeøkt')}
        onChange={(e) => { const v = e.target.value; edit((c) => { c.title = v }) }}
      />

      {s.exercises.map((ex, ei) => {
        const sug = ex.kind === 'dropset' ? null : findSuggestion(ex.name, nextSets)
        const first = ex.sets[0]
        const target = first
          ? `${ex.sets.length} × ${first.reps || '–'}${first.weightKg ? ` · ${String(first.weightKg).replace('.', lang === 'en' ? '.' : ',')} kg` : ''}`
          : ''
        const open = (kind) => panel?.ei === ei && panel.kind === kind
        const toggle = (kind) => setPanel(open(kind) ? null : { ei, kind })
        return (
          <div className="live-ex" key={ei}>
            <div className="live-ex-head">
              <span className="live-ex-name">
                <strong>{ex.name}{ex.kind === 'dropset' && <span className="muted"> · {t('dropsett')}</span>}</strong>
                <span className="muted live-target">{target}</span>
              </span>
              <button className="del" aria-label={t('Fjern øvelse')}
                      onClick={() => edit((c) => { c.exercises.splice(ei, 1) })}>✕</button>
            </div>
            <div className="live-actions-bar">
              {sug && (
                <button className={`live-action ${open('tip') ? 'on' : ''}`} aria-expanded={open('tip')} onClick={() => toggle('tip')}>
                  💡 {t('Tips')}
                </button>
              )}
              <button className="live-action" onClick={() => setSheet({ ei, kind: 'history' })}>🕘 {t('Historikk')}</button>
              <button className="live-action" onClick={() => setSheet({ ei, kind: 'swap' })}>⇄ {t('Bytt')}</button>
              <button className={`live-action ${open('note') || ex.note ? 'on' : ''}`} aria-expanded={open('note')} onClick={() => toggle('note')}>
                ✎ {t('Notat')}
              </button>
            </div>
            {sug && open('tip') && (
              <p className="live-hint muted">
                📈 {sug.lastWeightKg > 0 ? `${fmtKg(sug.lastWeightKg)} kg` : t('kroppsvekt')} × {sug.lastReps.join(', ')} → {sug.reason}
              </p>
            )}
            {open('note') && (
              <textarea className="live-note" rows="2" maxLength={300} value={ex.note || ''}
                        placeholder={t('F.eks. «setehøyde 4, smal grep»')}
                        onChange={(e) => { const v = e.target.value; edit((c) => { c.exercises[ei].note = v }) }} />
            )}
            {!open('note') && ex.note && <p className="muted live-note-preview">✎ {ex.note}</p>}
            <div className="live-grid live-grid-head" aria-hidden="true">
              <span>{t('Sett')}</span><span>kg</span><span>{t('Reps')}</span><span>✓</span>
            </div>
            {ex.sets.map((set, si) => (
              <div className={`live-grid live-set ${set.done ? 'done' : ''} ${current === `${ei}-${si}` ? 'current' : ''}`} key={si}>
                {prSets.has(`${ei}-${si}`)
                  ? <span className="live-set-no live-pr" role="img" aria-label={t('Ny rekord')}>🏆</span>
                  : <span className="live-set-no">{si + 1}</span>}
                <input
                  inputMode="decimal"
                  aria-label={t('Vekt (kg)')}
                  placeholder="–"
                  value={set.weightKg}
                  onChange={(e) => { const v = e.target.value; edit((c) => { c.exercises[ei].sets[si].weightKg = v }) }}
                />
                <input
                  inputMode="numeric"
                  aria-label={t('Reps')}
                  placeholder="–"
                  value={set.reps}
                  onChange={(e) => { const v = e.target.value; edit((c) => { c.exercises[ei].sets[si].reps = v }) }}
                />
                <button
                  className="live-check"
                  aria-label={set.done ? t('Angre sett') : t('Fullfør sett')}
                  aria-pressed={set.done}
                  onClick={() => toggleSet(ei, si)}
                >✓</button>
              </div>
            ))}
            <div className="live-ex-actions">
              <button
                className="mini"
                onClick={() => edit((c) => {
                  const sets = c.exercises[ei].sets
                  const last = sets[sets.length - 1]
                  sets.push({ reps: last?.reps ?? '', weightKg: last?.weightKg ?? '', done: false })
                })}
              >{t('+ Sett')}</button>
              {ex.sets.length > 1 && !ex.sets[ex.sets.length - 1].done && (
                <button className="mini" onClick={() => edit((c) => { c.exercises[ei].sets.pop() })}>{t('− Sett')}</button>
              )}
            </div>
          </div>
        )
      })}

      <button className="live-add-btn" onClick={() => setPicking(true)}>＋ {t('Legg til øvelse')}</button>

      <button className="live-cancel" onClick={() => setConfirming('discard')} disabled={busy}>{t('Avbryt økt uten å lagre')}</button>

      {picking && (
        <ExercisePicker
          title={t('Legg til øvelse')}
          mine={(nextSets || []).map((x) => x.exercise).filter((n) => !inSession.has(exerciseKey(n)))}
          liked={(memory?.liked || []).filter((n) => !inSession.has(exerciseKey(n)))}
          disliked={memory?.disliked}
          onPick={(name) => { addExercise(name); setPicking(false) }}
          onClose={() => setPicking(false)}
        />
      )}

      {sheet?.kind === 'history' && s.exercises[sheet.ei] && (
        <ExerciseHistory
          name={s.exercises[sheet.ei].name}
          planTitle={s.title}
          workouts={workouts}
          fmtKg={fmtKg}
          onClose={() => setSheet(null)}
        />
      )}

      {sheet?.kind === 'swap' && s.exercises[sheet.ei] && (
        <ExercisePicker
          title={t('Bytt {name}', { name: s.exercises[sheet.ei].name })}
          recommended={sameGroup(s.exercises[sheet.ei].name).filter((n) => !inSession.has(exerciseKey(n)))}
          mine={(nextSets || []).map((x) => x.exercise).filter((n) => !inSession.has(exerciseKey(n)))}
          liked={(memory?.liked || []).filter((n) => !inSession.has(exerciseKey(n)))}
          disliked={memory?.disliked}
          onClose={() => setSheet(null)}
          onPick={(name) => { swapExercise(sheet.ei, name); setSheet(null) }}
        />
      )}

      {confirming && (
        <div className="sheet-backdrop" onClick={() => setConfirming(null)}>
          <div className="sheet confirm-sheet" role="alertdialog" onClick={(e) => e.stopPropagation()}>
            <span className="sheet-handle" aria-hidden="true" />
            <p className="confirm-text">
              {confirming === 'discard'
                ? t('Avbryte økta? Avhukede sett blir ikke lagret.')
                : t('Avslutte økta? {n} sett er ikke huket av og lagres ikke.', { n: allSets.length - doneCount })}
            </p>
            <button
              className={confirming === 'discard' ? 'confirm-danger' : 'primary'}
              onClick={() => { const c = confirming; setConfirming(null); if (c === 'discard') onCancel(); else finish() }}
            >{confirming === 'discard' ? t('Avbryt økt') : t('Avslutt og lagre')}</button>
            <button className="confirm-keep" onClick={() => setConfirming(null)}>{t('Fortsett økta')}</button>
          </div>
        </div>
      )}

      <div className={`live-rest ${restLeft === 0 ? 'over' : ''} ${restLeft ? 'running' : ''}`}>
        {restLeft === null && <span>{t('Pause')}: {fmtClock(s.restSec)}</span>}
        {restLeft > 0 && <span className="live-rest-time">{t('Pause')} {fmtClock(restLeft)}</span>}
        {restLeft === 0 && <span className="live-rest-time">{t('Pause over – neste sett! 💪')}</span>}
        <span className="live-rest-btns">
          <button onClick={() => adjustRest(-15)} aria-label={t('15 sekunder kortere')}>−15</button>
          <button onClick={() => adjustRest(15)} aria-label={t('15 sekunder lengre')}>+15</button>
          {restLeft !== null && <button onClick={() => edit((c) => { c.restEnd = null })}>{t('Ferdig')}</button>}
        </span>
      </div>
    </div>
  )
}
