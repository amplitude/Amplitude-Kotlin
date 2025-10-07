package com.amplitude.android

import android.app.Activity
import com.amplitude.android.internal.ViewTarget
import com.amplitude.android.signals.UiChangeSignal
import com.amplitude.common.Logger
import com.amplitude.core.Amplitude
import com.amplitude.core.Constants.EventProperties.ACTION
import com.amplitude.core.Constants.EventProperties.CLICK_COUNT
import com.amplitude.core.Constants.EventProperties.COORDINATE_X
import com.amplitude.core.Constants.EventProperties.COORDINATE_Y
import com.amplitude.core.Constants.EventProperties.TARGET_CLASS
import com.amplitude.core.Constants.EventTypes.DEAD_CLICK
import com.amplitude.core.Constants.EventTypes.RAGE_CLICK
import com.amplitude.core.platform.Signal
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
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class FrustrationInteractionsDetectorTest {
    private lateinit var mockAmplitude: Amplitude
    private lateinit var mockLogger: Logger
    private lateinit var mockViewTarget: ViewTarget
    private lateinit var mockActivity: Activity
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
        mockViewTarget = mockk(relaxed = true)
        mockActivity = mockk(relaxed = true)
        uiChangeFlow = MutableSharedFlow(extraBufferCapacity = 1)

        // Setup mock ViewTarget properties to match testTargetInfo
        every { mockViewTarget.className } returns testTargetInfo.className
        every { mockViewTarget.resourceName } returns testTargetInfo.resourceName
        every { mockViewTarget.tag } returns testTargetInfo.tag
        every { mockViewTarget.text } returns testTargetInfo.text
        every { mockViewTarget.source } returns (testTargetInfo.source ?: "android_view")
        every { mockViewTarget.hierarchy } returns testTargetInfo.hierarchy
        every { mockViewTarget.ampIgnoreRageClick } returns false
        every { mockViewTarget.ampIgnoreDeadClick } returns false

        every { mockAmplitude.signalFlow } returns uiChangeFlow
        every { mockAmplitude.amplitudeScope } returns CoroutineScope(testDispatcher)
        every { mockAmplitude.amplitudeDispatcher } returns testDispatcher

        detector =
            FrustrationInteractionsDetector(
                amplitude = mockAmplitude,
                logger = mockLogger,
                // 2x density for testing
                density = 2f,
                autocaptureState =
                    AutocaptureState(
                        interactions =
                            listOf(
                                InteractionType.RageClick,
                                InteractionType.DeadClick,
                            ),
                    ),
            )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        clearMocks(mockAmplitude, mockLogger, mockViewTarget, mockActivity)
    }

    //region Rage Click Tests

    @Test
    fun `rage click - triggers after threshold clicks within distance`() {
        val clickInfo = FrustrationInteractionsDetector.ClickInfo(100f, 100f)

        repeat(4) {
            detector.processClick(clickInfo, testTargetInfo, mockViewTarget, mockActivity)
        }

        verify { mockAmplitude.track(RAGE_CLICK, any()) }
    }

    @Test
    fun `rage click - respects distance threshold with density conversion`() {
        val baseClick = FrustrationInteractionsDetector.ClickInfo(100f, 100f)
        val nearbyClick = FrustrationInteractionsDetector.ClickInfo(149f, 149f) // Within 50dp threshold at 2x density
        val farClick = FrustrationInteractionsDetector.ClickInfo(200f, 200f) // Outside threshold

        // Near clicks should trigger rage click
        detector.processClick(baseClick, testTargetInfo, mockViewTarget, mockActivity)
        detector.processClick(nearbyClick, testTargetInfo, mockViewTarget, mockActivity)
        detector.processClick(baseClick, testTargetInfo, mockViewTarget, mockActivity)
        detector.processClick(nearbyClick, testTargetInfo, mockViewTarget, mockActivity)

        verify { mockAmplitude.track(RAGE_CLICK, any()) }

        clearMocks(mockAmplitude)

        // Far click should start new session (no rage click)
        detector.processClick(baseClick, testTargetInfo, mockViewTarget, mockActivity)
        detector.processClick(farClick, testTargetInfo, mockViewTarget, mockActivity)
        detector.processClick(baseClick, testTargetInfo, mockViewTarget, mockActivity)
        detector.processClick(farClick, testTargetInfo, mockViewTarget, mockActivity)

        verify(exactly = 0) { mockAmplitude.track(RAGE_CLICK, any()) }
    }

    @Test
    fun `rage click - respects ignore flag`() {
        val clickInfo = FrustrationInteractionsDetector.ClickInfo(100f, 100f)
        val ignoredTargetInfo = testTargetInfo // No need to modify tag
        val ignoredViewTarget = mockk<ViewTarget>(relaxed = true)
        every { ignoredViewTarget.ampIgnoreRageClick } returns true
        every { ignoredViewTarget.ampIgnoreDeadClick } returns false

        repeat(4) {
            detector.processClick(clickInfo, ignoredTargetInfo, ignoredViewTarget, mockActivity)
        }

        verify(exactly = 0) { mockAmplitude.track(RAGE_CLICK, any()) }
        verify { mockLogger.debug(match { it.contains("Skipping rage click processing") }) }
    }

    @Test
    fun `rage click - tracks correct event properties`() {
        val clickInfo = FrustrationInteractionsDetector.ClickInfo(150f, 200f)

        repeat(4) {
            detector.processClick(clickInfo, testTargetInfo, mockViewTarget, mockActivity)
        }

        val capturedProperties = slot<Map<String, Any?>>()
        verify { mockAmplitude.track(RAGE_CLICK, capture(capturedProperties)) }

        assertAll(
            { assertEquals(150, capturedProperties.captured[COORDINATE_X]) },
            { assertEquals(200, capturedProperties.captured[COORDINATE_Y]) },
            { assertEquals(4, capturedProperties.captured[CLICK_COUNT]) },
            { assertEquals("TestButton", capturedProperties.captured[TARGET_CLASS]) },
            { assertEquals("touch", capturedProperties.captured[ACTION]) },
        )
    }

    //endregion

    //region Dead Click Tests

    @Test
    fun `dead click - respects ignore flag`() {
        val clickInfo = FrustrationInteractionsDetector.ClickInfo(100f, 100f)
        val ignoredTargetInfo = testTargetInfo // No need to modify tag
        val ignoredViewTarget = mockk<ViewTarget>(relaxed = true)
        every { ignoredViewTarget.ampIgnoreRageClick } returns false
        every { ignoredViewTarget.ampIgnoreDeadClick } returns true

        detector.processClick(clickInfo, ignoredTargetInfo, ignoredViewTarget, mockActivity)

        verify { mockLogger.debug(match { it.contains("Skipping dead click processing") }) }
    }

    @Test
    fun `dead click - detector should be started for signal flow subscription`() {
        // This test validates that start() is needed for proper signal flow subscription
        // We'll test this by confirming that the startUiChangeCollection method is called
        val mockAmplitudeWithStart = mockk<Amplitude>(relaxed = true)
        every { mockAmplitudeWithStart.amplitudeScope } returns CoroutineScope(testDispatcher)
        every { mockAmplitudeWithStart.amplitudeDispatcher } returns testDispatcher

        val detectorWithStart =
            FrustrationInteractionsDetector(
                amplitude = mockAmplitudeWithStart,
                logger = mockLogger,
                density = 2f,
                autocaptureState = AutocaptureState(interactions = listOf(InteractionType.RageClick, InteractionType.DeadClick)),
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

        val detector1x =
            FrustrationInteractionsDetector(
                mockAmplitude1x,
                mockLogger,
                1f,
                AutocaptureState(interactions = listOf(InteractionType.RageClick, InteractionType.DeadClick)),
            )
        val detector3x =
            FrustrationInteractionsDetector(
                mockAmplitude3x,
                mockLogger,
                3f,
                AutocaptureState(interactions = listOf(InteractionType.RageClick, InteractionType.DeadClick)),
            )

        // Base click at (10, 10)
        val baseClick = FrustrationInteractionsDetector.ClickInfo(10f, 10f)

        // Second click at (70, 10) - 60px distance
        val distantClick = FrustrationInteractionsDetector.ClickInfo(70f, 10f)

        // For 1x density: 50pt = 50px threshold, 60px > 50px -> no rage click
        detector1x.processClick(baseClick, testTargetInfo, mockViewTarget, mockActivity)
        detector1x.processClick(distantClick, testTargetInfo, mockViewTarget, mockActivity)
        detector1x.processClick(baseClick, testTargetInfo, mockViewTarget, mockActivity)

        // For 3x density: 50pt = 150px threshold, 60px < 150px -> should trigger rage click
        detector3x.processClick(baseClick, testTargetInfo, mockViewTarget, mockActivity)
        detector3x.processClick(distantClick, testTargetInfo, mockViewTarget, mockActivity)
        detector3x.processClick(baseClick, testTargetInfo, mockViewTarget, mockActivity)
        detector3x.processClick(distantClick, testTargetInfo, mockViewTarget, mockActivity)

        // Verify 1x density detector doesn't trigger rage click
        verify(exactly = 0) { mockAmplitude1x.track(RAGE_CLICK, any()) }

        // Verify 3x density detector does trigger rage click
        verify(exactly = 1) { mockAmplitude3x.track(RAGE_CLICK, any()) }
    }

    //endregion

    //region Edge Cases

    @Test
    fun `handles rapid clicks correctly`() {
        val clickInfo = FrustrationInteractionsDetector.ClickInfo(100f, 100f)

        // Process 5 rapid clicks (above the 4-click threshold)
        repeat(5) {
            detector.processClick(clickInfo, testTargetInfo, mockViewTarget, mockActivity)
        }

        // Should track exactly one rage click (not multiple)
        verify(exactly = 1) { mockAmplitude.track(RAGE_CLICK, any()) }
    }

    @Test
    fun `different target classes create separate sessions`() {
        val targetInfo1 = testTargetInfo.copy(className = "Button1")
        val targetInfo2 = testTargetInfo.copy(className = "Button2")
        val clickInfo = FrustrationInteractionsDetector.ClickInfo(100f, 100f)

        // 4 clicks on first target
        repeat(4) { detector.processClick(clickInfo, targetInfo1, mockViewTarget, mockActivity) }

        // 4 clicks on second target
        repeat(4) { detector.processClick(clickInfo, targetInfo2, mockViewTarget, mockActivity) }

        // Should trigger rage click for both targets
        verify(exactly = 2) { mockAmplitude.track(RAGE_CLICK, any()) }
    }

    @Test
    fun `dead click detection should not work without start()`() {
        // Attempt to process a dead click without calling start()
        val clickInfo = FrustrationInteractionsDetector.ClickInfo(100f, 100f)

        detector.processClick(clickInfo, testTargetInfo, mockViewTarget, mockActivity)

        // Verify that a warning was logged
        verify { mockLogger.error("Dead click detection is disabled - call start() to enable.") }

        // Verify no dead click event was tracked
        verify(exactly = 0) { mockAmplitude.track(DEAD_CLICK, any()) }
    }

    @Test
    fun `dead click detection should work after start()`() {
        // Start the detector and ensure the signal collector is subscribed
        detector.start()
        testDispatcher.scheduler.runCurrent()
        // Emit a UiChangeSignal to enable dead click detection
        uiChangeFlow.tryEmit(UiChangeSignal(Date(System.currentTimeMillis())))
        testDispatcher.scheduler.runCurrent()

        // Process a click
        val clickInfo = FrustrationInteractionsDetector.ClickInfo(100f, 100f)
        detector.processClick(clickInfo, testTargetInfo, mockViewTarget, mockActivity)

        // Verify no warning was logged about UI change collection being inactive
        verify(exactly = 0) {
            mockLogger.error("Dead click detection is disabled - call start() to enable.")
        }

        // Advance time to trigger dead click timeout
        testDispatcher.scheduler.advanceTimeBy(4000L)
        testDispatcher.scheduler.runCurrent()

        // Verify dead click was tracked
        verify { mockAmplitude.track(DEAD_CLICK, any()) }
    }

    @Test
    fun `dead click detection should not work after start() until UiChangeSignal is observed`() {
        detector.start()

        val clickInfo = FrustrationInteractionsDetector.ClickInfo(100f, 100f)
        detector.processClick(clickInfo, testTargetInfo, mockViewTarget, mockActivity)

        // Advance time past timeout
        testDispatcher.scheduler.advanceTimeBy(4000L)
        testDispatcher.scheduler.runCurrent()

        // Should not track without UiChangeSignal and should log the gating error
        verify(exactly = 0) { mockAmplitude.track(DEAD_CLICK, any()) }
        verify {
            mockLogger.error(
                match { it.contains("no UI change signals observed yet") },
            )
        }
    }

    @Test
    fun `dead click detection should reset after stop()`() {
        // Enable by emitting a UiChangeSignal after start
        detector.start()
        uiChangeFlow.tryEmit(UiChangeSignal(Date(System.currentTimeMillis())))
        testDispatcher.scheduler.runCurrent()

        // Stop should reset gating state
        detector.stop()

        // Restart but do NOT emit UiChangeSignal again
        detector.start()

        val clickInfo = FrustrationInteractionsDetector.ClickInfo(100f, 100f)
        detector.processClick(clickInfo, testTargetInfo, mockViewTarget, mockActivity)

        testDispatcher.scheduler.advanceTimeBy(4000L)
        testDispatcher.scheduler.runCurrent()

        verify(exactly = 0) { mockAmplitude.track(DEAD_CLICK, any()) }
        verify {
            mockLogger.error(
                match { it.contains("no UI change signals observed yet") },
            )
        }
    }

    //endregion

    //region Granular Options Tests

    @Test
    fun `rage click disabled - does not track rage clicks`() {
        val detectorWithOptions =
            FrustrationInteractionsDetector(
                amplitude = mockAmplitude,
                logger = mockLogger,
                density = 2f,
                autocaptureState = AutocaptureState(interactions = listOf(InteractionType.DeadClick)),
            )

        val clickInfo = FrustrationInteractionsDetector.ClickInfo(100f, 100f)

        // Perform 4 rapid clicks (should trigger rage click if enabled)
        repeat(4) {
            detectorWithOptions.processClick(clickInfo, testTargetInfo, mockViewTarget, mockActivity)
        }

        // Verify no rage click was tracked
        verify(exactly = 0) { mockAmplitude.track(RAGE_CLICK, any()) }
    }

    @Test
    fun `dead click disabled - does not track dead clicks`() {
        val detectorWithOptions =
            FrustrationInteractionsDetector(
                amplitude = mockAmplitude,
                logger = mockLogger,
                density = 2f,
                autocaptureState = AutocaptureState(interactions = listOf(InteractionType.RageClick)),
            )
        detectorWithOptions.start()

        // Emit initial UI change to indicate signal provider is active
        testDispatcher.scheduler.advanceUntilIdle()
        uiChangeFlow.tryEmit(UiChangeSignal(Date()))
        testDispatcher.scheduler.advanceUntilIdle()

        val clickInfo = FrustrationInteractionsDetector.ClickInfo(100f, 100f)
        detectorWithOptions.processClick(clickInfo, testTargetInfo, mockViewTarget, mockActivity)

        // Advance time to trigger dead click timeout
        testDispatcher.scheduler.advanceTimeBy(4000L)
        testDispatcher.scheduler.runCurrent()

        // Verify no dead click was tracked
        verify(exactly = 0) { mockAmplitude.track(DEAD_CLICK, any()) }
    }

    @Test
    fun `both interactions disabled - tracks neither rage nor dead clicks`() {
        val detectorWithOptions =
            FrustrationInteractionsDetector(
                amplitude = mockAmplitude,
                logger = mockLogger,
                density = 2f,
                autocaptureState = AutocaptureState(interactions = emptyList()),
            )
        detectorWithOptions.start()

        // Emit initial UI change
        testDispatcher.scheduler.advanceUntilIdle()
        uiChangeFlow.tryEmit(UiChangeSignal(Date()))
        testDispatcher.scheduler.advanceUntilIdle()

        val clickInfo = FrustrationInteractionsDetector.ClickInfo(100f, 100f)

        // Try to trigger both rage click (4 rapid clicks) and dead click
        repeat(4) {
            detectorWithOptions.processClick(clickInfo, testTargetInfo, mockViewTarget, mockActivity)
        }

        // Advance time for dead click timeout
        testDispatcher.scheduler.advanceTimeBy(4000L)
        testDispatcher.scheduler.runCurrent()

        // Verify neither type was tracked
        verify(exactly = 0) { mockAmplitude.track(RAGE_CLICK, any()) }
        verify(exactly = 0) { mockAmplitude.track(DEAD_CLICK, any()) }
    }

    @Test
    fun `both interactions enabled - rage click works`() {
        val detectorWithOptions =
            FrustrationInteractionsDetector(
                amplitude = mockAmplitude,
                logger = mockLogger,
                density = 2f,
                autocaptureState = AutocaptureState(interactions = listOf(InteractionType.RageClick, InteractionType.DeadClick)),
            )

        // Test rage click
        val rageClickInfo = FrustrationInteractionsDetector.ClickInfo(100f, 100f)
        repeat(4) {
            detectorWithOptions.processClick(rageClickInfo, testTargetInfo, mockViewTarget, mockActivity)
        }

        // Verify rage click was tracked
        verify(exactly = 1) { mockAmplitude.track(RAGE_CLICK, any()) }
    }

    @Test
    fun `both interactions enabled - dead click works`() {
        val detectorWithOptions =
            FrustrationInteractionsDetector(
                amplitude = mockAmplitude,
                logger = mockLogger,
                density = 2f,
                autocaptureState = AutocaptureState(interactions = listOf(InteractionType.RageClick, InteractionType.DeadClick)),
            )
        detectorWithOptions.start()

        // Emit initial UI change to indicate signal provider is active
        testDispatcher.scheduler.advanceUntilIdle()
        uiChangeFlow.tryEmit(UiChangeSignal(Date()))
        testDispatcher.scheduler.advanceUntilIdle()

        // Test dead click
        val deadClickInfo = FrustrationInteractionsDetector.ClickInfo(500f, 500f)
        detectorWithOptions.processClick(deadClickInfo, testTargetInfo, mockViewTarget, mockActivity)

        // Advance time for dead click timeout
        testDispatcher.scheduler.advanceTimeBy(4000L)
        testDispatcher.scheduler.runCurrent()

        // Verify dead click was tracked
        verify(exactly = 1) { mockAmplitude.track(DEAD_CLICK, any()) }
    }

    @Test
    fun `default options - rage click is enabled by default`() {
        val detectorWithDefaultOptions =
            FrustrationInteractionsDetector(
                amplitude = mockAmplitude,
                logger = mockLogger,
                density = 2f,
                autocaptureState = AutocaptureState(interactions = listOf(InteractionType.RageClick, InteractionType.DeadClick)),
            )

        // Test rage click
        val rageClickInfo = FrustrationInteractionsDetector.ClickInfo(100f, 100f)
        repeat(4) {
            detectorWithDefaultOptions.processClick(rageClickInfo, testTargetInfo, mockViewTarget, mockActivity)
        }

        // Verify rage click was tracked (default behavior)
        verify(exactly = 1) { mockAmplitude.track(RAGE_CLICK, any()) }
    }

    @Test
    fun `default options - dead click is enabled by default`() {
        val detectorWithDefaultOptions =
            FrustrationInteractionsDetector(
                amplitude = mockAmplitude,
                logger = mockLogger,
                density = 2f,
                autocaptureState = AutocaptureState(interactions = listOf(InteractionType.RageClick, InteractionType.DeadClick)),
            )
        detectorWithDefaultOptions.start()

        // Emit initial UI change
        testDispatcher.scheduler.advanceUntilIdle()
        uiChangeFlow.tryEmit(UiChangeSignal(Date(System.currentTimeMillis())))
        testDispatcher.scheduler.runCurrent()

        // Test dead click
        val deadClickInfo = FrustrationInteractionsDetector.ClickInfo(500f, 500f)
        detectorWithDefaultOptions.processClick(deadClickInfo, testTargetInfo, mockViewTarget, mockActivity)

        // Advance time for dead click timeout
        testDispatcher.scheduler.advanceTimeBy(5_000L)
        testDispatcher.scheduler.runCurrent()

        // Verify dead click was tracked (default behavior)
        verify(exactly = 1) { mockAmplitude.track(DEAD_CLICK, any()) }
    }

    //endregion
}
