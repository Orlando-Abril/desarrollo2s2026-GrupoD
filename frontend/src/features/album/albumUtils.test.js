import { describe, expect, it } from 'vitest'
import {
  buildPageWindow,
  deriveTeamOptions,
  filterByName,
  normalizeText,
  paginate,
  sortPlayers,
} from './albumUtils.js'

describe('albumUtils', () => {
  it('normalizes accents, case and surrounding whitespace', () => {
    expect(normalizeText('  ÁNGEL Di María  ')).toBe('angel di maria')
  })

  it('filters player names without distinguishing accents or case', () => {
    const players = [{ fullName: 'Ángel Correa' }, { fullName: 'Lionel Messi' }]
    expect(filterByName(players, 'ANGEL')).toEqual([players[0]])
    expect(filterByName(players, '   ')).toEqual(players)
  })

  it('sorts by fullName in Spanish without mutating input', () => {
    const players = [{ fullName: 'Zárate' }, { fullName: 'Ángel' }, { fullName: 'Benítez' }]
    const original = [...players]
    expect(sortPlayers(players).map(({ fullName }) => fullName)).toEqual(['Ángel', 'Benítez', 'Zárate'])
    expect(players).toEqual(original)
  })

  it('derives exact unique team options sorted in Spanish', () => {
    const players = [{ team: 'Écija' }, { team: 'Arsenal FC' }, { team: 'Écija' }, { team: 'ecija' }]
    expect(deriveTeamOptions(players)).toEqual([
      { value: 'Arsenal FC', label: 'Arsenal FC' },
      { value: 'ecija', label: 'ecija' },
      { value: 'Écija', label: 'Écija' },
    ])
  })

  it.each([
    [0, { total: 0, totalPages: 0, page: 1, from: 0, to: 0, count: 0 }],
    [24, { total: 24, totalPages: 1, page: 1, from: 1, to: 24, count: 24 }],
    [25, { total: 25, totalPages: 2, page: 2, from: 25, to: 25, count: 1 }],
  ])('paginates %s items at the boundaries', (length, expected) => {
    const result = paginate(Array.from({ length }, (_, index) => index + 1), length === 25 ? 2 : 1)
    expect({ ...result, items: undefined }).toEqual({
      total: expected.total,
      totalPages: expected.totalPages,
      page: expected.page,
      from: expected.from,
      to: expected.to,
      items: undefined,
    })
    expect(result.items).toHaveLength(expected.count)
  })

  it('clamps pages to the valid range', () => {
    expect(paginate(Array.from({ length: 25 }), 99).page).toBe(2)
  })

  it.each([
    [1, 10, [1, 2, 'ellipsis', 10]],
    [5, 10, [1, 'ellipsis', 4, 5, 6, 'ellipsis', 10]],
    [10, 10, [1, 'ellipsis', 9, 10]],
    [2, 3, [1, 2, 3]],
    [1, 0, []],
  ])('builds the pager window for page %s of %s', (page, totalPages, expected) => {
    expect(buildPageWindow(page, totalPages)).toEqual(expected)
  })
})
