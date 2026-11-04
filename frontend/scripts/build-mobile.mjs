import { build, loadEnv } from 'vite'
import { fileURLToPath } from 'node:url'

// Mobilpakken har eget mål. Webbygget til Spring skal ikke overskrives.
const root = fileURLToPath(new URL('../', import.meta.url))
const env = { ...loadEnv('native', root, 'VITE_'), ...loadEnv('mobile', root, 'VITE_') }
const apiBase = process.env.VITE_API_BASE ?? env.VITE_API_BASE

let api
try {
  api = new URL(apiBase)
} catch {
  throw new Error('Sett VITE_API_BASE til backendens HTTPS-adresse i .env.mobile.')
}
if (api.protocol !== 'https:' || api.username || api.password || api.search || api.hash || api.pathname !== '/') {
  throw new Error('VITE_API_BASE må være en HTTPS-adresse uten sti, innlogging, query eller fragment.')
}
for (const key of ['VITE_SUPABASE_URL', 'VITE_SUPABASE_ANON_KEY']) {
  if (!(process.env[key] ?? env[key])) {
    console.warn(`${key} mangler. Konto-innlogging er ikke tilgjengelig i dette bygget. Sett verdien i .env og bygg på nytt.`)
  }
}

// API-kallene legger selv til /api; unngå doble skråstreker.
process.env.VITE_API_BASE = api.origin
console.log(`Mobilbygg bruker backend: ${api.origin}`)
await build({ root, mode: 'mobile', build: { outDir: 'mobile-dist' } })
