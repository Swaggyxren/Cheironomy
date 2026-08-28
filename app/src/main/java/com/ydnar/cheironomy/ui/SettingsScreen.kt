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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.ydnar.cheironomy.data.GestureAction
import com.ydnar.cheironomy.data.SettingsRepository
import com.ydnar.cheironomy.ui.theme.CardBackground
import com.ydnar.cheironomy.ui.theme.PrimaryTeal

@Composable
fun SettingsScreen(
    settingsRepo: SettingsRepository
) {
    val context = LocalContext.current
    val settings by settingsRepo.settings.collectAsStateWithLifecycle()

    val canDrawOverlay = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Gesture Sensitivity & Timings",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )

        // 1. Detection Confidence Threshold Slider
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
                        text = "Detection Confidence",
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
                    text = "Lower values improve detection in harsh outdoor sunlight or dusk.",
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

        // 2. Cooldown Debounce Window
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
                        text = "Action Cooldown (Debounce)",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White
                    )
                    Text(
                        text = "${String.format("%.1f", settings.cooldownMs / 1000f)} s",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = PrimaryTeal
                    )
                }
                Text(
                    text = "Prevents accidental duplicate triggers within the cooldown window.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
                Slider(
                    value = settings.cooldownMs.toFloat(),
                    onValueChange = { settingsRepo.updateCooldownMs(it.toLong()) },
                    valueRange = 500f..3000f,
                    steps = 24,
                    colors = SliderDefaults.colors(
                        thumbColor = PrimaryTeal,
                        activeTrackColor = PrimaryTeal
                    )
                )
            }
        }

        // 3. Static Pose Hold Duration
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
                        text = "Open Palm Hold Duration",
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
                    text = "How long an open palm must be held steady before triggering.",
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

        // 4. Swipe Sensitivity Threshold
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
                        text = "Swipe Distance Sensitivity",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White
                    )
                    Text(
                        text = "${(settings.swipeSensitivity * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = PrimaryTeal
                    )
                }
                Text(
                    text = "Lower values require smaller hand movement across the camera frame.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
                Slider(
                    value = settings.swipeSensitivity,
                    onValueChange = { settingsRepo.updateSwipeSensitivity(it) },
                    valueRange = 0.12f..0.40f,
                    steps = 13,
                    colors = SliderDefaults.colors(
                        thumbColor = PrimaryTeal,
                        activeTrackColor = PrimaryTeal
                    )
                )
            }
        }

        // 5. Floating Overlay Toggle
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

        Spacer(modifier = Modifier.height(8.dp))

        // 6. Gesture-to-Action Remapping
        Text(
            text = "Gesture Actions Remapping",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )

        ActionMappingDropdown(
            label = "Open Palm Held",
            selectedAction = settings.openPalmAction,
            onActionSelected = { settingsRepo.updateAction(SettingsRepository.KEY_ACTION_OPEN_PALM, it) }
        )

        ActionMappingDropdown(
            label = "Swipe Left",
            selectedAction = settings.swipeLeftAction,
            onActionSelected = { settingsRepo.updateAction(SettingsRepository.KEY_ACTION_SWIPE_LEFT, it) }
        )

        ActionMappingDropdown(
            label = "Swipe Right",
            selectedAction = settings.swipeRightAction,
            onActionSelected = { settingsRepo.updateAction(SettingsRepository.KEY_ACTION_SWIPE_RIGHT, it) }
        )

        ActionMappingDropdown(
            label = "Swipe Up",
            selectedAction = settings.swipeUpAction,
            onActionSelected = { settingsRepo.updateAction(SettingsRepository.KEY_ACTION_SWIPE_UP, it) }
        )

        ActionMappingDropdown(
            label = "Swipe Down",
            selectedAction = settings.swipeDownAction,
            onActionSelected = { settingsRepo.updateAction(SettingsRepository.KEY_ACTION_SWIPE_DOWN, it) }
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionMappingDropdown(
    label: String,
    selectedAction: GestureAction,
    onActionSelected: (GestureAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = PrimaryTeal
            )
            Spacer(modifier = Modifier.height(6.dp))

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedAction.displayName,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = PrimaryTeal,
                        unfocusedBorderColor = Color.DarkGray
                    ),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    GestureAction.values().forEach { action ->
                        DropdownMenuItem(
                            text = { Text(action.displayName) },
                            onClick = {
                                onActionSelected(action)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}
