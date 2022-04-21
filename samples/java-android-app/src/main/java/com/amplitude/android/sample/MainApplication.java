package com.amplitude.android.sample;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.amplitude.android.Amplitude;
import com.amplitude.android.Configuration;
import com.amplitude.core.events.BaseEvent;
import com.amplitude.core.platform.Plugin;

import java.util.HashMap;

public class MainApplication extends Application {
    private static Amplitude amplitude;

    @Override
    public void onCreate() {
        super.onCreate();

        // init instance
        amplitude = new Amplitude(new Configuration(BuildConfig.AMPLITUDE_API_KEY, getApplicationContext()));

        // add sample plugin
        amplitude.add(new SamplePlugin());

        // identify a sample user
        amplitude.setUserId("android-java-sample-user");
    }

    public static Amplitude getAmplitude() {
        return amplitude;
    }
}

class SamplePlugin implements Plugin {

    private com.amplitude.core.Amplitude amplitude;

    @NonNull
    @Override
    public Type getType() {
        return Type.Enrichment;
    }

    @NonNull
    @Override
    public com.amplitude.core.Amplitude getAmplitude() {
        return amplitude;
    }

    @Override
    public void setAmplitude(@NonNull com.amplitude.core.Amplitude amplitude) {
        this.amplitude = amplitude;
    }

    @Override
    public void setup(@NonNull com.amplitude.core.Amplitude amplitude) {
        setAmplitude(amplitude);
    }

    @Nullable
    @Override
    public BaseEvent execute(@NonNull BaseEvent event) {
        if (event.getEventProperties() == null) {
            event.setEventProperties(new HashMap<>());
        }
        event.getEventProperties().put("custom android event property", "test");
        return event;
    }
}
