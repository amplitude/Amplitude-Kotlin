package com.amplitude.verification.experiment;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.amplitude.core.platform.UniversalPlugin;
import com.amplitude.experiment.AmplitudeExperimentPlugin;
import com.amplitude.experiment.ExperimentConfig;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ExperimentPluginJavaCompatibilityTest {
    @Test
    public void JavaCanConstructDefaultAndExplicitDeploymentPlugins() {
        Context context = ApplicationProvider.getApplicationContext();
        ExperimentConfig config = ExperimentConfig.builder()
            .fetchOnStart(false)
            .pollOnStart(false)
            .build();

        AmplitudeExperimentPlugin defaultPlugin = new AmplitudeExperimentPlugin(context);
        AmplitudeExperimentPlugin configuredPlugin = new AmplitudeExperimentPlugin(context, config);
        AmplitudeExperimentPlugin deploymentPlugin =
            new AmplitudeExperimentPlugin(context, config, "deployment-key");
        UniversalPlugin universalPlugin = deploymentPlugin;

        assertNotNull(defaultPlugin);
        assertNotNull(configuredPlugin);
        assertSame(deploymentPlugin, universalPlugin);
        assertEquals(
            AmplitudeExperimentPlugin.PLUGIN_NAME + "_deployment-key",
            deploymentPlugin.getName()
        );
    }
}
