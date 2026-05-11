package nl.korfbalelo.elo

import nl.korfbalelo.elo.Team.Companion.MAGIC_1500
import nl.korfbalelo.elo.graph.DyGraph

object RankingNew {
    val graph = DyGraph()
    var ranking: MutableMap<String, Team> = mutableMapOf()
    val aliases: MutableMap<String, String> = mutableMapOf()

    var partitions = mutableMapOf<Team, MutableSet<Team>>()
    fun doMatch(match: Match) {
        val homeString = aliases[match.home] ?: match.home
        val awayString = aliases[match.away] ?: match.away

        if (match.homeScore < 0) return
        if (homeString == awayString) error(match)
        val home = ranking[homeString] ?: error("${match.date} spawn $homeString > ")
        val homeScore = match.homeScore
        if (home.lastDate == null && home.created != match.date) error("Wrong create $home : ${match.date}")
        val away = ranking[awayString] ?: error("${match.date} spawn $awayString > ")
        val awayScore = match.awayScore
        if (away.lastDate == null && away.created != match.date) error("Wrong create $away : ${match.date}")

        home.firstMatchDate = home.firstMatchDate ?: match.date
        away.firstMatchDate = away.firstMatchDate ?: match.date
        home.lastMatchDate = match.date
        away.lastMatchDate = match.date

//        run {
//            val homeP = partitions[home]
//            val awayP = partitions[away]
//            if(homeP == null && awayP != null) {
//                partitions[home] = awayP.also { it.add(home) }
//            } else if(homeP != null && awayP == null) {
//                partitions[away] = homeP.also { it.add(away) }
//            } else if (homeP != null && awayP != null) {
//                homeP.addAll(awayP)
//                awayP.forEach { partitions[it] = homeP }
//            } else {
//                partitions[home] = mutableSetOf(home, away).also{ partitions[away] = it}
//            }
//        }

        ensureRated(home, away)
        ensureRated(away, home)
        home.setNewRD(match.date)
        away.setNewRD(match.date)
        if (match.date.isBefore(AccuracyTracker.ignoreMatchesBelow)) {
            return
        }

        val diffDistro = diffDistroBetween(home, away, match.date)
        PredictionBenchmark.record(match, home, away, diffDistro)
        val awayRating = away.newRating(home, awayScore, homeScore, false, match, -diffDistro.first to diffDistro.second)
        val homeRating = home.newRating(away, homeScore, awayScore, true, match, diffDistro)
        val (adjustedHomeRating, adjustedAwayRating) = ScoreRatingTweak.adjust(
            match = match,
            home = home,
            away = away,
            homeUpdate = homeRating,
            awayUpdate = awayRating,
        )
        ScoreSeasonality.learn(match, home, away, diffDistro)
//        val addedTotalRating = awayRating.first + homeRating.first - home.rating - away.rating

//        if (recordFrom.isBefore(match.date) && match.date.isBefore(recordTo)){
//            maxes.add(match to kotlin.run {
//                -(max(awayScore, homeScore)).absoluteValue
//            })
//            maxes.sortByDescending(Pair<Match, Int>::second)
//            if (maxes.size > 10) maxes.removeAt(10)
//        }

        home.setNewRating(adjustedHomeRating)
        away.setNewRating(adjustedAwayRating)
//        rebalance(addedTotalRating)
        if (ApplicationNew.log) {
            if (home.rd <= 200.0)
                graph.addRanking(match.date, home.graphName, home.rating.toInt())
            if (away.rd <= 200.0)
                graph.addRanking(match.date, away.graphName, away.rating.toInt())
        }
    }

    fun remove(name: String) {
        ranking.remove(name)
    }

