package nl.korfbalelo.elo

import org.apache.commons.math3.special.Erf
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.time.LocalDate
import java.util.SplittableRandom
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.roundToInt
import kotlin.time.measureTime

@Disabled
class GlickoTest {

    @Test
    fun testGlicko() {
//        val team1 = Team("Lol").apply {
//            rating = 1000.0
//            rd = 350.0
//            rv = 0.06
//            lastDate = LocalDate.now().minusDays(7)
//        }
//        val team2 = Team("Lol").apply {
//            rating = 1500.0
//            rd = 350.0
//            rv = 0.06
//            lastDate = LocalDate.now().minusDays(7)
//        }
////        val newRating1 = team1.newRating(team2, 20, 10, true, LocalDate.now())
////        val newRating2 = team2.newRating(team1, 15, 16, null, LocalDate.now())
//        Thread.yield()
    }


    @Test
    fun benchmarkRandom() {
//        ClassGraph()
//            .enableAllInfo()             // Scan classes, methods, fields, annotations
//            .scan().use { scanResult ->
//                for (clzz in scanResult.getClassesImplementing(RandomGenerator::class.java)) {
//                    try {
//                        val randomGenerator = clzz.loadClass().getConstructor().newInstance() as RandomGenerator
                        measureTime {
                            val rand = SplittableRandom()
                            repeat(100000000) {
                                rand.nextGaussian()
                            }
                        }.let { time ->
                            println(" - $time")
                        }
//                    } catch (e: Exception) {
////                        println("No default constructor: ${clzz.name}")
//                    }
//                }
//            }

    }
}
