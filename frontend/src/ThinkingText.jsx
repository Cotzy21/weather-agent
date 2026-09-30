import { useEffect, useState } from 'react'
import { useI18n } from './i18n.jsx'

const STEPS = [
  'Leser historikken din …',
  'Velger øvelser som passer nivået ditt …',
  'Setter sammen øktene …',
  'Sjekker planen mot utstyr og ønsker …',
]

// AI-svar tar noen sekunder; vis hva som skjer i stedet for bare en spinner.
export default function ThinkingText() {
  const { t } = useI18n()
  const [i, setI] = useState(0)
  useEffect(() => {
    const id = setInterval(() => setI((n) => Math.min(n + 1, STEPS.length - 1)), 2500)
    return () => clearInterval(id)
  }, [])
  return (
    <p className="muted typing" role="status">{t(STEPS[i])} <span>·</span><span>·</span><span>·</span></p>
  )
}
