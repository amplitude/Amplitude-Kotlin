@file:OptIn(RestrictedAmplitudeFeature::class)

package com.amplitude.core.remoteconfig

import com.amplitude.core.RestrictedAmplitudeFeature
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RemoteConfigConsumerRulesTest {
    @Test
    fun `consumer rules are packaged with analytics-core`() {
        assertNotNull(
            javaClass.classLoader.getResource(RULES_PATH),
            "$RULES_PATH must be on the classpath so R8 applies it to apps",
        )
    }

    @Test
    fun `consumer rules keep fields of the current remote config callback type`() {
        val rules = javaClass.classLoader.getResource(RULES_PATH)!!.readText()
        val callbackType = RemoteConfigClient.RemoteConfigCallback::class.java.name

        // If the callback type is renamed or moved, the rule silently stops matching.
        assertTrue(
            rules.contains("-keepclassmembers,allowobfuscation class com.amplitude.** {"),
            "rule must keep members of Amplitude classes without allowing shrinking",
        )
        assertTrue(
            rules.contains("$callbackType *;"),
            "rule must keep fields declared as $callbackType",
        )
    }

    private companion object {
        const val RULES_PATH = "META-INF/proguard/amplitude-remote-config.pro"
    }
}
