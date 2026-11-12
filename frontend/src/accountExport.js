import { apiUrl, readError } from './api.js'

/** A private download bypasses the general API cache and is cancelled on account changes. */
export async function fetchAccountExport({ headers, signal, fetchFn = fetch }) {
  const response = await fetchFn(apiUrl('/api/konto/eksport'), { headers, signal, cache: 'no-store' })
  if (!response.ok) throw new Error(await readError(response))
  if (!response.headers.get('content-type')?.includes('application/json')) throw new Error('Ugyldig eksportformat.')
  const blob = await response.blob()
  signal?.throwIfAborted()
  return blob
}

export function saveAccountExport(blob) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'weather-agent-data.json'
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
