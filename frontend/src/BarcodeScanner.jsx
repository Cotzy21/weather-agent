import { useEffect, useRef, useState } from 'react'
import { useI18n } from './i18n.jsx'
import { isValidBarcode, pickBarcode, scannerSupported } from './barcode.js'

// Strekkodeskanner: kameraet leser koden når nettleseren har BarcodeDetector (Chrome/Android). Uten (iPhone/Safari)
// eller uten kameratilgang skriver man koden inn; det feltet er alltid tilgjengelig som reserve.
export default function BarcodeScanner({ onCode, onClose }) {
  const { t } = useI18n()
  const videoRef = useRef(null)
  const [manual, setManual] = useState('')
  const [cameraError, setCameraError] = useState(null)
  const supported = scannerSupported()

  useEffect(() => {
    if (!supported) return undefined
    let stream = null
    let timer = null
    let stopped = false

    async function start() {
      try {
        stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' } }, audio: false })
        if (stopped) { stream.getTracks().forEach((tr) => tr.stop()); return }
        const video = videoRef.current
        video.srcObject = stream
        await video.play()
        let detector
        try { detector = new BarcodeDetector({ formats: ['ean_13', 'ean_8', 'upc_a', 'upc_e'] }) } catch { detector = new BarcodeDetector() }
        timer = setInterval(async () => {
          try {
            const code = pickBarcode(await detector.detect(video))
            if (code && !stopped) { stopped = true; onCode(code) }
          } catch { /* en ramme uten lesbar kode er normalt */ }
        }, 300)
      } catch {
        setCameraError(t('Kameraet er ikke tilgjengelig – skriv inn strekkoden i stedet.'))
      }
    }
    start()

    return () => {
      stopped = true
      clearInterval(timer)
      stream?.getTracks().forEach((tr) => tr.stop())
    }
  }, [supported]) // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <div className="barcode-scanner">
      {supported && !cameraError && (
        <>
          <video ref={videoRef} className="barcode-video" playsInline muted />
          <p className="muted">{t('Sikt kameraet mot strekkoden')}</p>
        </>
      )}
      {(!supported || cameraError) && (
        <p className="muted">{cameraError ?? t('Skanning med kamera støttes ikke i denne nettleseren – skriv inn strekkoden i stedet.')}</p>
      )}
      <div className="barcode-manual">
        <input inputMode="numeric" autoComplete="off" placeholder={t('Strekkode (8–14 siffer)')} value={manual}
               onChange={(e) => setManual(e.target.value)}
               onKeyDown={(e) => { if (e.key === 'Enter' && isValidBarcode(manual)) onCode(manual.trim()) }} />
        <button className="mini" disabled={!isValidBarcode(manual)} onClick={() => onCode(manual.trim())}>{t('Slå opp')}</button>
        <button className="mini" onClick={onClose}>{t('Lukk')}</button>
      </div>
    </div>
  )
}
