package nl.korfbalelo.elo

import java.io.File

// AI generated
object SeasonContext {
    enum class Mode {
        ZAAL,
        VELD,
    }

    data class Config(
        val seasonName: String,
        val mode: Mode,
    ) {
        val seasonCode: String = seasonName.filter(Char::isDigit)
    }

    val indoor = Config(seasonName = "zaal2627", mode = Mode.ZAAL)
    val outdoor = Config(seasonName = "veld2526vj", mode = Mode.VELD)
    private val extraKnownSeasonNames = listOf("zaal2526")

    val active: List<Config>
        get() = listOf(indoor, outdoor)

    val primaryActive: Config
        get() = outdoor

    fun writeFrontendSeasonModule(path: String = "web/src/season.ts") {
        val indoorMode = indoor.mode.name.lowercase()
        val outdoorMode = outdoor.mode.name.lowercase()
        val primaryActiveSeasonRef = if (primaryActive.mode == Mode.ZAAL) "INDOOR_SEASON" else "OUTDOOR_SEASON"
        val activeSeasonRefs = active.joinToString(", ") {
            if (it.mode == Mode.ZAAL) {
                "INDOOR_SEASON"
            } else {
                "OUTDOOR_SEASON"
            }
        }
        val extraKnownSeasonRefs = extraKnownSeasonNames.joinToString("") { " | '$it'" }
        File(path).writeText(
            """
            |export type SeasonMode = 'zaal' | 'veld'
            |
            |export interface SeasonConfig {
            |  seasonName: string
            |  seasonCode: string
            |  mode: SeasonMode
            |}
            |
            |export const INDOOR_SEASON: SeasonConfig = {
            |  seasonName: '${indoor.seasonName}',
            |  seasonCode: '${indoor.seasonCode}',
            |  mode: '${indoorMode}'
            |}
            |
            |export const OUTDOOR_SEASON: SeasonConfig = {
            |  seasonName: '${outdoor.seasonName}',
            |  seasonCode: '${outdoor.seasonCode}',
            |  mode: '${outdoorMode}'
            |}
            |
            |export const ACTIVE_SEASON: SeasonConfig = ${primaryActiveSeasonRef}
            |export const ACTIVE_SEASONS: SeasonConfig[] = [${activeSeasonRefs}]
            |export type KnownSeasonName = typeof INDOOR_SEASON.seasonName | typeof OUTDOOR_SEASON.seasonName${extraKnownSeasonRefs}
            |
            |export function isOutdoorSeasonName(seasonName: string): boolean {
            |  return seasonName.startsWith('veld')
            |}""".trimMargin() + "\n"
        )
    }
}
