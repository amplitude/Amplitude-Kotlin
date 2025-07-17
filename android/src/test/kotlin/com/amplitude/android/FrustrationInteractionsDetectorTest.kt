package com.amplitude.android

import com.amplitude.common.Logger
import com.amplitude.core.Amplitude
import com.amplitude.core.Constants.EventProperties.CLICK_COUNT
import com.amplitude.core.Constants.EventProperties.COORDINATE_X
import com.amplitude.core.Constants.EventProperties.COORDINATE_Y
import com.amplitude.core.Constants.EventProperties.HIERARCHY
import com.amplitude.core.Constants.EventProperties.TARGET_CLASS
import com.amplitude.core.Constants.EventProperties.TARGET_RESOURCE
import com.amplitude.core.Constants.EventProperties.TARGET_SOURCE
import com.amplitude.core.Constants.EventProperties.TARGET_TAG
import com.amplitude.core.Constants.EventProperties.TARGET_TEXT
import com.amplitude.core.Constants.EventTypes.DEAD_CLICK
import com.amplitude.core.Constants.EventTypes.RAGE_CLICK
import com.amplitude.core.platform.Signal
import io.mockk.called
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

@OptIn(ExperimentalCoroutinesApi::class)
class FrustrationInteractionsDetectorTest {
    private lateinit var mockAmplitude: Amplitude
    private lateinit var mockLogger: Logger
    private lateinit var uiChangeFlow: MutableSharedFlow<Signal>
    private lateinit var detector: FrustrationInteractionsDetector
    private val testDispatcher = UnconfinedTestDispatcher()

    private val testTargetInfo =
        FrustrationInteractionsDetector.TargetInfo(
            className = "TestButton",
            resourceName = "test_button",
            tag = "test",
            text = "Click me",
            source = "android_view",
            hierarchy = "Activity → LinearLayout → Button",
        )

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockAmplitude = mockk(relaxed = true)
        mockLogger = mockk(relaxed = true)
        uiChangeFlow = MutableSharedFlow()

        every { mockAmplitude.signalFlow } returns uiChangeFlow
        every { mockAmplitude.amplitudeScope } returns CoroutineScope(testDispatcher)
        every { mockAmplitude.amplitudeDispatcher } returns testDispatcher

        detector =
            FrustrationInteractionsDetector(
                amplitude = mockAmplitude,
                logger = mockLogger,
                // 2x density for testing
                density = 2f,
            )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    //region Rage Click Tests

    @Test
    fun `rage click - triggers after threshold clicks within distance`() {
        val clickInfo = FrustrationInteractionsDetector.ClickInfo(100f, 100f)

        // Perform 3 clicks (threshold) in same location
        repeat(3) {
            detector.processClick(clickInfo, testTargetInfo)
        }

        // Verify rage click event was tracked
        verify { mockAmplitude.track(RAGE_CLICK, any()) }
    }

    @Test
    fun `rage click - respects distance threshold with density conversion`() {
        val baseClick = FrustrationInteractionsDetector.ClickInfo(100f, 100f)

        // With 2x density: 50pt * 2 = 100px threshold
        // Click at distance 99px (within threshold)
        val nearbyClick = FrustrationInteractionsDetector.ClickInfo(199f, 100f) // 99px away
        val farClick = FrustrationInteractionsDetector.ClickInfo(201f, 100f) // 101px away

        // 3 clicks within threshold should trigger rage click
        detector.processClick(baseClick, testTargetInfo)
        detector.processClick(nearbyClick, testTargetInfo)
        detector.processClick(baseClick, testTargetInfo)

        verify { mockAmplitude.track(RAGE_CLICK, any()) }
        clearMocks(mockAmplitude)

        // Far click should start new session (no rage click)
        detector.processClick(baseClick, testTargetInfo)
        detector.processClick(farClick, testTargetInfo)
        detector.processClick(baseClick, testTargetInfo)

        verify { mockAmplitude.track(RAGE_CLICK, any()) wasNot called }
    }

    @Test
    fun `rage click - respects ignore flag`() {
        val clickInfo = FrustrationInteractionsDetector.ClickInfo(100f, 100f)
        val ignoredProperties = mapOf("isIgnoredForRageClick" to true)

        repeat(3) {
            detector.processClick(clickInfo, testTargetInfo, ignoredProperties)
        }

        verify { mockAmplitude.track(RAGE_CLICK, any()) wasNot called }
        verify { mockLogger.debug(match { it.contains("Skipping rage click processing") }) }
    }

