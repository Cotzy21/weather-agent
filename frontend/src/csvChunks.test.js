import { describe, it, expect } from 'vitest'
import { splitCsvRows, csvChunks } from './csvChunks.js'

describe('splitCsvRows', () => {
  it('handles CRLF, BOM and blank lines', () => {
    expect(splitCsvRows('﻿a,b\r\n1,2\r\n\r\n3,4\r\n')).toEqual(['a,b', '1,2', '3,4'])
  })
  it('keeps a newline inside a quoted field within its row', () => {
    const rows = splitCsvRows('Tittel,Tid\n"Push\nA",10\nPull,20')
    expect(rows).toEqual(['Tittel,Tid', '"Push\nA",10', 'Pull,20'])
  })
  it('treats doubled quotes as an escaped quote, not the end of the field', () => {
    expect(splitCsvRows('a\n"si ""hei""\nfortsatt",1\nb')).toEqual(['a', '"si ""hei""\nfortsatt",1', 'b'])
  })
  it('copes with no trailing newline and with empty input', () => {
    expect(splitCsvRows('a\nb')).toEqual(['a', 'b'])
    expect(splitCsvRows('')).toEqual([])
    expect(splitCsvRows(null)).toEqual([])
  })
})

describe('csvChunks', () => {
  const csv = ['Type,Dato,Tittel', ...Array.from({ length: 10 }, (_, i) => `Styrke,2026-01-${String(i + 1).padStart(2, '0')},Økt ${i}`)].join('\n')

  it('repeats the header in every chunk and covers every row exactly once', () => {
    const chunks = csvChunks(csv, 4)
    expect(chunks.map((c) => c.rows)).toEqual([4, 4, 2])
    for (const c of chunks) expect(c.text.startsWith('Type,Dato,Tittel\n')).toBe(true)
    const all = chunks.flatMap((c) => c.text.split('\n').slice(1))
    expect(all.length).toBe(10)
    expect(new Set(all).size).toBe(10)
  })
  it('gives no chunks for a file without data rows', () => {
    expect(csvChunks('Type,Dato,Tittel\n')).toEqual([])
    expect(csvChunks('')).toEqual([])
  })
  it('handles several hundred activities without losing any', () => {
    const big = ['Type,Dato,Tittel', ...Array.from({ length: 837 }, (_, i) => `Styrke,2026-01-01,Økt ${i}`)].join('\n')
    const chunks = csvChunks(big, 250)
    expect(chunks.length).toBe(4)
    expect(chunks.reduce((n, c) => n + c.rows, 0)).toBe(837)
  })
})
