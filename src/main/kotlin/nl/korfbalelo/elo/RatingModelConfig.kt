package nl.korfbalelo.elo

// AI generated
data class RatingModelConfig(
    val initialHomeAdvantage: Double = 35.0,
    val homeAdvantageSpeed: Double = 1.0 / 64.0,
    val scoreSpeedInv: Double = 100.0,
    val scoreSdSlope: Double = 0.166,
    val scoreSdIntercept: Double = 1.85,
    // AI generated: balanced calibration selected on 2000-2022 and checked on later seasons.
    val marginRatingScale: Double = 360.0,
    val rdMax: Double = 350.0,
    val rdPeriodDays: Double = 1.0,
    val minAverageScore: Double = 0.5,
    // AI generated: bound the inverse-information multiplier used to learn scoring pace.
    val scoreLearningWeightCap: Double = 4.0,
    // AI generated: volatility policies are independently calibrated in sequential replay.
    val volatility: VolatilityConfig = VolatilityConfig(),
    val initialRdFraction: Double = 0.5,
) {
    init {
        require(initialRdFraction > 0 && initialRdFraction <= 1)
        require(scoreLearningWeightCap > 0.0) { "Score learning weight cap must be positive" }
    }

    override fun toString(): String =
        "H=$initialHomeAdvantage Hs=$homeAdvantageSpeed scoreInv=$scoreSpeedInv " +
            "sd=$scoreSdSlope/$scoreSdIntercept marginScale=$marginRatingScale " +
            "rd=$rdMax/$rdPeriodDays minScore=$minAverageScore scoreWeightCap=$scoreLearningWeightCap " +
            "volatility=$volatility initialRdFraction=$initialRdFraction"

    companion object {
        val standard = RatingModelConfig()

        fun fromSystemProperties(): RatingModelConfig {
            val defaults = RatingProfiles.selected()?.model ?: standard
            return RatingModelConfig(
                volatility = VolatilityConfig.fromSystemProperties(defaults.volatility),
                initialRdFraction = System.getProperty("elo.model.initialRdFraction")?.toDouble() ?: defaults.initialRdFraction,
                initialHomeAdvantage = System.getProperty("elo.model.initialHomeAdvantage")?.toDoubleOrNull()
                    ?: defaults.initialHomeAdvantage,
                homeAdvantageSpeed = System.getProperty("elo.model.homeAdvantageSpeed")?.toDoubleOrNull()
                    ?: defaults.homeAdvantageSpeed,
                scoreSpeedInv = System.getProperty("elo.model.scoreSpeedInv")?.toDoubleOrNull()
                    ?: defaults.scoreSpeedInv,
                scoreSdSlope = System.getProperty("elo.model.scoreSdSlope")?.toDoubleOrNull()
                    ?: defaults.scoreSdSlope,
                scoreSdIntercept = System.getProperty("elo.model.scoreSdIntercept")?.toDoubleOrNull()
                    ?: defaults.scoreSdIntercept,
                marginRatingScale = System.getProperty("elo.model.marginRatingScale")?.toDoubleOrNull()
                    ?: defaults.marginRatingScale,
                rdMax = System.getProperty("elo.model.rdMax")?.toDoubleOrNull() ?: defaults.rdMax,
                rdPeriodDays = System.getProperty("elo.model.rdPeriodDays")?.toDoubleOrNull()
                    ?: defaults.rdPeriodDays,
                scoreLearningWeightCap = System.getProperty("elo.model.scoreLearningWeightCap")?.toDoubleOrNull()
                    ?: defaults.scoreLearningWeightCap,
                minAverageScore = System.getProperty("elo.model.minAverageScore")?.toDoubleOrNull()
                    ?: defaults.minAverageScore,
            )
        }
    }
}

object RatingModel {
    var config: RatingModelConfig = RatingModelConfig.fromSystemProperties()
}
