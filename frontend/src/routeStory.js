// «Fortell om turen»: send ruteplanens tall til backend og få en kort omtale fra språkmodellen tilbake.
import { apiUrl, readError } from './api.js'

const clamp = (v, min, max) => Math.min(max, Math.max(min, Number(v) || 0))
const clampOrNull = (v, min, max) => (v == null || Number.isNaN(Number(v)) ? null : clamp(v, min, max))
const texts = (list, maxLen) => (Array.isArray(list) ? list : []).slice(0, 10).map((s) => String(s).slice(0, maxLen))

/** Ruteplanen (svaret fra /api/rute) som forespørselen backend forventer. Verdiene holdes innenfor backends grenser. */
export function storyRequestFromPlan(plan, name, lang) {
  const a = plan.assessment
  return {
    name: String(name ?? '').trim().slice(0, 120) || (lang === 'en' ? 'The hike' : 'Turen'),
    distanceKm: clamp(plan.distanceKm, 0, 1000),
    ascentM: clamp(plan.ascentM, 0, 20000),
    hours: clamp(plan.hours, 0, 200),
    calories: Math.round(clamp(plan.calories, 0, 100000)),
    difficulty: a?.difficultyLabel ? String(a.difficultyLabel).slice(0, 40) : null,
    highestPointM: clampOrNull(a?.highestPointM, -500, 9000),
    maxGradientPct: clampOrNull(a?.maxGradientPct, 0, 100),
    challenges: texts(a?.challenges, 200),
    snacks: texts(plan.snacks, 120),
    lang: lang === 'en' ? 'en' : 'nb',
  }
}

/** POST /api/ruter/fortelling. Returnerer omtalen som tekst, eller kaster med en lesbar melding (også 429 når AI-kvoten er brukt opp). */
export async function fetchRouteStory({ plan, name, lang, headers, fetchFn = fetch }) {
  const res = await fetchFn(apiUrl('/api/ruter/fortelling'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify(storyRequestFromPlan(plan, name, lang)),
  })
  if (!res.ok) throw new Error(await readError(res))
  return (await res.json()).story
}
