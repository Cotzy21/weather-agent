import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Dev-tjeneren kjører på :5173. Vi proxyer /api videre til Spring på :8080,
// slik at nettleseren ser samme opphav og vi slipper CORS-oppsett.
// https://vite.dev/config/
export default defineConfig(({ mode }) => ({
  plugins: [react()],
  server: {
    // Øvelseskatalogen deles med backend (src/main/resources/exercises.json), utenfor frontend-mappa.
    fs: { allow: ['..', '../src/main/resources'] },
    // Tillater mobil-testing via Cloudflare-tunnel (`npm run tunnel`).
    allowedHosts: ['.trycloudflare.com'],
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'node',
    include: ['src/**/*.test.js'],
  },
  build: {
    // `npm run build` legger den ferdige SPA-en rett inn i Spring sin static-mappe,
    // så `mvn package` pakker alt i én kjørbar jar (én port i produksjon).
    // Native bygg (`--mode native`, se MOBILE.md) går til egen mappe, så Spring-bygget ikke overskrives med et
    // bygg som peker på en fast API-adresse.
    outDir: mode === 'native' ? 'dist-native' : '../src/main/resources/static',
    emptyOutDir: true,
  },
}))
