import { useState, useEffect } from 'react'
import { authHeaders } from './supabase'
import { apiUrl, readError, cachedGet, getCached } from './api'
import MusclePicker from './MusclePicker'
import { useReveal } from './anim'
import { useI18n } from './i18n.jsx'

const TYPES = [
  { v: 'STYRKE', t: '🏋️ Styrke' },
  { v: 'LØPING', t: '🏃 Løping' },
  { v: 'SVØMMING', t: '🏊 Svømming' },
  { v: 'SYKKEL', t: '🚴 Sykkel' },
  { v: 'BULDRING', t: '🧗 Buldring' },
  { v: 'HIKING', t: '🥾 Hiking' },
  { v: 'FRISTIL', t: '✨ Fristil' },
]
const CARDIO = ['LØPING', 'SVØMMING', 'SYKKEL']

const EXERCISES = [
  // Bryst
  'Benkpress', 'Skråbenkpress', 'Hantelpress', 'Flies', 'Dips', 'Push-ups',
  // Rygg
  'Markløft', 'Rumensk markløft', 'Nedtrekk', 'Stående roing', 'Sittende roing',
  'Pull-ups', 'Chins', 'T-bar roing', 'Face pulls',
  // Bein
  'Knebøy', 'Frontbøy', 'Leg press', 'Utfall', 'Bulgarske utfall', 'Leg extension',
  'Leg curl', 'Tåhev', 'Hip thrust',
  // Skuldre
  'Skulderpress', 'Sidehev', 'Fronthev', 'Bakre flies', 'Arnold press', 'Opprekk',
  // Armer
  'Bicepscurl', 'Hammercurl', 'Konsentrasjonscurl', 'Triceps pushdown',
  'Triceps extension', 'Skullcrushers',
  // Mage
  'Planke', 'Sit-ups', 'Russian twists', 'Hanging leg raise', 'Cable crunch',
  // Helkropp
  'Kettlebell swing', 'Clean and press', 'Thruster', 'Burpees', 'Mountain climbers',
]

const today = () => new Date().toISOString().slice(0, 10)
const newSet = () => ({ reps: '', weightKg: '' })
const newExercise = () => ({ kind: 'exercise', name: '', sets: [newSet()] })
const newDropset = () => ({ kind: 'dropset', name: '', drops: [newSet()] })
// Supersett har runder (à la Garmins «Repeat x Sets»): hele gruppa gjentas N ganger.
const newSuperset = () => ({ kind: 'superset', rounds: 3, exercises: [{ name: '', sets: [newSet()] }] })

function cleanSets(arr) {
  return (arr || [])
    .filter((s) => s.reps !== '' && s.reps != null)
    .map((s) => ({ reps: Number(s.reps), weightKg: Number(s.weightKg) || 0 }))
}

// Fra AI-forslag (tall) til byggerens redigerbare felter.
function mapSets(arr) {
  const out = (arr || []).map((s) => ({ reps: s.reps ?? '', weightKg: s.weightKg ?? '' }))
  return out.length ? out : [newSet()]
}