    @Test
    fun `rage click - tracks correct event properties`() {
        val clickInfo = FrustrationInteractionsDetector.ClickInfo(150f, 200f)
        val additionalProps = mapOf("screen_name" to "TestScreen")

        repeat(3) {
            detector.processClick(clickInfo, testTargetInfo, additionalProps)
        }

        val capturedProperties = slot<Map<String, Any?>>()
        verify { mockAmplitude.track(RAGE_CLICK, capture(capturedProperties)) }

        assertAll(
            { assertEquals(150, capturedProperties.captured[COORDINATE_X]) },
            { assertEquals(200, capturedProperties.captured[COORDINATE_Y]) },
            { assertEquals(3, capturedProperties.captured[CLICK_COUNT]) },
            { assertEquals("TestButton", capturedProperties.captured[TARGET_CLASS]) },
            { assertEquals("TestScreen", capturedProperties.captured["screen_name"]) },
        )
    }

    //endregion

    //region Dead Click Tests

    @Test
    fun `dead click - respects ignore flag`() {
        val clickInfo = FrustrationInteractionsDetector.ClickInfo(100f, 100f)
        val ignoredProperties = mapOf("isIgnoredForDeadClick" to true)

        detector.processClick(clickInfo, testTargetInfo, ignoredProperties)

        verify { mockLogger.debug(match { it.contains("Skipping dead click processing") }) }
    }

    @Test
    fun `dead click - detector should be started for signal flow subscription`() {
        // This test validates that start() is needed for proper signal flow subscription
        // We'll test this by confirming that the startUiChangeCollection method is called
        val mockAmplitudeWithStart = mockk<Amplitude>(relaxed = true)
        val uiChangeFlow = MutableSharedFlow<Signal>()
        every { mockAmplitudeWithStart.signalFlow } returns uiChangeFlow
        every { mockAmplitudeWithStart.amplitudeScope } returns CoroutineScope(testDispatcher)
        every { mockAmplitudeWithStart.amplitudeDispatcher } returns testDispatcher

        val detectorWithStart =
            FrustrationInteractionsDetector(
                amplitude = mockAmplitudeWithStart,
                logger = mockLogger,
                density = 2f,
            )

        // Call start() to ensure signal flow subscription
        detectorWithStart.start()

        // Verify that the detector is now set up to receive UI change signals
        // (In real usage, this would prevent false dead click detection)
        verify { mockAmplitudeWithStart.signalFlow }

        detectorWithStart.stop()
    }

    //endregion

    //region Density Tests

    @Test
    fun `density scaling - different densities produce different thresholds`() {
        val mockAmplitude1x = mockk<Amplitude>(relaxed = true)
        val mockAmplitude3x = mockk<Amplitude>(relaxed = true)

        every { mockAmplitude1x.amplitudeScope } returns CoroutineScope(testDispatcher)
        every { mockAmplitude1x.amplitudeDispatcher } returns testDispatcher
        every { mockAmplitude3x.amplitudeScope } returns CoroutineScope(testDispatcher)
        every { mockAmplitude3x.amplitudeDispatcher } returns testDispatcher

        val detector1x = FrustrationInteractionsDetector(mockAmplitude1x, mockLogger, 1f)
        val detector3x = FrustrationInteractionsDetector(mockAmplitude3x, mockLogger, 3f)

        // Base click at (10, 10)
        val baseClick = FrustrationInteractionsDetector.ClickInfo(10f, 10f)

        // Second click at (70, 10) - 60px distance
        val distantClick = FrustrationInteractionsDetector.ClickInfo(70f, 10f)

        // For 1x density: 50pt = 50px threshold, 60px > 50px -> no rage click
        detector1x.processClick(baseClick, testTargetInfo)
        detector1x.processClick(distantClick, testTargetInfo)
        detector1x.processClick(baseClick, testTargetInfo)

        // For 3x density: 50pt = 150px threshold, 60px < 150px -> should trigger rage click
        detector3x.processClick(baseClick, testTargetInfo)
        detector3x.processClick(distantClick, testTargetInfo)
        detector3x.processClick(baseClick, testTargetInfo)

        // Verify 1x density detector doesn't trigger rage click
        verify { mockAmplitude1x.track(RAGE_CLICK, any()) wasNot called }

        // Verify 3x density detector does trigger rage click
        verify(exactly = 1) { mockAmplitude3x.track(RAGE_CLICK, any()) }
    }

