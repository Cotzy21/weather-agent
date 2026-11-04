import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
const state = vi.hoisted(() => ({ layers: [], elements: [], controls: [] }))
vi.mock('leaflet', () => ({ default: {
  tileLayer: (url, options) => {
    const events = {}
    const layer = { url, options, on: (event, fn) => { events[event] = fn; return layer }, emit: event => events[event]?.(), addTo: map => { map.layers.add(layer); return layer }, redraw: vi.fn() }
    state.layers.push(layer); return layer
  },
  control: Object.assign(() => { const c = { addTo: () => { state.controls.push(c); return c } }; return c }, { layers: () => ({ addTo: vi.fn() }) }),
  DomUtil: { create: (tag, className, parent) => { const e = { tag, className, children: [], setAttribute: vi.fn() }; parent?.children.push(e); state.elements.push(e); return e } },
  DomEvent: { disableClickPropagation: vi.fn(), disableScrollPropagation: vi.fn(), on: (el, event, fn) => { el[event] = fn }, off: (el, event) => { delete el[event] } },
} }))
import { addMapLayers, MAP_SOURCES } from './mapLayers.js'
function mockMap() {
  const events = {}
  return { layers: new Set(), center: { lat: 59, lng: 6 }, removeLayer(layer) { this.layers.delete(layer) }, on: (e, fn) => { events[e] = fn }, once: (e, fn) => { events[e] = fn }, off: e => { delete events[e] }, emit: (e, payload) => events[e]?.(payload), getCenter() { return this.center } }
}
beforeEach(() => { vi.useFakeTimers(); state.layers = []; state.elements = []; state.controls = [] })
afterEach(() => { vi.useRealTimers() })
describe('map provider recovery', () => {
  it('uses the documented WMTS coordinate order and replaces OSM tiles', () => {
    expect(MAP_SOURCES.standard.url).toContain('/{z}/{y}/{x}.png')
    expect(Object.values(MAP_SOURCES).some(s => s.url.includes('openstreetmap'))).toBe(false)
  })
  it('switches to existing worldwide imagery after repeated tile failures', () => {
    const map = mockMap(); const layers = addMapLayers(map)
    for (let i = 0; i < 4; i++) layers.standard.emit('tileerror')
    expect(map.layers.has(layers.standard)).toBe(false)
    expect(map.layers.has(layers.satellite)).toBe(true)
    layers.satellite.emit('tileload')
    expect(state.elements[1].textContent).toContain('satellittkart')
    expect(state.elements[2].hidden).toBe(false)
    map.emit('unload')
  })
  it('shows a retry action instead of switching forever when both services fail', () => {
    const map = mockMap(); const layers = addMapLayers(map)
    vi.advanceTimersByTime(8000); vi.advanceTimersByTime(8000)
    expect(state.elements[1].textContent).toContain('kan ikke lastes')
    expect(map.layers.has(layers.satellite)).toBe(true)
    state.elements[2].click()
    expect(map.layers.has(layers.standard)).toBe(true)
    expect(layers.standard.redraw).toHaveBeenCalledOnce()
    map.emit('unload')
  })
  it('does not fall back when images load successfully and cancels timers on unmount', () => {
    const map = mockMap(); const layers = addMapLayers(map)
    layers.standard.emit('tileload'); vi.advanceTimersByTime(9000)
    expect(map.layers.has(layers.standard)).toBe(true)
    expect(state.elements[0].hidden).toBe(true)
    layers.standard.emit('loading'); map.emit('unload'); vi.advanceTimersByTime(9000)
    expect(map.layers.has(layers.satellite)).toBe(false)
  })
  it('uses worldwide imagery outside the national coverage', () => {
    const map = mockMap(); const layers = addMapLayers(map)
    map.center = { lat: 48, lng: 10 }; map.emit('moveend')
    expect(map.layers.has(layers.satellite)).toBe(true)
    map.center = { lat: 59, lng: 6 }; map.emit('moveend')
    expect(map.layers.has(layers.standard)).toBe(true)
    map.emit('unload')
  })
})
