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
  const [aiPlan, setAiPlan] = useState(null)   // planen når assistenten har levert den
  const [aiSaving, setAiSaving] = useState(false)
  const [aiMessages, setAiMessages] = useState([]) // samtalen med assistenten

  // Training-fanen har to moduser: 'overview' (landing med oppsummering + logg)
  // og 'builder' (der man faktisk lager/logger en økt). Bygg-fra-kroppen vises
  // bare i byggeren, ikke som det første man møter.
  const [mode, setMode] = useState('overview')

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

  // Aksepter planen: lagre HVER økt som en gjenbrukbar plan i profilen.
  async function acceptPlan() {
    if (!aiPlan) return
    setAiSaving(true)
    setAiError(null)
    try {
      for (const w of aiPlan.workouts) {
        const res = await fetch(apiUrl('/api/trening/planer'), {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', ...(await authHeaders()) },
          body: JSON.stringify({ title: w.title, type: w.type, content: w.content, rationale: w.rationale || null }),
        })
        if (!res.ok) throw new Error(await readError(res))
      }
      setAiPlan(null)
      setAiFocus('')
      setAiMessages([])
      loadPlans()
    } catch (e) {
      setAiError(e.message)
    } finally {
      setAiSaving(false)
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
      setMode('overview') // tilbake til oversikten, der den nye økta + oppsummeringen vises
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

  // Send én melding i samtalen med assistenten. Svaret er ENTEN oppfølgings-
  // spørsmål (vises i chatten) ELLER en ferdig plan (vises med aksepter/forkast).
  async function sendMessage() {
    const text = aiFocus.trim()
    if (!text || aiBusy) return
    const next = [...aiMessages, { role: 'user', content: text }]
    setAiMessages(next)
    setAiFocus('')
    setAiBusy(true)
    setAiError(null)
    try {
      const res = await fetch(apiUrl('/api/trening/assistent'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...(await authHeaders()) },
        // Fold assistentens oppfølgingsspørsmål inn i innholdet som sendes tilbake,
        // ellers «glemmer» modellen hva den nettopp spurte om.
        body: JSON.stringify({
          messages: next.map((m) => ({
            role: m.role,
            content: m.questions?.length ? `${m.content}\n${m.questions.join('\n')}` : m.content,
          })),
        }),
      })
      if (!res.ok) throw new Error(await readError(res))
      const data = await res.json()
      setAiMessages([...next, { role: 'assistant', content: data.reply, questions: data.questions || [] }])
      setAiPlan(data.plan || null)
    } catch (e) {
      setAiError(e.message)
    } finally {
      setAiBusy(false)
    }
  }

  function resetChat() {
    setAiMessages([]); setAiPlan(null); setAiError(null); setAiFocus('')
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
    setMode('builder') // ta brukeren til byggeren med økta forhåndsutfylt
  }

  const revealRef = useReveal([session, mode])

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
  const summaries = computeSummaries(workouts)

  return (
    <div className="training" ref={revealRef}>
      <datalist id="exercises">{EXERCISES.map((e) => <option key={e} value={e} />)}</datalist>

      {mode === 'overview' && (
        <>
          <h2 className="detail-title" data-reveal>{t('Trening')}</h2>
          <button className="create-workout" data-reveal onClick={() => setMode('builder')}>
            <span className="cw-icon">➕</span>
            <span className="cw-text">
              <strong>{t('Lag en økt')}</strong>
              <span className="muted">{t('Bygg fra kroppen, en mal, eller fra bunnen av')}</span>
            </span>
            <span className="cw-arrow">→</span>
          </button>
        </>
      )}

      {mode === 'builder' && (
        <>
          <button className="back" data-reveal onClick={() => setMode('overview')}>← {t('Til oversikt')}</button>
          <MusclePicker onCreate={applyMuscles} />
        </>
      )}

      {mode === 'overview' && (
      <div className="ai-panel" data-reveal>
        <div className="ai-panel-head">
          <span className="ai-title">{t('🤖 AI-treningsassistent')}</span>
          {aiMessages.length > 0 && (
            <button className="mini" onClick={resetChat} disabled={aiBusy || aiSaving}>{t('Ny samtale')}</button>
          )}
        </div>

        {aiMessages.length === 0 && (
          <p className="muted ai-intro">{t('Be om et opplegg – f.eks. «lag en push pull legs split» eller «jeg vil begynne med mer cardio». Assistenten spør om det trenger mer, og bruker historikken din til progressiv overload.')}</p>
        )}

        {aiMessages.map((m, i) => (
          <div key={i} className={`ai-msg ${m.role}`}>
            {m.content && <p className="ai-msg-text">{m.content}</p>}
            {m.questions?.length > 0 && (
              <ul className="ai-questions">{m.questions.map((q, qi) => <li key={qi}>{q}</li>)}</ul>
            )}
          </div>
        ))}
        {aiBusy && <p className="muted typing">{t('Assistenten tenker')} <span>·</span><span>·</span><span>·</span></p>}

        <div className="ai-row">
          <input placeholder={aiMessages.length === 0
            ? t('Beskriv ønsket, f.eks. «push pull legs split»')
            : t('Svar assistenten …')}
                 value={aiFocus} onChange={(e) => setAiFocus(e.target.value)}
                 onKeyDown={(e) => { if (e.key === 'Enter') sendMessage() }} />
          <button className="primary" onClick={sendMessage} disabled={aiBusy || !aiFocus.trim()}>
            {t('Send')}
          </button>
        </div>
        {aiError && <p className="error">{aiError}</p>}

        {aiPlan && (
          <div className="ai-plan">
            <div className="ai-plan-head">
              <strong>{aiPlan.title}</strong>
              <span className="ai-plan-count muted">{t('{n} økter', { n: aiPlan.workouts.length })}</span>
            </div>
            {aiPlan.summary && <p className="muted ai-plan-summary">{aiPlan.summary}</p>}

            {aiPlan.workouts.map((w, i) => (
              <div className="ai-workout" key={i}>
                <div className="ai-workout-head">
                  {w.content?.day && <span className="ai-workout-day">{w.content.day}</span>}
                  <strong>{w.title}</strong>
                  <span className="ai-workout-type">{t((TYPES.find((tp) => tp.v === w.type)?.t) || w.type)}</span>
                  <button className="mini" onClick={() => applySuggestion(w)}>{t('Til bygger ↓')}</button>
                </div>
                {w.rationale && <p className="muted ai-workout-why">{w.rationale}</p>}
                <WorkoutBody workout={w} />
              </div>
            ))}

            <div className="ai-plan-actions">
              <button className="primary" onClick={acceptPlan} disabled={aiSaving}>
                {aiSaving ? t('Lagrer …') : t('✓ Aksepter og lagre planen')}
              </button>
              <button className="mini" onClick={() => setAiPlan(null)} disabled={aiSaving}>{t('Forkast')}</button>
            </div>
          </div>
        )}

        {plans.length > 0 && (
          <div className="plan-list">
            <span className="ai-title">{t('📋 Mine planer')}</span>
            <ul>
              {plans.map((p) => (
                <li key={p.id}>
                  {p.content?.day && <span className="plan-day">{p.content.day}</span>}
                  <strong>{p.title}</strong>{' '}
                  <span className="muted">({t((TYPES.find((tp) => tp.v === p.type)?.t) || p.type)})</span>
                  <button className="mini" onClick={() => applySuggestion(p)}>{t('Bruk i ny økt')}</button>
                  <button className="del" onClick={() => deletePlan(p.id)} aria-label={t('Slett plan')}>✕</button>
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>
      )}

      {mode === 'builder' && (
      <>
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
      </>
      )}

      {mode === 'overview' && (
      <>
      <h3 className="detail-h3" data-reveal>{t('Oppsummering (siste 7 dager)')}</h3>
      {summaries.length > 0 ? (
        <div className="train-summary" data-reveal>
          {summaries.map((s) => <SummaryCard key={s.type} s={s} />)}
        </div>
      ) : (
        <p className="muted" data-reveal>{t('Logg noen økter, så ser du progresjonen din her.')}</p>
      )}

      <div className="import-panel">
        <span className="ai-title">{t('⌚ Importer fra Garmin')}</span>
        <p className="muted import-hint">
          {t('Garmin Connect → Aktiviteter → Alle aktiviteter → «Eksporter CSV», og velg fila her.')}
          {' '}{t('Øktene dine havner i dagboka med ekte kalorier, og dagsbalansen bruker dem automatisk.')}
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
      </>
      )}
    </div>
  )
}

const CARDIO_TYPES = ['LØPING', 'SYKKEL', 'SVØMMING', 'HIKING']

// Samlet styrkevolum (Σ reps×kg) i en økt, inkl. supersett-runder.
function strengthVolume(content) {
  let vol = 0
  for (const b of content?.blocks ?? []) {
    if (b.kind === 'superset') {
      let sv = 0
      for (const ex of b.exercises ?? []) {
        for (const s of ex.sets ?? []) sv += (Number(s.reps) || 0) * (Number(s.weightKg) || 0)
      }
      vol += sv * (Number(b.rounds) || 1)
    } else {
      const sets = b.kind === 'dropset' ? (b.drops ?? []) : (b.sets ?? [])
      for (const s of sets) vol += (Number(s.reps) || 0) * (Number(s.weightKg) || 0)
    }
  }
  return vol
}

// Per-sport-oppsummering: siste 7 dager mot de 7 før, med den relevante
// metrikken (styrke = volum i kg, kondisjon = km, ellers antall økter).
function computeSummaries(workouts) {
  const dayMs = 86400000
  const now = new Date(); now.setHours(0, 0, 0, 0)
  const thisStart = now.getTime() - 6 * dayMs
  const prevStart = now.getTime() - 13 * dayMs
  const prevEnd = now.getTime() - 7 * dayMs
  const at = (w) => new Date(`${w.date}T00:00:00`).getTime()
  const metric = (w) => {
    if (w.type === 'STYRKE') return strengthVolume(w.content)
    if (CARDIO_TYPES.includes(w.type)) return Number(w.content?.distanceKm) || 0
    return 1 // antall økter for øvrige typer
  }
  const order = TYPES.map((tp) => tp.v)
  const present = [...new Set(workouts.map((w) => w.type))]
    .sort((a, b) => (order.indexOf(a) < 0 ? 99 : order.indexOf(a)) - (order.indexOf(b) < 0 ? 99 : order.indexOf(b)))

  return present.map((type) => {
    const items = workouts.filter((w) => w.type === type)
    const thisItems = items.filter((w) => at(w) >= thisStart)
    const sum = (arr) => arr.reduce((s, w) => s + metric(w), 0)
    const thisVal = sum(thisItems)
    const prevVal = sum(items.filter((w) => at(w) >= prevStart && at(w) <= prevEnd))
    const kind = type === 'STYRKE' ? 'volume' : CARDIO_TYPES.includes(type) ? 'distance' : 'sessions'
    const deltaPct = prevVal > 0 ? Math.round(((thisVal - prevVal) / prevVal) * 100) : null
    return {
      type,
      label: TYPES.find((tp) => tp.v === type)?.t || type,
      sessions: thisItems.length,
      total: items.length,
      thisVal,
      kind,
      deltaPct,
    }
  })
}

// Ett oppsummeringskort per sport, med hovedtall + trend mot forrige uke.
function SummaryCard({ s }) {
  const { t } = useI18n()
  const value = s.kind === 'volume'
    ? `${Math.round(s.thisVal).toLocaleString('nb-NO')} kg`
    : s.kind === 'distance'
      ? `${s.thisVal.toFixed(1)} km`
      : `${s.sessions}`
  const metricLabel = s.kind === 'volume' ? t('volum denne uka')
    : s.kind === 'distance' ? t('denne uka')
      : t('økter denne uka')
  const trendCls = s.deltaPct == null ? '' : s.deltaPct > 0 ? 'up' : s.deltaPct < 0 ? 'down' : ''
  return (
    <div className="summary-card">
      <span className="sc-label">{t(s.label)}</span>
      <span className="sc-value">{value}</span>
      <span className="sc-sub muted">
        {s.kind !== 'sessions' && <>{t('{n} økter', { n: s.sessions })} · </>}{metricLabel}
      </span>
      {s.deltaPct != null && s.kind !== 'sessions' && (
        <span className={`sc-trend ${trendCls}`}>
          {s.deltaPct > 0 ? '▲' : s.deltaPct < 0 ? '▼' : '–'} {Math.abs(s.deltaPct)}% {t('vs forrige uke')}
        </span>
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