    //endregion

    //region TargetInfo Tests

    @Test
    fun `TargetInfo toEventProperties formats source correctly`() {
        val targetInfo =
            FrustrationInteractionsDetector.TargetInfo(
                className = "Button",
                resourceName = "submit_btn",
                tag = "submit",
                text = "Submit",
                source = "android_view",
                hierarchy = "Activity → Layout → Button",
            )

        val properties = targetInfo.toEventProperties()

        assertAll(
            { assertEquals("Button", properties[TARGET_CLASS]) },
            { assertEquals("submit_btn", properties[TARGET_RESOURCE]) },
            { assertEquals("submit", properties[TARGET_TAG]) },
            { assertEquals("Submit", properties[TARGET_TEXT]) },
            { assertEquals("Android View", properties[TARGET_SOURCE]) },
            { assertEquals("Activity → Layout → Button", properties[HIERARCHY]) },
        )
    }

    @Test
    fun `TargetInfo toEventProperties handles null values`() {
        val targetInfo =
            FrustrationInteractionsDetector.TargetInfo(
                className = null,
                resourceName = null,
                tag = null,
                text = null,
                source = null,
                hierarchy = null,
            )

        val properties = targetInfo.toEventProperties()

        assertAll(
            { assertEquals(null, properties[TARGET_CLASS]) },
            { assertEquals(null, properties[TARGET_RESOURCE]) },
            { assertEquals(null, properties[TARGET_TAG]) },
            { assertEquals(null, properties[TARGET_TEXT]) },
            { assertEquals(null, properties[TARGET_SOURCE]) },
            { assertEquals(null, properties[HIERARCHY]) },
        )
    }

    //endregion

    //region Edge Cases

    @Test
    fun `handles rapid clicks correctly`() {
        val clickInfo = FrustrationInteractionsDetector.ClickInfo(100f, 100f)

        // Process 5 rapid clicks
        repeat(5) {
            detector.processClick(clickInfo, testTargetInfo)
        }

        // Should track exactly one rage click (not multiple)
        verify(exactly = 1) { mockAmplitude.track(RAGE_CLICK, any()) }
    }

    @Test
    fun `different target classes create separate sessions`() {
        val targetInfo1 = testTargetInfo.copy(className = "Button1")
        val targetInfo2 = testTargetInfo.copy(className = "Button2")
        val clickInfo = FrustrationInteractionsDetector.ClickInfo(100f, 100f)

        // 3 clicks on first target
        repeat(3) { detector.processClick(clickInfo, targetInfo1) }

        // 3 clicks on second target
        repeat(3) { detector.processClick(clickInfo, targetInfo2) }

        // Should trigger rage click for both targets
        verify(exactly = 2) { mockAmplitude.track(RAGE_CLICK, any()) }
    }

    @Test
    fun `dead click detection should not work without start()`() {
        // Attempt to process a dead click without calling start()
        val clickInfo = FrustrationInteractionsDetector.ClickInfo(100f, 100f)

        detector.processClick(clickInfo, testTargetInfo)

        // Verify that a warning was logged
        verify { mockLogger.error("Dead click detection is disabled - call start() to enable.") }

        // Verify no dead click event was tracked
        verify(exactly = 0) { mockAmplitude.track(DEAD_CLICK, any()) }
    }

    @Test
    fun `dead click detection should work after start()`() {
        // Start the detector
        detector.start()

        // Process a click
        val clickInfo = FrustrationInteractionsDetector.ClickInfo(100f, 100f)
        detector.processClick(clickInfo, testTargetInfo)

        // Verify no warning was logged about UI change collection being inactive
        verify(exactly = 0) {
            mockLogger.error("Dead click detection is disabled - call start() to enable.")
        }

        // Advance time to trigger dead click timeout
        testDispatcher.scheduler.advanceTimeBy(4000L)
        testDispatcher.scheduler.runCurrent()

        // Verify dead click was tracked (since no UI change signal was emitted)
        verify { mockAmplitude.track(DEAD_CLICK, any()) }
    }

    //endregion
}
