import { describe, expect, it, vi } from 'vitest'
import { Team } from '@/types'
import { sampleND } from '@/utils'

// AI generated: verify that simulation consumes the shared exported growth rate.
vi.mock('@/simulator/SeasonSimulator', () => ({
  metaData: { RD_GROWTH_PER_SQRT_DAY: 10.423068, RD_MAX: 450 },
}))
vi.mock('@/utils', () => ({
  formattedDiff: vi.fn(),
  sampleND: vi.fn(([rating]: [number, number]) => rating),
}))

describe('future team strength sampling', () => {
  it('ages the current RD and adds fresh uncertainty after an earlier sample', () => {
    const team = new Team('A', 1500, new Date('2026-01-01'), 0, 'Test', 100, 15)
    team.sampleRating('2026-01-31')
    expect(sampleND).toHaveBeenLastCalledWith([1500, Math.sqrt(10000 + 30 * 10.423068 ** 2)])
    expect(team.rd).toBe(0)
    expect(team.lastMatchDate.toISOString().slice(0, 10)).toBe('2026-01-31')
    team.sampleRating('2026-02-10')
    expect(sampleND).toHaveBeenLastCalledWith([1500, Math.sqrt(10 * 10.423068 ** 2)])
  })
})
