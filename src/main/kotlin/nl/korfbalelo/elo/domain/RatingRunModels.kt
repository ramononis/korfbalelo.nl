package nl.korfbalelo.elo.domain

import java.time.LocalDate

data class RatingRunWindow(
    val from: LocalDate,
    val to: LocalDate,
)

data class RatingSnapshotRow(
    val name: String,
    val rating: Double,
    val rd: Double,
    val lastDate: LocalDate?,
)

data class RatingRunResult(
    val preSplitRanking: List<RatingSnapshotRow>,
)

