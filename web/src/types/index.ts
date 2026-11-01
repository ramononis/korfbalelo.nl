import { metaData } from '@/simulator/SeasonSimulator'
import { formattedDiff, sampleND } from '@/utils'

const D = 173.7178
const DAY_MILLIS = 1000 * 60 * 60 * 24;
export class Team {
  constructor(
    public name: string, // e.g. PKC, or TOP (S), or Achilles (Al)
    public rating: number,
    public lastMatchDate: Date,
    public seasonRatingDiff: number,
    public place: string,
    public rd: number,
    public rv: number,
    public averageScore: number,
    public firstMatchDate: Date = lastMatchDate,
  ) {}

  get formattedRating(): string {
    return this.rating.toFixed(0);
  }

  sampleRating(d: string | Date) {
    const date = new Date(d)
    const phiPre = this.rd / D
    const periods = (date.getTime() - this.lastMatchDate.getTime())
      / DAY_MILLIS / metaData!.RD_PERIOD_DAYS
    const rd = Math.min(
      metaData!.RD_MAX / D,
      Math.sqrt(phiPre * phiPre + periods * this.rv * this.rv)
    ) * D
    this.lastMatchDate = date
    this.rd = 0.0
    this.rating = sampleND([this.rating, rd])
  }

  // Getter for formatted rating difference
  get formattedRatingDiff(): string {
    return formattedDiff(this.seasonRatingDiff);
  }

  get teamLink(): string {
    return `/team/${encodeURIComponent(this.name)}`;
  }

  get teamName(): string {
    return this.name.replace(/ \(.*\)/, '');
  }

  get formattedLastMatchDate(): string {
    return this.lastMatchDate.toLocaleDateString('nl-NL');
  }

  get formattedFirstMatchDate(): string {
    return this.firstMatchDate.toLocaleDateString('nl-NL');
  }
}

export interface PouleData {
  results: MatchFixture[];
  fixtures: MatchFixture[];
  standing: StandingEntry[];
}

export interface MatchFixture {
  date: string;
  home: string;
  away: string;
  homeScore: number;
  awayScore: number;
  pHome: number;
  pDraw: number;
  pAway: number;
  guessHome: number;
  guessAway: number;
  homeDiff: number;
  awayDiff: number;
  homeRating: number;
  awayRating: number;
  homeRd: number;
  awayRd: number;
  special?: boolean
}
export function winner(match: MatchFixture): string | null {
  if (match.homeScore > match.awayScore) {
    return match.home
  } else if (match.homeScore < match.awayScore) {
    return match.away
  } else {
    return null
  }
}
export function loser(match: MatchFixture): string | null {
  if (match.homeScore > match.awayScore) {
    return match.away
  } else if (match.homeScore < match.awayScore) {
    return match.home
  } else {
    return null
  }
}


export function toFixtureString(match: MatchFixture) {
  return `${match.date}: ${match.home} - ${match.away}`
}

export interface StandingEntry {
  team: {
    name: string;
  };
  stats: {
    penalties: {
      points: number;
    };
    position: number;
    points: number;
    goals: {
      for: number;
      against: number;
    };
    won: number;
    draw: number;
    lost: number;
  };
}
