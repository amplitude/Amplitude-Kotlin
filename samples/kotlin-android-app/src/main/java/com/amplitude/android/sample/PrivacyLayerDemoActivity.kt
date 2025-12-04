package com.amplitude.android.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import com.amplitude.android.Amplitude
import com.amplitude.android.plugins.privacylayer.models.DetectedPii
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.launch

class PrivacyLayerDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0A0E27),
                ) {
                    PrivacyLayerDemoScreen(MainApplication.amplitude)
                }
            }
        }
    }
}

@Composable
fun PrivacyLayerDemoScreen(amplitude: Amplitude? = null) {
    var inputText by remember { mutableStateOf("") }
    var originalText by remember { mutableStateOf("") }
    var redactedText by remember { mutableStateOf("") }
    var detectionTime by remember { mutableStateOf(0L) }
    var detectedCount by remember { mutableStateOf(0) }
    var useCrazyLong by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // Sample PII data
    val sampleData =
        listOf(
            "Email: john.doe@example.com, Phone: (555) 123-4567",
            "Credit Card: 4111 1111 1111 1111, CVV: 123",
            "Address: 350 Third Street, Cambridge MA 02142",
            "Contact me at jane@company.com or call 555-9876",
            "IBAN: CH52 0483 0000 0000 0000 9",
        )

    // Extra long sample data (1000+ chars each)
    val crazyLongSampleData =
        listOf(
            // Sample 1: Email and Phone heavy
            buildString {
                append("Our team members can be reached at: ")
                repeat(15) { i ->
                    append("john.doe${i}@example.com, ")
                    append("jane.smith${i}@company.org, ")
                    append("contact${i}@business.net, ")
                }
                append("\nPhone directory: ")
                repeat(20) { i ->
                    append("(555) ${100 + i}-${4567 + i}, ")
                }
                append("\nFor urgent matters, email support@critical-systems.com or call our hotline at (555) 999-9999. ")
                append("Additional contacts: alice@wonderland.io, bob@builder.com, charlie@chocolate.factory, ")
                append("david@goliath.net, eve@genesis.org. Mobile numbers: (555) 100-2000, (555) 200-3000, (555) 300-4000.")
            },
            // Sample 2: Credit Card and CVV heavy
            buildString {
                append("Payment information for testing: ")
                repeat(8) { i ->
                    append("Card ${i + 1}: 4111 1111 1111 ${1111 + i}, CVV: ${100 + i * 11}, ")
                    append("Card ${i + 9}: 5555 5555 5555 ${4444 + i}, CVV: ${200 + i * 7}, ")
                }
                append("\nVisa cards: 4532 1488 0343 6467 (CVV: 321), 4556 7375 8689 9855 (CVV: 456), ")
                append("4539 3719 0356 1982 (CVV: 789). ")
                append("Mastercard: 5425 2334 3010 9903 (CVV: 234), 5167 9048 3218 7654 (CVV: 567). ")
                repeat(10) { i ->
                    append("Test card ${i}: 4111 ${1000 + i * 111} ${2000 + i * 222} ${3000 + i}, CVV: ${100 + i * 3}. ")
                }
            },
            // Sample 3: Address heavy
            buildString {
                append("Shipping addresses for our locations: ")
                val streets = listOf("Main St", "Oak Ave", "Pine Rd", "Elm Blvd", "Maple Dr", "Cedar Ln", "Birch Way", "Willow Ct")
                val cities = listOf("Boston", "Cambridge", "Somerville", "Brookline", "Newton", "Quincy", "Waltham", "Medford")
                repeat(15) { i ->
                    val num = 100 + i * 50
                    append("${num} ${streets[i % streets.size]}, ${cities[i % cities.size]} MA ${
                        (2140 + i).toString().padStart(
                            5,
                            '0'
                        )
                    }; ")
                }
                append("\nWarehouse: 350 Third Street, Cambridge MA 02142. ")
                append("Distribution: 1600 Pennsylvania Avenue, Washington DC 20500. ")
                append("Headquarters: 1 Infinite Loop, Cupertino CA 95014. ")
                repeat(8) { i ->
                    append("Branch ${i + 1}: ${200 + i * 25} Commerce Street, Suite ${100 + i * 50}, Springfield IL ${
                        (62701 + i).toString().padStart(
                            5,
                            '0'
                        )
                    }. ")
                }
            },
            // Sample 4: Mixed contact info
            buildString {
                append("Contact directory - ")
                repeat(12) { i ->
                    append("Employee ${i + 1}: email${i}@corp.com, mobile: (555) ${123 + i * 11}-${4567 + i * 13}, ")
                    if (i % 3 == 0) append("card: 4111 1111 1111 ${1111 + i * 123}, ")
                }
                append("\nCustomer service: help@support.com, (555) 888-8888. ")
                append("Sales team: sales@company.com, (555) 777-7777. ")
                append("Technical support: tech@help.com, (555) 666-6666. ")
                repeat(10) { i ->
                    append("Department ${i + 1}: dept${i}@organization.org, phone: (555) ${200 + i * 22}-${3000 + i * 33}. ")
                }
                append("For billing inquiries: billing@accounts.com or call (555) 444-4444. ")
                append("Additional emails: info@general.com, contact@reach.com, hello@greetings.com, welcome@onboard.com.")
            },
            // Sample 5: IBAN and international data
            buildString {
                append("International banking information: ")
                repeat(12) { i ->
                    append("IBAN ${i + 1}: CH${(52 + i).toString().padStart(2, '0')} 0483 0000 0000 ${
                        (1000 + i * 111).toString().padStart(
                            4,
                            '0'
                        )
                    } ${i.toString().padStart(1, '0')}, ")
                }
                append("\nEuropean accounts: DE89 3704 0044 0532 0130 00, FR14 2004 1010 0505 0001 3M02 606, ")
                append("GB29 NWBK 6016 1331 9268 19, IT60 X054 2811 1010 0000 0123 456. ")
                repeat(10) { i ->
                    append("Account ${i + 1}: CH${(30 + i).toString().padStart(2, '0')} ${(1000 + i * 100).toString().padStart(4, '0')} ${
                        (2000 + i * 200).toString().padStart(
                            4,
                            '0'
                        )
                    } ${(3000 + i * 300).toString().padStart(4, '0')} ${(4000 + i * 400).toString().padStart(4, '0')} ${i}, ")
                }
                append("Contact: banking@international.com, support: (555) 321-9876.")
            },
        )

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Input Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor = Color(0xFF1E293B),
                ),
            elevation = CardDefaults.cardElevation(4.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Sample data buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Quick Samples:",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = useCrazyLong,
                            onCheckedChange = { useCrazyLong = it },
                            colors =
                                CheckboxDefaults.colors(
                                    checkedColor = Color(0xFF6366F1),
                                    uncheckedColor = Color.Gray,
                                    checkmarkColor = Color.White,
                                ),
                        )
                        Text(
                            text = "make it crazy long",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            fontSize = 11.sp,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sampleData.forEachIndexed { index, sample ->
                        SampleButton(sample, index + 1) {
                            val selectedSample = if (useCrazyLong) crazyLongSampleData[index] else sample
                            inputText = selectedSample
                            originalText = selectedSample
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        ComparisonCard(
            title = "Original Input",
            content = originalText,
            color = Color(0xFFEF4444)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Scan Button
        Button(
            onClick = {
                if (inputText.isNotBlank()) {
                    scope.launch {
                        originalText = inputText

                        // Measure PII detection overhead
                        var detectedPii: List<DetectedPii> = emptyList()
                        val scanTime =
                            measureTimeMillis {
                                // Call the plugin's scanForPii directly to measure overhead
                                detectedPii =
                                    MainApplication.privacyLayerPlugin.scanForPii(inputText)
                            }

                        detectionTime = scanTime
                        detectedCount = detectedPii.size
                        redactedText =
                            if (detectedPii.isEmpty()) {
                                "No PII detected"
                            } else {
                                "Found ${detectedPii.size} PII entities:\n" +
                                    detectedPii.joinToString(
                                        "\n"
                                    ) { "• ${it.type}: \"${it.text}\"" }
                            }

                        // Also track the event (PII will be redacted automatically)
                        amplitude?.track(
                            "privacy_layer_demo",
                            mapOf(
                                "user_message" to inputText,
                                "scan_time_ms" to scanTime,
                                "detected_count" to detectedPii.size,
                            ),
                        )
                    }
                }
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6366F1),
                ),
            enabled = inputText.isNotBlank(),
        ) {
            Text("🔍 Scan for PII", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        ComparisonCard(
            title = "Detection Results",
            content = redactedText,
            color = Color(0xFF10B981),
            showCharCount = false
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Stats Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                title = "Detection Time",
                value = "${detectionTime}ms",
                color = Color(0xFF10B981),
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = "PII Found",
                value = "$detectedCount",
                color = Color(0xFFF59E0B),
            )
        }
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    color: Color,
) {
    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = Color(0xFF1E293B),
            ),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = color,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
fun SampleButton(
    text: String,
    number: Int,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = Color(0xFF334155),
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(24.dp)
                        .background(Color(0xFF6366F1), shape = RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$number",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                fontSize = 12.sp,
                color = Color.White,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun ComparisonCard(
    title: String,
    content: String,
    color: Color,
    showCharCount: Boolean = true,
    maxLines: Int = 10
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = Color(0xFF1E293B),
            ),
        elevation = CardDefaults.cardElevation(4.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(12.dp)
                            .background(color, shape = RoundedCornerShape(50)),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(16.dp),
            ) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 20.sp,
                    maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (showCharCount && content.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${content.length} characters",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0E27)
@Composable
fun PrivacyLayerDemoPreview() {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0A0E27),
        ) {
            PrivacyLayerDemoScreen()
        }
    }
}
