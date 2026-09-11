package com.amplitude.android

import android.graphics.PointF
import com.amplitude.android.Constants.EventProperties.BEGIN_TIME
import com.amplitude.android.Constants.EventProperties.CLICKS
import com.amplitude.android.Constants.EventProperties.CLICK_COUNT
import com.amplitude.android.Constants.EventProperties.COORDINATE_X
import com.amplitude.android.Constants.EventProperties.COORDINATE_Y
import com.amplitude.android.Constants.EventProperties.DURATION
import com.amplitude.android.Constants.EventProperties.END_TIME
import com.amplitude.android.Constants.EventTypes.DEAD_CLICK
import com.amplitude.android.Constants.EventTypes.RAGE_CLICK
import com.amplitude.android.InteractionType.DeadClick
import com.amplitude.android.InteractionType.RageClick
import com.amplitude.android.internal.ViewTarget
import com.amplitude.android.internal.buildElementInteractedProperties
import com.amplitude.android.signals.UiChangeSignal
import com.amplitude.common.Logger
import com.amplitude.core.Amplitude
import com.amplitude.core.RestrictedAmplitudeFeature
import com.amplitude.core.platform.InterfaceChangeSignal
import com.amplitude.core.platform.InterfaceSignalProvider
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

/**
 * Core frustration interactions detector that handles rage click and dead click detection.
 *
 * **Important**: Call [start] to enable dead click detection. Dead clicks require an active
 * [InterfaceSignalProvider]. [UiChangeSignal] remains a compatible signal payload, but its provider
 * must expose current availability through [InterfaceSignalProvider].
 */
