package com.amplitude.verification.unified;

import android.app.Application;

import androidx.test.core.app.ApplicationProvider;

import com.amplitude.core.utilities.InMemoryStorageProvider;
import com.amplitude.experiment.ExperimentConfig;
import com.amplitude.id.IMIdentityStorageProvider;
import com.amplitude.unified.AmplitudeUnified;
import com.amplitude.unified.UnifiedConfigurationBuilder;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class UnifiedWrapperJavaCompatibilityTest {
    @Test
    public void javaConsumerCanConstructUnifiedConfigurationBuilderAndAmplitudeUnified() {
        Application application = ApplicationProvider.getApplicationContext();
        UnifiedConfigurationBuilder builder = new UnifiedConfigurationBuilder("test-api-key", application);
        builder.getAnalytics().setInstanceName("sdk-verification-unified-java-" + System.nanoTime());
        builder.getAnalytics().setOffline(true);
        builder.getAnalytics().setAutocapture(Collections.emptySet());
        builder.getAnalytics().setStorageProvider(new InMemoryStorageProvider());
        builder.getAnalytics().setIdentifyInterceptStorageProvider(new InMemoryStorageProvider());
        builder.getAnalytics().setIdentityStorageProvider(new IMIdentityStorageProvider());
        builder.getSessionReplay().setAutoStart(false);
        builder.getSessionReplay().setEnableRemoteConfig(false);
        builder.getExperiment().setConfig(
            ExperimentConfig.builder()
                .fetchOnStart(false)
                .pollOnStart(false)
                .build()
        );
        builder.getEngagement().setEnabled(false);

        AmplitudeUnified amplitude = new AmplitudeUnified(builder);

        assertSame(amplitude, amplitude.getAnalytics());
        assertNotNull(amplitude.getSessionReplay());
        assertNotNull(amplitude.getExperiment());
        assertNull(amplitude.getEngagement());
        assertSame(amplitude, amplitude.track("java unified event"));
    }
}
