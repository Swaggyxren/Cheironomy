package com.ydnar.cheironomy.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ydnar.cheironomy.data.SettingsRepository
import com.ydnar.cheironomy.data.template.GestureTemplate
import com.ydnar.cheironomy.data.template.GestureTemplate.MotionGestureTemplate
import com.ydnar.cheironomy.ui.components.TrajectoryThumbnail
import com.ydnar.cheironomy.ui.recording.GestureRecordingDialog
import com.ydnar.cheironomy.ui.theme.CardBackground
import com.ydnar.cheironomy.ui.theme.PrimaryTeal
import com.ydnar.cheironomy.ui.theme.StatusGreen
import com.ydnar.cheironomy.ui.theme.StatusRed

@Composable
fun SettingsScreen(
    settingsRepo: SettingsRepository
) {
    val context = LocalContext.current
    val settings by settingsRepo.settings.collectAsStateWithLifecycle()
    var showRecordingDialog by remember { mutableStateOf(false) }

    if (showRecordingDialog) {
        GestureRecordingDialog(
            onDismissRequest = { showRecordingDialog = false },
            onSaveTemplate = { template ->
                settingsRepo.addCustomTemplate(template)
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section 1: User-Recorded Custom Gestures
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "My Recorded Gestures",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )

            Button(
                onClick = { showRecordingDialog = true },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Record Gesture", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        if (settings.customTemplates.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Gesture,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No gestures recorded yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Cheironomy uses pure user templates. Record your first motion or static pose above!",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                settings.customTemplates.forEach { template ->
                    CustomGestureItemCard(
                        template = template,
                        onDelete = { settingsRepo.removeCustomTemplate(template.id) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Section 2: Nearest-Neighbor Recognition & Thresholds
        Text(
            text = "Recognition Tuning (Nearest-Neighbor)",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )

        // 1. Detection Confidence Threshold
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Hand Detection Confidence",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White
                    )
                    Text(
                        text = "${(settings.confidenceThreshold * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = PrimaryTeal
                    )
                }
                Text(
                    text = "Minimum MediaPipe confidence required before evaluating gestures.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
                Slider(
                    value = settings.confidenceThreshold,
                    onValueChange = { settingsRepo.updateConfidenceThreshold(it) },
                    valueRange = 0.25f..0.85f,
                    steps = 11,
                    colors = SliderDefaults.colors(
                        thumbColor = PrimaryTeal,
                        activeTrackColor = PrimaryTeal
                    )
                )
            }
        }

        // 2. Motion DTW Reject Ceiling
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Motion Reject Ceiling (DTW)",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White
                    )
                    Text(
                        text = String.format("%.2f", settings.motionRejectCeiling),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = PrimaryTeal
                    )
                }
                Text(
                    text = "Maximum DTW path distance to accept a match; larger values allow looser motion shapes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
                Slider(
                    value = settings.motionRejectCeiling,
                    onValueChange = { settingsRepo.updateMotionRejectCeiling(it) },
                    valueRange = 0.10f..0.40f,
                    steps = 14,
                    colors = SliderDefaults.colors(
                        thumbColor = PrimaryTeal,
                        activeTrackColor = PrimaryTeal
                    )
                )
            }
        }

        // 3. Motion Winner Margin
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Motion Runner-Up Margin",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White
                    )
                    Text(
                        text = "${(settings.motionMarginThreshold * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = PrimaryTeal
                    )
                }
                Text(
                    text = "Winner must be at least this much closer than the runner-up template to resolve ambiguity.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
                Slider(
                    value = settings.motionMarginThreshold,
                    onValueChange = { settingsRepo.updateMotionMarginThreshold(it) },
                    valueRange = 0.05f..0.35f,
                    steps = 6,
                    colors = SliderDefaults.colors(
                        thumbColor = PrimaryTeal,
                        activeTrackColor = PrimaryTeal
                    )
                )
            }
        }

        // 4. Static Pose Reject Ceiling
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Static Pose Reject Ceiling",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White
                    )
                    Text(
                        text = String.format("%.2f", settings.staticRejectCeiling),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = PrimaryTeal
                    )
                }
                Text(
                    text = "Maximum mean Euclidean landmark distance for held pose recognition.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
                Slider(
                    value = settings.staticRejectCeiling,
                    onValueChange = { settingsRepo.updateStaticRejectCeiling(it) },
                    valueRange = 0.06f..0.24f,
                    steps = 17,
                    colors = SliderDefaults.colors(
                        thumbColor = PrimaryTeal,
                        activeTrackColor = PrimaryTeal
                    )
                )
            }
        }

        // 5. Static Pose Margin
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Static Pose Runner-Up Margin",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White
                    )
                    Text(
                        text = "${(settings.staticMarginThreshold * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = PrimaryTeal
                    )
                }
                Text(
                    text = "Winner must beat second closest static pose by this percentage margin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
                Slider(
                    value = settings.staticMarginThreshold,
                    onValueChange = { settingsRepo.updateStaticMarginThreshold(it) },
                    valueRange = 0.05f..0.35f,
                    steps = 6,
                    colors = SliderDefaults.colors(
                        thumbColor = PrimaryTeal,
                        activeTrackColor = PrimaryTeal
                    )
                )
            }
        }

        // 6. Static Pose Hold Duration
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Static Pose Hold Duration",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White
                    )
                    Text(
                        text = "${settings.holdDurationMs} ms",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = PrimaryTeal
                    )
                }
                Text(
                    text = "How long a pose must be held steady before triggering its action.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
                Slider(
                    value = settings.holdDurationMs.toFloat(),
                    onValueChange = { settingsRepo.updateHoldDurationMs(it.toLong()) },
                    valueRange = 300f..1500f,
                    steps = 11,
                    colors = SliderDefaults.colors(
                        thumbColor = PrimaryTeal,
                        activeTrackColor = PrimaryTeal
                    )
                )
            }
        }

        // 7. Floating Overlay Toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Floating Status Overlay",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White
                        )
                        Text(
                            text = "Displays draggable live status pill on top of other apps.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray
                        )
                    }
                    Switch(
                        checked = settings.isOverlayEnabled,
                        onCheckedChange = { isChecked ->
                            if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                                context.startActivity(intent)
                            } else {
                                settingsRepo.updateOverlayEnabled(isChecked)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = PrimaryTeal
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun CustomGestureItemCard(
    template: GestureTemplate,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                TrajectoryThumbnail(
                    template = template,
                    size = 52.dp
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = template.name,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = PrimaryTeal.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = if (template is MotionGestureTemplate) "Motion" else "Static",
                                color = PrimaryTeal,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "→ ${template.action.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusGreen
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Gesture", tint = StatusRed.copy(alpha = 0.8f))
            }
        }
    }
}
