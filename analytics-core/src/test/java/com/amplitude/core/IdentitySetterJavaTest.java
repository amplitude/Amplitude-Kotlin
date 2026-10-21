package com.amplitude.core;

import com.amplitude.core.utils.FakeAmplitude;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentitySetterJavaTest {
    @Test
    void shouldExposeIdentitySetterOverloadsToJava() {
        Amplitude amplitude = new FakeAmplitude();
        amplitude.setIdentity("user", "device", Collections.singletonMap("plan", "paid"));
        assertEquals("paid", amplitude.getIdentity().getUserProperties().get("plan"));

        amplitude.setIdentity("user", "device");
        assertTrue(amplitude.getIdentity().getUserProperties().isEmpty());

        amplitude.setIdentity(new AnalyticsIdentity() {
            @Override
            public String getUserId() {
                return "replacement-user";
            }

            @Override
            public String getDeviceId() {
                return "replacement-device";
            }
        });
        assertEquals("replacement-user", amplitude.getIdentity().getUserId());
    }
}
