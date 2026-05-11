package nl.korfbalelo.elo

// AI generated
data class RatingModelConfig(
    val initialHomeAdvantage: Double = 35.0,
    val homeAdvantageSpeed: Double = 1.0 / 64.0,
    val scoreSpeedInv: Double = 100.0,
    val scoreSdSlope: Double = 0.166,
    val scoreSdIntercept: Double = 1.85,
    val marginRatingScale: Double = 400.0,
    val rdMax: Double = 350.0,
    val rdPeriodDays: Double = 1.0,
    val minAverageScore: Double = 0.5,
) {
    override fun toString(): String =
        "H=$initialHomeAdvantage Hs=$homeAdvantageSpeed scoreInv=$scoreSpeedInv " +
            "sd=$scoreSdSlope/$scoreSdIntercept marginScale=$marginRatingScale " +
            "rd=$rdMax/$rdPeriodDays minScore=$minAverageScore"

    companion object {
        val standard = RatingModelConfig()

        fun fromSystemProperties(): RatingModelConfig =
            RatingModelConfig(
                initialHomeAdvantage = System.getProperty("elo.model.initialHomeAdvantage")?.toDoubleOrNull()
                    ?: standard.initialHomeAdvantage,
                homeAdvantageSpeed = System.getProperty("elo.model.homeAdvantageSpeed")?.toDoubleOrNull()
                    ?: standard.homeAdvantageSpeed,
                scoreSpeedInv = System.getProperty("elo.model.scoreSpeedInv")?.toDoubleOrNull()
                    ?: standard.scoreSpeedInv,
                scoreSdSlope = System.getProperty("elo.model.scoreSdSlope")?.toDoubleOrNull()
                    ?: standard.scoreSdSlope,
                scoreSdIntercept = System.getProperty("elo.model.scoreSdIntercept")?.toDoubleOrNull()
                    ?: standard.scoreSdIntercept,
                marginRatingScale = System.getProperty("elo.model.marginRatingScale")?.toDoubleOrNull()
                    ?: standard.marginRatingScale,
                rdMax = System.getProperty("elo.model.rdMax")?.toDoubleOrNull() ?: standard.rdMax,
                rdPeriodDays = System.getProperty("elo.model.rdPeriodDays")?.toDoubleOrNull()
                    ?: standard.rdPeriodDays,
                minAverageScore = System.getProperty("elo.model.minAverageScore")?.toDoubleOrNull()
                    ?: standard.minAverageScore,
            )
    }
}

object RatingModel {
    var config: RatingModelConfig = RatingModelConfig.fromSystemProperties()
}
