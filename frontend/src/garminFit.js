// Leser en Garmin FIT-fil (styrkeøkt) til en ferdig tolket økt: hvilke øvelser, og reps og vekt per sett.
// Dette skjer i nettleseren: serveren (512 MB) skal aldri pakke ut eller tolke FIT-filer, bare ta imot små økter.
// FIT-biblioteket lastes først når du faktisk importerer, så det ikke tynger vanlig bruk av appen.
import { localIso } from './trainingStats.js'

const MAX_SETS_PER_BLOCK = 100 // samme tak som backend
const MAX_BLOCKS = 60

/** «barbellBenchPress» -> «Barbell Bench Press». */
export function humanizeExerciseName(camel) {
  return String(camel ?? '')
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/_/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .replace(/^./, (c) => c.toUpperCase())
    .replace(/ (\w)/g, (_, c) => ` ${c.toUpperCase()}`)
}

/** Stabil UUID-formet id ut fra en tekst (samme økt gir alltid samme id, så reimport aldri lager duplikater). */
export function uuidFromString(text) {
  // cyrb128: rask 128-biters hash uten avhengigheter. Ikke til sikkerhet, bare til gjenkjenning.
  let h1 = 1779033703, h2 = 3144134277, h3 = 1013904242, h4 = 2773480762
  for (let i = 0; i < text.length; i++) {
    const k = text.charCodeAt(i)
    h1 = h2 ^ Math.imul(h1 ^ k, 597399067)
    h2 = h3 ^ Math.imul(h2 ^ k, 2869860233)
    h3 = h4 ^ Math.imul(h3 ^ k, 951274213)
    h4 = h1 ^ Math.imul(h4 ^ k, 2716044179)
  }
  h1 = Math.imul(h3 ^ (h1 >>> 18), 597399067)
  h2 = Math.imul(h4 ^ (h2 >>> 22), 2869860233)
  h3 = Math.imul(h1 ^ (h3 >>> 17), 951274213)
  h4 = Math.imul(h2 ^ (h4 >>> 19), 2716044179)
  const hex = [h1, h2, h3, h4].map((n) => (n >>> 0).toString(16).padStart(8, '0')).join('')
  // versjon 4-format (8-4-4-4-12) med gyldige versjons-/variantbiter
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-4${hex.slice(13, 16)}-${'89ab'[parseInt(hex[16], 16) % 4]}${hex.slice(17, 20)}-${hex.slice(20, 32)}`
}

const num = (x) => (typeof x === 'number' && Number.isFinite(x) ? x : null)

/**
 * Setter (sortert) -> blokker med etterfølgende sett av samme øvelse. `nameOf(set)` gir øvelsesnavnet.
 * Bare arbeidssett med reps telles (hvile og tomme sett hoppes over).
 */
export function blocksFromSets(sets, nameOf) {
  const active = sets
    .filter((s) => s.setType !== 'rest' && num(s.repetitions) > 0)
    .map((s) => ({ name: nameOf(s), reps: Math.round(s.repetitions), weightKg: Math.round(Math.max(0, num(s.weight) ?? 0) * 100) / 100 }))
    .filter((s) => s.name && s.reps <= 1000 && s.weightKg <= 1000)

  const blocks = []
  for (const s of active) {
    const last = blocks.at(-1)
    if (last && last.name === s.name && last.sets.length < MAX_SETS_PER_BLOCK) last.sets.push({ reps: s.reps, weightKg: s.weightKg })
    else blocks.push({ name: s.name.slice(0, 80), sets: [{ reps: s.reps, weightKg: s.weightKg }] })
  }
  return blocks.slice(0, MAX_BLOCKS)
}

/**
 * En FIT-fil (Uint8Array) -> { session } for en styrkeøkt, { skipped: 'reason' } for annen aktivitet, eller { error }.
 * `sdk` kan sendes inn (tester); ellers lastes @garmin/fitsdk ved behov.
 */
export async function sessionFromFit(bytes, sdk) {
  const { Decoder, Stream, Profile } = sdk ?? (await import('@garmin/fitsdk'))
  let messages
  try {
    const stream = Stream.fromArrayBuffer(bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength))
    const decoder = new Decoder(stream)
    if (!decoder.isFIT()) return { skipped: 'ikke FIT' }
    const result = decoder.read()
    messages = result.messages
    if (!messages) return { error: 'Kunne ikke lese filen' }
  } catch (e) {
    return { error: e?.message || 'Kunne ikke lese filen' }
  }

  const setMesgs = messages.setMesgs ?? []
  const session = messages.sessionMesgs?.[0]
  if (!setMesgs.length) return { skipped: 'ingen sett (ikke styrke)' }

  // Egne øvelsesnavn brukeren har gitt i klokka (exercise_title) går foran Garmins standardnavn.
  const titles = new Map()
  for (const t of messages.exerciseTitleMesgs ?? []) {
    const name = Array.isArray(t.wktStepName) ? t.wktStepName[0] : t.wktStepName
    if (name) titles.set(`${t.exerciseCategory}:${t.exerciseName}`, name)
  }
  const nameOf = (set) => {
    const category = set.category?.[0]
    if (category == null) return null
    const sub = set.categorySubtype?.[0]
    const custom = titles.get(`${category}:${sub}`)
    if (custom) return custom
    const standard = sub != null ? Profile?.types?.[`${category}ExerciseName`]?.[sub] : null
    return humanizeExerciseName(standard ?? category)
  }

  const sorted = [...setMesgs].sort((a, b) => (a.messageIndex ?? 0) - (b.messageIndex ?? 0)
    || new Date(a.startTime ?? 0) - new Date(b.startTime ?? 0))
  const blocks = blocksFromSets(sorted, nameOf)
  if (!blocks.length) return { skipped: 'ingen arbeidssett' }

  const start = session?.startTime ?? sorted[0].startTime ?? sorted[0].timestamp
  const startDate = start ? new Date(start) : null
  if (!startDate || Number.isNaN(startDate.getTime())) return { error: 'Mangler starttid' }

  const seconds = num(session?.totalTimerTime) ?? num(session?.totalElapsedTime)
  const workoutName = messages.workoutMesgs?.[0]?.wktName
  return {
    session: {
      clientId: uuidFromString(`fit:${startDate.toISOString()}`),
      date: localIso(startDate),
      title: workoutName ? String(workoutName).slice(0, 120) : null,
      durationMin: seconds != null ? Math.round((seconds / 60) * 10) / 10 : null,
      kcal: num(session?.totalCalories),
      avgHr: num(session?.avgHeartRate),
      maxHr: num(session?.maxHeartRate),
      blocks,
    },
  }
}