@OptIn(RestrictedAmplitudeFeature::class)
public class FrustrationInteractionsDetector(
    private val amplitude: Amplitude,
    private val logger: Logger,
    density: Float,
    private val autocaptureStateProvider: () -> AutocaptureState,
) {
    private val autocaptureState: AutocaptureState
        get() = autocaptureStateProvider()

    public companion object {
        /**
         * multiplier for density-independent pixels (dp)
         */
        private const val RAGE_CLICK_DISTANCE_THRESHOLD: Float = 50f
        private const val DEAD_CLICK_TIMEOUT: Long = 3_000L // 3 seconds
        private const val MAX_RECENT_UI_CHANGES: Int = 32
        private const val RAGE_CLICK_THRESHOLD: Int = 4
        private const val RAGE_CLICK_TIME_WINDOW: Long = 1_000L // 1 second
    }

    // Convert pt to pixels for density-independent behavior
    private val rageClickDistanceThresholdPx: Float = RAGE_CLICK_DISTANCE_THRESHOLD * density

    @Volatile
    private var started = false
    private val stateMutex = Mutex()
    private var lifecycleGeneration = 0L
    private var uiChangeCollectionJob: Job? = null

    // Rage click detection
    private val pendingRageClicks = ConcurrentHashMap<String, RageClickSession>()

    // Dead click detection
    private val pendingDeadClicks = mutableMapOf<String, DeadClickSession>()
    private val recentUiChangeTimes = ArrayDeque<Long>(MAX_RECENT_UI_CHANGES)

    /**
     * Starts the detector and begins subscribing to UI change signals.
     * This is required for proper dead click detection.
     */
    public fun start() {
        amplitude.amplitudeScope.launch(
            amplitude.amplitudeDispatcher,
            start = CoroutineStart.UNDISPATCHED,
        ) {
            val didStart =
                stateMutex.withLock {
                    if (started) return@withLock false

                    started = true
                    startUiChangeCollection()
                    true
                }
            if (didStart) {
                logger.debug("FrustrationInteractionsDetector started - UI change collection is now active")
            }
        }
    }

    public fun stop() {
        amplitude.amplitudeScope.launch(
            amplitude.amplitudeDispatcher,
            start = CoroutineStart.UNDISPATCHED,
        ) {
            val jobsToCancel =
                stateMutex.withLock {
                    val pendingJobs = pendingDeadClicks.values.mapNotNull { it.job }
                    started = false
                    lifecycleGeneration++
                    pendingDeadClicks.clear()
                    recentUiChangeTimes.clear()
                    pendingRageClicks.clear()
                    uiChangeCollectionJob?.cancel()
                    uiChangeCollectionJob = null
                    pendingJobs
                }
            jobsToCancel.forEach { it.cancel() }
            logger.debug("FrustrationInteractionsDetector stopped - UI change collection is now inactive")
        }
    }

    /**
     * Processes a click event for both rage click and dead click detection.
     */
    public fun processClick(
        clickInfo: ClickInfo,
        targetInfo: TargetInfo,
        target: ViewTarget,
        activityName: String,
    ) {
        val clickTime = System.currentTimeMillis()
        val clickId = generateClickId(clickInfo, targetInfo)

        // Process for rage click detection if enabled
        if (RageClick in autocaptureState.interactions) {
            val isIgnoredForRageClick = isRageClickIgnored(target)
            if (!isIgnoredForRageClick) {
                processRageClick(clickInfo, targetInfo, target, activityName, clickTime)
            } else {
                logger.debug("Skipping rage click processing for ignored target: ${targetInfo.className}")
            }
        }

        // Process for dead click detection if enabled
        if (DeadClick in autocaptureState.interactions) {
            val isIgnoredForDeadClick = isDeadClickIgnored(target)
            if (!isIgnoredForDeadClick) {
                processDeadClick(clickInfo, targetInfo, target, activityName, clickTime, clickId)
            } else {
                logger.debug(
                    "Skipping dead click processing for ignored target: ${targetInfo.className}",
                )
            }
        }
    }

    private fun processRageClick(
        clickInfo: ClickInfo,
        targetInfo: TargetInfo,
        target: ViewTarget,
        activityName: String,
        clickTime: Long,
    ) {
        val locationKey = generateLocationKey(clickInfo, targetInfo)

        val existingSession = pendingRageClicks[locationKey]
        if (existingSession != null) {
            // Check if this click is within the time window
            if (clickTime - existingSession.firstClickTime <= RAGE_CLICK_TIME_WINDOW) {
                // Check if this click is within the distance threshold
                if (isWithinDistanceThreshold(
                        clickInfo.x,
                        clickInfo.y,
                        existingSession.firstClickX,
                        existingSession.firstClickY,
                    )
                ) {
                    existingSession.clickCount++
                    existingSession.lastClickTime = clickTime
                    existingSession.clicks.add(clickInfo.copy(timestamp = clickTime))

                    // Check if we've reached the rage click threshold (4+ clicks in 1s to match iOS)
                    if (existingSession.clickCount >= RAGE_CLICK_THRESHOLD) {
                        trackRageClick(existingSession, target, activityName)
                        pendingRageClicks.remove(locationKey)
                    }
                } else {
                    // Click is outside distance threshold, start new session
                    startNewRageClickSession(locationKey, clickInfo, targetInfo, clickTime)
                }
            } else {
                // Click is outside time window, start new session
                startNewRageClickSession(locationKey, clickInfo, targetInfo, clickTime)
            }
        } else {
            // First click in this location
            startNewRageClickSession(locationKey, clickInfo, targetInfo, clickTime)
        }
    }

    private fun processDeadClick(
        clickInfo: ClickInfo,
        targetInfo: TargetInfo,
        target: ViewTarget,
        activityName: String,
        clickTime: Long,
        clickId: String,
    ) {
        if (!started) {
            logger.error("Dead click detection is disabled - call start() to enable.")
            return
        }

        amplitude.amplitudeScope.launch(
            amplitude.amplitudeDispatcher,
            start = CoroutineStart.UNDISPATCHED,
        ) {
            stateMutex.withLock {
                if (!started) {
                    logger.error("Dead click detection is disabled - call start() to enable.")
                    return@withLock
                }

                if (!hasActiveSignalProvider()) {
                    logger.error("Dead click detection is disabled - no UI change signal provider is active.")
                    return@withLock
                }

                pendingDeadClicks[clickId]?.job?.cancel()

                val deadClickSession =
                    DeadClickSession(
                        target = target,
                        activityName = activityName,
                        clickInfo = clickInfo.copy(timestamp = clickTime),
                        targetInfo = targetInfo,
                        lifecycleGeneration = lifecycleGeneration,
                        uiChanged =
                            recentUiChangeTimes.any { timestampMillis ->
                                timestampMillis >= clickTime
                            },
                    )

                val job =
                    amplitude.amplitudeScope.launch(
                        amplitude.amplitudeDispatcher,
                        start = CoroutineStart.LAZY,
                    ) {
                        delay(DEAD_CLICK_TIMEOUT)

                        val shouldTrackDeadClick =
                            stateMutex.withLock {
                                pendingDeadClicks.remove(clickId, deadClickSession) &&
                                    started &&
                                    lifecycleGeneration == deadClickSession.lifecycleGeneration &&
                                    hasActiveSignalProvider() &&
                                    !deadClickSession.uiChanged
                            }
                        if (shouldTrackDeadClick) {
                            trackDeadClick(deadClickSession)
                        }
                    }

                deadClickSession.job = job
                pendingDeadClicks[clickId] = deadClickSession
                job.start()
            }
        }
    }

    private fun startUiChangeCollection() {
        uiChangeCollectionJob =
            amplitude.amplitudeScope.launch(
                amplitude.amplitudeDispatcher,
                start = CoroutineStart.UNDISPATCHED,
            ) {
                logger.debug("Starting UI change signal collection for dead click detection")
                amplitude.signalFlow.collectLatest { signal ->
                    if (signal is InterfaceChangeSignal) {
                        recordUiChange(signal.timestampMillis)
                    }
                }
            }
    }

    private suspend fun recordUiChange(timestampMillis: Long) {
        val didRecordChange =
            stateMutex.withLock {
                if (!started) return@withLock false

                if (recentUiChangeTimes.size == MAX_RECENT_UI_CHANGES) {
                    recentUiChangeTimes.removeFirst()
                }
                recentUiChangeTimes.addLast(timestampMillis)
                pendingDeadClicks.values.forEach { session ->
                    if (timestampMillis >= session.clickInfo.timestamp) {
                        session.uiChanged = true
                    }
                }
                true
            }
        if (didRecordChange) {
            logger.debug("UI change detected at $timestampMillis")
        }
    }

    private fun hasActiveSignalProvider(): Boolean =
        amplitude.plugins(InterfaceSignalProvider::class.java).any { provider ->
            runCatching { provider.isProviding }.getOrDefault(false)
        }

    private fun startNewRageClickSession(
        locationKey: String,
        clickInfo: ClickInfo,
        targetInfo: TargetInfo,
        clickTime: Long,
    ) {
        val session =
            RageClickSession(
                firstClickTime = clickTime,
                lastClickTime = clickTime,
                clickCount = 1,
                firstClickX = clickInfo.x,
                firstClickY = clickInfo.y,
                targetInfo = targetInfo,
                clicks = mutableListOf(clickInfo.copy(timestamp = clickTime)),
            )
        pendingRageClicks[locationKey] = session
    }

    private fun trackRageClick(
        session: RageClickSession,
        target: ViewTarget,
        activityName: String,
    ) {
        // Build final properties: ELEMENT_INTERACTED + RAGE_CLICK specific
        val properties =
            buildElementInteractedProperties(target, activityName) +
                buildRageClickProperties(session)

        amplitude.track(RAGE_CLICK, properties)
        logger.debug("Rage click detected with ${session.clickCount} clicks")
    }

    private fun trackDeadClick(session: DeadClickSession) {
        // Build final properties: ELEMENT_INTERACTED + DEAD_CLICK specific
        val properties =
            buildElementInteractedProperties(session.target, session.activityName) +
                buildDeadClickProperties(session)

        amplitude.track(DEAD_CLICK, properties)
        logger.debug("Dead click detected")
    }

    private fun generateClickId(
        clickInfo: ClickInfo,
        targetInfo: TargetInfo,
    ): String =
        "${targetInfo.className ?: "null"}_" +
            "${clickInfo.x.toInt()}_" +
            "${clickInfo.y.toInt()}_" +
            "${System.currentTimeMillis()}"

    private fun generateLocationKey(
        clickInfo: ClickInfo,
        targetInfo: TargetInfo,
    ): String =
        "${targetInfo.className}_" +
            "${(clickInfo.x / rageClickDistanceThresholdPx).toInt()}_" +
            "${(clickInfo.y / rageClickDistanceThresholdPx).toInt()}"

    private fun isWithinDistanceThreshold(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
    ): Boolean {
        // Use Android's built-in PointF.length() for distance calculation
        val point1 = PointF(x1, y1)
        val point2 = PointF(x2, y2)
        val distance = PointF.length(point1.x - point2.x, point1.y - point2.y)
        return distance <= rageClickDistanceThresholdPx
    }

    /**
     * Information about a click event (platform-agnostic)
     */
    public data class ClickInfo(
        val x: Float,
        val y: Float,
        val timestamp: Long = System.currentTimeMillis(),
    )

    /**
     * Information about the target element (platform-agnostic)
     */
    public data class TargetInfo(
        val className: String?,
        val resourceName: String?,
        val tag: String?,
        val text: String?,
        val source: String?,
        val hierarchy: String?,
    )

    internal data class RageClickSession(
        val firstClickTime: Long,
        var lastClickTime: Long,
        var clickCount: Int,
        val firstClickX: Float,
        val firstClickY: Float,
        val targetInfo: TargetInfo,
        val clicks: MutableList<ClickInfo>,
    )

    internal data class DeadClickSession(
        val target: ViewTarget,
        val activityName: String,
        val clickInfo: ClickInfo,
        val targetInfo: TargetInfo,
        val lifecycleGeneration: Long,
        var uiChanged: Boolean = false,
        var job: Job? = null,
    )

    /**
     * Builds only the rage-click specific properties.
     */
    private fun buildRageClickProperties(session: RageClickSession): Map<String, Any?> =
        mapOf(
            BEGIN_TIME to session.firstClickTime,
            END_TIME to session.lastClickTime,
            DURATION to (session.lastClickTime - session.firstClickTime),
            COORDINATE_X to session.firstClickX.toInt(),
            COORDINATE_Y to session.firstClickY.toInt(),
            CLICK_COUNT to session.clickCount,
            CLICKS to
                session.clicks.map {
                    mapOf(
                        COORDINATE_X to it.x.toInt(),
                        COORDINATE_Y to it.y.toInt(),
                        "timestamp" to it.timestamp,
                    )
                },
        )

    /**
     * Builds only the dead-click specific properties.
     */
    private fun buildDeadClickProperties(session: DeadClickSession): Map<String, Any?> =
        mapOf(
            BEGIN_TIME to session.clickInfo.timestamp,
            END_TIME to (session.clickInfo.timestamp + DEAD_CLICK_TIMEOUT),
            DURATION to DEAD_CLICK_TIMEOUT,
            COORDINATE_X to session.clickInfo.x.toInt(),
            COORDINATE_Y to session.clickInfo.y.toInt(),
            CLICK_COUNT to 1,
        )

    /**
     * Checks if rage click detection should be ignored for this target.
     * Supports programmatic API and Compose AmpFrustrationIgnoreElement.
     */
    private fun isRageClickIgnored(target: ViewTarget): Boolean {
        // Check programmatic/XML/compose ignore flags
        return target.ampIgnoreRageClick
    }

    /**
     * Checks if dead click detection should be ignored for this target.
     * Supports programmatic API and Compose AmpFrustrationIgnoreElement.
     */
    private fun isDeadClickIgnored(target: ViewTarget): Boolean {
        // Check programmatic/XML/compose ignore flags
        return target.ampIgnoreDeadClick
    }
}
