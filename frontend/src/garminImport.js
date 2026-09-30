// Import fra Garmin: CSV (alle aktiviteter, bare totaler) og FIT/ZIP (styrkeøkter med øvelser og vekter).
// Tung regning skjer her på enheten; serveren får små, ferdige biter. `post(path, body, contentType)` sender til API-et.
import { csvChunks } from './csvChunks.js'
import { forEachFit } from './garminZip.js'
import { sessionFromFit } from './garminFit.js'
import { applyExerciseNames } from './exerciseMatch.js'

/** CSV -> flere små opplastinger. Returnerer { imported, skipped, merged, rows } (merged: slått sammen med økter som alt kom fra FIT). */
export async function importCsv(file, { post, onProgress = () => {}, rowsPerChunk = 250 }) {
  const chunks = csvChunks(await file.text(), rowsPerChunk)
  const total = { imported: 0, skipped: 0, merged: 0, rows: 0 }
  for (let i = 0; i < chunks.length; i++) {
    const res = await post('/api/trening/import/garmin', chunks[i].text, 'text/plain')
    total.imported += res.imported ?? 0
    total.skipped += res.skipped ?? 0
    total.merged += res.merged ?? 0
    total.rows += chunks[i].rows
    onProgress({ phase: 'csv', done: i + 1, total: chunks.length, ...total })
  }
  return total
}

/**
 * FIT-filer og/eller zip-filer -> styrkeøkter med øvelser, i tre trinn:
 *  1. les alle filene på enheten (økter samles i minnet, noen få MB selv for flere tusen økter),
 *  2. koble øvelsesnavnene til appens øvelser (`matchNames`, se exerciseMatch.js; valgfritt),
 *  3. send øktene til serveren i små grupper.
 * Returnerer { files, fitFiles, strength, imported, merged, skipped, notStrength, unreadable, names }.
 */
export async function importFit(files, { post, onProgress = () => {}, batchSize = 20, decode = sessionFromFit, matchNames }) {
  const t = { files: 0, fitFiles: 0, strength: 0, imported: 0, merged: 0, skipped: 0, notStrength: 0, unreadable: 0,
    names: { total: 0, matched: 0, unmatched: 0, viaAi: 0 } }
  const sessions = new Map() // clientId -> økt (samme økt to ganger i eksporten telles én gang)

  // 1. Les
  for (const file of files) {
    t.files++
    await forEachFit(file, async (bytes) => {
      t.fitFiles++
      const r = await decode(bytes)
      if (r.session) {
        if (!sessions.has(r.session.clientId)) t.strength++
        sessions.set(r.session.clientId, r.session)
      } else if (r.error) t.unreadable++
      else t.notStrength++
      if (t.fitFiles % 25 === 0) onProgress({ phase: 'read', ...t })
    }, { onSkip: () => { t.unreadable++ } })
  }
  onProgress({ phase: 'read', ...t })
  let ordered = [...sessions.values()].sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : 0))

  // 2. Koble øvelsesnavn
  if (matchNames && ordered.length) {
    onProgress({ phase: 'match', ...t })
    const names = [...new Set(ordered.flatMap((s) => s.blocks.map((b) => b.name)))]
    try {
      const m = await matchNames(names)
      ordered = applyExerciseNames(ordered, m.map)
      t.names = { total: names.length, matched: m.matched, unmatched: m.unmatched, viaAi: m.viaAi }
    } catch {
      t.names = { total: names.length, matched: 0, unmatched: names.length, viaAi: 0 }
    }
  }

  // 3. Send
  for (let i = 0; i < ordered.length; i += batchSize) {
    const batch = ordered.slice(i, i + batchSize)
    const res = await post('/api/trening/import/fit', JSON.stringify({ sessions: batch }), 'application/json')
    t.imported += res.imported ?? 0
    t.merged += res.merged ?? 0
    t.skipped += res.skipped ?? 0
    onProgress({ phase: 'send', sent: Math.min(i + batchSize, ordered.length), of: ordered.length, ...t })
  }
  return t
}
