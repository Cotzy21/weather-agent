// Pakker ut .fit-filer fra .zip-filer (også zip inni zip, slik Garmins full dataeksport er bygget opp) uten å laste
// hele zip-filen i minnet: den leses som en strøm, og bare .fit-filene (små) pakkes ut. Alt annet i eksporten hoppes over.
import { Unzip, UnzipInflate, UnzipPassThrough } from 'fflate'

const MAX_DEPTH = 3
const isFit = (name) => /\.fit$/i.test(name)
const isZip = (name) => /\.zip$/i.test(name)

function concat(chunks) {
  const out = new Uint8Array(chunks.reduce((n, c) => n + c.length, 0))
  let at = 0
  for (const c of chunks) { out.set(c, at); at += c.length }
  return out
}

/**
 * Kaller `onFit(bytes, name)` for hver .fit-fil i `file` (en .fit eller .zip; File/Blob). Én om gangen,
 * så kalleren kan tolke og sende videre i sitt eget tempo uten at filene hoper seg opp.
 * `onSkip(name)` kalles for filer som ikke kan leses (ødelagt zip o.l.).
 */
export async function forEachFit(file, onFit, { onSkip = () => {} } = {}) {
  const name = file.name ?? ''
  if (isFit(name)) {
    await onFit(new Uint8Array(await file.arrayBuffer()), name)
    return
  }
  if (!isZip(name) && file.type !== 'application/zip') return

  const ready = [] // ferdig utpakkede .fit-filer som venter på å bli behandlet

  function makeUnzip(depth) {
    const uz = new Unzip()
    uz.register(UnzipInflate)
    uz.register(UnzipPassThrough)
    uz.onfile = (entry) => {
      if (isFit(entry.name)) {
        const chunks = []
        entry.ondata = (err, chunk, final) => {
          if (err) { onSkip(entry.name); return }
          chunks.push(chunk)
          if (final) ready.push({ bytes: concat(chunks), name: entry.name })
        }
        entry.start()
      } else if (isZip(entry.name) && depth < MAX_DEPTH) {
        const child = makeUnzip(depth + 1)
        entry.ondata = (err, chunk, final) => {
          if (err) { onSkip(entry.name); return }
          try { child.push(chunk, final) } catch { onSkip(entry.name) }
        }
        entry.start()
      }
      // alt annet (JSON, bilder, gps-spor ...) startes aldri, så det pakkes heller ikke ut
    }
    return uz
  }

  const root = makeUnzip(0)
  const reader = file.stream().getReader()
  for (;;) {
    const { done, value } = await reader.read()
    try {
      root.push(value ?? new Uint8Array(0), done)
    } catch {
      onSkip(name)
      return
    }
    while (ready.length) {
      const next = ready.shift()
      await onFit(next.bytes, next.name)
    }
    if (done) break
  }
}
