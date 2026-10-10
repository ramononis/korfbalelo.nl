package nl.korfbalelo.mijnkorfbal

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.InetSocketAddress
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.*

// AI generated: shortened observed portal responses; error cases and zero/penalty variants are synthetic.
internal object PortalFixtures {
    val pools = """{"poolsData":[{"division":{"name":"Ereklasse nj"},"pools":[{"name":"EK-01","ref_id":103073}]}]}"""
    val standing = """[{"standings":[{"team":{"name":"Heerenveen 1"},"stats":{"penalties":{"points":0},"position":1,"points":12,"goals":{"for":149,"against":69},"won":6,"draw":0,"lost":0}},{"team":{"name":"WKS/European Aerosols 1"},"stats":{"penalties":{"points":0},"position":2,"points":8,"goals":{"for":110,"against":87},"won":4,"draw":0,"lost":2}}]}]"""
    val result = """{"date":"2026-10-10","datetime":"2026-10-10T15:30:00+0200","week":"Week 41","homeTeam":{"teamName":"Heerenveen 1","score":13},"awayTeam":{"teamName":"WKS/European Aerosols 1","score":19},"stats":{"home":{"score":13},"away":{"score":19}}}"""
    val fixture = """{"date":"2026-10-10","datetime":"2026-10-10T12:00:00+0200","week":"Week 41","homeTeam":{"teamName":"Hoogkerk/Schelwald Optiek 4","score":null},"awayTeam":{"teamName":"Wêz Handich/DOW/TFS 4","score":null}}"""
    fun envelope(data: String) = """{"success":true,"statusCode":200,"message":"","data":$data}"""
}

// AI generated: test actual HTTP/cache paths without relying on a changing live API.
class CompetitionApiTest {
    @TempDir lateinit var cache: File
    private lateinit var server: HttpServer
    private lateinit var baseUrl: String
    private var body = ""
    private var status = 200
    private var rateLimitsRemaining = 0
    private val queries = CopyOnWriteArrayList<String>()

