package com.amplitude.android.sample

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons.AutoMirrored.Filled
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.amplitude.core.events.EventOptions
import com.amplitude.core.events.Plan

class ComposeActivity : ComponentActivity() {
    private val amplitude by lazy { MainApplication.amplitude }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ComposeView()
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ComposeView() {
        val context = LocalContext.current
        val label = remember {
            if (context is ComponentActivity) {
                val activityInfo = context.packageManager.getActivityInfo(
                    context.componentName, 0
                )
                activityInfo.loadLabel(context.packageManager).toString()
            } else {
                // Fallback for Compose Preview or non-Activity context
                "Compose - Basic Event"
            }
        }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(label) },
                    navigationIcon = {
                        IconButton(onClick = { (context as? ComponentActivity)?.finish() }) {
                            Icon(
                                imageVector = Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                )
            },
            modifier = Modifier.padding(32.dp)
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {

                    Text(
                        text = "Welcome to Kotlin Android example!",
                        modifier = Modifier.padding(16.dp)
                    )

                    Button(
                        onClick = {
                            val options = EventOptions()
                            options.plan = Plan(branch = "test")
                            amplitude.track(
                                "test event properties", mapOf("test" to "test event property value"),
                                options
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Text("Send Event")
                    }

                    Button(
                        onClick = {
                            val intent = Intent(this@ComposeActivity, ComposeAdvancedActivity::class.java)
                            startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF03A9F4)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("advanced_events_button")
                    ) {
                        Text("Advanced Events")
                    }
                }
            }
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun DefaultPreview() {
        MaterialTheme {
            ComposeView()
        }
    }
}

