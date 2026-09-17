package com.amplitude.verification.engagement;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.amplitude.android.engagement.AmplitudeEngagementPluginFactory;
import com.amplitude.core.platform.UniversalPlugin;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class EngagementPluginJavaCompatibilityTest {
    @Test
    public void javaConsumerCanCreateUniversalPlugin() {
        Context context = ApplicationProvider.getApplicationContext();

        UniversalPlugin plugin = AmplitudeEngagementPluginFactory.make(context);

        assertEquals(AmplitudeEngagementPluginFactory.NAME, plugin.getName());
    }
}
