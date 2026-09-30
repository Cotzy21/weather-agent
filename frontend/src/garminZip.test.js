import { describe, it, expect } from 'vitest'
import { zipSync, strToU8 } from 'fflate'
import { forEachFit } from './garminZip.js'
import { makeFit } from './fitTestUtils.js'

const fitA = makeFit({ start: '2026-05-05T17:00:00Z', sets: [{ category: 'squat', sub: 0, reps: 5, weight: 100 }] })
const fitB = makeFit({ start: '2026-05-06T17:00:00Z', sets: [{ category: 'benchPress', sub: 1, reps: 5, weight: 80 }] })

const file = (bytes, name) => new File([bytes], name)

async function collect(f) {
  const found = []
  const skipped = []
  await forEachFit(f, async (bytes, name) => { found.push({ len: bytes.length, name }) }, { onSkip: (n) => skipped.push(n) })
  return { found, skipped }
}

describe('forEachFit', () => {
  it('passes a plain .fit file straight through', async () => {
    const { found } = await collect(file(fitA, 'aktivitet.fit'))
    expect(found).toEqual([{ len: fitA.length, name: 'aktivitet.fit' }])
  })

  it('extracts only .fit files from a zip and ignores everything else', async () => {
    const zip = zipSync({ 'a.fit': fitA, 'mappe/b.FIT': fitB, 'notater.txt': strToU8('hei'), 'bilde.png': new Uint8Array(1000) })
    const { found } = await collect(file(zip, 'eksport.zip'))
    expect(found.map((f) => f.name).sort()).toEqual(['a.fit', 'mappe/b.FIT'])
    expect(found.every((f) => f.len > 50)).toBe(true)
  })

  it('opens zips inside zips, like Garmin\'s full data export', async () => {
    const inner = zipSync({ 'UploadedFiles/1.fit': fitA, 'UploadedFiles/2.fit': fitB })
    const outer = zipSync({ 'DI_CONNECT/DI-Connect-Uploaded-Files/UploadedFiles_0-_Part1.zip': [inner, { level: 0 }], 'DI_CONNECT/summary.json': strToU8('{}') })
    const { found } = await collect(file(outer, 'export.zip'))
    expect(found).toHaveLength(2)
  })

  it('returns the exact bytes of each file', async () => {
    const zip = zipSync({ 'a.fit': fitA })
    let got
    await forEachFit(file(zip, 'x.zip'), async (bytes) => { got = bytes })
    expect(Array.from(got)).toEqual(Array.from(fitA))
  })

  it('ignores files that are neither fit nor zip, and survives a broken zip', async () => {
    expect((await collect(file(strToU8('x'), 'notater.txt'))).found).toEqual([])
    const broken = await collect(file(strToU8('PK dette er ikke en ekte zip'), 'kaputt.zip'))
    expect(broken.found).toEqual([])
  })

  it('processes files one at a time, in order', async () => {
    const zip = zipSync({ '1.fit': fitA, '2.fit': fitB })
    const order = []
    await forEachFit(file(zip, 'x.zip'), async (_b, name) => {
      order.push(`start ${name}`)
      await new Promise((r) => setTimeout(r, 5))
      order.push(`end ${name}`)
    })
    expect(order).toEqual(['start 1.fit', 'end 1.fit', 'start 2.fit', 'end 2.fit'])
  })
})
