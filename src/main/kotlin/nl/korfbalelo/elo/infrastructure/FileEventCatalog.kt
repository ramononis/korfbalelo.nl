package nl.korfbalelo.elo.infrastructure

import nl.korfbalelo.elo.Match
import nl.korfbalelo.elo.MatchFileReader
import nl.korfbalelo.elo.RankingEvent
import nl.korfbalelo.elo.parseEvent
import java.io.File
import java.util.LinkedHashSet

class FileEventCatalog(
    private val matchesDirectory: File = File("matches"),
    private val commandsFile: File = File("club_events.txt"),
    private val currentMatchesFile: File = File("matches/current.txt"),
) {
    fun loadCurrentMatches(): List<Match> {
        return currentMatchesFile.takeIf(File::exists)?.let(MatchFileReader::readFile) ?: emptyList()
    }

    fun loadAllEvents(currentMatches: Collection<RankingEvent> = loadCurrentMatches()): Set<RankingEvent> {
        val commands = commandsFile.readLines().filter(String::isNotBlank).map(::parseEvent)
        val historicalMatches =
            matchesDirectory.listFiles()
                ?.sortedBy { it.name }
                ?.flatMap(MatchFileReader::readFile)
                ?: error("Error reading matches")
        return (currentMatches + commands + historicalMatches).toCollection(LinkedHashSet())
    }
}
