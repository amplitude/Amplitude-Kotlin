package com.amplitude.android.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.amplitude.core.events.Identify
import com.amplitude.core.events.Revenue

class ComposeAdvancedActivity : ComponentActivity() {
    private val amplitude by lazy { MainApplication.amplitude }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ComposeAdvancedView()
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ComposeAdvancedView() {
        val context = LocalContext.current
        val label =
            remember {
                if (context is ComponentActivity) {
                    val activityInfo =
                        context.packageManager.getActivityInfo(
                            context.componentName,
                            0,
                        )
                    activityInfo.loadLabel(context.packageManager).toString()
                } else {
                    // Fallback for Compose Preview or non-Activity context
                    "Compose - Advanced Events"
                }
            }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(label) },
                    navigationIcon = {
                        IconButton(onClick = { (context as? ComponentActivity)?.finish() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                        .padding(innerPadding),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Advanced Events in Compose!",
                        modifier = Modifier.padding(16.dp),
                    )

                    Button(
                        onClick = {
                            sendAdvancedEvents()
                        },
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50),
                            ),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp, vertical = 8.dp),
                    ) {
                        Text("Send Advanced Events")
                    }
                }
            }
        }
    }

    private fun sendAdvancedEvents() {
        // set user properties
        val identify = Identify()
        identify.set("user-platform", "android")
            .set("custom-properties", "sample")
        amplitude.identify(identify)

        // set groups for this user
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

    @Preview(showBackground = true)
    @Composable
    fun DefaultPreview() {
        MaterialTheme {
            ComposeAdvancedView()
        }
    }
}
