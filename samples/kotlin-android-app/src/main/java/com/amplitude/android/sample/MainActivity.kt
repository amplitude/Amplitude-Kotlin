package com.amplitude.android.sample

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.amplitude.core.events.EventOptions
import com.amplitude.core.events.Plan


class MainActivity : AppCompatActivity() {
    private val amplitude = MainApplication.amplitude

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val options = EventOptions()
        options.plan = Plan(branch = "test")

        val sendEventButton: Button = findViewById(R.id.send_event_button)
        // track event with event properties
        sendEventButton.setOnClickListener {
            amplitude.track("test event properties", mapOf("test" to "test event property value"), options)
        }

        // navigate to the advanced events view
        val naviToAdvancedActivityButton: Button = findViewById(R.id.navi_to_advanced_activity_button)
        naviToAdvancedActivityButton.setOnClickListener {
            val intent = Intent(this, AdvancedActivity::class.java)
            startActivity(intent)
        }
    }
}
