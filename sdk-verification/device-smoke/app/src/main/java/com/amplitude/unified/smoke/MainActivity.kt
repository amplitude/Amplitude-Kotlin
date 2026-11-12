package com.amplitude.unified.smoke

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.amplitude.unified.AmplitudeUnified
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    private val amplitude: AmplitudeUnified
        get() = (application as SmokeApplication).amplitude
    private val worker = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var status: LinearLayout
    private var lastVariant: String? = null
    private var lastFetch: String? = null
    private val ticker =
        object : Runnable {
            override fun run() {
                refresh()
                mainHandler.postDelayed(this, 500)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val column =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 32, 32, 32)
                addView(status)
                button("Track Event") { amplitude.track("smoke_event", mapOf("step" to "track")) }
                button("Set User") { amplitude.setUserId("smoke-user") }
                button("Fetch Flags") {
                    lastFetch = null
                    worker.execute {
                        val result = runCatching { checkNotNull(amplitude.experiment).fetch().get(10, TimeUnit.SECONDS) }
                        mainHandler.post {
                            lastFetch = if (result.isSuccess) "ok" else "${result.exceptionOrNull()}"
                            refresh()
                        }
                    }
                }
                button("Read Variant") { lastVariant = amplitude.experiment?.variant("checkout")?.value }
                button("Toggle Opt Out") {
                    amplitude.optOut = !amplitude.optOut
                    if (amplitude.optOut) amplitude.track("smoke_event_opted_out")
                }
                button("Reset") { amplitude.reset() }
                button("Flush") {
                    amplitude.flush()
                    amplitude.sessionReplay?.flush()
                }
                button("Open Second Screen") { startActivity(Intent(this@MainActivity, SecondActivity::class.java)) }
            }
        setContentView(ScrollView(this).apply { addView(column) })
        refresh()
    }

    override fun onResume() {
        super.onResume()
        mainHandler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        mainHandler.removeCallbacks(ticker)
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun LinearLayout.button(
        label: String,
        action: () -> Unit,
    ) {
        addView(
            Button(this@MainActivity).apply {
                text = label
                isAllCaps = false
                setOnClickListener {
                    action()
                    refresh()
                }
            },
        )
    }

    // One TextView per field so UI tests can assert each value exactly.
    private fun refresh() {
        val sessionReplay = amplitude.sessionReplay
        val lines =
            listOf(
                "sr=${sessionReplay != null}",
                "recording=${sessionReplay?.shouldRecord()}",
                "exp=${amplitude.experiment != null}",
                "fetch=$lastFetch",
                "variant=$lastVariant",
                "user=${amplitude.getUserId()}",
                "device=${amplitude.getDeviceId()}",
                "srDevice=${sessionReplay?.getDeviceId()}",
                "session=${amplitude.sessionId}",
                "optOut=${amplitude.optOut}",
            )
        while (status.childCount < lines.size) status.addView(TextView(this).apply { textSize = 14f })
        lines.forEachIndexed { index, line -> (status.getChildAt(index) as TextView).text = line }
    }
}

class SecondActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "Second screen" })
    }
}
