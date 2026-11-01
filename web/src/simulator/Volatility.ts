import type { MetaData } from './SeasonSimulator'

// AI generated: calendar preparation shared by backend and browser simulations.
export function prepareVolatility(sigma: number, elapsedDays: number, meta: Partial<MetaData>): number {
  if (!meta.VOLATILITY_MODE || meta.VOLATILITY_MODE === 'LEGACY') return sigma
  const initial = meta.VOLATILITY_INITIAL ?? 0.06
  if (meta.VOLATILITY_MODE === 'SHARED') return initial
  const halfLife = meta.VOLATILITY_HALF_LIFE_DAYS ?? 730
  const retention = halfLife > 0 ? Math.pow(0.5, elapsedDays / halfLife) : 1
  return Math.max(meta.VOLATILITY_MINIMUM ?? 0.015, Math.min(meta.VOLATILITY_MAXIMUM ?? 0.20,
    Math.exp(Math.log(initial) + retention * (Math.log(sigma) - Math.log(initial)))))
}
