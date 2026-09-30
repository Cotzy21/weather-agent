// Store CSV-filer (flere hundre økter) sendes i små biter, så verken telefonen eller serveren (512 MB)
// må ta alt på én gang, og så forespørselen ikke tidsavbrytes. Hver bit får med overskriftsraden,
// så serveren kan tolke den for seg. Deler på rader, ikke på tegn: linjeskift inne i «...» hører til feltet.

/** Deler CSV-tekst i rader (uten linjeskift). Tåler BOM, \r\n og felt med linjeskift i anførselstegn. */
export function splitCsvRows(text) {
  const src = String(text ?? '').replace(/^﻿/, '')
  const rows = []
  let start = 0
  let inQuotes = false
  for (let i = 0; i < src.length; i++) {
    const c = src[i]
    if (c === '"') inQuotes = !inQuotes
    else if (!inQuotes && (c === '\n' || c === '\r')) {
      const row = src.slice(start, i)
      if (row.trim()) rows.push(row)
      if (c === '\r' && src[i + 1] === '\n') i++
      start = i + 1
    }
  }
  const last = src.slice(start)
  if (last.trim()) rows.push(last)
  return rows
}

/** [{ text, rows }]: overskriftsraden + opptil `rowsPerChunk` datarader per bit. Tom liste uten datarader. */
export function csvChunks(text, rowsPerChunk = 250) {
  const [header, ...data] = splitCsvRows(text)
  if (!header || !data.length) return []
  const chunks = []
  for (let i = 0; i < data.length; i += rowsPerChunk) {
    const rows = data.slice(i, i + rowsPerChunk)
    chunks.push({ text: [header, ...rows].join('\n'), rows: rows.length })
  }
  return chunks
}
