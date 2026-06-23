import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Dev-tjeneren kjører på :5173. Vi proxyer /api videre til Spring på :8080,
// slik at nettleseren ser samme opphav og vi slipper CORS-oppsett.
// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  build: {
    // `npm run build` legger den ferdige SPA-en rett inn i Spring sin static-mappe,
    // så `mvn package` pakker alt i én kjørbar jar (én port i produksjon).
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
  },
})
