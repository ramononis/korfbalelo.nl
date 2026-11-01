import { describe, expect, it } from 'vitest'
import { ageRatingDeviation } from '../RatingDeviation'

// AI generated: fixtures shared with the backend calendar-aging tests.
describe('Glicko uncertainty growth', () => {
  it('adds variance by elapsed time, independently of the number of calls', () => {
    const direct = ageRatingDeviation(100, 30, 10.423068, 450)
    const staged = ageRatingDeviation(ageRatingDeviation(100, 10, 10.423068, 450), 20, 10.423068, 450)
    expect(direct).toBeCloseTo(115.14864478568005, 10)
    expect(staged).toBeCloseTo(direct, 10)
  })
  it('caps uncertainty and never ages backwards', () => {
    expect(ageRatingDeviation(100, 10000, 10.423068, 450)).toBe(450)
    expect(ageRatingDeviation(100, -10, 10.423068, 450)).toBe(100)
  })
})
