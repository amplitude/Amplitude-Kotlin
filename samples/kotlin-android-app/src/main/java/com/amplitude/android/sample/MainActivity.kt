package com.amplitude.android.sample

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.amplitude.core.events.EventOptions
import com.amplitude.core.events.Identify
import com.amplitude.core.events.Plan
import com.amplitude.core.events.Revenue
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private val amplitude = MainApplication.amplitude

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // set user properties
        val identify = Identify()
        identify.set("user-platform", "android")
            .set("custom-properties", "sample")
        val options = EventOptions()
        options.plan = Plan(branch = "test")
        amplitude.identify(identify, options)

        // set groups fro this user
        val groupType = "test-group-type"
        val groupName = "android-kotlin-sample"
        amplitude.setGroup(groupType, groupName)
        amplitude.setGroup("orgId", "15")
        amplitude.setGroup("sport", arrayOf("tennis", "soccer")) // list values

        // group identify to set group properties
        val groupIdentifyObj = Identify().set("key", "value")
        amplitude.groupIdentify(groupType, groupName, groupIdentifyObj)

        // log revenue call
        val revenue = Revenue()
        revenue.productId = "com.company.productId"
        revenue.price = 3.99
        revenue.quantity = 3
        amplitude.revenue(revenue)

        // track event with event properties
        amplitude.track("test event properties", JSONObject().put("test", "test event property value"))
    }
}
