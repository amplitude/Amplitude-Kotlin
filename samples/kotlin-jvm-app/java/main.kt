import com.amplitude.Amplitude
import com.amplitude.Configuration
import com.amplitude.events.BaseEvent
import kotlinx.coroutines.runBlocking

fun main() {
    runBlocking {
        val amplitude = Amplitude(
            Configuration(
                apiKey = "API-Key"
            )
        )
        val event = BaseEvent()
        event.eventType = "Kotlin JVM Test"
        event.userId = "kotlin-test-user"
        event.deviceId = "kotlin-test-device"
        amplitude.track(event)
    }
}