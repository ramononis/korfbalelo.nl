package nl.korfbalelo.elo

// AI generated
data class RatingModelConfig(
    val initialHomeAdvantage: Double = 35.0,
    val homeAdvantageSpeed: Double = 1.0 / 64.0,
    val scoreSpeedInv: Double = 100.0,
    val scoreSdSlope: Double = 0.12,
    val scoreSdIntercept: Double = 1.9,
    val marginRatingScale: Double = 320.0,
    val rdMax: Double = 450.0,
    val rdGrowthPerSqrtDay: Double = GlickoCalculator.DEFAULT_RD_GROWTH,
    val minAverageScore: Double = 0.5,
    // AI generated: bound the inverse-information multiplier used to learn scoring pace.
    val scoreLearningWeightCap: Double = 4.0,
    val initialRdFraction: Double = 0.75,
) {
    init {
        require(rdGrowthPerSqrtDay.isFinite() && rdGrowthPerSqrtDay >= 0)
        require(rdMax.isFinite() && rdMax > 0)
        require(initialRdFraction > 0 && initialRdFraction <= 1)
        require(scoreLearningWeightCap > 0.0) { "Score learning weight cap must be positive" }
    }

    override fun toString(): String =
        "H=$initialHomeAdvantage Hs=$homeAdvantageSpeed scoreInv=$scoreSpeedInv " +
            "sd=$scoreSdSlope/$scoreSdIntercept marginScale=$marginRatingScale " +
            "rd=$rdMax growth=$rdGrowthPerSqrtDay minScore=$minAverageScore scoreWeightCap=$scoreLearningWeightCap " +
            "initialRdFraction=$initialRdFraction"

    companion object {
        val standard = RatingModelConfig()

        fun fromSystemProperties(): RatingModelConfig {
            val defaults = standard
            return RatingModelConfig(
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
                rdGrowthPerSqrtDay = System.getProperty("elo.model.rdGrowthPerSqrtDay")?.toDoubleOrNull()
                    ?: defaults.rdGrowthPerSqrtDay,
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
