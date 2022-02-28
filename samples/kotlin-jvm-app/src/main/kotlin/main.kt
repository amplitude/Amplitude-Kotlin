import com.amplitude.core.Amplitude
import com.amplitude.core.Configuration
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.utilities.FileStorageProvider
import kotlinx.coroutines.runBlocking

fun main() {
    runBlocking {
        val amplitude = Amplitude(
            Configuration(
                apiKey = "bbdf6e7b53f5d0e48b40f2eb51cd8ab4",
                storageProvider = FileStorageProvider()
            )
        )
        val event = BaseEvent()
        event.eventType = "Kotlin JVM Test"
        event.userId = "kotlin-test-user"
        event.deviceId = "kotlin-test-device"
        amplitude.track(event)
    }
}
