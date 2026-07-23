package com.amplitude.core.events

import com.amplitude.common.jvm.ConsoleLogger
import org.json.JSONException
import org.json.JSONObject

public open class Plan
    @JvmOverloads
    constructor(
        public val branch: String? = null,
        public val source: String? = null,
        public val version: String? = null,
        public val versionId: String? = null,
    ) {
        /**
         * Get JSONObject of current tracking plan
         * @return JSONObject including plan information
         */
        internal fun toJSONObject(): JSONObject {
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

        /**
         * Get a cloned Plan object, to isolate the potentially value changes
         */
        public fun clone(): Plan {
            return Plan(branch, source, version, versionId)
        }

        public companion object {
            public const val AMP_PLAN_BRANCH: String = "branch"
            public const val AMP_PLAN_SOURCE: String = "source"
            public const val AMP_PLAN_VERSION: String = "version"
            public const val AMP_PLAN_VERSION_ID: String = "versionId"

            public fun fromJSONObject(jsonObject: JSONObject): Plan {
                val branch = jsonObject.optString(AMP_PLAN_BRANCH, null)
                val source = jsonObject.optString(AMP_PLAN_SOURCE, null)
                val version = jsonObject.optString(AMP_PLAN_VERSION, null)
                val versionId = jsonObject.optString(AMP_PLAN_VERSION_ID, null)
                return Plan(branch, source, version, versionId)
            }
        }
    }
