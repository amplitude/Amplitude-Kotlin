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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amplitude.android.Amplitude
import kotlinx.coroutines.launch
import kotlin.system.measureTimeMillis

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

enum class DetectionMode {
    REGEX,
    MLKIT,
    HYBRID,
}

@Composable
fun PrivacyLayerDemoScreen(amplitude: Amplitude) {
    var inputText by remember { mutableStateOf("") }
    var originalText by remember { mutableStateOf("") }
    var redactedText by remember { mutableStateOf("") }
    var detectionTime by remember { mutableStateOf(0L) }
    var detectedCount by remember { mutableStateOf(0) }
    var selectedMode by remember { mutableStateOf(DetectionMode.HYBRID) }

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

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
    ) {
        // Detection Mode Selector
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor = Color(0xFF1E293B),
                ),
            elevation = CardDefaults.cardElevation(2.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Detection Engine",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ModeButton(
                        text = "Regex",
                        isSelected = selectedMode == DetectionMode.REGEX,
                        modifier = Modifier.weight(1f),
                    ) {
                        selectedMode = DetectionMode.REGEX
                    }
                    ModeButton(
                        text = "MLKit",
                        isSelected = selectedMode == DetectionMode.MLKIT,
                        modifier = Modifier.weight(1f),
                    ) {
                        selectedMode = DetectionMode.MLKIT
                    }
                    ModeButton(
                        text = "Hybrid",
                        isSelected = selectedMode == DetectionMode.HYBRID,
                        modifier = Modifier.weight(1f),
                    ) {
                        selectedMode = DetectionMode.HYBRID
                    }
                }
            }
        }

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
                Text(
                    text = "Test Input",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                    placeholder = {
                        Text(
                            "Enter text with PII (email, phone, credit card, etc.)",
                            color = Color.Gray,
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Sample data buttons
                Text(
                    text = "Quick Samples:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sampleData.forEachIndexed { index, sample ->
                        SampleButton(sample, index + 1) {
                            inputText = sample
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Scan Button
        Button(
            onClick = {
                if (inputText.isNotBlank()) {
                    scope.launch {
                        originalText = inputText

                        // Measure PII detection overhead
                        var detectedPii: List<com.amplitude.android.plugins.privacylayer.models.DetectedPii> = emptyList()
                        val scanTime =
                            measureTimeMillis {
                                // Call the plugin's scanForPii directly to measure overhead
                                detectedPii = MainApplication.privacyLayerPlugin.scanForPii(inputText)
                            }

                        detectionTime = scanTime
                        detectedCount = detectedPii.size
                        redactedText =
                            if (detectedPii.isEmpty()) {
                                "No PII detected"
                            } else {
                                "Found ${detectedPii.size} PII entities:\n" +
                                    detectedPii.joinToString("\n") { "• ${it.type}: \"${it.text}\"" }
                            }

                        // Also track the event (PII will be redacted automatically)
                        amplitude.track(
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

        Spacer(modifier = Modifier.height(24.dp))

        // Before/After Results
        ComparisonCard(
            title = "Original Input",
            content = originalText.ifEmpty { "Enter text and track an event to see results" },
            color = Color(0xFFEF4444),
        )

        Spacer(modifier = Modifier.height(16.dp))

        ComparisonCard(
            title = "Detection Results",
            content =
                if (redactedText.isEmpty()) {
                    "MLKit Privacy Layer Scanner:\n\n" +
                        "Click 'Scan for PII' to measure detection overhead.\n\n" +
                        "The scanner uses MLKit Entity Extraction to detect PII like " +
                        "emails, phones, credit cards, addresses, and more.\n\n" +
                        "Detection time shows the ML model inference overhead."
                } else {
                    redactedText
                },
            color = Color(0xFF10B981),
        )
    }
}

@Composable
fun ModeButton(
    text: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor =
                    if (isSelected) {
                        Color(0xFF6366F1)
                    } else {
                        Color(0xFF334155)
                    },
            ),
        elevation =
            ButtonDefaults.buttonElevation(
                defaultElevation = if (isSelected) 4.dp else 0.dp,
            ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = Color.White,
            )
            if (isSelected) {
                Box(
                    modifier =
                        Modifier
                            .padding(top = 4.dp)
                            .width(24.dp)
                            .height(2.dp)
                            .background(Color.White, shape = RoundedCornerShape(1.dp)),
                )
            }
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
                color = Color.Gray,
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
                )
            }
        }
    }
}
