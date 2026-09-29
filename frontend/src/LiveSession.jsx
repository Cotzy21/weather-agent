import { useState, useEffect, useRef } from 'react'
import { useI18n } from './i18n.jsx'
import { saveLive, liveExercise, findSuggestion, toContent } from './liveSession.js'

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

export default function LiveSession({ userId, initial, nextSets, fmtKg, onFinish, onCancel }) {
  const { t } = useI18n()
  const [s, setS] = useState(initial)
  const [now, setNow] = useState(() => Date.now())
  const [newName, setNewName] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const audio = useRef(null)
  const alerted = useRef(null)

  useEffect(() => { saveLive(userId, s) }, [userId, s])

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
    edit((c) => {
      const set = c.exercises[ei].sets[si]
      set.done = !set.done
      if (set.done) c.restEnd = start + c.restSec * 1000
    })
  }

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
    setNewName('')
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

  function cancel() {
    if (window.confirm(t('Avbryte økta? Avhukede sett blir ikke lagret.'))) onCancel()
  }

  const allSets = s.exercises.flatMap((e) => e.sets)
  const doneCount = allSets.filter((x) => x.done).length
  const inSession = new Set(s.exercises.map((e) => e.name.trim().toLowerCase()))
  const quickAdd = (nextSets || []).filter((x) => !inSession.has(x.exercise.toLowerCase()))

  return (
    <div className="live">
      <div className="live-head">
        <input
          className="live-title"
          value={s.title}
          placeholder={t('Styrkeøkt')}
          onChange={(e) => { const v = e.target.value; edit((c) => { c.title = v }) }}
        />
        <div className="live-stats">
          <span>⏱ {fmtClock(Math.max(0, Math.floor((now - s.startedAt) / 1000)))}</span>
          <span>{t('{done}/{total} sett', { done: doneCount, total: allSets.length })}</span>
        </div>
      </div>

      {s.exercises.map((ex, ei) => {
        const sug = ex.kind === 'dropset' ? null : findSuggestion(ex.name, nextSets)
        return (
          <div className="live-ex" key={ei}>
            <div className="live-ex-head">
              <strong>{ex.name}{ex.kind === 'dropset' && <span className="muted"> · {t('dropsett')}</span>}</strong>
              <button
                className="del"
                aria-label={t('Fjern øvelse')}
                onClick={() => edit((c) => { c.exercises.splice(ei, 1) })}
              >✕</button>
            </div>
            {sug && (
              <p className="live-hint muted">
                📈 {sug.nextWeightKg > 0 ? `${fmtKg(sug.nextWeightKg)} kg` : t('kroppsvekt')} × {sug.nextReps} · {sug.reason}
              </p>
            )}
            {ex.sets.map((set, si) => (
              <div className={`live-set ${set.done ? 'done' : ''}`} key={si}>
                <span className="live-set-no">{si + 1}</span>
                <input
                  inputMode="decimal"
                  aria-label={t('Vekt (kg)')}
                  placeholder="kg"
                  value={set.weightKg}
                  onChange={(e) => { const v = e.target.value; edit((c) => { c.exercises[ei].sets[si].weightKg = v }) }}
                />
                <span className="muted">kg ×</span>
                <input
                  inputMode="numeric"
                  aria-label={t('Reps')}
                  placeholder="reps"
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

      <div className="live-add">
        <form onSubmit={(e) => { e.preventDefault(); addExercise(newName) }}>
          <input
            list="exercises"
            placeholder={t('Legg til øvelse …')}
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
          />
          <button type="submit" disabled={!newName.trim()}>{t('Legg til')}</button>
        </form>
        {quickAdd.length > 0 && (
          <div className="live-chips">
            {quickAdd.map((x) => (
              <button key={x.exercise} className="type-chip" onClick={() => addExercise(x.exercise)}>+ {x.exercise}</button>
            ))}
          </div>
        )}
      </div>

      {error && <p className="error">{error}</p>}
      <div className="live-actions">
        <button className="primary" disabled={busy} onClick={finish}>{busy ? t('Lagrer …') : t('Fullfør økt')}</button>
        <button onClick={cancel} disabled={busy}>{t('Avbryt')}</button>
      </div>

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
