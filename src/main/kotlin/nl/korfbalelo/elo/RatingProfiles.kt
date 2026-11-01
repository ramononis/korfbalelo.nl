package nl.korfbalelo.elo

// AI generated: complete calibrated configurations; individual system properties still override them.
object RatingProfiles {
    // AI generated: selected shared calibration; legacy remains an explicit replay option.
    const val DEFAULT_PROFILE = "shared-matched"
    private data class Catalog(val profiles: Map<String, RatingModelBenchmarkConfig>)

    private val profiles: Map<String, RatingModelBenchmarkConfig> by lazy {
        val resource = requireNotNull(javaClass.getResourceAsStream("/rating-profiles.json")) {
            "Missing bundled rating profiles"
        }
        resource.bufferedReader().use {
            gson.fromJson(it, Catalog::class.java).profiles
        }
    }

    fun selected(): RatingModelBenchmarkConfig? {
        val name = System.getProperty("elo.model.profile", DEFAULT_PROFILE)
        if (name == "legacy") return null
        return requireNotNull(profiles[name]) { "Unknown rating profile: $name" }
    }
}
