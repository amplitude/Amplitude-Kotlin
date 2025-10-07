package com.amplitude.android

import android.app.Activity
import android.graphics.PointF
import com.amplitude.android.internal.ViewTarget
import com.amplitude.android.internal.buildElementInteractedProperties
import com.amplitude.android.signals.UiChangeSignal
import com.amplitude.common.Logger
import com.amplitude.core.Amplitude
import com.amplitude.core.Constants.EventProperties.BEGIN_TIME
import com.amplitude.core.Constants.EventProperties.CLICKS
import com.amplitude.core.Constants.EventProperties.CLICK_COUNT
import com.amplitude.core.Constants.EventProperties.COORDINATE_X
import com.amplitude.core.Constants.EventProperties.COORDINATE_Y
import com.amplitude.core.Constants.EventProperties.DURATION
import com.amplitude.core.Constants.EventProperties.END_TIME
import com.amplitude.core.Constants.EventTypes.DEAD_CLICK
import com.amplitude.core.Constants.EventTypes.RAGE_CLICK
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Core frustration interactions detector that handles rage click and dead click detection.
 *
 * **Important**: Call `start()` to enable proper dead click detection. Dead clicks require
 * active subscription to UI change signals to function correctly. If `start()` is not called,
 * dead click events will not be tracked and a warning will be logged.
 */
class FrustrationInteractionsDetector(
    private val amplitude: Amplitude,
    private val logger: Logger,
    density: Float,
    private val interactionsOptions: InteractionsOptions = InteractionsOptions(),
) {
    companion object {
        /**
         * multiplier for density-independent pixels (dp)
         */
        private const val RAGE_CLICK_DISTANCE_THRESHOLD: Float = 50f
        private const val DEAD_CLICK_TIMEOUT: Long = 3_000L // 3 seconds
        private const val RAGE_CLICK_THRESHOLD: Int = 4
        private const val RAGE_CLICK_TIME_WINDOW: Long = 1_000L // 1 second
    }

    // Convert pt to pixels for density-independent behavior
    private val rageClickDistanceThresholdPx: Float = RAGE_CLICK_DISTANCE_THRESHOLD * density

    private var uiChangeCollectionJob: Job? = null

    // Rage click detection
    private val pendingRageClicks = ConcurrentHashMap<String, RageClickSession>()

    // Dead click detection
    private val pendingDeadClicks = ConcurrentHashMap<String, DeadClickSession>()
    private var lastUiChangeTime = 0L

    /**
     * Starts the detector and begins subscribing to UI change signals.
     * This is required for proper dead click detection.
     */
    fun start() {
        startUiChangeCollection()
        logger.debug("FrustrationInteractionsDetector started - UI change collection is now active")
    }

    fun stop() {
        lastUiChangeTime = 0L
        uiChangeCollectionJob?.cancel()
        // Cancel all pending dead click jobs to prevent resource leaks
        pendingDeadClicks.values.forEach { it.job?.cancel() }
        pendingDeadClicks.clear()
        pendingRageClicks.clear()
        logger.debug("FrustrationInteractionsDetector stopped - UI change collection is now inactive")
    }

    /**
     * Processes a click event for both rage click and dead click detection.
     */
    fun processClick(
        clickInfo: ClickInfo,
        targetInfo: TargetInfo,
        target: ViewTarget,
        activity: Activity,
    ) {
        val clickTime = System.currentTimeMillis()
        val clickId = generateClickId(clickInfo, targetInfo)

        // Process for rage click detection if enabled
        if (interactionsOptions.rageClick.enabled) {
            val isIgnoredForRageClick = isRageClickIgnored(target)
            if (!isIgnoredForRageClick) {
                processRageClick(clickInfo, targetInfo, target, activity, clickTime)
            } else {
                logger.debug("Skipping rage click processing for ignored target: ${targetInfo.className}")
            }
        }

        // Process for dead click detection if enabled
        if (interactionsOptions.deadClick.enabled) {
            val isIgnoredForDeadClick = isDeadClickIgnored(target)
            if (!isIgnoredForDeadClick) {
                processDeadClick(clickInfo, targetInfo, target, activity, clickTime, clickId)
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
        activity: Activity,
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
                        trackRageClick(existingSession, target, activity)
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
        activity: Activity,
        clickTime: Long,
        clickId: String,
    ) {
        if (uiChangeCollectionJob?.isActive != true) {
            logger.error("Dead click detection is disabled - call start() to enable.")
            return
        }

        // Dead click detection requires an active SignalProvider plugin to emit UI change signals
        if (!hasActiveSignalProvider()) {
            logger.error("Dead click detection is disabled - no UI change signals observed yet. Ensure SessionReplay plugin is active.")
            return
        }

        // Cancel any existing dead click job for this location to prevent accumulation
        pendingDeadClicks[clickId]?.job?.cancel()

        val deadClickSession =
            DeadClickSession(
                target = target,
                activity = activity,
                clickInfo = clickInfo.copy(timestamp = clickTime),
                targetInfo = targetInfo,
                preClickUiChangeTime = lastUiChangeTime,
            )

        pendingDeadClicks[clickId] = deadClickSession

        // Schedule dead click detection
        val job =
            amplitude.amplitudeScope.launch(amplitude.amplitudeDispatcher) {
                delay(DEAD_CLICK_TIMEOUT)

                // Check if UI changed after the click
                if (lastUiChangeTime <= deadClickSession.preClickUiChangeTime) {
                    // No UI change detected, this is a dead click
                    trackDeadClick(deadClickSession)
                }

                // Clean up when done
                pendingDeadClicks.remove(clickId)
            }

        deadClickSession.job = job
    }

    private fun startUiChangeCollection() {
        uiChangeCollectionJob =
            amplitude.amplitudeScope.launch(amplitude.amplitudeDispatcher) {
                logger.debug("Starting UI change signal collection for dead click detection")
                amplitude.signalFlow.collectLatest { signal ->
                    if (signal is UiChangeSignal) {
                        lastUiChangeTime = signal.timestamp.time
                        logger.debug("UI change detected at $lastUiChangeTime")
                    }
                }
            }
    }

    /**
     * This is used to determine if a SignalProvider-backed plugin is active.
     * So if we have seen at least one UiChangeSignal, lastUiChangeTime will be > 0.
     */
    private fun hasActiveSignalProvider(): Boolean = lastUiChangeTime > 0L

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
        activity: Activity,
    ) {
        // Build final properties: ELEMENT_INTERACTED + RAGE_CLICK specific
        val properties =
            buildElementInteractedProperties(target, activity) +
                buildRageClickProperties(session)

        amplitude.track(RAGE_CLICK, properties)
        logger.debug("Rage click detected with ${session.clickCount} clicks")
    }

    private fun trackDeadClick(session: DeadClickSession) {
        // Build final properties: ELEMENT_INTERACTED + DEAD_CLICK specific
        val properties =
            buildElementInteractedProperties(session.target, session.activity) +
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
    data class ClickInfo(
        val x: Float,
        val y: Float,
        val timestamp: Long = System.currentTimeMillis(),
    )

    /**
     * Information about the target element (platform-agnostic)
     */
    data class TargetInfo(
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
        val activity: Activity,
        val clickInfo: ClickInfo,
        val targetInfo: TargetInfo,
        val preClickUiChangeTime: Long,
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
