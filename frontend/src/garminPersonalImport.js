import { forEachFit } from './garminZip.js'
import { sessionFromFit } from './garminFit.js'

const BATCH_SIZE = 5
const MAX_FIT_BYTES = 4 * 1024 * 1024
const divide = (value, divisor) => value == null ? null : value / divisor

async function strengthSession(zip) {
  const bytes = Uint8Array.from(atob(zip), (character) => character.charCodeAt(0))
  let session = null
  await forEachFit(new Blob([bytes], { type: 'application/zip' }), async (fit) => {
    if (!session) session = (await sessionFromFit(fit)).session ?? null
  }, { maxFitBytes: MAX_FIT_BYTES })
  return session
}

export async function importGarminActivities(activities, { download, save, onBatch }) {
  for (let i = 0; i < activities.length; i += BATCH_SIZE) {
    const batch = activities.slice(i, i + BATCH_SIZE)
    const strengthIds = batch.filter((entry) => entry.type?.includes('strength')).map((entry) => entry.id)
    const files = new Map(strengthIds.length ? (await download(strengthIds)).map((file) => [file.id, file.zip]) : [])
    const entries = []
    let summaryOnly = 0
    for (const entry of batch) {
      const strength = files.has(entry.id) ? await strengthSession(files.get(entry.id)) : null
      if (!strength && strengthIds.includes(entry.id)) summaryOnly++
      entries.push({
        activityId: entry.id,
        date: entry.startLocal.slice(0, 10),
        title: (entry.name?.trim() || 'Garmin').slice(0, 120),
        type: entry.type,
        distanceKm: divide(entry.distanceMeters, 1000),
        durationMin: divide(entry.durationSeconds, 60),
        ascentM: entry.ascentM,
        kcal: entry.kcal,
        totalSets: entry.totalSets,
        totalReps: entry.totalReps,
        avgHr: entry.avgHr,
        maxHr: entry.maxHr,
        strength,
      })
    }
    const result = await save(entries)
    onBatch(batch.map((entry) => entry.id), { ...result, summaryOnly })
  }
}
