import { metaData } from '@/simulator/SeasonSimulator'
import { ageRatingDeviation } from '@/simulator/RatingDeviation'
import { formattedDiff, sampleND } from '@/utils'

const DAY_MILLIS = 1000 * 60 * 60 * 24;
export class Team {
  constructor(
    public name: string, // e.g. PKC, or TOP (S), or Achilles (Al)
    public rating: number,
    public lastMatchDate: Date,
    public seasonRatingDiff: number,
    public place: string,
    public rd: number,
    public averageScore: number,
    public firstMatchDate: Date = lastMatchDate,
  ) {}

  get formattedRating(): string {
    return this.rating.toFixed(0);
  }

  sampleRating(d: string | Date) {
    const date = new Date(d)
    // AI generated: use the shared calendar growth exported by the backend.
    const days = (date.getTime() - this.lastMatchDate.getTime()) / DAY_MILLIS
    const rd = ageRatingDeviation(this.rd, days, metaData!.RD_GROWTH_PER_SQRT_DAY, metaData!.RD_MAX)
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