    @BeforeEach fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/competition") { exchange ->
            queries.add(exchange.requestURI.toString())
            val bytes = body.toByteArray()
            val code = if (rateLimitsRemaining > 0) {
                rateLimitsRemaining--
                exchange.responseHeaders.add("Retry-After", "0")
                429
            } else status
            exchange.sendResponseHeaders(code, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}/competition"
    }

    @AfterEach fun stopServer() = server.stop(0)
    private fun api(force: Boolean = false) = CompetitionApi(baseUrl, cache, force, requestIntervalMillis = 0)

    @Test fun `rate limit is retried before caching a successful response`() {
        body = PortalFixtures.envelope("[${PortalFixtures.result}]")
        rateLimitsRemaining = 1
        assertEquals(13, api().results(103073, "2026-10-10", "2026-10-10").single().toMatch().homeScore)
        assertEquals(2, queries.size)
        assertEquals(1, cache.listFiles()!!.size)
        rateLimitsRemaining = 4
        assertFailsWith<IllegalStateException> { api(true).results(1, "2026-10-10", "2026-10-10") }
        assertEquals(1, cache.listFiles()!!.size)
    }

    @Test fun `pools and standings retain concrete list types and penalties`() {
        body = PortalFixtures.envelope(PortalFixtures.pools)
        assertEquals(103073, api().pools("district", "sport", "category").poolsData.single().pools.single().refId)
        assertTrue(queries.single().contains("district=district&sport=sport&category=category"))
        body = PortalFixtures.envelope(PortalFixtures.standing.replace("\"penalties\":{\"points\":0}", "\"penalties\":{\"points\":-2}"))
        val standings = api().standing(103073).single().standings
        assertEquals("Heerenveen 1", standings.first().team.name)
        assertEquals(-2, standings.first().stats.penalties.points)
    }

    @Test fun `flat results map scores names date and first teams`() {
        body = PortalFixtures.envelope("[${PortalFixtures.result}]")
        val transport = api().results(103073, "2026-10-10", "2026-10-10").single()
        assertTrue(transport.isFirstTeamMatch())
        val match = transport.toMatch()
        assertEquals("Heerenveen", match.home)
        assertEquals("WKS", match.away)
        assertEquals(13, match.homeScore)
        assertEquals(19, match.awayScore)
        assertEquals(LocalDate.of(2026, 10, 10), match.date)
        assertTrue(queries.single().endsWith("dateFrom=2026-10-10&dateTo=2026-10-10"))
        body = body.replace("\"score\":13", "\"score\":0")
        assertEquals(0, api(true).results(103073, "2026-10-10", "2026-10-10").single().toMatch().homeScore)
    }

    @Test fun `grouped programme has unplayed scores and excludes reserves and youth`() {
        body = PortalFixtures.envelope("[{\"year\":2026,\"week\":41,\"matches\":[${PortalFixtures.fixture}]}]")
        val group = api().program(103433, "2000-08-11", "2100-09-11").single()
        assertEquals(41, group.week)
        val fixture = group.matches.single()
        assertFalse(fixture.isFirstTeamMatch())
        assertEquals(-1, fixture.toMatch().homeScore)
        assertEquals(-1, fixture.toMatch().awayScore)
        assertFalse(fixture.copy(homeTeam = CompetitionTeam("Hoogkerk U19-1"), awayTeam = CompetitionTeam("WKS 1")).isFirstTeamMatch())
    }

    @Test fun `local calendar dates survive winter summer and near midnight timestamps`() {
        for ((date, timestamp) in listOf("2026-01-10" to "2026-01-10T00:10:00+0100", "2026-07-10" to "2026-07-10T00:10:00+0200")) {
            body = PortalFixtures.envelope("[${PortalFixtures.result.replace("2026-10-10", date).replace("15:30:00+0200", timestamp.substringAfter('T'))}]")
            assertEquals(LocalDate.parse(date), api(true).results(103073, date, date).single().toMatch().date)
        }
    }

    @Test fun `empty result and programme arrays are valid`() {
        body = PortalFixtures.envelope("[]")
        assertTrue(api().results(103073, "2050-01-01", "2050-01-02").isEmpty())
        assertTrue(api().program(103073, "2050-01-01", "2050-01-02").isEmpty())
    }

    @Test fun `errors and malformed payloads fail without populating cache`() {
        val invalid = listOf(
            404 to "Not found", 200 to "not JSON", 200 to "{}",
            200 to """{"success":false,"statusCode":200,"message":"synthetic failure","data":[]}""",
            200 to """{"success":true,"statusCode":500,"data":[]}""",
            200 to """{"success":true,"statusCode":200}""",
            200 to PortalFixtures.envelope("null"),
            200 to PortalFixtures.envelope("{}"),
            200 to PortalFixtures.envelope("[{\"date\":\"2026-10-10\"}]"),
            200 to PortalFixtures.envelope("[${PortalFixtures.fixture}]"),
            200 to PortalFixtures.envelope("[${PortalFixtures.result.replace("\"score\":13", "\"score\":null")}]") ,
            200 to PortalFixtures.envelope("[${PortalFixtures.result.replace("2026-10-10", "invalid-date")}]")
        )
        for ((code, response) in invalid) {
            status = code
            body = response
            val error = assertFailsWith<IllegalStateException> { api(true).results(1, "2026-10-10", "2026-10-10") }
            assertContains(error.message.orEmpty(), "KNKV request failed:")
            assertTrue(cache.listFiles().orEmpty().isEmpty(), response)
        }
        status = 200
        body = PortalFixtures.envelope(PortalFixtures.pools.replace("\"ref_id\":103073", "\"ref_id\":null"))
        assertFailsWith<IllegalStateException> { api().pools("district", "sport", "category") }
        body = PortalFixtures.envelope(PortalFixtures.standing.replace("\"penalties\":{\"points\":0}", "\"penalties\":{}"))
        assertFailsWith<IllegalStateException> { api().standing(1) }
        assertTrue(cache.listFiles().orEmpty().isEmpty())
    }

    @Test fun `network refresh and offline cache return identical normalized data`() {
        body = PortalFixtures.envelope("[${PortalFixtures.result}]")
        val first = api(true).results(103073, "2026-10-10", "2026-10-10")
        assertEquals(first, api().results(103073, "2026-10-10", "2026-10-10"))
        assertEquals(1, queries.size)
        body = body.replace("\"score\":13", "\"score\":0")
        val refreshed = api(true).results(103073, "2026-10-10", "2026-10-10")
        assertEquals(0, refreshed.single().toMatch().homeScore)
        assertEquals(2, queries.size)
        body = """{"success":false,"statusCode":200,"data":[]}"""
        assertFailsWith<IllegalStateException> { api(true).results(103073, "2026-10-10", "2026-10-10") }
        server.stop(0)
        assertEquals(refreshed, api().results(103073, "2026-10-10", "2026-10-10"))
        cache.listFiles()!!.single().writeText("invalid cached JSON")
        assertFailsWith<IllegalStateException> { api().results(103073, "2026-10-10", "2026-10-10") }
    }
}
