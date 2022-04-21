package com.amplitude.core.events

import com.amplitude.common.jvm.ConsoleLogger
import org.json.JSONException
import org.json.JSONObject

data class Plan @JvmOverloads constructor(
    val branch: String? = null,
    val source: String? = null,
    val version: String? = null,
    val versionId: String? = null,
) {

    /**
     * Get JSONObject of current tacking plan
     * @return JSONObject including plan information
     */
    internal fun toJSONObject(): JSONObject? {
        val plan = JSONObject()
        try {
            if (!branch.isNullOrEmpty()) {
                plan.put(AMP_PLAN_BRANCH, branch)
            }
            if (!source.isNullOrEmpty()) {
                plan.put(AMP_PLAN_SOURCE, source)
            }
            if (!version.isNullOrEmpty()) {
                plan.put(AMP_PLAN_VERSION, version)
            }
            if (!versionId.isNullOrEmpty()) {
                plan.put(AMP_PLAN_VERSION_ID, versionId)
            }
        } catch (e: JSONException) {
            ConsoleLogger.logger.error("JSON Serialization of tacking plan object failed")
        }
        return plan
    }

    companion object {
        const val AMP_PLAN_BRANCH = "branch"
        const val AMP_PLAN_SOURCE = "source"
        const val AMP_PLAN_VERSION = "version"
        const val AMP_PLAN_VERSION_ID = "versionId"

        internal fun fromJSONObject(jsonObject: JSONObject): Plan {
            val branch = jsonObject.optString(AMP_PLAN_BRANCH, null)
            val source = jsonObject.optString(AMP_PLAN_SOURCE, null)
            val version = jsonObject.optString(AMP_PLAN_VERSION, null)
            val versionId = jsonObject.optString(AMP_PLAN_VERSION_ID, null)
            return Plan(branch, source, version, versionId)
        }
    }
}