    fun ensureRated(team: Team, opponent: Team) {
        if (team.rating == MAGIC_1500) {
            check(team.games == 0)
            team.averageScore = opponent.averageScore
            if (opponent.rating == MAGIC_1500) {
                check(opponent.games == 0)
                team.rating = 1500.0 + team.startOffset
            } else {
                team.rating = opponent.rating + team.startOffset
            }
            if (team.rating !in 900.0..2000.0) {
                val d = if (team.rating > 2000.0) team.rating - 2000.0
                else 900.0 - team.rating
                PredictionBenchmark.recordStartRatingClampPenalty(d)
            }
            team.rating = team.rating.coerceIn(900.0, 2000.0)
        }
    }

//    fun rebalance(addedTotalRating: Double) {
//        val size = ranking.count{it.value.rating != 0.0}
//        val toSubtract = addedTotalRating / size
//        ranking.values.forEach { if (it.rating != 0.0) it.rating -= toSubtract }
//    }

    fun rebalance() {
        val s = ranking.values.sumOf{it.rating - 1500.0}

        val toSubtract =  s / ranking.size
        ranking.values.forEach { it.rating -= toSubtract }
    }

    fun add(team: Team) {
        ranking[team.name] = team
    }
    val regex = Regex("(.*) \\d+")
    val mapper = mapOf(
        "ADOS" to "Ados",
        "AWDTV/IJskouddebeste" to "AW.DTV",
        "Achilles (A)/AKC" to "Achilles (A) / AKC",
        "Achilles (Hg)" to "Achilles (H)",
        "Amicitia" to "Amicitia (V)",
        "Animo" to "Animo (G)",
        "Antilopen/Bloemendal Bouw" to "Antilopen",
        "Avanti/Post Makelaardij" to "Avanti (P)",
        "Blauw-Wit (A)" to "Blauw Wit",
        "Celeritas" to "Celeritas (A)",
        "DES (D)" to "DES (De)",
        "DOS '46/VDK Groep" to "DOS '46",
        "DOS Kampen/Unique Waterontharders" to "DOS Kampen",
        "DVO/Transus" to "DVO",
        "DWA/Argo" to "DWA / Argo",
        "DWS" to "DWS (K)",
        "Dalto/Klaverblad Verzekeringen" to "Dalto",
        "DeetosSnel/QLS" to "DeetosSnel",
        "Drachten/Van der Wiel" to "Drachten",
        "EKCA/CIBOD" to "EKCA",
        "Excelsior" to "Excelsior (D)",
        "Flamingo's" to "Flamingo's (B)",
        "Fluks" to "Fluks (N)",
        "Fortuna/Ruitenheer" to "Fortuna (D)",
        "GG/RMcD Huis Emma" to "Groen Geel",
        "GKV (H)" to "GKV",
        "GKV/Enomics" to "GKV (G)",
        "HBC" to "HBC (Be)",
        "HKC/Creon Kozijnen" to "HKC (Ha)",
        "HKV/Ons Eibernest" to "HKV / Ons Eibernest",
        "Hoogkerk/Schelwald Optiek" to "Hoogkerk",
        "It Fean/Boelenslaan" to "It Fean",
        "KCC/CK Kozijnen" to "KCC",
        "KVS/Groeneveld Keukens" to "KVS",
        "KZ Danaïden" to "KZ Danaiden",
        "KZ/Keukensale.com" to "KZ",
        "Kinea/Udiros" to "Kinea / Udiros",
        "Udiros/Kinea" to "Kinea / Udiros",
        "LDODK/Rinsma Modeplein" to "LDODK",
        "Merwede/Multiplaat" to "Merwede",
        "Mid-Fryslân/ReduRisk" to "Mid Fryslân",
        "Nic." to "Nic",
        "ODIK/JansenvdGrift" to "ODIK",
        "ODO" to "ODO (M)",
        "OKO/BIES" to "OKO",
        "Oost-Arnhem" to "Oost Arnhem",
        "Oranje Zwart/De Hoekse" to "Oranje Zwart (M)",
        "PKC/Vertom" to "PKC",
        "PSV/RM Verkeersdiensten" to "PSV",
        "Pallas '08/thebagstore.nl " to "Pallas '08",
        "Phoenix" to "Phoenix (Z)",
        "Rapid" to "Rapid (H)",
        "Roda" to "RODA (W)",
        "Rohda" to "Rohda (A)",
        "Rood-Wit" to "Rood Wit (W)",
        "Rust Roest" to "Rust Roest (E)",
        "DTS (S)/Harkema" to "DTS (S) / Harkema",
        "SCO/European Aerosols" to "SCO",
        "SIOS '61/Diderna" to "SIOS '61",
        "SDO/Fiable" to "SDO (K)",
        "SEV" to "SEV (Z)",
        "SKV" to "SKV (S)",
        "Sparta (N)/Djops" to "Sparta (N)",
        "Spirit" to "Spirit (Vo)",
        "Sporting Badhoevedorp" to "Badhoevedorp",
        "Sporting West/Bonarius.com" to "Sporting West",
        "Swift (A)" to "Swift",
        "TFS/DOW/Wêz Handich" to "Wez Handich",
        "DOW/TFS/Wêz Handich" to "Wez Handich",
        "TOGO" to "TOGO (G)",
        "TOP/IAA fresh" to "TOP (S)",
        "Telstar/RJJ Keukens" to "Telstar",
        "Trekvogels" to "Trekvogels (R)",
        "Unitas/Perspectief" to "Unitas",
        "Valto/Verbakel Bouwbedrijf" to "Valto",
        "Velocitas" to "Velocitas (L)",
        "Vlug & Vaardig (A)" to "Vlug en Vaardig (A)",
        "Vlug & Vaardig (G)/Quick '21" to "Quick '21 / Vlug en Vaardig (G)",
        "Voltreffers" to "Voltreffers (O)",
        "Wit-Blauw/Green Organics" to "Wit Blauw",
        "Wolderwijd/DYZLE" to "Wolderwijd",
        "Wordt Kwiek" to "WK",
        "Invicta" to "IFC '25",
        "Wêz Handich/DOW/TFS" to "Wez Handich",
        "Zwaluwen" to "Zwaluwen (Ze)",
        "Vefo/KIA" to "KIA",
        "KIA/Vefo" to "KIA",
        "Aurora/DKV (IJ)" to "Aurora / DKV (IJ)",
        "DKV (IJ)/Aurora" to "Aurora / DKV (IJ)",
        "Sperwers" to "Zuid '27",
        "BKC" to "BKC (Br)",
//        "Boelenslaan/It Fean" to "It Fean",
//        "DKV (IJ)/Aurora" to "Aurora / DKV (IJ)",
//        "DOW/TFS/Wêz Handich" to "Wez Handich",
        "Deinum" to "SF Deinum",
        "Diderna/Visser Sloopwerken" to "Diderna",
        "Duko/Wesstar" to "Duko",
        "Leonidas/SIOS Jumbo Wolvega" to "SIOS / Leonidas",
        "SIOS/Jumbo Wolvega/Leonidas" to "SIOS / Leonidas",
        "Mélynas" to "Melynas",
        "Noviomagum/Keizer Karel" to "Noviomagum",
        "Aladna/De Issel" to "Aladna / De Issel",
//        "NKC (N)" to "NKC (No)",
        "Thor" to "Thor (H)",
        "Oosterkwartier" to "Oosterkwartier (H)",
        "Oranje Nassau/Triaz" to "Oranje Nassau / Triaz",
        "Triaz/Oranje Nassau" to "Oranje Nassau / Triaz",
        "Quick '21/Vlug & Vaardig (G)" to "Quick '21 / Vlug en Vaardig (G)",
        "WIKO" to "Wiko",
        "ZKV" to "ZKV (Zu)",
        "Pallas '08/thebagstore.nl" to "Pallas '08",
        "t Capproen" to "'t Capproen",
    )

    fun map(teamName: String): String {
        val clubName = regex.matchEntire(teamName)?.groupValues?.get(1) ?: teamName
        return mapper.getOrDefault(clubName, clubName)
    }
}
