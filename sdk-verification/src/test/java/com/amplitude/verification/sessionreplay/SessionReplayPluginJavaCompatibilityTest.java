package com.amplitude.verification.sessionreplay;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.amplitude.android.plugins.SessionReplayPlugin;
import com.amplitude.core.platform.UniversalPlugin;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SessionReplayPluginJavaCompatibilityTest {
    @Test
    public void javaConsumerCanUseConvenienceConstructors() {
        Context context = ApplicationProvider.getApplicationContext();

        SessionReplayPlugin defaultPlugin = new SessionReplayPlugin(context);
        SessionReplayPlugin sampledPlugin = new SessionReplayPlugin(context, 1.0);
        UniversalPlugin universalPlugin = sampledPlugin;

        assertEquals(SessionReplayPlugin.PLUGIN_NAME, defaultPlugin.getName());
        assertSame(sampledPlugin, universalPlugin);
    }
}
