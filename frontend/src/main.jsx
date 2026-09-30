import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import '@fontsource-variable/dm-sans'
import './index.css'
import App from './App.jsx'
import { I18nProvider } from './i18n.jsx'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <I18nProvider>
      <App />
    </I18nProvider>
  </StrictMode>,
)

// Service worker (kun produksjon på http/https, ikke i den native Capacitor-appen som har filene lokalt).
if (import.meta.env.PROD && 'serviceWorker' in navigator && location.protocol.startsWith('http')
    && !window.Capacitor?.isNativePlatform?.()) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => { /* ikke kritisk */ })
  })
}
