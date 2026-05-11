export type SeasonMode = 'zaal' | 'veld'

export interface SeasonConfig {
  seasonName: string
  seasonCode: string
  mode: SeasonMode
}

export const INDOOR_SEASON: SeasonConfig = {
  seasonName: 'zaal2627',
  seasonCode: '2627',
  mode: 'zaal'
}

export const OUTDOOR_SEASON: SeasonConfig = {
  seasonName: 'veld2526vj',
  seasonCode: '2526',
  mode: 'veld'
}

export const ACTIVE_SEASON: SeasonConfig = OUTDOOR_SEASON
export const ACTIVE_SEASONS: SeasonConfig[] = [INDOOR_SEASON, OUTDOOR_SEASON]
export type KnownSeasonName = typeof INDOOR_SEASON.seasonName | typeof OUTDOOR_SEASON.seasonName | 'zaal2526'

export function isOutdoorSeasonName(seasonName: string): boolean {
  return seasonName.startsWith('veld')
}
