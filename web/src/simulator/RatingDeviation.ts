// AI generated: shared Glicko calendar aging, in rating points and elapsed days.
export function ageRatingDeviation(rd: number, days: number, growthPerSqrtDay: number, maxRd: number): number {
  return Math.min(maxRd, Math.sqrt(rd * rd + Math.max(0, days) * growthPerSqrtDay * growthPerSqrtDay))
}
