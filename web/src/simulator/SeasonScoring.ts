// AI generated: mirrors Kotlin SeasonScoring; state is immutable during simulation.
export type ScoringState = number[][]
export function scoringValue(state: ScoringState, date: Date): number {
  const month = date.getUTCMonth() + 1
  const s = state[month >= 4 && month <= 10 ? 0 : 1]
  if (!s) return 0
  const day = Math.floor(date.getTime() / 86400000)
  const decay = 0.5 ** (Math.max(0, day - s[2]!) / 180)
  const pending = day > s[5]! ? 0.5 ** ((day - s[5]!) / 180) : 0
  return (s[0]! * decay + s[3]! * pending) / (1 + s[1]! * decay + s[4]! * pending)
}
export function scoringCorrection(home: ScoringState, away: ScoringState, date: Date | undefined, sd: number): number {
  if (!date) return 0
  return Math.max(-3, Math.min(3, 0.75 * sd * (scoringValue(home, date) + scoringValue(away, date)) / 2))
}
