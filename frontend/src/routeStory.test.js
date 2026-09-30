import { describe, it, expect } from 'vitest'
import { fetchRouteStory, storyRequestFromPlan } from './routeStory.js'

const plan = {
  distanceKm: 12.34, ascentM: 850.4, hours: 4.56, calories: 780.4, snacks: ['Nøtter', 'Vann'],
  assessment: { difficultyLabel: 'Middels', highestPointM: 1120.5, maxGradientPct: 18.4, challenges: ['Bratt siste del'] },
}

describe('storyRequestFromPlan', () => {
  it('maps the route plan to the request the backend expects', () => {
    expect(storyRequestFromPlan(plan, '  Slogen ', 'nb')).toEqual({
      name: 'Slogen', distanceKm: 12.34, ascentM: 850.4, hours: 4.56, calories: 780,
      difficulty: 'Middels', highestPointM: 1120.5, maxGradientPct: 18.4,
      challenges: ['Bratt siste del'], snacks: ['Nøtter', 'Vann'], lang: 'nb',
    })
  })

  it('falls back to a generic name in the right language', () => {
    expect(storyRequestFromPlan(plan, '', 'nb').name).toBe('Turen')
    expect(storyRequestFromPlan(plan, undefined, 'en').name).toBe('The hike')
    expect(storyRequestFromPlan(plan, 'x', 'sv').lang).toBe('nb') // ukjent språk -> norsk
  })

  it('keeps every value inside the backend limits so the request is never rejected for size', () => {
    const wild = {
      distanceKm: 99999, ascentM: -5, hours: 1e6, calories: 1e9,
      snacks: Array.from({ length: 30 }, () => 'y'.repeat(500)),
      assessment: { difficultyLabel: 'z'.repeat(99), highestPointM: 99999, maxGradientPct: 250,
        challenges: Array.from({ length: 30 }, () => 'c'.repeat(500)) },
    }
    const r = storyRequestFromPlan(wild, 'n'.repeat(500), 'nb')
    expect(r.name.length).toBe(120)
    expect([r.distanceKm, r.ascentM, r.hours, r.calories]).toEqual([1000, 0, 200, 100000])
    expect([r.highestPointM, r.maxGradientPct]).toEqual([9000, 100])
    expect(r.difficulty.length).toBe(40)
    expect(r.challenges.length).toBe(10)
    expect(r.challenges[0].length).toBe(200)
    expect(r.snacks.length).toBe(10)
    expect(r.snacks[0].length).toBe(120)
  })

  it('leaves out terrain data when the elevation profile was unavailable', () => {
    const r = storyRequestFromPlan({ distanceKm: 5, ascentM: 0, hours: 1, calories: 300, snacks: [] }, 'Rett', 'nb')
    expect([r.difficulty, r.highestPointM, r.maxGradientPct]).toEqual([null, null, null])
    expect(r.challenges).toEqual([])
  })
})

describe('fetchRouteStory', () => {
  it('posts to the story endpoint and returns the text', async () => {
    let seen
    const fetchFn = async (url, init) => { seen = { url, init }; return { ok: true, json: async () => ({ story: 'En fin tur.' }) } }
    const text = await fetchRouteStory({ plan, name: 'Slogen', lang: 'nb', headers: { Authorization: 'Bearer x' }, fetchFn })
    expect(text).toBe('En fin tur.')
    expect(seen.url).toMatch(/\/api\/ruter\/fortelling$/)
    expect(seen.init.method).toBe('POST')
    expect(seen.init.headers).toEqual({ 'Content-Type': 'application/json', Authorization: 'Bearer x' })
    expect(JSON.parse(seen.init.body).name).toBe('Slogen')
  })

  it("shows the server's message, e.g. when the AI quota is used up", async () => {
    const fetchFn = async () => ({ ok: false, status: 429, json: async () => ({ error: 'Du har brukt opp AI-kvoten for timen.' }) })
    await expect(fetchRouteStory({ plan, name: 'x', lang: 'nb', headers: {}, fetchFn })).rejects.toThrow('AI-kvoten')
  })
})
