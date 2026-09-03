package com.amplitude.android.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.amplitude.android.network.NetworkTrackingOptions.CaptureBody;
import com.amplitude.android.network.NetworkTrackingOptions.CaptureHeader;
import com.amplitude.android.network.NetworkTrackingOptions.CaptureRule;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

public class NetworkTrackingOptionsJavaOverloadsTest {
    private static List<CaptureRule> sampleRules() {
        return Collections.singletonList(new CaptureRule(Collections.singletonList("api.example.com")));
    }

    @Test
    public void oneArgumentConstructorUsesDefaultedFields() {
        List<CaptureRule> captureRules = sampleRules();
        NetworkTrackingOptions options = new NetworkTrackingOptions(captureRules);

        assertEquals(1, options.getCaptureRules().size());
        assertEquals(
                Collections.singletonList("api.example.com"),
                options.getCaptureRules().get(0).getHosts());
        assertEquals(100, options.getCaptureRules().get(0).getStatusCodeRange().size());
        assertEquals(500, options.getCaptureRules().get(0).getStatusCodeRange().get(0).intValue());
        assertEquals(599, options.getCaptureRules().get(0).getStatusCodeRange().get(99).intValue());
        assertTrue(options.getIgnoreHosts().isEmpty());
        assertTrue(options.getIgnoreAmplitudeRequests());
        assertTrue(options.getEnabled());
        assertTrue(options.getEnableRemoteConfig());
    }

    @Test
    public void twoArgumentConstructorSetsIgnoreHostsAndKeepsRemainingDefaults() {
        List<CaptureRule> captureRules = sampleRules();
        List<String> ignoreHosts = Arrays.asList("ignored.example.com");
        NetworkTrackingOptions options = new NetworkTrackingOptions(captureRules, ignoreHosts);

        assertEquals(ignoreHosts, options.getIgnoreHosts());
        assertTrue(options.getIgnoreAmplitudeRequests());
        assertTrue(options.getEnabled());
        assertTrue(options.getEnableRemoteConfig());
    }

    @Test
    public void threeArgumentConstructorSetsIgnoreAmplitudeRequests() {
        List<CaptureRule> captureRules = sampleRules();
        List<String> ignoreHosts = Arrays.asList("ignored.example.com");
        NetworkTrackingOptions options =
                new NetworkTrackingOptions(captureRules, ignoreHosts, false);

        assertEquals(ignoreHosts, options.getIgnoreHosts());
        assertFalse(options.getIgnoreAmplitudeRequests());
        assertTrue(options.getEnabled());
        assertTrue(options.getEnableRemoteConfig());
    }

    @Test
    public void fourArgumentConstructorSetsEnabled() {
        List<CaptureRule> captureRules = sampleRules();
        List<String> ignoreHosts = Arrays.asList("ignored.example.com");
        NetworkTrackingOptions options =
                new NetworkTrackingOptions(captureRules, ignoreHosts, false, false);

        assertFalse(options.getIgnoreAmplitudeRequests());
        assertFalse(options.getEnabled());
        assertTrue(options.getEnableRemoteConfig());
    }

    @Test
    public void captureHeaderOverloadsUseDefaultedFields() {
        CaptureHeader empty = new CaptureHeader();
        assertTrue(empty.getAllowlist().isEmpty());
        assertTrue(empty.getCaptureSafeHeaders());

        List<String> allowlist = Collections.singletonList("X-Custom");
        CaptureHeader withAllowlist = new CaptureHeader(allowlist);
        assertEquals(allowlist, withAllowlist.getAllowlist());
        assertTrue(withAllowlist.getCaptureSafeHeaders());
    }

    @Test
    public void captureBodyOneArgumentConstructorUsesEmptyExcludelist() {
        List<String> allowlist = Collections.singletonList("user");
        CaptureBody body = new CaptureBody(allowlist);

        assertEquals(allowlist, body.getAllowlist());
        assertTrue(body.getExcludelist().isEmpty());
    }
}
