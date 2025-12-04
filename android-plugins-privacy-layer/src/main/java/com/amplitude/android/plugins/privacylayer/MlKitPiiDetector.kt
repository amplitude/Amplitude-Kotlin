package com.amplitude.android.plugins.privacylayer

import android.content.Context
import com.amplitude.android.plugins.privacylayer.models.DetectedPii
import com.amplitude.android.plugins.privacylayer.models.EntityType
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractionParams
import com.google.mlkit.nl.entityextraction.EntityExtractor
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * MLKit-based PII detector using Entity Extraction API.
 *
 * This detector uses Google's MLKit to identify PII entities in text.
 * It requires the MLKit model to be downloaded before use.
 *
 * @property context Android context for model downloads
 * @property config Configuration for detection
 */
class MlKitPiiDetector(
    private val context: Context,
    private val config: PrivacyLayerConfig,
) {
    private var extractor: EntityExtractor? = null
    private var modelDownloaded = false

    init {
        // Initialize entity extractor with English language
        val options =
            EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH)
                .build()
        extractor = EntityExtraction.getClient(options)
    }

    /**
     * Ensure the MLKit model is downloaded and ready.
     * This should be called during plugin setup.
     *
     * @return true if model is ready, false if download failed
     */
    suspend fun ensureModelDownloaded(): Boolean =
        withContext(Dispatchers.IO) {
            if (modelDownloaded) return@withContext true

            try {
                val downloadTask = extractor?.downloadModelIfNeeded()
                if (downloadTask != null) {
                    // Wait for download to complete (with 30 second timeout)
                    Tasks.await(downloadTask, 30, TimeUnit.SECONDS)
                    modelDownloaded = true
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                // Model download failed
                false
            }
        }

    /**
     * Detect PII entities in the given text using MLKit.
     *
     * @param text The text to analyze
     * @return List of detected PII entities
     */
    suspend fun detectPii(text: String): List<DetectedPii> =
        withContext(Dispatchers.IO) {
            if (!modelDownloaded || extractor == null) {
                return@withContext emptyList()
            }

            try {
                val params =
                    EntityExtractionParams.Builder(text)
                        .build()

                val annotateTask = extractor!!.annotate(params)
                val annotations = Tasks.await(annotateTask, 10, TimeUnit.SECONDS)

                val results = mutableListOf<DetectedPii>()

                for (annotation in annotations) {
                    for (entity in annotation.entities) {
                        // Map MLKit entity type to our EntityType
                        val entityType = mapMlKitEntityType(entity.type) ?: continue

                        // Skip if not in configured entity types
                        if (entityType !in config.entityTypes) {
                            continue
                        }

                        results.add(
                            DetectedPii(
                                text = text.substring(annotation.start, annotation.end),
                                type = entityType,
                                startIndex = annotation.start,
                                endIndex = annotation.end,
                            ),
                        )
                    }
                }

                results
            } catch (e: Exception) {
                // Detection failed, return empty list
                emptyList()
            }
        }

    /**
     * Map MLKit entity type to our EntityType enum.
     *
     * @param mlKitType The MLKit entity type constant
     * @return Corresponding EntityType, or null if not supported
     */
    private fun mapMlKitEntityType(mlKitType: Int): EntityType? =
        when (mlKitType) {
            Entity.TYPE_ADDRESS -> EntityType.ADDRESS
            Entity.TYPE_DATE_TIME -> EntityType.DATE_TIME
            Entity.TYPE_EMAIL -> EntityType.EMAIL
            Entity.TYPE_FLIGHT_NUMBER -> EntityType.FLIGHT_NUMBER
            Entity.TYPE_IBAN -> EntityType.IBAN
            Entity.TYPE_ISBN -> EntityType.ISBN
            Entity.TYPE_MONEY -> EntityType.MONEY
            Entity.TYPE_PAYMENT_CARD -> EntityType.PAYMENT_CARD
            Entity.TYPE_PHONE -> EntityType.PHONE_NUMBER
            Entity.TYPE_TRACKING_NUMBER -> EntityType.TRACKING_NUMBER
            Entity.TYPE_URL -> EntityType.URL
            else -> null
        }

    /**
     * Clean up resources.
     * Call this when the plugin is torn down.
     */
    fun close() {
        extractor?.close()
        extractor = null
        modelDownloaded = false
    }
}
