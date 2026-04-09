package com.amplitude.android.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons.AutoMirrored.Filled
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.amplitude.core.Constants
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.Plugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class NetworkTrackingActivity : ComponentActivity() {
    private var httpLog by mutableStateOf("")
    private var eventLog by mutableStateOf("(no events captured yet)")

    // Plugin that observes network tracking events and writes to eventLog
    private val eventObserver =
        object : Plugin {
            override val type = Plugin.Type.Destination
            override lateinit var amplitude: com.amplitude.core.Amplitude

            override fun execute(event: BaseEvent): BaseEvent? {
                if (event.eventType == Constants.EventTypes.NETWORK_TRACKING) {
                    val props = event.eventProperties ?: emptyMap()
                    val formatted =
                        buildString {
                            appendLine("--- [Amplitude] Network Request ---")
                            props.entries
                                .sortedBy { it.key }
                                .forEach { (key, value) ->
                                    val display =
                                        when (value) {
                                            is Map<*, *> -> value.entries.joinToString(", ") { "${it.key}: ${it.value}" }
                                            is String -> if (value.length > 200) value.take(200) + "..." else value
                                            else -> value.toString()
                                        }
                                    appendLine("  $key = $display")
                                }
                        }
                    eventLog = formatted + "\n" + eventLog
                }
                return event
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MainApplication.amplitude.add(eventObserver)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    NetworkTrackingScreen()
                }
            }
        }
    }

    override fun onDestroy() {
        MainApplication.amplitude.remove(eventObserver)
        super.onDestroy()
    }

    private fun makeRequest(request: Request) {
        httpLog = "Loading..."
        CoroutineScope(Dispatchers.Main).launch {
            val result =
                withContext(Dispatchers.IO) {
                    try {
                        val response = MainApplication.sharedOkHttpClient.newCall(request).execute()
                        val body = response.body?.string()?.take(500) ?: "(empty body)"
                        "${response.code} ${response.message}\n\n$body"
                    } catch (e: Exception) {
                        "Error: ${e.message}"
                    }
                }
            httpLog = result
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun NetworkTrackingScreen() {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Network Tracking Debug") },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(
                                imageVector = Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Each button exercises a different capture rule. Check the event log below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 4.dp),
                )

                RequestButton(
                    label = "GET 200",
                    description = "Rule 2 (exact URL). Captures response headers only — no body.",
                ) {
                    makeRequest(
                        Request.Builder()
                            .url("https://httpbin.org/get")
                            .build(),
                    )
                }

                RequestButton(
                    label = "POST JSON",
                    description = "Rule 3 (regex + POST). Body allowlist: user/*. Blocks **/password.",
                ) {
                    makeRequest(
                        Request.Builder()
                            .url("https://httpbin.org/post")
                            .post(
                                """{"user":{"name":"John","password":"secret","email":"john@test.com"}}"""
                                    .toRequestBody("application/json".toMediaType()),
                            )
                            .build(),
                    )
                }

                RequestButton(
                    label = "GET 404",
                    description = "Rule 5 (status regex). Full headers + body capture.",
                ) {
                    makeRequest(
                        Request.Builder()
                            .url("https://httpbin.org/status/404")
                            .build(),
                    )
                }

                RequestButton(
                    label = "GET 500",
                    description = "Rule 5 (status regex). Full headers + body capture.",
                ) {
                    makeRequest(
                        Request.Builder()
                            .url("https://httpbin.org/status/500")
                            .build(),
                    )
                }

                RequestButton(
                    label = "GET Headers",
                    description = "Rule 4 (exact URL). Safe headers + X-Custom. Authorization blocked.",
                ) {
                    makeRequest(
                        Request.Builder()
                            .url("https://httpbin.org/headers")
                            .addHeader("X-Custom", "test-value")
                            .addHeader("Authorization", "Bearer secret-token")
                            .addHeader("Content-Type", "application/json")
                            .build(),
                    )
                }

                RequestButton(
                    label = "POST Nested JSON",
                    description = "Rule 3 (regex + POST). Body filtered through user/* allowlist.",
                ) {
                    makeRequest(
                        Request.Builder()
                            .url("https://httpbin.org/post")
                            .post(
                                """{"user":{"name":"Jane","password":"hidden","role":"admin"},"metadata":{"ts":12345}}"""
                                    .toRequestBody("application/json".toMediaType()),
                            )
                            .build(),
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text("HTTP Response", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = httpLog,
                    style = MaterialTheme.typography.bodySmall,
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text("Captured Event Properties", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = eventLog,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 32.dp),
                )
            }
        }
    }

    @Composable
    private fun RequestButton(
        label: String,
        description: String = "",
        onClick: () -> Unit,
    ) {
        Column {
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50),
                    ),
            ) {
                Text(label)
            }
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
            }
        }
    }
}
