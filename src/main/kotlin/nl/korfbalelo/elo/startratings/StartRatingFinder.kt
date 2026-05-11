package nl.korfbalelo.elo.startratings

import nl.korfbalelo.elo.ApplicationNew
import nl.korfbalelo.elo.ApplicationNew.log
import nl.korfbalelo.elo.ApplicationNew.startRatings
import nl.korfbalelo.elo.ApplicationNew.isolatedClusters
import nl.korfbalelo.elo.SpawnEvent
import nl.korfbalelo.elo.Team.Companion.deltaBenchmark
import nl.korfbalelo.elo.Team.Companion.minDeltaBenchmark
import nl.korfbalelo.elo.parseEvent
import java.io.File
import kotlin.math.max

/**
 * Used to find sensible start ratings for teams.
 * Although standard ELO dictates a fixed start rating (e.g. 1500), Korfbal Elo works with predetermined start ratings.
 * These ratings are determined by this class as follows:
 *  - Given a baseline set of start ratings, pick a random team from a queue list
 *  - For that team, try out different start ratings
 *  - Find the start rating that gives the _entire_ set of loaded matches the best predictive performance
 *  - Repeat infinitely for all teams in the queue, refilling the queue when it's empty
 */
object StartRatingFinder {
    var off = 34011.32786836952

    var times = mutableListOf<Double>()
    var window = 1.0
    fun doIt(event: String, startRating: Double): Double {
        startRatings[event] = startRating
        ApplicationNew.main(emptyArray())
        log = false

        return off - deltaBenchmark
    }
    fun doIt(event: String): Double {
        val before = startRatings[event] ?: 0.0
        var x1 = (before - window)
        var x2 = before
        var x3 = (before + window)
        val memo = mutableMapOf(before to off - minDeltaBenchmark)
        fun doIt2(startRating: Double) = memo.getOrPut(startRating) {
            doIt(event, startRating)
        }
        log = false
        var y2 = off - minDeltaBenchmark
        var y1 = doIt2(x1)
        var y3 = doIt2(x3)
        while(!(y2 >= y1 && y2 >= y3)
            || (x3 - x1) > window * 2
        ) {
            if (y1 > y2 && y1 > y3) {
                x3 = x2
                y3 = y2
                x2 = x1
                y2 = y1
                x1 -= (x3 - x2) * 2
                y1 = doIt2(x1)
            } else if(y3 > y2) {
                x1 = x2
                y1 = y2
                x2 = x3
                y2 = y3
                x3 -= (x1 - x2) * 2
                y3 = doIt2(x3)
            } else if (x2 - x1 > x3 - x2) {
                x1 += (x2 - x1) / 2
                y1 = doIt2(x1)
            } else {
                x3 += (x2 - x3) / 2
                y3 = doIt2(x3)
            }
        }
        return x2
    }

    @JvmStatic
    fun main(args: Array<String>) {
        ApplicationNew.main(emptyArray())
        replaceStringInFile()
//        off = deltaBenchmark
        val commands = File("club_events.txt").readLines().filter(String::isNotBlank).map(::parseEvent).filterIsInstance<SpawnEvent>().filter {
            it.toString() !in isolatedClusters
        }
        val commandsEvents = commands.map{it.toString()}.toSet()
        commands.forEach {
            if (it.toString() !in startRatings) {
                val before = minDeltaBenchmark
                val prevStartRating = startRatings[it.toString()]
                val startRating = doIt(it.toString())
                startRatings[it.toString()] = startRating
//                println("Choosing: $it $startRating")
                val performance = before - minDeltaBenchmark
//                if (performance <= 0.0 && prevStartRating != startRating) {
//                    error("WTF: $it")
//                }
                File("startratings.csv").appendText("\n$it,$startRating,$performance")
            }
        }
        val todo = commandsEvents.toMutableSet()
        (startRatings.keys - todo - isolatedClusters.toSet()).takeIf { it.isNotEmpty() }?.let(::error)
        val succesCommands = mutableSetOf<String>()
        var maxPerf = 1.0
        val commandFile = File("commands.txt")
        while(true) {
            val (event, oldStart, prevPerf) = File("startratings.csv").readLines().filter(String::isNotBlank).map{it.split(",")}.maxBy { it.getOrNull(2)?.toDouble() ?: 9999.0 }
            if (prevPerf == "0.0") {
                if (maxPerf == 0.0) {
                    if (todo.size == commandsEvents.size) {
                        window /= 2.0
                        commandFile.appendText("${off - minDeltaBenchmark}\twindow increase to $window\n")
                    }
                    todo.clear()
                    todo.addAll(commandsEvents)
                    succesCommands.clear()
                    commandFile.appendText("${off - minDeltaBenchmark}\treset\n")
                } else {
                    todo.clear()
                    todo.addAll(succesCommands)
                    commandFile.appendText("${off - minDeltaBenchmark}\treset, restrict to ${todo.size}\n")
                    succesCommands.clear()
                }
                maxPerf = 0.0
                replaceStringInFile()
                continue
            }
            val before = minDeltaBenchmark
//            print(prevPerf + "\t")
            val oldStartMem = startRatings[event]
            if (oldStart.toDouble() != oldStartMem) {
                Thread.yield()
            }
            val startRating = if (event in todo) doIt(event) else oldStart.toDouble()
//            println("set $event to $startRating")
            startRatings[event] = startRating
            val performance = before - minDeltaBenchmark
            maxPerf = max(maxPerf, performance)
            if (performance > 0.0) {
                succesCommands += event
                print("\t" +
                    "${off - minDeltaBenchmark}")
            }
            replaceLineInFile("startratings.csv", event, "$event,$startRating,$performance\n")
        }
    }
    fun replaceLineInFile(filePath: String, startingString: String, replacement: String) {
        val tempFile = File("$filePath.tmp")
        val inputFile = File(filePath)

        val written = mutableSetOf<String>()
        inputFile.bufferedReader().useLines { lines ->
            tempFile.bufferedWriter().use { writer ->
                lines.forEach { line ->
                    val towrite = if (line.startsWith(startingString)) {
                        replacement
                    } else {
                        line + "\n"
                    }
                    if (towrite.substringBefore(",") !in written) {
                        writer.write(towrite)
                        written.add(towrite.substringBefore(","))
                    }
                }
            }
        }

        if (inputFile.delete()) {
            tempFile.renameTo(inputFile)
        } else {
            println("Error: Unable to delete the original file.")
        }
    }
    fun replaceStringInFile() {
        // Read the content of the file
        val file = File("startratings.csv")
        val content = file.readText()

        // Replace '0.0' with '10.0'
        val updatedContent = content.replace(",0.0\n", ",10.0\n")
        // Write the updated content back to the file
        file.writeText(updatedContent)
    }

}

