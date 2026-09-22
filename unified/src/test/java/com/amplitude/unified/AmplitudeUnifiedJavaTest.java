package com.amplitude.unified;

import android.app.Application;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertSame;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class AmplitudeUnifiedJavaTest {
    @Test
    public void constructsAndConfiguresFromJava() {
        Application application = ApplicationProvider.getApplicationContext();
        UnifiedConfigurationBuilder builder = new UnifiedConfigurationBuilder("api-key", application);
        builder.getAnalytics().setInstanceName("java-construction");
        builder.getAnalytics().setOffline(true);
        builder.getSessionReplay().setEnabled(false);
        builder.getExperiment().setEnabled(false);

        AmplitudeUnified amplitude = new AmplitudeUnified(builder);

        assertSame(amplitude, amplitude.getAnalytics());
        assertSame(amplitude, amplitude.track("java inherited track"));
    }
}
