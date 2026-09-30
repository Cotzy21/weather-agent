// Import fra Garmin: CSV (alle aktiviteter, bare totaler) og FIT/ZIP (styrkeøkter med øvelser og vekter).
// Tung regning skjer her på enheten; serveren får små, ferdige biter. `post(path, body, contentType)` sender til API-et.
import { csvChunks } from './csvChunks.js'
import { forEachFit } from './garminZip.js'
import { sessionFromFit } from './garminFit.js'

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
 * FIT-filer og/eller zip-filer -> styrkeøkter med øvelser. Returnerer
 * { files, fitFiles, strength, imported, merged, skipped, notStrength, unreadable }.
 */
export async function importFit(files, { post, onProgress = () => {}, batchSize = 20, decode = sessionFromFit }) {
  const t = { files: 0, fitFiles: 0, strength: 0, imported: 0, merged: 0, skipped: 0, notStrength: 0, unreadable: 0 }
  let batch = []

  async function flush() {
    if (!batch.length) return
    const res = await post('/api/trening/import/fit', JSON.stringify({ sessions: batch }), 'application/json')
    t.imported += res.imported ?? 0
    t.merged += res.merged ?? 0
    t.skipped += res.skipped ?? 0
    batch = []
    onProgress({ phase: 'fit', ...t })
  }

  for (const file of files) {
    t.files++
    await forEachFit(file, async (bytes) => {
      t.fitFiles++
      const r = await decode(bytes)
      if (r.session) {
        t.strength++
        batch.push(r.session)
        if (batch.length >= batchSize) await flush()
      } else if (r.error) t.unreadable++
      else t.notStrength++
      if (t.fitFiles % 25 === 0) onProgress({ phase: 'fit', ...t })
    }, { onSkip: () => { t.unreadable++ } })
  }
  await flush()
  onProgress({ phase: 'fit', ...t })
  return t
}
