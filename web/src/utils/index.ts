// Add this function to your script section
import { Team } from '@/types'
import { metaData } from '@/simulator/SeasonSimulator'
const _2PI = Math.PI * 2;

function gaussian(mu: number, sigma: number): number {
  let u = 0, v = 0
  while (u === 0) u = Math.random()
  while (v === 0) v = Math.random()

  const z = Math.sqrt(-2.0 * Math.log(u)) * Math.cos(_2PI * v)
  return z * sigma + mu
}

export function getRatingColor(rating: number | string): string {
  const ratingNumber = Number(rating)
  // Map rating from 900-2100 to 120-0 (hue: green to red)
  const hue = (-60 - (Math.max(800, ratingNumber) - 2500) * (160 / 1000) + 360) % 360;
  const colorValue = window.matchMedia("(prefers-color-scheme: dark)").matches ? '100%' : '30%';

  return `hsl(${hue}, ${colorValue}, 50%)`;}

function adjustedAverageScore(averageScore: number, date?: Date): number {
  if (
    metaData?.SCORE_SEASONALITY_MODE !== 'MONTHLY_OFFSET' ||
    !date ||
    !metaData.SCORE_SEASONALITY_MONTH_OFFSETS
  ) {
    return averageScore
  }

  const monthOffset = metaData.SCORE_SEASONALITY_MONTH_OFFSETS[date.getMonth()] ?? 0.0
  return Math.max(metaData.MIN_AVERAGE_SCORE ?? 0.5, averageScore + monthOffset)
}

export function distroBetween(home: Team, away: Team, neutral: boolean = false, date?: Date): [[number, number], [number, number]] {
  const rh = home.rating + (neutral ? 0.0 : metaData!.H)
  const ra = away.rating
  const muH = adjustedAverageScore(home.averageScore, date)
  const muA = adjustedAverageScore(away.averageScore, date)
  const marginScale = metaData!.MARGIN_RATING_SCALE ?? 400.0
  const twoP = 2.0 / (1.0 + 10 ** ((ra - rh) / marginScale))
  const sdH = muH * metaData!.SD_A + metaData!.SD_B
  const sdA = muA * metaData!.SD_A + metaData!.SD_B
  const sdDiff = Math.sqrt(sdH * sdH + sdA * sdA)
  const a = sdDiff * erfinv(twoP - 1.0) / SQRT2 - (muH - muA) / 2.0
  return [[muH + a, sdH], [muA - a, sdA]]
}
export function sampleND(nd: [number, number]): number {
  return gaussian(nd[0], nd[1])
}

export function sampleMatch([nd1, nd2]: [[number, number], [number, number]]): [number, number] {
  return [Math.round(sampleND(nd1)), Math.round(sampleND(nd2))]
}
export function erfinv(x: number) {
  // maximum relative error = .00013
  const a  = 0.147
  //if (0 == x) { return 0 }
  const b = 2/(Math.PI * a) + Math.log(1-x**2)/2
  const sqrt1 = Math.sqrt( b**2 - Math.log(1-x**2)/a )
  const sqrt2 = Math.sqrt( sqrt1 - b )
  return sqrt2 * Math.sign(x)
}
export const SQRT2 = Math.sqrt(2.0)

export function formattedDiff(diff: number): string {
  if (diff >= 0) {
    return `+${diff.toFixed(0)}`
  } else {
    return diff.toFixed(0)
  }
}
