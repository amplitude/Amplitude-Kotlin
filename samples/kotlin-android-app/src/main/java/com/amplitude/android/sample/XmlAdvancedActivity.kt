package com.amplitude.android.sample

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.amplitude.core.events.Identify
import com.amplitude.core.events.Revenue

class XmlAdvancedActivity : AppCompatActivity() {
    private val amplitude = MainApplication.amplitude

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_advanced)

        val sendAdvancedEventsButton: Button = findViewById(R.id.send_advanced_events_button)
        sendAdvancedEventsButton.setOnClickListener {
            // set user properties
            val identify = Identify()
            identify.set("user-platform", "android")
                .set("custom-properties", "sample")
            amplitude.identify(identify)

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
        }

        val backButton: Button = findViewById(R.id.back_button)
        backButton.setOnClickListener {
            this.finish()
        }
    }
}
