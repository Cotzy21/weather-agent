// Strekkodeskanning: gjenkjenning fra kameraet (BarcodeDetector) og oppslag mot backend. Ren logikk, så den kan testes uten kamera.
import { apiUrl, readError } from './api.js'

/** EAN-8, UPC-A, EAN-13 og GTIN-14: 8-14 siffer. Samme regel som backend. */
export function isValidBarcode(code) {
  return /^\d{8,14}$/.test(String(code ?? '').trim())
}

/** Første gyldige strekkode blant det BarcodeDetector fant, eller null. */
export function pickBarcode(detected) {
  for (const d of detected ?? []) {
    const value = String(d?.rawValue ?? '').trim()
    if (isValidBarcode(value)) return value
  }
  return null
}

/** Støtter nettleseren kameraskanning? iPhone/Safari har ikke BarcodeDetector, så der skriver man koden inn. */
export function scannerSupported(env = globalThis) {
  return typeof env.BarcodeDetector === 'function' && Boolean(env.navigator?.mediaDevices?.getUserMedia)
}

/**
 * Slå opp en strekkode hos backend (egne + delte varer, ellers Open Food Facts).
 * Returnerer { status: 'found', code, food } eller { status: 'notfound', code }; kaster ved ugyldig kode eller feil.
 */
export async function lookupBarcode({ code, headers, fetchFn = fetch }) {
  const clean = String(code ?? '').trim()
  if (!isValidBarcode(clean)) throw new Error('Strekkoden må være 8–14 siffer.')
  const res = await fetchFn(apiUrl(`/api/kosthold/strekkode/${clean}`), { headers })
  if (res.status === 404) return { status: 'notfound', code: clean }
  if (!res.ok) throw new Error(await readError(res))
  return { status: 'found', code: clean, food: await res.json() }
}
