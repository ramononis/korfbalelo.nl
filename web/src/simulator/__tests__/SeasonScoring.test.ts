// AI generated: same numeric vectors as Kotlin, including pending-day semantics.
import { describe, it, expect } from 'vitest'
import { scoringValue, scoringCorrection } from '../SeasonScoring'
describe('season scoring', () => {
  const day = Date.UTC(2026, 10, 1) / 86400000
  const state = [[0, 0, 0, 0, 0, -1000000], [0, 0, day, 4, 2, day]]
  it('withholds same-day outcomes and ages to fixture dates', () => {
    expect(scoringValue(state, new Date('2026-11-01'))).toBe(0)
    const decay = 0.5 ** (1 / 180)
    expect(scoringValue(state, new Date('2026-11-02'))).toBeCloseTo(4 * decay / (1 + 2 * decay), 12)
    expect(scoringValue(state, new Date('2027-04-01'))).toBe(0)
  })
  it('accepts older exports, caps symmetrically and does not mutate history', () => {
    expect(scoringValue([], new Date('2026-11-02'))).toBe(0)
    const before = JSON.stringify(state)
    expect(scoringCorrection(state, state, new Date('2026-11-02'), 10)).toBe(3)
    scoringValue(state, new Date('2030-11-01'))
    expect(JSON.stringify(state)).toBe(before)
  })
})
