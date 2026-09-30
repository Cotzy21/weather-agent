import { useState, useEffect } from 'react'
import { authHeaders } from './supabase'
import { apiUrl, readError, cachedGet, getCached } from './api'
import MusclePicker from './MusclePicker'
import { useReveal } from './anim'
import { useI18n } from './i18n.jsx'
import LiveSession from './LiveSession.jsx'
import ExercisePicker from './ExercisePicker.jsx'
import { exerciseKey } from './exercises'
import TrainingMemoryCard from './TrainingMemoryCard.jsx'
import TrainingOnboarding from './TrainingOnboarding.jsx'
import ThinkingText from './ThinkingText.jsx'
import GarminImport from './GarminImport.jsx'
import ExerciseTrends from './ExerciseTrends.jsx'
import { useGarminImport } from './useGarminImport.js'
import { loadLive, clearLive, newLive, newId, fromPlan } from './liveSession.js'
import { workoutQueue, onPendingChange, setQueueOwner } from './offlineQueue.js'
import { dayIndex, strengthVolume, localIso } from './trainingStats.js'
import WeekProgram from './WeekProgram.jsx'

const TYPES = [
  { v: 'STYRKE', t: '🏋️ Styrke' },
  { v: 'LØPING', t: '🏃 Løping' },
  { v: 'SVØMMING', t: '🏊 Svømming' },
  { v: 'SYKKEL', t: '🚴 Sykkel' },
  { v: 'BULDRING', t: '🧗 Buldring' },
  { v: 'KAMPSPORT', t: '🥋 Kampsport' },
  { v: 'HIKING', t: '🥾 Hiking' },
  { v: 'FRISTIL', t: '✨ Fristil' },
]
const CARDIO = ['LØPING', 'SVØMMING', 'SYKKEL']


const today = () => localIso(new Date())
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

const READINESS_ICONS = { GOD: '💪', MIDDELS: '🙂', LAV: '😴' }
const READINESS_LABELS = { GOD: 'God', MIDDELS: 'Middels', LAV: 'Lav' }

// Merkelapper for progressive overload-forslagene (nøklene oversettes via t()).
const ACTION_LABELS = {
  OK_VEKT: '↑ mer vekt',
  FLERE_REPS: '+ reps',
  DELOAD: '↓ deload',
  KROPPSVEKT: '+ reps',
}

