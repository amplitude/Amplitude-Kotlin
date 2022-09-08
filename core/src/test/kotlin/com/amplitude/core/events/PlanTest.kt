package com.amplitude.core.events

import org.json.JSONObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlanTest {

    @Test
    fun `test plan to json object`() {
        val branch = "main"
        val version = "1.0.0"
        val source = "mobile"
        val versionId = "9ec23ba0-275f-468f-80d1-66b88bff9529"
        val plan = Plan(branch, source, version, versionId)
        val result = plan.toJSONObject()
        assertEquals(branch, result.getString(Plan.AMP_PLAN_BRANCH))
        assertEquals(source, result.getString(Plan.AMP_PLAN_SOURCE))
        assertEquals(version, result.getString(Plan.AMP_PLAN_VERSION))
        assertEquals(versionId, result.getString(Plan.AMP_PLAN_VERSION_ID))
    }

    @Test
    fun `test Plan clone new object`() {
        val branch = "main"
        val version = "1.0.0"
        val source = "mobile"
        val versionId = "9ec23ba0-275f-468f-80d1-66b88bff9529"
        val plan = Plan(branch, source, version, versionId)
        val clone = plan.clone()
        assertEquals(branch, plan.branch)
        assertEquals(source, plan.source)
        assertEquals(version, plan.version)
        assertEquals(versionId, plan.versionId)
        Assertions.assertFalse(plan === clone)
    }

    @Test
    fun `test plan from json object`() {
        val jsonObject = JSONObject()
        val branch = "main"
        val version = "1.0.0"
        val source = "mobile"
        val versionId = "9ec23ba0-275f-468f-80d1-66b88bff9529"
        jsonObject.put(Plan.AMP_PLAN_BRANCH, branch)
            .put(Plan.AMP_PLAN_SOURCE, source)
            .put(Plan.AMP_PLAN_VERSION, version)
            .put(Plan.AMP_PLAN_VERSION_ID, versionId)
        val plan = Plan.fromJSONObject(jsonObject)
        assertEquals(branch, plan.branch)
        assertEquals(source, plan.source)
        assertEquals(version, plan.version)
        assertEquals(versionId, plan.versionId)
    }
}
