package nl.korfbalelo.mijnkorfbal

import com.github.kittinunf.fuel.httpGet
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Semaphore

// AI generated: transport and validated raw-response cache for the official KNKV portal.
internal class CompetitionApi(
    private val baseUrl: String = System.getProperty(
        "elo.scraper.apiBase", "https://api-saas-site-prod-236.dotlab.net/general/api/competition"
    ),
    private val cacheDirectory: File = File(System.getProperty("elo.scraper.cacheDirectory", "cache")),
    private val forceNetwork: Boolean = false,
    private val requestIntervalMillis: Long = System.getProperty("elo.scraper.requestIntervalMillis", "200").toLong(),
) {
    private val gson = Gson()
    private val networkSlots = Semaphore(4)
    private var nextRequestAt = 0L

    init {
        require(requestIntervalMillis >= 0) { "Request interval must be nonnegative" }
    }

    fun pools(district: String, sport: String, category: String): PoolsResult = load(
        "pools", listOf("district" to district, "sport" to sport, "category" to category)
    ) { data ->
        data.field("poolsData").array().forEach { group ->
            group.field("division").field("name").string()
            group.field("pools").array().forEach { pool ->
                pool.field("name").string()
                require(pool.field("ref_id").integer() > 0) { "Invalid pool ref_id" }
            }
        }
    }

    fun standing(poolId: Int): StandingResult = load("matches/pools/$poolId/standing") { data ->
        data.array().forEach { group ->
            group.field("standings").array().forEach { standing ->
                standing.field("team").field("name").string()
                val stats = standing.field("stats")
                stats.field("penalties").field("points").integer()
                listOf("position", "points", "won", "draw", "lost").forEach { stats.field(it).integer() }
                listOf("for", "against").forEach { stats.field("goals").field(it).integer() }
            }
        }
    }

    fun results(poolId: Int, dateFrom: String, dateTo: String): ResultsResult = load(
        "matches/pools/$poolId/results", dateParameters(dateFrom, dateTo)
    ) { data -> data.array().forEach { validateMatch(it, played = true) } }

    fun program(poolId: Int, dateFrom: String, dateTo: String): ProgramResult = load(
        "matches/pools/$poolId/program", dateParameters(dateFrom, dateTo)
    ) { data ->
        data.array().forEach { group ->
            group.field("week").integer()
            group.field("matches").array().forEach { validateMatch(it, played = false) }
        }
    }

    private fun dateParameters(from: String, to: String) = listOf("dateFrom" to from, "dateTo" to to)

    private fun validateMatch(data: JsonElement, played: Boolean) {
        data.field("homeTeam").field("teamName").string()
        data.field("awayTeam").field("teamName").string()
        data.field("date").string()
        val stats = data.asJsonObject.get("stats")?.takeUnless { it.isJsonNull }
        require(!played || stats != null) { "Result has no scores" }
        if (stats != null) {
            listOf("home", "away").forEach {
                require(stats.field(it).field("score").integer() >= 0) { "Invalid match score" }
            }
        }
        // Validate the calendar date before saving a response, including reserve-team matches.
        gson.fromJson(data, CompetitionMatch::class.java).toMatch()
    }

    private inline fun <reified T> load(
        path: String,
        parameters: List<Pair<String, String>> = emptyList(),
        validate: (JsonElement) -> Unit,
    ): T {
        val url = "${baseUrl.trimEnd('/')}/$path"
        val key = url + parameters.joinToString("&", prefix = "?") { "${it.first}=${it.second}" }
        val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val cache = File(cacheDirectory, "knkv-v2-$hash.json")
        try {
            val cached = cache.exists() && !forceNetwork
            val body = if (cached) cache.readText() else fetch(url, parameters)
            val envelope = JsonParser.parseString(body)
            val success = envelope.field("success")
            check(success.isJsonPrimitive && success.asJsonPrimitive.isBoolean && success.asBoolean) {
                "API success=false: ${(envelope as? JsonObject)?.get("message") ?: "no message"}"
            }
            check(envelope.field("statusCode").integer() in 200..299) { "API statusCode indicates failure" }
            val data = envelope.field("data")
            validate(data)
            val decoded: T = gson.fromJson(data, object : TypeToken<T>() {}.type)
            if (!cached) {
                cacheDirectory.mkdirs()
                val temporary = Files.createTempFile(cacheDirectory.toPath(), "knkv-", ".tmp")
                try {
                    Files.writeString(temporary, body)
                    Files.move(temporary, cache.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } finally {
                    Files.deleteIfExists(temporary)
                }
            }
            return decoded
        } catch (exception: Exception) {
            throw IllegalStateException("KNKV request failed: $key: ${exception.message}", exception)
        }
    }

    // AI generated: stay below the observed 500/minute limit and honor shared server cooldowns.
    private fun fetch(url: String, parameters: List<Pair<String, String>>): String {
        networkSlots.acquire()
        try {
            repeat(4) { attempt ->
                paceRequest()
                val (_, response, result) = url.httpGet(parameters)
                    .timeout(30_000).timeoutRead(30_000).responseString()
                if (response.statusCode in 200..299) return result.get()
                if (attempt < 3 && response.statusCode in listOf(429, 502, 503, 504)) {
                    val retryAfter = response.headers["Retry-After"].firstOrNull()
                    val delay = retryAfter?.toLongOrNull()?.times(1_000)
                        ?: retryAfter?.let {
                            runCatching {
                                ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME)
                                    .toInstant().toEpochMilli() - System.currentTimeMillis()
                            }.getOrNull()
                        }
                        ?: if (response.statusCode == 429) 60_000L else 1_000L * (1L shl attempt)
                    synchronized(this) {
                        nextRequestAt = maxOf(nextRequestAt, System.currentTimeMillis() + delay.coerceAtLeast(0))
                    }
                    println("KNKV HTTP ${response.statusCode}; retry ${attempt + 1}/3 after ${delay.coerceAtLeast(0)} ms: $url")
                } else {
                    error("HTTP ${response.statusCode}")
                }
            }
            error("KNKV retries exhausted")
        } finally {
            networkSlots.release()
        }
    }

    @Synchronized
    private fun paceRequest() {
        while (System.currentTimeMillis() < nextRequestAt) {
            Thread.sleep((nextRequestAt - System.currentTimeMillis()).coerceIn(1, 60_000))
        }
        nextRequestAt = System.currentTimeMillis() + requestIntervalMillis
    }

    private fun JsonElement.field(name: String): JsonElement {
        require(isJsonObject) { "Expected object containing $name" }
        return requireNotNull(asJsonObject.get(name)?.takeUnless { it.isJsonNull }) { "Missing $name" }
    }

    private fun JsonElement.array() = also { require(isJsonArray) { "Expected array" } }.asJsonArray

    private fun JsonElement.string(): String {
        require(isJsonPrimitive && asJsonPrimitive.isString && asString.isNotBlank()) { "Expected nonempty string" }
        return asString
    }

    private fun JsonElement.integer(): Int {
        require(isJsonPrimitive && asJsonPrimitive.isNumber) { "Expected integer" }
        return asBigDecimal.intValueExact()
    }
}
