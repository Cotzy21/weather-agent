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
})
