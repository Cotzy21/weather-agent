import { describe, it, expect } from 'vitest'
import { mergeLayout, WIDGETS } from './dashboardLayout.js'

describe('mergeLayout', () => {
  it('gives every widget, visible, in default order when nothing is saved', () => {
    const l = mergeLayout(null)
    expect(l.map((w) => w.id)).toEqual(WIDGETS.map((w) => w.id))
    expect(l.every((w) => w.on)).toBe(true)
  })

  it('keeps the saved order and hidden state', () => {
    const l = mergeLayout([{ id: 'nutrition', on: false }, { id: 'week', on: true }])
    expect(l[0]).toEqual({ id: 'nutrition', on: false })
    expect(l[1]).toEqual({ id: 'week', on: true })
  })

  it('appends widgets added after the user saved, visible', () => {
    const l = mergeLayout([{ id: 'week', on: true }])
    expect(l.length).toBe(WIDGETS.length)
    expect(l.at(-1).on).toBe(true)
  })

  it('drops widgets that no longer exist', () => {
    const l = mergeLayout([{ id: 'gammelt-kort', on: true }, { id: 'week', on: true }])
    expect(l.find((w) => w.id === 'gammelt-kort')).toBeUndefined()
  })
})
