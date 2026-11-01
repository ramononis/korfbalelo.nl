import { describe, expect, it } from 'vitest'
import { prepareVolatility } from '../Volatility'

// AI generated: identical numerical fixtures to backend volatility preparation.
describe('volatility calendar preparation', () => {
  it('preserves legacy snapshots and fixes shared volatility', () => {
    expect(prepareVolatility(.04, 100, {})).toBe(.04)
    expect(prepareVolatility(.04, 100, { VOLATILITY_MODE: 'LEGACY' })).toBe(.04)
    expect(prepareVolatility(.04, 100, { VOLATILITY_MODE: 'SHARED', VOLATILITY_INITIAL: .075 })).toBe(.075)
  })
  it('halves log distance to the prior over one half-life', () => {
    expect(prepareVolatility(.03, 365, { VOLATILITY_MODE: 'GOAL_MARGIN',
      VOLATILITY_INITIAL: .06, VOLATILITY_HALF_LIFE_DAYS: 365 })).toBeCloseTo(Math.sqrt(.03 * .06), 12)
    expect(prepareVolatility(.03, 365, { VOLATILITY_MODE: 'GOAL_MARGIN',
      VOLATILITY_HALF_LIFE_DAYS: 0 })).toBeCloseTo(.03, 12)
  })
})
