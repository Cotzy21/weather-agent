import { useState } from 'react'
import { useI18n } from './i18n.jsx'
import ChoiceChips from './ChoiceChips.jsx'

const EMPTY_PROFILE = {
  experienceLevel: '', trainingMonths: null, sessionsPerWeek: null, goal: '', equipment: '',
  daysPerWeek: 3, sessionMinutes: 45, technique: '', explanationStyle: 'BRIEF',
  injuries: '', background: '', strengths: '', weaknesses: '',
}

const LEVELS = [
  { v: 'BEGINNER', t: 'Helt ny', d: 'Har aldri trent, eller nesten ikke' },
  { v: 'NOVICE', t: 'Litt erfaren', d: 'Har trent under ca. 1 år' },
  { v: 'INTERMEDIATE', t: 'Middels erfaren', d: 'Trent jevnt i 1–3 år' },
  { v: 'ADVANCED', t: 'Avansert', d: 'Over 3 år, vet hva jeg vil ha' },
]
// [months, label] – «hvor lenge har du trent»
const MONTHS = [[0, 'Aldri'], [3, '< 6 mnd'], [9, '6–12 mnd'], [24, '1–3 år'], [60, '3–7 år'], [96, '7+ år']]
const SESSIONS = [[0, '0'], [1, '1'], [2, '2'], [3, '3'], [4, '4'], [5, '5+']]
const GOALS = [
  ['STRENGTH', 'Bli sterkere'], ['MUSCLE', 'Bygge muskler'], ['FAT_LOSS', 'Gå ned i vekt'],
  ['ENDURANCE', 'Kondisjon'], ['HEALTH', 'Generell helse'],
]
const EQUIPMENT = [['GYM', 'Treningssenter'], ['HOME_WEIGHTS', 'Hjemme m/ manualer'], ['BODYWEIGHT', 'Kun kroppsvekt']]
const TECHNIQUE = [['LOW', 'Kan lite'], ['MEDIUM', 'Grunnleggende'], ['HIGH', 'Trygg']]
const MINUTES = [[30, '30'], [45, '45'], [60, '60'], [90, '90']]
const DAYS = [[2, '2'], [3, '3'], [4, '4'], [5, '5'], [6, '6']]
const STEPS = 4

const opts = (list) => list.map(([v, tx]) => ({ v, tx }))

// Onboarding: spør om erfaring, mål og begrensninger, så AI-en kan lage planer som passer.
// Brukes både første gang (initial = tom) og fra «Rediger treningsprofil».
export default function TrainingOnboarding({ initial, onSave, onSkip }) {
  const { t } = useI18n()
  const [p, setP] = useState({ ...EMPTY_PROFILE, ...initial })
  const [step, setStep] = useState(0)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const set = (patch) => setP((cur) => ({ ...cur, ...patch }))
  const chips = (list, key) => (
    <ChoiceChips options={opts(list).map((o) => ({ v: o.v, t: t(o.tx) }))} value={p[key]} onChange={(v) => set({ [key]: v })} />
  )

  // Hvert steg må ha sine påkrevde svar før «Neste».
  const complete = [
    p.experienceLevel && p.trainingMonths !== null && p.sessionsPerWeek !== null,
    p.goal && p.equipment,
    !!p.technique,
    true,
  ][step]

  async function finish() {
    setBusy(true); setError(null)
    try {
      await onSave(p)
    } catch (e) {
      setError(e.message)
      setBusy(false)
    }
  }

  return (
    <div className="sheet-backdrop">
      <div className="sheet onboarding" role="dialog" aria-label={t('Fortell oss om treningen din')}>
        <span className="sheet-handle" aria-hidden="true" />
        <div className="picker-head">
          <strong>{t('Fortell oss om treningen din')}</strong>
          <span className="muted">{step + 1}/{STEPS}</span>
        </div>
        <div className="onboarding-body">
          {step === 0 && (
            <>
              <p className="muted">{t('Slik kan AI-en lage planer som passer nivået ditt. Du kan endre alt senere.')}</p>
              <div className="onboarding-levels" role="radiogroup" aria-label={t('Erfaringsnivå')}>
                {LEVELS.map((l) => (
                  <button key={l.v} type="button" role="radio" aria-checked={p.experienceLevel === l.v}
                          className={`level-card ${p.experienceLevel === l.v ? 'on' : ''}`}
                          onClick={() => set({ experienceLevel: l.v })}>
                    <strong>{t(l.t)}</strong><span>{t(l.d)}</span>
                  </button>
                ))}
              </div>
              <div className="field">{t('Hvor lenge har du trent totalt?')}
                {chips(MONTHS.map(([v, tx]) => [v, tx]), 'trainingMonths')}
              </div>
              <div className="field">{t('Økter per uke siste 3 måneder')}
                {chips(SESSIONS, 'sessionsPerWeek')}
              </div>
            </>
          )}
          {step === 1 && (
            <>
              <div className="field">{t('Hva er målet ditt?')}{chips(GOALS, 'goal')}</div>
              <div className="field">{t('Hvor trener du?')}{chips(EQUIPMENT, 'equipment')}</div>
              <div className="field">{t('Dager per uke du vil trene')}{chips(DAYS, 'daysPerWeek')}</div>
              <div className="field">{t('Minutter per økt')}{chips(MINUTES, 'sessionMinutes')}</div>
            </>
          )}
          {step === 2 && (
            <>
              <div className="field">{t('Hvor trygg er du på teknikken i knebøy, markløft og benkpress?')}
                {chips(TECHNIQUE, 'technique')}
              </div>
              <label className="field">{t('Skader eller smerter (valgfritt)')}
                <textarea rows="2" maxLength={300} value={p.injuries} onChange={(e) => set({ injuries: e.target.value })}
                          placeholder={t('F.eks. «vondt i venstre kne», «ryggproblemer»')} />
              </label>
              <label className="field">{t('Tidligere trening (valgfritt)')}
                <input maxLength={200} value={p.background} onChange={(e) => set({ background: e.target.value })}
                       placeholder={t('F.eks. «fotball i 10 år», «løper 5 km»')} />
              </label>
            </>
          )}
          {step === 3 && (
            <>
              <label className="field">{t('Hva er du god på? (valgfritt)')}
                <input maxLength={200} value={p.strengths} onChange={(e) => set({ strengths: e.target.value })}
                       placeholder={t('F.eks. «sterk overkropp»')} />
              </label>
              <label className="field">{t('Hva vil du bli bedre på? (valgfritt)')}
                <input maxLength={200} value={p.weaknesses} onChange={(e) => set({ weaknesses: e.target.value })}
                       placeholder={t('F.eks. «svake bein», «dårlig mobilitet»')} />
              </label>
              <div className="field">{t('Hvordan vil du ha forklaringene?')}
                {chips([['BRIEF', 'Korte og konkrete'], ['DETAILED', 'Utdypende']], 'explanationStyle')}
              </div>
            </>
          )}
          {error && <p className="error">{error}</p>}
        </div>
        <div className="onboarding-actions">
          {step > 0
            ? <button className="confirm-keep" onClick={() => setStep(step - 1)}>{t('Tilbake')}</button>
            : onSkip && <button className="confirm-keep" onClick={onSkip}>{t('Hopp over')}</button>}
          {step < STEPS - 1
            ? <button disabled={!complete} onClick={() => setStep(step + 1)}>{t('Neste')}</button>
            : <button disabled={busy} onClick={finish}>{busy ? t('Lagrer…') : t('Lagre profil')}</button>}
        </div>
      </div>
    </div>
  )
}
