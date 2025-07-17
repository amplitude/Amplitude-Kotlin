package com.amplitude.android

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
import com.amplitude.core.Constants.EventProperties.HIERARCHY
import com.amplitude.core.Constants.EventProperties.TARGET_CLASS
import com.amplitude.core.Constants.EventProperties.TARGET_RESOURCE
import com.amplitude.core.Constants.EventProperties.TARGET_SOURCE
import com.amplitude.core.Constants.EventProperties.TARGET_TAG
import com.amplitude.core.Constants.EventProperties.TARGET_TEXT
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
) {
    companion object {
        /**
         * multiplier for density-independent pixels (dp)
         */
        private const val RAGE_CLICK_DISTANCE_THRESHOLD: Float = 50f
        private const val DEAD_CLICK_TIMEOUT: Long = 3_000L // 3 seconds
        private const val RAGE_CLICK_THRESHOLD: Int = 3
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
        uiChangeCollectionJob?.cancel()
        logger.debug("FrustrationInteractionsDetector stopped - UI change collection is now inactive")
    }

    /**
     * Processes a click event for both rage click and dead click detection.
     */
    fun processClick(
        clickInfo: ClickInfo,
        targetInfo: TargetInfo,
        additionalProperties: Map<String, Any?> = emptyMap(),
    ) {
        val clickTime = System.currentTimeMillis()
        val clickId = generateClickId(clickInfo, targetInfo)

        // Process for rage click detection (check specific ignore flag)
        val isIgnoredForRageClick =
            additionalProperties["isIgnoredForRageClick"] as? Boolean ?: false
        if (!isIgnoredForRageClick) {
            processRageClick(clickInfo, targetInfo, clickTime, additionalProperties)
        } else {
            logger.debug("Skipping rage click processing for ignored target: ${targetInfo.className}")
        }

        // Process for dead click detection (check specific ignore flag)
        val isIgnoredForDeadClick =
            additionalProperties["isIgnoredForDeadClick"] as? Boolean ?: false
        if (!isIgnoredForDeadClick) {
            processDeadClick(clickInfo, targetInfo, clickTime, clickId, additionalProperties)
        } else {
            logger.debug(
                "Skipping dead click processing for ignored target: ${targetInfo.className}",
            )
        }
    }

    private fun processRageClick(
        clickInfo: ClickInfo,
        targetInfo: TargetInfo,
        clickTime: Long,
        additionalProperties: Map<String, Any?>,
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

                    // Check if we've reached the rage click threshold
                    if (existingSession.clickCount >= RAGE_CLICK_THRESHOLD) {
                        trackRageClick(existingSession, targetInfo, additionalProperties)
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
        clickTime: Long,
        clickId: String,
        additionalProperties: Map<String, Any?>,
    ) {
        if (uiChangeCollectionJob?.isActive != true) {
            logger.error("Dead click detection is disabled - call start() to enable.")
            return
        }

        val deadClickSession =
            DeadClickSession(
                clickInfo = clickInfo.copy(timestamp = clickTime),
                targetInfo = targetInfo,
                preClickUiChangeTime = lastUiChangeTime,
                additionalProperties = additionalProperties,
            )

        pendingDeadClicks[clickId] = deadClickSession

        // Schedule dead click detection
        amplitude.amplitudeScope.launch(amplitude.amplitudeDispatcher) {
            delay(DEAD_CLICK_TIMEOUT)

            // Check if UI changed after the click
            if (lastUiChangeTime <= deadClickSession.preClickUiChangeTime) {
                // No UI change detected, this is a dead click
                trackDeadClick(deadClickSession)
            }

            pendingDeadClicks.remove(clickId)
        }
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
        targetInfo: TargetInfo,
        additionalProperties: Map<String, Any?>,
    ) {
        val properties =
            mutableMapOf<String, Any?>(
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

        // Add target information
        properties.putAll(targetInfo.toEventProperties())

        // Add additional platform-specific properties
        properties.putAll(additionalProperties)

        amplitude.track(RAGE_CLICK, properties)
        logger.debug("Rage click detected with ${session.clickCount} clicks")
    }

    private fun trackDeadClick(session: DeadClickSession) {
        val properties =
            mutableMapOf<String, Any?>(
                BEGIN_TIME to session.clickInfo.timestamp,
                END_TIME to (session.clickInfo.timestamp + DEAD_CLICK_TIMEOUT),
                DURATION to DEAD_CLICK_TIMEOUT,
                COORDINATE_X to session.clickInfo.x.toInt(),
                COORDINATE_Y to session.clickInfo.y.toInt(),
                CLICK_COUNT to 1,
            )

        // Add target information
        properties.putAll(session.targetInfo.toEventProperties())

        // Add additional platform-specific properties
        properties.putAll(session.additionalProperties)

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
        val distance = kotlin.math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2))
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
    ) {
        fun toEventProperties(): Map<String, Any?> =
            mapOf(
                TARGET_CLASS to className,
                TARGET_RESOURCE to resourceName,
                TARGET_TAG to tag,
                TARGET_TEXT to text,
                TARGET_SOURCE to
                    source
                        ?.replace("_", " ")
                        ?.split(" ")
                        ?.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
                HIERARCHY to hierarchy,
            )
    }

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
        val clickInfo: ClickInfo,
        val targetInfo: TargetInfo,
        val preClickUiChangeTime: Long,
        val additionalProperties: Map<String, Any?>,
    )
}
