package nl.korfbalelo.mijnkorfbal

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.*

// AI generated: synthetic competitions verify orchestration, publication, and failure behavior.
class ScraperIntegrationTest {
    @TempDir lateinit var directory: File

    @Test fun `complete scrape preserves static indoor postseason and outputs on failure`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val requests = CopyOnWriteArrayList<String>()
        var fail = false
        var unpublishedIndoor = false
        var resultsWithoutStandings = false
        server.createContext("/competition") { exchange ->
            val uri = exchange.requestURI
            requests.add(uri.toString())
            val data = when {
                uri.path.endsWith("/pools") && uri.query.contains("category=SN") ->
                    PortalFixtures.pools.replace("[{\"name\":\"EK-01\",\"ref_id\":103073}]", "[{\"name\":\"EK-01\",\"ref_id\":103073},{\"name\":\"KL(2) Eindfase\",\"ref_id\":2}]")
                uri.path.endsWith("/pools") && uri.query.contains("category=ZS") ->
                    if (unpublishedIndoor) PortalFixtures.pools.replace("103073", "3") else PortalFixtures.pools
                uri.path.endsWith("/pools") -> "{\"poolsData\":[]}"
                uri.path.endsWith("/3/standing") -> "[]"
                uri.path.endsWith("/3/results") -> if (resultsWithoutStandings) "[${PortalFixtures.result}]" else "[]"
                uri.path.endsWith("/3/program") -> "[]"
                uri.path.endsWith("/2/standing") -> "[]"
                uri.path.endsWith("/standing") -> PortalFixtures.standing
                uri.path.endsWith("/2/results") -> "[${PortalFixtures.result.replace("2026-10-10", "2026-10-09")}]"
                uri.path.endsWith("/results") -> "[${PortalFixtures.result},${PortalFixtures.result.replace("Heerenveen 1", "Heerenveen 2")}]"
                uri.path.endsWith("/program") -> "[{\"week\":41,\"matches\":[${PortalFixtures.fixture}]}]"
                else -> error("Unexpected request: $uri")
            }
            val body = if (fail && uri.path.endsWith("/results")) "{\"success\":false,\"statusCode\":200,\"message\":\"synthetic failure\",\"data\":[]}" else PortalFixtures.envelope(data)
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        val properties = mapOf(
            "elo.scraper.apiBase" to "http://127.0.0.1:${server.address.port}/competition",
            "elo.scraper.cacheDirectory" to File(directory, "cache").path,
            "elo.scraper.outputDirectory" to directory.path,
            "elo.scraper.requestIntervalMillis" to "0",
            "elo.scraper.useStaticIndoorPoules" to "true",
            "elo.scraper.writeSpecialMatches" to "true",
        )
        val oldProperties = properties.mapValues { System.getProperty(it.key) }
        val oldForce = Scraper.forceNetwork
        val oldOutdoor = Scraper.outdoorPoules.toMap()
        val oldIndoor = Scraper.indoorPoules.toMap()
        val oldSpecial = Scraper.specialMatches.toMap()
        val oldActive = Scraper.activeTeams.toSet()
        try {
            properties.forEach { (key, value) -> System.setProperty(key, value) }
            Scraper.forceNetwork = true
            val matches = Scraper.scrapePoules()
            assertEquals(2, matches.size)
            assertTrue(matches.all { it.home == "Heerenveen" && it.away == "WKS" })
            assertEquals(setOf("EK-01", "KL(2) Eindfase"), Scraper.outdoorPoules.keys)
            assertTrue(Scraper.specialMatches.values.single().special)
            assertFalse(requests.any { it.contains("KORFBALL-ZA-WK") })
            assertEquals(StaticPoules.loadIndoorPoules("zaal2627").keys, Scraper.indoorPoules.keys)
            assertTrue(Scraper.activeTeams.containsAll(listOf("Heerenveen", "WKS")))
            val output = File(directory, "matches/current.txt")
            assertContains(output.readText(), "2026-10-10,Heerenveen,WKS,13,19")
            val specials = File(directory, "web/public/specialszaal2627.json")
            assertContains(specials.readText(), "2026-10-09,Heerenveen,WKS")

            // Explicitly exercise the live indoor route despite the versioned static file.
            System.setProperty("elo.scraper.useStaticIndoorPoules", "false")
            assertEquals(2, Scraper.scrapePoules().size)
            assertTrue(requests.any { it.contains("category=ZS") })
            assertTrue(requests.any { it.contains("category=ZR&") || it.endsWith("category=ZR") })
            assertTrue(requests.any { it.contains("category=ZRB") })
            assertEquals(setOf("EK-01"), Scraper.indoorPoules.keys)

            unpublishedIndoor = true
            assertEquals(2, Scraper.scrapePoules().size)
            assertTrue(Scraper.indoorPoules.isEmpty())

            val previousOutput = output.readText()
            val previousSpecials = specials.readText()
            val previousOutdoor = Scraper.outdoorPoules.toMap()
            val previousIndoor = Scraper.indoorPoules.toMap()
            val previousSpecial = Scraper.specialMatches.toMap()
            val previousActive = Scraper.activeTeams.toSet()
            resultsWithoutStandings = true
            assertFailsWith<IllegalStateException> { Scraper.scrapePoules() }
            assertEquals(previousOutput, output.readText())
            assertEquals(previousIndoor, Scraper.indoorPoules.toMap())
            resultsWithoutStandings = false
            fail = true
            assertFailsWith<IllegalStateException> { Scraper.scrapePoules() }
            assertEquals(previousOutput, output.readText())
            assertEquals(previousSpecials, specials.readText())
            assertEquals(previousOutdoor, Scraper.outdoorPoules.toMap())
            assertEquals(previousIndoor, Scraper.indoorPoules.toMap())
            assertEquals(previousSpecial, Scraper.specialMatches.toMap())
            assertEquals(previousActive, Scraper.activeTeams.toSet())
        } finally {
            server.stop(0)
            oldProperties.forEach { (key, value) -> if (value == null) System.clearProperty(key) else System.setProperty(key, value) }
            Scraper.forceNetwork = oldForce
            Scraper.outdoorPoules.clear(); Scraper.outdoorPoules.putAll(oldOutdoor)
            Scraper.indoorPoules.clear(); Scraper.indoorPoules.putAll(oldIndoor)
            Scraper.specialMatches.clear(); Scraper.specialMatches.putAll(oldSpecial)
            Scraper.activeTeams.clear(); Scraper.activeTeams.addAll(oldActive)
        }
    }
}