export default function TrainingView({ session }) {
  const { t, lang } = useI18n()
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
  const [savedQueued, setSavedQueued] = useState(false) // lagret på enheten, sendes når nettet er tilbake

  // Drag-and-drop-omorganisering av blokker (pilene finnes fortsatt for touch).
  const [dragIdx, setDragIdx] = useState(null)
  const [dragOver, setDragOver] = useState(null)

  // Garmin-import: CSV (alle økter) og FIT/ZIP (øvelser og vekter), lest på enheten.
  const imp = useGarminImport({ onDone: () => Promise.all([loadWorkouts(), loadNextSets()]) })

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
  // Tredje modus: 'live' (økt pågår). En påbegynt økt gjenopptas automatisk.
  const [mode, setMode] = useState(() => (session && loadLive(session.user.id) ? 'live' : 'overview'))
  const [live, setLive] = useState(() => (session ? loadLive(session.user.id) : null))
  const [liveSaved, setLiveSaved] = useState(false) // true | 'queued' (lagret på enheten, venter på nett)
  const queue = workoutQueue(authHeaders)
  const [pendingCount, setPendingCount] = useState(0) // økter som venter på nett

  // «Ny»-arket (økt vs. plan) og om muskelvelgeren skal vises i byggeren.
  const [sheetOpen, setSheetOpen] = useState(false)
  const [showPicker, setShowPicker] = useState(false)
  // Hvilket øvelsesfelt velgeren fyller: { bi, ei? } i byggeren, eller 'progress'.
  const [pickFor, setPickFor] = useState(null)
  // Hvor byggeren ble åpnet fra, så «tilbake» kan skrive endringene dit:
  // { kind: 'ai-plan', index } | { kind: 'ai-workout' } | { kind: 'saved', id } | null.
  const [editTarget, setEditTarget] = useState(null)
  const [planMsg, setPlanMsg] = useState(null)

  // AI-økt: én økt til i dag (forskjellig fra AI-treningsplan, som lager flere).
  const [aiwFocus, setAiwFocus] = useState('')
  const [aiwBusy, setAiwBusy] = useState(false)
  const [aiwError, setAiwError] = useState(null)
  const [aiwResult, setAiwResult] = useState(null)

  const [plans, setPlans] = useState(() => getCached('/api/trening/planer') ?? [])

  // Progressive overload: vekt/reps-forslag for neste økt per øvelse. Begrunnelsene
  // lages på valgt språk i backend, så språket er med i cache-nøkkelen.
  const nextKey = `/api/ovelser/neste?lang=${lang}`
  const [nextSets, setNextSets] = useState(() => getCached(nextKey) ?? [])

  // Treningsminnet (liker / liker ikke / notater + vaner). null = ikke hentet ennå.
  const [memory, setMemory] = useState(() => getCached('/api/trening/minne') ?? null)
  const [memoryError, setMemoryError] = useState(null)

  // Onboarding-profilen. undefined = ikke hentet ennå, null = ikke fylt ut (viser onboarding).
  const [profile, setProfile] = useState(undefined)
  const [onboarding, setOnboarding] = useState(false) // åpnet manuelt fra «Rediger»
  const [onboardingSkipped, setOnboardingSkipped] = useState(() => sessionStorage.getItem('onboardingSkipped') === '1')

  // Dagsform fra søvnen i habit trackeren. null = ikke hentet/ingen søvndata.
  const [readiness, setReadiness] = useState(null)

  useEffect(() => {
    if (session) { loadWorkouts(); loadPlans(); loadNextSets(); loadReadiness(); loadMemory(); loadProfile() }
    else { setWorkouts([]); setPlans([]); setNextSets([]); setReadiness(null); setMemory(null); setProfile(undefined) }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session, lang])

  // Send økter som ventet på nett: ved oppstart, når nettet kommer tilbake, og jevnlig.
  useEffect(() => {
    setQueueOwner(session?.user?.id ?? null)
    if (!session) return undefined
    const off = onPendingChange(setPendingCount)
    const flush = () => queue.flush().then((sent) => { if (sent) { loadWorkouts(); loadNextSets() } }).catch(() => {})
    queue.count().then(setPendingCount).catch(() => {})
    flush()
    window.addEventListener('online', flush)
    const timer = setInterval(flush, 60000)
    return () => { off(); window.removeEventListener('online', flush); clearInterval(timer) }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session])

  // 204 = brukeren logger ikke søvn -> vis et hint i stedet for kortet.
  async function loadReadiness() {
    try {
      const res = await fetch(apiUrl(`/api/trening/dagsform?lang=${lang}`), { headers: await authHeaders() })
      setReadiness(res.status === 200 ? await res.json() : { none: true })
    } catch { /* sekundært */ }
  }

  // 204 = ikke onboardet ennå.
  async function loadProfile() {
    try {
      const res = await fetch(apiUrl('/api/trening/profil'), { headers: await authHeaders() })
      if (res.status === 200) setProfile(await res.json())
      else if (res.status === 204) setProfile(null)
    } catch { /* sekundært */ }
  }

  async function saveProfile(next) {
    const res = await fetch(apiUrl('/api/trening/profil'), {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', ...(await authHeaders()) },
      body: JSON.stringify(next),
    })
    if (!res.ok) throw new Error(await readError(res))
    setProfile(await res.json())
    setOnboarding(false)
  }

  function skipOnboarding() {
    try { sessionStorage.setItem('onboardingSkipped', '1') } catch { /* privat modus */ }
    setOnboardingSkipped(true)
    setOnboarding(false)
  }

  async function loadMemory() {
    try {
      setMemory(await cachedGet('/api/trening/minne', await authHeaders()))
    } catch { /* sekundært – behold cachet */ }
  }

  async function saveMemory(next) {
    const res = await fetch(apiUrl('/api/trening/minne'), {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', ...(await authHeaders()) },
      body: JSON.stringify(next),
    })
    if (!res.ok) throw new Error(await readError(res))
    setMemory(await res.json())
  }

  // ♥ / 👎 på en øvelse: legg den i listen (og ut av den motsatte), eller ta den ut igjen.
  function toggleMemory(name, list) {
    if (!memory) return
    const same = (x) => exerciseKey(x) === exerciseKey(name)
    const other = list === 'liked' ? 'disliked' : 'liked'
    const has = memory[list].some(same)
    setMemoryError(null)
    saveMemory({
      ...memory,
      [list]: has ? memory[list].filter((x) => !same(x)) : [...memory[list], name],
      [other]: memory[other].filter((x) => !same(x)),
    }).catch((e) => setMemoryError(e.message))
  }

  async function loadNextSets() {
    try {
      setNextSets(await cachedGet(nextKey, await authHeaders()))
    } catch { /* sekundært – behold cachet */ }
  }

  const fmtHours = (h) => {
    const s = Number.isInteger(h) ? String(h) : h.toFixed(1)
    return lang === 'en' ? s : s.replace('.', ',')
  }

  // 82.5 -> "82,5" på norsk, "82.5" på engelsk; hele tall uten desimal.
  const fmtKg = (v) => {
    const s = Number.isInteger(v) ? String(v) : v.toFixed(1)
    return lang === 'en' ? s : s.replace('.', ',')
  }

  // Legg en øvelse inn i byggeren med forslått vekt/reps (samme antall sett som
  // sist), og åpne byggeren. Tomme startblokker fjernes så lista blir ren.
  function addNextToBuilder(s) {
    setType('STYRKE')
    const block = {
      kind: 'exercise',
      name: s.exercise,
      sets: Array.from({ length: Math.max(1, s.sets) }, () => ({
        reps: String(s.nextReps),
        weightKg: s.nextWeightKg > 0 ? String(s.nextWeightKg) : '',
      })),
    }
    setBlocks((prev) => [...prev.filter((b) => b.kind !== 'exercise' || b.name.trim()), block])
    setEditTarget(null)
    setShowPicker(false)
    setMode('builder')
    window.scrollTo(0, 0)
  }

  function startLive(title, exercises, plannedId = null) {
    setLive(newLive(title, exercises, plannedId))
    setMode('live')
    window.scrollTo(0, 0)
  }

  function endLive() {
    clearLive(session.user.id)
    setLive(null)
    setMode('overview')
  }

  // Økta lagres først på enheten og sendes så; uten nett blir den liggende i køen til nettet er tilbake.
  async function finishLive({ title, content }) {
    const clientId = live?.clientId ?? newId()
    const body = { date: today(), title, type: 'STYRKE', content, notes: null, clientId, plannedId: live?.plannedId ?? null }
    const { queued } = await queue.submit({ clientId, body })
    endLive()
    setLiveSaved(queued ? 'queued' : true)
    setTimeout(() => setLiveSaved(false), 4000)
    if (!queued) {
      loadWorkouts()
      loadNextSets()
    }
  }

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
      setMode('overview')
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
    return { durationMin: num(cardio.durationMin) } // FRISTIL / BULDRING / KAMPSPORT
  }

  async function submit() {
    if (!title.trim()) { setError(t('Gi økta en tittel.')); return }
    setBusy(true)
    setError(null)
    try {
      const clientId = newId()
      const { queued } = await queue.submit({ clientId, body: {
        date, title: title.trim(), type, content: buildContent(), notes: notes.trim() || null, clientId,
      } })
      setSavedQueued(queued)
      setTitle(''); setNotes(''); setBlocks([newExercise()]); setCardio({ distanceKm: '', durationMin: '', ascentM: '' })
      setSaved(true)
      setTimeout(() => setSaved(false), 2500)
      loadWorkouts()
      loadNextSets() // ny økt -> nye progresjonsforslag
      setMode('overview') // tilbake til oversikten, der den nye økta + oppsummeringen vises
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }


  async function deleteWorkout(id) {
    try {
      await fetch(apiUrl(`/api/treningsokter/${id}`), { method: 'DELETE', headers: await authHeaders() })
      loadWorkouts()
    } catch { /* ignorer */ }
  }

  function pickNew(kind) {
    setSheetOpen(false)
    setEditTarget(null)
    if (kind === 'build' || kind === 'muscles') {
      setShowPicker(kind === 'muscles')
      setMode('builder')
    } else {
      setMode(kind)
    }
    window.scrollTo(0, 0)
  }

  async function suggestWorkout(preset) {
    const focus = (typeof preset === 'string' ? preset : aiwFocus).trim()
    if (!focus || aiwBusy) return
    setAiwFocus(focus)
    setAiwBusy(true)
    setAiwError(null)
    setAiwResult(null)
    try {
      const res = await fetch(apiUrl('/api/trening/forslag'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...(await authHeaders()) },
        body: JSON.stringify({ focus, type: 'STYRKE', lang }),
      })
      if (!res.ok) throw new Error(await readError(res))
      setAiwResult(await res.json())
    } catch (e) {
      setAiwError(e.message)
    } finally {
      setAiwBusy(false)
    }
  }

  async function loadProgress(name = progressName) {
    if (!name.trim()) return
    setProgressName(name)
    setProgress(null)
    try {
      const params = new URLSearchParams({ navn: name.trim() })
      const res = await fetch(apiUrl(`/api/ovelser/progresjon?${params}`), { headers: await authHeaders() })
      if (res.ok) setProgress(await res.json())
    } catch { setProgress([]) }
  }

  // Send én melding i samtalen med assistenten. Svaret er ENTEN oppfølgings-
  // spørsmål (vises i chatten) ELLER en ferdig plan (vises med aksepter/forkast).
  async function sendMessage(preset) {
    const text = (typeof preset === 'string' ? preset : aiFocus).trim()
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
          // Planen slik brukeren ser den nå (inkl. endringer fra byggeren), så AI-en kan endre trinnvis.
          currentPlan: aiPlan,
          lang,
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

  function resetBuilder() {
    setTitle(''); setNotes(''); setBlocks([newExercise()]); setCardio({ distanceKm: '', durationMin: '', ascentM: '' })
  }

  // Skriv byggerens endringer tilbake dit økta kom fra. Ukedagen i planen beholdes;
  // resten av innholdet erstattes (typen kan ha endret seg i byggeren).
  async function returnFromBuilder() {
    const target = editTarget
    const edited = { title: title.trim(), type, content: buildContent() }
    const merge = (orig) => ({
      title: edited.title || orig.title,
      type: edited.type,
      content: {
        ...(orig.content?.day ? { day: orig.content.day } : {}),
        ...(orig.content?.time ? { time: orig.content.time } : {}),
        ...edited.content,
      },
    })
    setEditTarget(null)
    window.scrollTo(0, 0)
    if (target.kind === 'ai-plan') {
      setAiPlan((p) => p && { ...p, workouts: p.workouts.map((w, i) => (i === target.index ? { ...w, ...merge(w) } : w)) })
      setMode('ai-plan')
    } else if (target.kind === 'ai-workout') {
      setAiwResult((r) => r && { ...r, ...merge(r) })
      setMode('ai-workout')
    } else {
      const orig = plans.find((x) => x.id === target.id)
      setMode('overview')
      if (orig) {
        try {
          const res = await fetch(apiUrl(`/api/trening/planer/${target.id}`), {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json', ...(await authHeaders()) },
            body: JSON.stringify({ ...merge(orig), rationale: orig.rationale || null }),
          })
          if (!res.ok) throw new Error(await readError(res))
          setPlanMsg(t('✓ Planen er oppdatert'))
          loadPlans()
        } catch (e) {
          setPlanMsg(e.message)
        }
        setTimeout(() => setPlanMsg(null), 3000)
      }
    }
    resetBuilder()
  }

  // Fyll forslaget inn i byggeren så brukeren kan finpusse og lagre.
  function applySuggestion(s, target = null) {
    setEditTarget(target)
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
    setShowPicker(false)
    setMode('builder') // ta brukeren til byggeren med økta forhåndsutfylt
    window.scrollTo(0, 0)
  }

  const revealRef = useReveal([session, mode])

  if (!session) {
    return (
      <div className="training" ref={revealRef}>

      {session && profile !== undefined && (onboarding || (profile === null && !onboardingSkipped)) && (
        <TrainingOnboarding initial={profile} onSave={saveProfile} onSkip={skipOnboarding} />
      )}
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

  const progressChart = progress && (progress.length === 0
    ? <p className="muted">{t('Ingen logget for denne øvelsen ennå.')}</p>
    : (
      <div className="progress">
        {progress.map((p, i) => (
          <div className="prog-row" key={i}>
            <span className="prog-date">{p.date}</span>
            <span className="prog-bar"><span style={{ width: `${maxW ? (p.maxWeight / maxW) * 100 : 0}%` }} /></span>
            <span className="prog-val">{fmtKg(p.maxWeight)} kg</span>
          </div>
        ))}
      </div>
    ))

  const dayKey = (p) => { const i = dayIndex(p.content?.day); return i < 0 ? 7 : i }
  const sortedPlans = [...plans].sort((a, b) => dayKey(a) - dayKey(b))
  const myExercises = nextSets.map((x) => x.exercise)

  function planSubtitle(p) {
    const parts = []
    if (p.content?.day) parts.push(p.content.time ? `${p.content.day} ${p.content.time}` : p.content.day)
    if (p.type === 'STYRKE') {
      const n = (p.content?.blocks || []).reduce((sum, b) => sum + (b.kind === 'superset' ? (b.exercises || []).length : 1), 0)
      parts.push(t(n === 1 ? '1 øvelse' : '{n} øvelser', { n }))
    } else {
      parts.push(t((TYPES.find((tp) => tp.v === p.type)?.t) || p.type))
    }
    return parts.join(' · ')
  }

  return (
    <div className="training" ref={revealRef}>

      {mode === 'live' && live && (
        <LiveSession
          key={live.startedAt}
          userId={session.user.id}
          initial={live}
          nextSets={nextSets}
          memory={memory}
          workouts={workouts}
          fmtKg={fmtKg}
          onFinish={finishLive}
          onCancel={endLive}
        />
      )}

      {pickFor && (
        <ExercisePicker
          title={t('Velg øvelse')}
          mine={myExercises}
          liked={memory?.liked}
          disliked={memory?.disliked}
          onClose={() => setPickFor(null)}
          onPick={(name) => {
            const target = pickFor
            setPickFor(null)
            if (target === 'progress') loadProgress(name)
            else if (target.ei != null) editBlocks((c) => { c[target.bi].exercises[target.ei].name = name })
            else editBlocks((c) => { c[target.bi].name = name })
          }}
        />
      )}

      {mode === 'overview' && (
        <>
          <h2 className="detail-title" data-reveal>{t('Trening')}</h2>
          <div className="train-actions" data-reveal>
            <button className="train-action" onClick={() => setSheetOpen(true)}>
              <span className="ta-icon" aria-hidden="true">＋</span>{t('Ny')}
            </button>
            <button className="train-action go" onClick={() => startLive('', [])}>
              <span className="ta-icon" aria-hidden="true">▶</span>{t('Start')}
            </button>
            <button className="train-action" onClick={() => { setMode('progress'); window.scrollTo(0, 0) }}>
              <span className="ta-icon" aria-hidden="true">📈</span>{t('Progresjon')}
            </button>
          </div>
          <WeekProgram
            plans={plans}
            workouts={workouts}
            onStart={(p) => (p.type === 'STYRKE' ? startLive(p.title, fromPlan(p.content, nextSets), p.id ?? null) : applySuggestion(p))}
          />
          {pendingCount > 0 && (
            <p className="offline-banner" role="status">
              📡 {t('{n} økter venter på nett og sendes automatisk', { n: pendingCount })}
            </p>
          )}
          {liveSaved && <p className="success">{liveSaved === 'queued' ? t('✓ Lagret på enheten – sendes når nettet er tilbake') : t('✓ Økt lagret')}</p>}
          <GarminImport imp={imp} />

          {readiness && !readiness.none && (
            <details className={`readiness-line ${readiness.level.toLowerCase()}`} data-reveal>
              <summary>
                {READINESS_ICONS[readiness.level]} {t('Dagsform')}: <strong>{t(READINESS_LABELS[readiness.level])}</strong>
                <span className="muted"> · {fmtHours(readiness.lastNightHours)} {t('t søvn')}</span>
              </summary>
              <p className="muted">{readiness.advice}</p>
            </details>
          )}

          <h3 className="detail-h3" data-reveal>{t('Mine planer')}</h3>
          {planMsg && <p className={planMsg.startsWith('✓') ? 'success' : 'error'}>{planMsg}</p>}
          {plans.length === 0 ? (
            <button className="plan-empty" data-reveal onClick={() => pickNew('ai-plan')}>
              ✨ {t('Lag en treningsplan med AI')}
            </button>
          ) : (
            <ul className="plan-cards" data-reveal>
              {sortedPlans.map((p) => (
                <li key={p.id} className="plan-card">
                  <button className="plan-card-main" onClick={() => applySuggestion(p, { kind: 'saved', id: p.id })} title={t('Rediger i byggeren')}>
                    <strong>{p.title}</strong>
                    <span className="muted">{planSubtitle(p)}</span>
                  </button>
                  {p.type === 'STYRKE' && (
                    <button className="plan-play" aria-label={t('Start live')}
                            onClick={() => startLive(p.title, fromPlan(p.content, nextSets), p.id)}>▶</button>
                  )}
                  <button className="del" onClick={() => deletePlan(p.id)} aria-label={t('Slett plan')}>✕</button>
                </li>
              ))}
            </ul>
          )}
        </>
      )}

      {sheetOpen && (
        <div className="sheet-backdrop" onClick={() => setSheetOpen(false)}>
          <div className="sheet" role="dialog" aria-label={t('Ny')} onClick={(e) => e.stopPropagation()}>
            <span className="sheet-handle" aria-hidden="true" />
            <p className="sheet-label">{t('Økt')}</p>
            <button className="sheet-item" onClick={() => pickNew('build')}>
              <span className="sheet-icon">✏️</span><span className="sheet-text">{t('Bygg selv')}</span>
            </button>
            <button className="sheet-item" onClick={() => pickNew('muscles')}>
              <span className="sheet-icon">💪</span><span className="sheet-text">{t('Velg muskler')}</span>
            </button>
            <button className="sheet-item" onClick={() => pickNew('ai-workout')}>
              <span className="sheet-icon">✨</span>
              <span className="sheet-text">{t('AI-økt')}<small>{t('Én økt til i dag')}</small></span>
            </button>
            <p className="sheet-label">{t('Plan')}</p>
            <button className="sheet-item" onClick={() => pickNew('ai-plan')}>
              <span className="sheet-icon">🗓️</span>
              <span className="sheet-text">{t('AI-treningsplan')}<small>{t('Flere økter i uka')}</small></span>
            </button>
            <p className="sheet-label">{t('Import')}</p>
            <label className="sheet-item">
              <span className="sheet-icon">⌚</span>
              <span className="sheet-text">{t('Importer fra Garmin')}<small>{t('Garmin Connect → Aktiviteter → «Eksporter CSV»')}</small></span>
              <input type="file" accept=".csv,text/csv" hidden
                     onChange={(e) => { setSheetOpen(false); imp.runCsv(e.target.files[0]); e.target.value = '' }} />
            </label>
          </div>
        </div>
      )}

      {mode !== 'overview' && mode !== 'live' && (
        mode === 'builder' && editTarget ? (
          <button className="back" onClick={returnFromBuilder}>
            ← {t(editTarget.kind === 'ai-plan' ? 'Tilbake til planen'
              : editTarget.kind === 'ai-workout' ? 'Tilbake til AI-økta' : 'Lagre og tilbake')}
          </button>
        ) : (
          <button className="back" onClick={() => setMode('overview')}>← {t('Trening')}</button>
        )
      )}

      {mode === 'builder' && showPicker && <MusclePicker onCreate={applyMuscles} />}

      {mode === 'ai-workout' && (
        <div className="ai-panel">
          <h2 className="detail-title">{t('AI-økt')}</h2>
          <p className="muted ai-intro">{t('Hva vil du trene i dag? Økta tilpasses historikken din.')}</p>
          <div className="ai-examples">
            {[t('Push, 45 min'), t('Bein'), t('Overkropp'), t('Fullkropp')].map((ex) => (
              <button key={ex} className="ai-example" disabled={aiwBusy} onClick={() => suggestWorkout(ex)}>{ex}</button>
            ))}
          </div>
          <form className="ai-row" onSubmit={(e) => { e.preventDefault(); suggestWorkout() }}>
            <input placeholder={t('F.eks. «rygg og biceps»')} value={aiwFocus} onChange={(e) => setAiwFocus(e.target.value)} />
            <button className="primary" type="submit" disabled={aiwBusy || !aiwFocus.trim()}>{t('Lag')}</button>
          </form>
          {aiwBusy && <ThinkingText />}
          {aiwError && <p className="error">{aiwError}</p>}
          {aiwResult && (
            <div className="ai-workout">
              <div className="ai-workout-head"><strong>{aiwResult.title}</strong></div>
              {aiwResult.rationale && <p className="muted ai-workout-why">{aiwResult.rationale}</p>}
              <WorkoutBody workout={aiwResult} />
              <div className="ai-plan-actions">
                <button className="primary" onClick={() => startLive(aiwResult.title, fromPlan(aiwResult.content, nextSets))}>
                  ▶ {t('Start nå')}
                </button>
                <button className="mini" onClick={() => applySuggestion(aiwResult, { kind: 'ai-workout' })}>{t('Rediger')}</button>
              </div>
            </div>
          )}
        </div>
      )}

      {mode === 'ai-plan' && (
      <div className="ai-panel">
        <div className="ai-panel-head">
          <h2 className="detail-title">{t('AI-treningsplan')}</h2>
          {aiMessages.length > 0 && (
            <button className="mini" onClick={resetChat} disabled={aiBusy || aiSaving}>{t('Ny samtale')}</button>
          )}
        </div>

        {aiMessages.length === 0 && (
          <>
            <p className="muted ai-intro">{t('Be om et opplegg – f.eks. «lag en push pull legs split» eller «jeg vil begynne med mer cardio». Assistenten spør om det trenger mer, og bruker historikken din til progressiv overload.')}</p>
            <div className="ai-examples">
              {[
                t('Lag en push/pull/legs-split'),
                t('Upper/lower, 4 dager i uka'),
                t('Jeg vil begynne med mer cardio'),
                t('Fullkropp 3x i uka'),
              ].map((ex) => (
                <button key={ex} className="ai-example" disabled={aiBusy} onClick={() => sendMessage(ex)}>{ex}</button>
              ))}
            </div>
          </>
        )}

        {aiMessages.map((m, i) => (
          <div key={i} className={`ai-msg ${m.role}`}>
            {m.content && <p className="ai-msg-text">{m.content}</p>}
            {m.questions?.length > 0 && (
              <ul className="ai-questions">{m.questions.map((q, qi) => <li key={qi}>{q}</li>)}</ul>
            )}
          </div>
        ))}
        {aiBusy && <ThinkingText />}

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
              <span className="ai-plan-count muted">{t(aiPlan.workouts.length === 1 ? '1 økt' : '{n} økter', { n: aiPlan.workouts.length })}</span>
            </div>
            {aiPlan.summary && <p className="muted ai-plan-summary">{aiPlan.summary}</p>}

            {aiPlan.workouts.map((w, i) => (
              <div className="ai-workout" key={i}>
                <div className="ai-workout-head">
                  {w.content?.day && <span className="ai-workout-day">{w.content.day}{w.content.time ? ` · ${w.content.time}` : ''}</span>}
                  <strong>{w.title}</strong>
                  <span className="ai-workout-type">{t((TYPES.find((tp) => tp.v === w.type)?.t) || w.type)}</span>
                  <button className="mini" onClick={() => applySuggestion(w, { kind: 'ai-plan', index: i })}>{t('Rediger')}</button>
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
                      <button className={`exercise-pick ${ex.name ? '' : 'empty'}`} onClick={() => setPickFor({ bi, ei })}>
                        {ex.name || t('Velg øvelse')}
                      </button>
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
                  <button className={`exercise-pick ${b.name ? '' : 'empty'}`} onClick={() => setPickFor({ bi })}>
                    {b.name || t('Velg øvelse')}
                  </button>
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
        {saved && <span className="success">{savedQueued ? t('✓ Lagret på enheten – sendes når nettet er tilbake') : t('✓ Økt lagret')}</span>}
      </div>
      {error && <p className="error">{error}</p>}

      </>
      )}

      {mode === 'overview' && (
      <>
      <h3 className="detail-h3" data-reveal>{t('Siste 7 dager')}</h3>
      {summaries.length > 0 ? (
        <div className="train-summary" data-reveal>
          {summaries.map((s) => <SummaryCard key={s.type} s={s} />)}
        </div>
      ) : (
        <p className="muted" data-reveal>{t('Logg noen økter, så ser du progresjonen din her.')}</p>
      )}

      <h3 className="detail-h3">{t('Historikk')}</h3>
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

      {mode === 'progress' && (
        <div className="progress-page">
          <h2 className="detail-title">{t('Progresjon')}</h2>
          {memory && <TrainingMemoryCard key={memory.notes} memory={memory} mine={myExercises} onSave={saveMemory}
                                                profile={profile} onEditProfile={() => setOnboarding(true)} />}
          {memoryError && <p className="error">{memoryError}</p>}
          {nextSets.length === 0 && (
            <p className="muted">{t('Logg noen styrkeøkter, så ser du hva som forventes neste gang.')}</p>
          )}
          <ul className="prog-list">
            {nextSets.map((s) => {
              const open = progressName === s.exercise
              const kg = (v) => (v > 0 ? `${fmtKg(v)} kg` : t('kroppsvekt'))
              return (
                <li key={s.exercise} className={`prog-item ${open ? 'open' : ''}`}>
                  <button className="prog-item-head" aria-expanded={open}
                          onClick={() => (open ? setProgressName('') : loadProgress(s.exercise))}>
                    <span className="prog-item-name">
                      <strong>{s.exercise}</strong>
                      <span className="muted">
                        {kg(s.lastWeightKg)} × {s.lastReps.join(', ')} → <strong>{kg(s.nextWeightKg)} × {s.nextReps}</strong>
                      </span>
                    </span>
                    <span className={`next-set-tag ${s.action.toLowerCase()}`}>{t(ACTION_LABELS[s.action])}</span>
                  </button>
                  {open && (
                    <div className="prog-detail">
                      <p><strong>{t('Neste økt')}:</strong> {s.sets} × {s.nextReps} · {kg(s.nextWeightKg)}</p>
                      <p className="muted">{s.reason}</p>
                      {progressChart}
                      <div className="prog-actions">
                        <button className="mini" onClick={() => addNextToBuilder(s)}>{t('+ Legg i økt')}</button>
                        {memory && ['liked', 'disliked'].map((list) => {
                          const on = memory[list].some((x) => exerciseKey(x) === exerciseKey(s.exercise))
                          return (
                            <button key={list} className={`mini memory-toggle ${on ? 'on' : ''}`} aria-pressed={on}
                                    onClick={() => toggleMemory(s.exercise, list)}>
                              {list === 'liked' ? `♥ ${t('Liker')}` : `👎 ${t('Liker ikke')}`}
                            </button>
                          )
                        })}
                      </div>
                    </div>
                  )}
                </li>
              )
            })}
          </ul>

          <ExerciseTrends
            workouts={workouts}
            openName={progressName}
            chart={progressChart}
            onOpen={(name) => (progressName === name ? (setProgressName(''), setProgress(null)) : loadProgress(name))}
          />

          <h3 className="detail-h3">{t('Søk etter øvelse')}</h3>
          <button className="exercise-pick" onClick={() => setPickFor('progress')}>
            🔎 {progressName && !nextSets.some((x) => x.exercise === progressName) ? progressName : t('Søk etter øvelse')}
          </button>
          {progressName && !nextSets.some((s) => s.exercise === progressName) && progressChart}

          <p className="muted source-note">
            {t('Dobbel progresjon: flere reps til alle sett når toppen av området, så mer vekt. Tommelfingerregler – lytt til kroppen.')}
          </p>
        </div>
      )}
    </div>
  )
}


const CARDIO_TYPES = ['LØPING', 'SYKKEL', 'SVØMMING', 'HIKING']


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
        {s.kind !== 'sessions' && <>{t(s.sessions === 1 ? '1 økt' : '{n} økter', { n: s.sessions })} · </>}{metricLabel}
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
