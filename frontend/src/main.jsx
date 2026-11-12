/* eslint-disable react-refresh/only-export-components */
import { StrictMode, Suspense, lazy } from 'react'
import { createRoot } from 'react-dom/client'
import '@fontsource-variable/dm-sans'
import './index.css'
import Observatory from './Observatory.jsx'
const App = lazy(() => import('./App.jsx'))
const params = new URLSearchParams(location.search)
const showApp = params.has('app') || params.has('check') || location.hash === '#personvern' || window.Capacitor?.isNativePlatform?.()
import { I18nProvider } from './i18n.jsx'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <I18nProvider>
      <Suspense fallback={<p role="status">Laster …</p>}>
        {showApp ? <App /> : <Observatory />}
      </Suspense>
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