export default function TrainingView({ session }) {
  const { t } = useI18n()
  const [type, setType] = useState('STYRKE')
  const [date, setDate] = useState(today())
  const [title, setTitle] = useState('')
  const [notes, setNotes] = useState('')
  const [blocks, setBlocks] = useState([newExercise()])
  const [cardio, setCardio] = useState({ distanceKm: '', durationMin: '', ascentM: '' })

  // Hydrer fra cachen (delt nøkkel med Hjem) så fanen tegnes med data straks.
  const [workouts, setWorkouts] = useState(() => getCached('/api/treningsokter') ?? [])
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)
  const [saved, setSaved] = useState(false) // kort kvittering etter lagring

  // Drag-and-drop-omorganisering av blokker (pilene finnes fortsatt for touch).
  const [dragIdx, setDragIdx] = useState(null)
  const [dragOver, setDragOver] = useState(null)

  // Garmin-import (CSV-eksport fra Garmin Connect).
  const [importBusy, setImportBusy] = useState(false)
  const [importMsg, setImportMsg] = useState(null)

  const [progressName, setProgressName] = useState('')
  const [progress, setProgress] = useState(null)

  const [aiFocus, setAiFocus] = useState('')
  const [aiBusy, setAiBusy] = useState(false)
  const [aiError, setAiError] = useState(null)
  const [suggestion, setSuggestion] = useState(null)

  const [plans, setPlans] = useState(() => getCached('/api/trening/planer') ?? [])

  useEffect(() => {
    if (session) { loadWorkouts(); loadPlans() }
    else { setWorkouts([]); setPlans([]) }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session])

  async function loadWorkouts() {
    try {
      setWorkouts(await cachedGet('/api/treningsokter', await authHeaders()))
    } catch { /* sekundært – behold cachet */ }
  }

  async function loadPlans() {
    try {
      setPlans(await cachedGet('/api/trening/planer', await authHeaders()))
    } catch { /* sekundært – behold cachet */ }
  }

  // Lagre AI-forslaget i profilen så det kan gjenbrukes senere.
  async function savePlan(s) {
    setAiError(null)
    try {
      const res = await fetch(apiUrl('/api/trening/planer'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...(await authHeaders()) },
        body: JSON.stringify({ title: s.title, type: s.type, content: s.content, rationale: s.rationale || null }),
      })
      if (!res.ok) throw new Error(await readError(res))
      setSuggestion(null)
      loadPlans()
    } catch (e) {
      setAiError(e.message)
    }
  }

  async function deletePlan(id) {
    try {
      await fetch(apiUrl(`/api/trening/planer/${id}`), { method: 'DELETE', headers: await authHeaders() })
      loadPlans()
    } catch { /* ignorer */ }
  }

  // Dyp, immutabel redigering av blocks via en mutator på en kopi.
  function editBlocks(mutator) {
    setBlocks((bs) => { const copy = structuredClone(bs); mutator(copy); return copy })
  }

  // Flytt en blokk opp (-1) eller ned (+1) i økta, som i Garmin-appen.
  function moveBlock(index, delta) {
    editBlocks((c) => {
      const to = index + delta
      if (to < 0 || to >= c.length) return
      const [moved] = c.splice(index, 1)
      c.splice(to, 0, moved)
    })
  }

  // Slipp en dratt blokk på plassen til `target`.
  function dropBlock(target) {
    if (dragIdx != null && dragIdx !== target) {
      editBlocks((c) => {
        const [moved] = c.splice(dragIdx, 1)
        c.splice(target, 0, moved)
      })
    }
    setDragIdx(null)
    setDragOver(null)
  }

  function buildContent() {
    if (type === 'STYRKE') {
      const out = blocks.map((b) => {
        if (b.kind === 'superset') {
          return {
            kind: 'superset',
            rounds: Math.max(1, Number(b.rounds) || 1),
            exercises: b.exercises
              .filter((e) => e.name.trim())
              .map((e) => ({ name: e.name.trim(), sets: cleanSets(e.sets) })),
          }
        }
        const key = b.kind === 'dropset' ? 'drops' : 'sets'
        return { kind: b.kind, name: b.name.trim(), [key]: cleanSets(b[key]) }
      }).filter((b) => (b.kind === 'superset' ? b.exercises.length : b.name))
      return { blocks: out }
    }
    const num = (x) => (x === '' ? undefined : Number(x))
    if (type === 'HIKING') return { distanceKm: num(cardio.distanceKm), durationMin: num(cardio.durationMin), ascentM: num(cardio.ascentM) }
    if (CARDIO.includes(type)) return { distanceKm: num(cardio.distanceKm), durationMin: num(cardio.durationMin) }
    return { durationMin: num(cardio.durationMin) } // FRISTIL / BULDRING
  }

  async function submit() {
    if (!title.trim()) { setError(t('Gi økta en tittel.')); return }
    setBusy(true)
    setError(null)
    try {
      const res = await fetch(apiUrl('/api/treningsokter'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...(await authHeaders()) },
        body: JSON.stringify({ date, title: title.trim(), type, content: buildContent(), notes: notes.trim() || null }),
      })
      if (!res.ok) throw new Error(await readError(res))
      setTitle(''); setNotes(''); setBlocks([newExercise()]); setCardio({ distanceKm: '', durationMin: '', ascentM: '' })
      setSaved(true)
      setTimeout(() => setSaved(false), 2500)
      loadWorkouts()
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }

  // Les CSV-fila i nettleseren og send råteksten til backend, som parser og
  // hopper over økter som finnes fra før (idempotent).
  async function importGarmin(file) {
    if (!file) return
    setImportBusy(true)
    setImportMsg(null)
    try {
      const text = await file.text()
      const res = await fetch(apiUrl('/api/trening/import/garmin'), {
        method: 'POST',
        headers: { 'Content-Type': 'text/plain', ...(await authHeaders()) },
        body: text,
      })
      if (!res.ok) throw new Error(await readError(res))
      const r = await res.json()
      setImportMsg(r.imported === 0 && r.skipped === 0
        ? t('Fant ingen aktiviteter i fila – er det CSV-eksporten fra Garmin Connect?')
        : `✓ ${r.imported} ${t('økter importert')}${r.skipped ? ` · ${r.skipped} ${t('hoppet over (fantes fra før)')}` : ''}`)
      loadWorkouts()
    } catch (e) {
      setImportMsg(`Feil: ${e.message}`)
    } finally {
      setImportBusy(false)
    }
  }

  async function deleteWorkout(id) {
    try {
      await fetch(apiUrl(`/api/treningsokter/${id}`), { method: 'DELETE', headers: await authHeaders() })
      loadWorkouts()
    } catch { /* ignorer */ }
  }

  async function loadProgress() {
    if (!progressName.trim()) return
    try {
      const params = new URLSearchParams({ navn: progressName.trim() })
      const res = await fetch(apiUrl(`/api/ovelser/progresjon?${params}`), { headers: await authHeaders() })
      if (res.ok) setProgress(await res.json())
    } catch { setProgress([]) }
  }

  async function suggest() {
    if (!aiFocus.trim()) return
    setAiBusy(true)
    setAiError(null)
    setSuggestion(null)
    try {
      const res = await fetch(apiUrl('/api/trening/forslag'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...(await authHeaders()) },
        body: JSON.stringify({ focus: aiFocus.trim(), type: '' }),
      })
      if (!res.ok) throw new Error(await readError(res))
      setSuggestion(await res.json())
    } catch (e) {
      setAiError(e.message)
    } finally {
      setAiBusy(false)
    }
  }

  // Fra muskel-velgeren: øvelsene inn i byggeren med valgt antall sett, og
  // reps forhåndsutfylt etter fokus (styrke ~5 / volum ~10). Vekt fyller
  // brukeren inn selv.
  function applyMuscles(exerciseNames, muscleLabels, plan) {
    setType('STYRKE')
    if (!title.trim()) setTitle(muscleLabels.join(' + '))
    const bs = exerciseNames.map((name) => ({
      kind: 'exercise',
      name,
      sets: Array.from({ length: plan.sets }, () => ({ reps: String(plan.reps), weightKg: '' })),
    }))
    setBlocks((prev) => {
      const existing = prev.filter((b) => b.kind !== 'exercise' || b.name.trim())
      return [...existing, ...bs]
    })
  }

  // Fyll forslaget inn i byggeren så brukeren kan finpusse og lagre.
  function applySuggestion(s) {
    const t = s.type || 'STYRKE'
    setType(t)
    setTitle(s.title || '')
    const c = s.content || {}
    if (t === 'STYRKE') {
      const bs = (c.blocks || []).map((b) => {
        if (b.kind === 'superset') {
          return {
            kind: 'superset',
            rounds: b.rounds ?? 3,
            exercises: (b.exercises || []).map((e) => ({ name: e.name || '', sets: mapSets(e.sets) })),
          }
        }
        if (b.kind === 'dropset') {
          return { kind: 'dropset', name: b.name || '', drops: mapSets(b.drops) }
        }
        return { kind: 'exercise', name: b.name || '', sets: mapSets(b.sets) }
      })
      setBlocks(bs.length ? bs : [newExercise()])
    } else {
      setCardio({ distanceKm: c.distanceKm ?? '', durationMin: c.durationMin ?? '', ascentM: c.ascentM ?? '' })
    }
    setSuggestion(null)
  }

  const revealRef = useReveal([session])

  if (!session) {
    return (
      <div className="training" ref={revealRef}>
        <h2 className="detail-title" data-reveal>{t('Trening')}</h2>
        <p className="muted" data-reveal>
          {t('Logg inn for å lagre økter, se progresjon og få AI-forslag – men prøv gjerne kroppsmodellen:')}
        </p>
        <MusclePicker onCreate={() => {}} />
      </div>
    )
  }

  const maxW = progress && progress.length ? Math.max(...progress.map((p) => p.maxWeight)) : 0

  return (
    <div className="training" ref={revealRef}>
      <datalist id="exercises">{EXERCISES.map((e) => <option key={e} value={e} />)}</datalist>

      <MusclePicker onCreate={applyMuscles} />

      <div className="ai-panel" data-reveal>
        <span className="ai-title">{t('🤖 AI-forslag')}</span>
        <div className="ai-row">
          <input placeholder={t('Fokus, f.eks. større bein, bedre cardio, forberede BJJ')}
                 value={aiFocus} onChange={(e) => setAiFocus(e.target.value)} />
          <button className="primary" onClick={suggest} disabled={aiBusy || !aiFocus.trim()}>
            {aiBusy ? t('Tenker …') : t('Foreslå økt')}
          </button>
        </div>
        {aiError && <p className="error">{aiError}</p>}
        {suggestion && (
          <div className="ai-result">
            <strong>{suggestion.title}</strong>{' '}
            <span className="muted">({(TYPES.find((t) => t.v === suggestion.type)?.t) || suggestion.type})</span>
            {suggestion.rationale && <p className="muted">{suggestion.rationale}</p>}
            <button className="mini" onClick={() => applySuggestion(suggestion)}>{t('Bruk i bygger ↓')}</button>
            <button className="mini" onClick={() => savePlan(suggestion)}>{t('💾 Lagre som plan')}</button>
          </div>
        )}

        {plans.length > 0 && (
          <div className="plan-list">
            <span className="ai-title">{t('📋 Mine planer')}</span>
            <ul>
              {plans.map((p) => (
                <li key={p.id}>
                  <strong>{p.title}</strong>{' '}
                  <span className="muted">({(TYPES.find((t) => t.v === p.type)?.t) || p.type})</span>
                  <button className="mini" onClick={() => applySuggestion(p)}>{t('Bruk i ny økt')}</button>
                  <button className="del" onClick={() => deletePlan(p.id)} aria-label={t('Slett plan')}>✕</button>
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>

      <h2 className="detail-title" data-reveal>{t('Ny økt')}</h2>
      <div className="type-select" data-reveal>
        {TYPES.map((tp) => (
          <button key={tp.v} className={`type-chip ${type === tp.v ? 'active' : ''}`} onClick={() => setType(tp.v)}>
            {t(tp.t)}
          </button>
        ))}
      </div>

      <div className="log-meta">
        <label>{t('Dato')}<input type="date" value={date} onChange={(e) => setDate(e.target.value)} /></label>
        <label className="grow">{t('Tittel')}<input placeholder={t('f.eks. Push A / Langtur')} value={title}
               className={error === t('Gi økta en tittel.') && !title.trim() ? 'invalid' : ''}
               onChange={(e) => setTitle(e.target.value)} /></label>
      </div>

      {type === 'STYRKE' ? (
        <div className="builder">
          {blocks.map((b, bi) => (
            <div
              className={`block ${b.kind} ${dragIdx === bi ? 'dragging' : ''} ${dragOver === bi && dragIdx !== bi ? 'drag-over' : ''}`}
              key={bi}
              onDragOver={(e) => { if (dragIdx != null) { e.preventDefault(); setDragOver(bi) } }}
              onDragLeave={() => { if (dragOver === bi) setDragOver(null) }}
              onDrop={(e) => { e.preventDefault(); dropBlock(bi) }}
            >
              <div className="block-head">
                <span
                  className="block-handle"
                  title={t('Dra for å flytte')}
                  draggable
                  onDragStart={(e) => {
                    setDragIdx(bi)
                    e.dataTransfer.effectAllowed = 'move'
                    e.dataTransfer.setData('text/plain', String(bi))
                    // Vis hele blokka som drabilde, ikke bare håndtaket.
                    e.dataTransfer.setDragImage(e.currentTarget.closest('.block'), 20, 20)
                  }}
                  onDragEnd={() => { setDragIdx(null); setDragOver(null) }}
                >⣿</span>
                <span className="block-tag">{b.kind === 'dropset' ? t('Dropsett') : b.kind === 'superset' ? t('Supersett') : t('Øvelse')}</span>
                {b.kind === 'superset' && (
                  <span className="stepper small" title={t('Hvor mange runder gruppa gjentas')}>
                    <button onClick={() => editBlocks((c) => { c[bi].rounds = Math.max(1, (c[bi].rounds ?? 3) - 1) })}
                            aria-label="Færre runder">−</button>
                    <span>{b.rounds ?? 3}×</span>
                    <button onClick={() => editBlocks((c) => { c[bi].rounds = Math.min(10, (c[bi].rounds ?? 3) + 1) })}
                            aria-label="Flere runder">+</button>
                  </span>
                )}
                <span className="block-move">
                  <button className="move" disabled={bi === 0} title={t('Flytt opp')}
                          onClick={() => moveBlock(bi, -1)} aria-label="Flytt opp">▲</button>
                  <button className="move" disabled={bi === blocks.length - 1} title={t('Flytt ned')}
                          onClick={() => moveBlock(bi, 1)} aria-label="Flytt ned">▼</button>
                </span>
                <button className="del" onClick={() => editBlocks((c) => c.splice(bi, 1))} aria-label={t('Fjern')}>✕</button>
              </div>

              {b.kind === 'superset' ? (
                <>
                  {b.exercises.map((ex, ei) => (
                    <div className="ss-exercise" key={ei}>
                      <input list="exercises" placeholder={t('Øvelse')} value={ex.name}
                             onChange={(e) => editBlocks((c) => { c[bi].exercises[ei].name = e.target.value })} />
                      {ex.sets.map((s, si) => (
                        <div className="set-row" key={si}>
                          <input type="number" min="1" placeholder="reps" value={s.reps}
                                 onChange={(e) => editBlocks((c) => { c[bi].exercises[ei].sets[si].reps = e.target.value })} />
                          <input type="number" min="0" step="2.5" placeholder="kg" value={s.weightKg}
                                 onChange={(e) => editBlocks((c) => { c[bi].exercises[ei].sets[si].weightKg = e.target.value })} />
                          <button className="del" onClick={() => editBlocks((c) => { if (c[bi].exercises[ei].sets.length > 1) c[bi].exercises[ei].sets.splice(si, 1) })}>✕</button>
                        </div>
                      ))}
                      <button className="mini" onClick={() => editBlocks((c) => c[bi].exercises[ei].sets.push(newSet()))}>{t('+ sett')}</button>
                    </div>
                  ))}
                  <button className="mini" onClick={() => editBlocks((c) => c[bi].exercises.push({ name: '', sets: [newSet()] }))}>{t('+ øvelse i supersett')}</button>
                </>
              ) : (
                <>
                  <input list="exercises" placeholder={t('Øvelse')} value={b.name}
                         onChange={(e) => editBlocks((c) => { c[bi].name = e.target.value })} />
                  {(b.kind === 'dropset' ? b.drops : b.sets).map((s, si) => {
                    const key = b.kind === 'dropset' ? 'drops' : 'sets'
                    return (
                      <div className="set-row" key={si}>
                        <input type="number" min="1" placeholder={b.kind === 'dropset' ? 'reps (drop)' : 'reps'} value={s.reps}
                               onChange={(e) => editBlocks((c) => { c[bi][key][si].reps = e.target.value })} />
                        <input type="number" min="0" step="2.5" placeholder="kg" value={s.weightKg}
                               onChange={(e) => editBlocks((c) => { c[bi][key][si].weightKg = e.target.value })} />
                        <button className="del" onClick={() => editBlocks((c) => { if (c[bi][key].length > 1) c[bi][key].splice(si, 1) })}>✕</button>
                      </div>
                    )
                  })}
                  <button className="mini" onClick={() => editBlocks((c) => c[bi][b.kind === 'dropset' ? 'drops' : 'sets'].push(newSet()))}>
                    {b.kind === 'dropset' ? t('+ drop') : t('+ sett')}
                  </button>
                </>
              )}
            </div>
          ))}

          <div className="builder-add">
            <button onClick={() => setBlocks((bs) => [...bs, newExercise()])}>{t('+ Øvelse')}</button>
            <button onClick={() => setBlocks((bs) => [...bs, newDropset()])}>{t('+ Dropsett')}</button>
            <button onClick={() => setBlocks((bs) => [...bs, newSuperset()])}>{t('+ Supersett')}</button>
          </div>
        </div>
      ) : (
        <div className="cardio-inputs">
          {(CARDIO.includes(type) || type === 'HIKING') && (
            <label>{t('Distanse (km)')}<input type="number" min="0" step="0.1" value={cardio.distanceKm}
                   onChange={(e) => setCardio({ ...cardio, distanceKm: e.target.value })} /></label>
          )}
          <label>{t('Varighet (min)')}<input type="number" min="0" value={cardio.durationMin}
                 onChange={(e) => setCardio({ ...cardio, durationMin: e.target.value })} /></label>
          {type === 'HIKING' && (
            <label>{t('Stigning (m)')}<input type="number" min="0" value={cardio.ascentM}
                   onChange={(e) => setCardio({ ...cardio, ascentM: e.target.value })} /></label>
          )}
        </div>
      )}

      <label className="notes-label">{t('Notater')}
        <textarea rows="2" placeholder={t('Valgfritt')} value={notes} onChange={(e) => setNotes(e.target.value)} />
      </label>

      <div className="log-actions">
        <button className="primary" onClick={submit} disabled={busy}>{busy ? t('Lagrer …') : t('Lagre økt')}</button>
        {saved && <span className="success">{t('✓ Økt lagret')}</span>}
      </div>
      {error && <p className="error">{error}</p>}

      <h3 className="detail-h3">{t('Progresjon (styrke)')}</h3>
      <div className="progress-search">
        <input list="exercises" placeholder={t('Øvelse, f.eks. Benkpress')} value={progressName} onChange={(e) => setProgressName(e.target.value)} />
        <button onClick={loadProgress}>{t('Vis')}</button>
      </div>
      {progress && (progress.length === 0
        ? <p className="muted">{t('Ingen logget for denne øvelsen ennå.')}</p>
        : (
          <div className="progress">
            {progress.map((p, i) => (
              <div className="prog-row" key={i}>
                <span className="prog-date">{p.date}</span>
                <span className="prog-bar"><span style={{ width: `${maxW ? (p.maxWeight / maxW) * 100 : 0}%` }} /></span>
                <span className="prog-val">{p.maxWeight.toFixed(1)} kg</span>
              </div>
            ))}
          </div>
        ))}

      <div className="import-panel">
        <span className="ai-title">{t('⌚ Importer fra Garmin')}</span>
        <p className="muted import-hint">
          {t('Garmin Connect → Aktiviteter → Alle aktiviteter → «Eksporter CSV», og velg fila her.')}
          Øktene dine havner i dagboka med ekte kalorier, og dagsbalansen bruker dem automatisk.
        </p>
        <label className={`file-btn ${importBusy ? 'busy' : ''}`}>
          {importBusy ? t('Importerer …') : t('📂 Velg CSV-fil')}
          <input type="file" accept=".csv,text/csv" hidden disabled={importBusy}
                 onChange={(e) => { importGarmin(e.target.files[0]); e.target.value = '' }} />
        </label>
        {importMsg && (
          <p className={importMsg.startsWith('✓') ? 'success' : importMsg.startsWith('Feil') ? 'error' : 'muted'}>
            {importMsg}
          </p>
        )}
      </div>

      <h3 className="detail-h3">{t('Tidligere økter')}</h3>
      {workouts.length === 0 ? (
        <p className="muted">{t('Ingen økter ennå.')}</p>
      ) : (
        <div className="workout-groups">
          {groupByType(workouts).map((g) => (
            <WorkoutGroup key={g.type} group={g} onDelete={deleteWorkout} />
          ))}
        </div>
      )}
    </div>
  )
}

// Grupper øktene etter type, i TYPES-rekkefølgen, og ta bare med typer som
// faktisk finnes (ingen tomme containere). Ukjente typer havner sist.
function groupByType(workouts) {
  const order = TYPES.map((tp) => tp.v)
  const present = [...new Set(workouts.map((w) => w.type))]
    .sort((a, b) => {
      const ia = order.indexOf(a); const ib = order.indexOf(b)
      return (ia < 0 ? 99 : ia) - (ib < 0 ? 99 : ib)
    })
  return present.map((type) => ({
    type,
    label: TYPES.find((tp) => tp.v === type)?.t || type,
    items: workouts.filter((w) => w.type === type),
  }))
}

// Én sammenleggbar container per treningstype (Garmin-stil kategorier).
function WorkoutGroup({ group, onDelete }) {
  const { t } = useI18n()
  const [open, setOpen] = useState(false)
  const latest = group.items[0]?.date
  return (
    <div className="workout-group">
      <button className="workout-group-head" onClick={() => setOpen(!open)}>
        <span className="wg-title">{t(group.label)}</span>
        <span className="wg-count">{group.items.length}</span>
        {latest && <span className="wg-latest muted">{t('sist')} {latest}</span>}
        <span className="wg-chevron">{open ? '▾' : '▸'}</span>
      </button>
      {open && (
        <div className="workouts">
          {group.items.map((w) => (
            <div className="workout" key={w.id}>
              <div className="workout-head">
                <strong>{w.title}</strong>
                <span className="muted">{w.date}</span>
                <button className="del" onClick={() => onDelete(w.id)} aria-label={t('Slett')}>✕</button>
              </div>
              <WorkoutBody workout={w} />
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

const sumSets = (arr) => (arr || []).map((s) => `${s.reps}×${s.weightKg}`).join(', ')

function WorkoutBody({ workout: w }) {
  const c = w.content || {}
  if (w.type === 'STYRKE') {
    return (
      <ul>
        {(c.blocks || []).map((b, i) => (
          <li key={i}>
            {b.kind === 'superset'
              ? <>Supersett{(b.rounds ?? 1) > 1 ? ` ×${b.rounds}` : ''}: {(b.exercises || []).map((e) => `${e.name} (${sumSets(e.sets)})`).join(' + ')}</>
              : <>{b.name} – {b.kind === 'dropset' ? `dropsett: ${sumSets(b.drops)}` : sumSets(b.sets)}</>}
          </li>
        ))}
      </ul>
    )
  }
  const bits = []
  if (c.distanceKm != null) bits.push(`${c.distanceKm} km`)
  if (c.durationMin != null) bits.push(`${c.durationMin} min`)
  if (c.ascentM != null) bits.push(`${c.ascentM} m stigning`)
  return <p className="muted">{bits.join(' · ') || '—'}{w.notes ? ` · ${w.notes}` : ''}</p>
}
