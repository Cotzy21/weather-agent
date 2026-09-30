import { describe, it, expect } from 'vitest'
import { escapeHtml } from './escapeHtml.js'

describe('escapeHtml', () => {
  it('neutralises markup from user-editable map data', () => {
    expect(escapeHtml('<img src=x onerror=alert(1)>')).toBe('&lt;img src=x onerror=alert(1)&gt;')
    expect(escapeHtml('"><script>x</script>')).toBe('&quot;&gt;&lt;script&gt;x&lt;/script&gt;')
  })
  it('escapes ampersands first so entities cannot be smuggled in', () => {
    expect(escapeHtml('&lt;')).toBe('&amp;lt;')
  })
  it('leaves ordinary Norwegian names alone and copes with null', () => {
    expect(escapeHtml('Trolltunga – Ødegård')).toBe('Trolltunga – Ødegård')
    expect(escapeHtml(null)).toBe('')
    expect(escapeHtml(undefined)).toBe('')
  })
})
