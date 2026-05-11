package nl.korfbalelo.elo

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.apache.commons.math3.special.Erf
import java.lang.reflect.Type
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.pow
import kotlin.math.sqrt


fun <A, B>Iterable<A>.pmap(f: suspend (A) -> B): List<B> = runBlocking {
    map { async(Dispatchers.Default) { f(it) } }.awaitAll()
}

//fun solve
operator fun Regex.contains(text: CharSequence): Boolean = this.matches(text)

// serialize LocalDate as yyy-mm-dd:
internal class LocalDateAdapter : JsonSerializer<LocalDate?> {
    override fun serialize(date: LocalDate?, typeOfSrc: Type?, context: JsonSerializationContext): JsonElement {
        return JsonPrimitive(date?.format(DateTimeFormatter.ISO_LOCAL_DATE)) // "yyyy-mm-dd"
    }
}
val gson = GsonBuilder()
    .registerTypeAdapter(LocalDate::class.java, LocalDateAdapter())
    .create()

fun diffDistroBetween(home: Team, away: Team, date: LocalDate? = null): ND {
    val rh = home.rating + Team.H
    val ra = away.rating
    val muA = ScoreSeasonality.adjustedAverageScore(home.averageScore, date)
    val muB = ScoreSeasonality.adjustedAverageScore(away.averageScore, date)
    val twoP = 2.0 / (1.0 + 10.0.pow((ra - rh) / RatingModel.config.marginRatingScale))
    val sdH = muA * Team.SD_A + Team.SD_B
    val sdA = muB * Team.SD_A + Team.SD_B
    val sdDiff = sqrt(sdH * sdH + sdA * sdA)
    val a =  sdDiff * Erf.erfInv(twoP - 1.0) / PoulePredicter.SQRT2 - (muA - muB) / 2.0
    val result = ND(muA - muB + 2 * a, sdDiff)
    return result
}

fun distroBetween(home: Team, away: Team, neutral: Boolean = false, date: LocalDate? = null): Pair<ND, ND> {
    val rh = home.rating + (if (neutral) 0.0 else Team.H)
    val ra = away.rating
    val muH = ScoreSeasonality.adjustedAverageScore(home.averageScore, date)
    val muA = ScoreSeasonality.adjustedAverageScore(away.averageScore, date)
    val twoP = 2.0 / (1.0 + 10.0.pow((ra - rh) / RatingModel.config.marginRatingScale))
    val sdH = muH * Team.SD_A + Team.SD_B
    val sdA = muA * Team.SD_A + Team.SD_B
    val sdDiff = sqrt(sdH * sdH + sdA * sdA)
    val a =  sdDiff * Erf.erfInv(twoP - 1.0) / PoulePredicter.SQRT2 - (muH - muA) / 2.0

    return ND(muH + a, sdH) to ND(muA - a, sdA)
}
