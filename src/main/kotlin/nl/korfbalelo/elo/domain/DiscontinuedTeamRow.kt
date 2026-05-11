package nl.korfbalelo.elo.domain

import java.time.LocalDate

data class DiscontinuedTeamRow(
    val name: String,
    val place: String,
    val lastEventDate: LocalDate,
    val lastMatchDate: LocalDate?,
    val firstMatchDate: LocalDate?,
    val topRating: Double?,
    val lastRating: Double?,
    val matches: Int,
    val fate: String,
    val fateType: String,
    val fateTeams: List<String>,
)
