package nl.korfbalelo.elo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.apache.commons.math3.special.Erf
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.time.LocalDate
import java.util.SplittableRandom
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.time.measureTime

class ChannelTest {




    @Test
    fun test() = runBlocking {
        publishEvent("bla")

        val post = withTimeout(1000) {
            channel.receive()
        }

        assertEquals(post, "bla")

    }
    companion object {
        val channel = Channel<String>()


        fun publishEvent(str: String) {
            Thread{
                runBlocking {
                    launch {
                        delay(200)
                        sendEvent(str)
                    }
                }
            }.start()
        }

        suspend fun sendEvent(str: String) {
            channel.send(str)
        }
    }

}
