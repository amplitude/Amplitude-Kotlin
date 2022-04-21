package com.amplitude.android.sample;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import com.amplitude.android.Amplitude;
import com.amplitude.core.events.EventOptions;
import com.amplitude.core.events.Identify;
import com.amplitude.core.events.Plan;
import com.amplitude.core.events.Revenue;

import java.util.HashMap;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Amplitude amplitude = MainApplication.getAmplitude();

        // set user properties
        Identify identify = new Identify()
                .set("user-platform", "android")
                .set("custom-properties", "sample");
        EventOptions options = new EventOptions();
        options.setPlan(new Plan("test"));
        amplitude.identify(identify, options);

        // set groups fro this user
        String groupType = "test-group-type";
        String groupName = "android-kotlin-sample";
        amplitude.setGroup(groupType, groupName);
        amplitude.setGroup("orgId", "15");
        amplitude.setGroup("sport", new String[]{"tennis", "soccer"}); // list values

        // group identify to set group properties
        Identify groupIdentifyObj = new Identify().set("key", "value");
        amplitude.groupIdentify(groupType, groupName, groupIdentifyObj);

        // log revenue call
        Revenue revenue = new Revenue();
        revenue.setProductId("com.company.productId");
        revenue.setPrice(3.99);
        revenue.setQuantity(3);
        amplitude.revenue(revenue);

        // track event with event properties
        amplitude.track("test event properties", new HashMap() {{
            put("test", "test event property value");
        }});
    }
}
