import { describe, it, expect } from 'vitest'
import { isValidBarcode, lookupBarcode, pickBarcode, scannerSupported } from './barcode.js'

describe('isValidBarcode', () => {
  it('accepts 8 to 14 digits (EAN-8, UPC-A, EAN-13, GTIN-14)', () => {
    for (const ok of ['12345678', '036000291452', '7622210449283', '10012345678902', ' 7622210449283 ']) {
      expect(isValidBarcode(ok)).toBe(true)
    }
  })
  it('rejects everything else', () => {
    for (const no of ['', '1234567', '123456789012345', '76222104492x3', '7622 2104 49283', null, undefined]) {
      expect(isValidBarcode(no)).toBe(false)
    }
  })
})

describe('pickBarcode', () => {
  it('returns the first valid code the detector found and skips junk', () => {
    expect(pickBarcode([{ rawValue: 'https://x.no' }, { rawValue: '7622210449283' }, { rawValue: '12345678' }])).toBe('7622210449283')
  })
  it('returns null when nothing usable was seen', () => {
    expect(pickBarcode([])).toBeNull()
    expect(pickBarcode([{ rawValue: 'QR-tekst' }])).toBeNull()
    expect(pickBarcode(undefined)).toBeNull()
  })
})

describe('scannerSupported', () => {
  it('needs both BarcodeDetector and getUserMedia', () => {
    expect(scannerSupported({ BarcodeDetector: function () {}, navigator: { mediaDevices: { getUserMedia() {} } } })).toBe(true)
    expect(scannerSupported({ navigator: { mediaDevices: { getUserMedia() {} } } })).toBe(false) // iPhone/Safari
    expect(scannerSupported({ BarcodeDetector: function () {}, navigator: {} })).toBe(false)
    expect(scannerSupported({})).toBe(false)
  })
})

describe('lookupBarcode', () => {
  const headers = { Authorization: 'Bearer x' }

  it('returns the food when the server knows the code', async () => {
    let seen
    const fetchFn = async (url, init) => { seen = { url, init }; return { ok: true, status: 200, json: async () => ({ name: 'Prince', source: 'OPEN_FOOD_FACTS' }) } }
    const r = await lookupBarcode({ code: ' 7622210449283 ', headers, fetchFn })
    expect(r).toEqual({ status: 'found', code: '7622210449283', food: { name: 'Prince', source: 'OPEN_FOOD_FACTS' } })
    expect(seen.url).toMatch(/\/api\/kosthold\/strekkode\/7622210449283$/)
    expect(seen.init.headers).toEqual(headers)
  })

  it('reports notfound on 404 so the UI can offer to add the food', async () => {
    const fetchFn = async () => ({ ok: false, status: 404 })
    expect(await lookupBarcode({ code: '7038010009457', headers, fetchFn })).toEqual({ status: 'notfound', code: '7038010009457' })
  })

  it('never calls the server for an invalid code', async () => {
    let called = false
    const fetchFn = async () => { called = true; return { ok: true } }
    await expect(lookupBarcode({ code: '123', headers, fetchFn })).rejects.toThrow('8–14')
    expect(called).toBe(false)
  })

  it("shows the server's message on other errors (e.g. the own-food limit)", async () => {
    const fetchFn = async () => ({ ok: false, status: 400, json: async () => ({ error: 'Du har allerede 200 egne matvarer - slett noen først.' }) })
    await expect(lookupBarcode({ code: '7622210449283', headers, fetchFn })).rejects.toThrow('200 egne')
  })
})
