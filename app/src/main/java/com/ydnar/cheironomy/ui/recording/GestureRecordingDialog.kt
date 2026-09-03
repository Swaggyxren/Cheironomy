package com.ydnar.cheironomy.ui.recording

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.ydnar.cheironomy.camera.CameraPreview
import com.ydnar.cheironomy.data.GestureAction
import com.ydnar.cheironomy.data.template.GestureTemplate
import com.ydnar.cheironomy.data.template.GestureTemplate.MotionGestureTemplate
import com.ydnar.cheironomy.data.template.GestureTemplate.StaticGestureTemplate
import com.ydnar.cheironomy.data.template.Point2D
import com.ydnar.cheironomy.data.template.TrajectoryStats
import com.ydnar.cheironomy.gesture.classifier.PalmCentroidHelper
import com.ydnar.cheironomy.gesture.classifier.StaticTemplateMatcher
import com.ydnar.cheironomy.gesture.classifier.TrajectoryNormalizer
import com.ydnar.cheironomy.gesture.model.HandLandmarkResultBundle
import com.ydnar.cheironomy.ui.components.TrajectoryThumbnail
import com.ydnar.cheironomy.ui.overlay.HandLandmarkOverlay
import com.ydnar.cheironomy.ui.theme.BackgroundDark
import com.ydnar.cheironomy.ui.theme.CardBackground
import com.ydnar.cheironomy.ui.theme.PrimaryTeal
import com.ydnar.cheironomy.ui.theme.StatusGreen
import com.ydnar.cheironomy.ui.theme.StatusRed
import kotlinx.coroutines.delay
import java.util.UUID
import kotlin.math.hypot

private enum class RecordingState {
    READY,
    COUNTDOWN,
    RECORDING,
    REVIEW
}

private enum class RecordingMode {
    MOTION,
    STATIC_POSE
}


/**
 * Trims leading and trailing stillness from a recorded motion trajectory so the
 * template represents the deliberate stroke, not the hand idling before/after it.
 */
private fun trimStillness(points: List<Point2D>): List<Point2D> {
    if (points.size < 3) return points

    val steps = mutableListOf<Float>()
    for (i in 1 until points.size) {
        steps.add(hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y))
    }
    if (steps.isEmpty()) return points
    val sorted = steps.sorted()
    val median = sorted[sorted.size / 2]
    val threshold = (median * 0.35f).coerceAtLeast(0.004f)

    var start = 0
    while (start < points.size - 1 && hypot(points[start + 1].x - points[start].x, points[start + 1].y - points[start].y) < threshold) {
        start++
    }

    var end = points.size - 1
    while (end > start + 1 && hypot(points[end].x - points[end - 1].x, points[end].y - points[end - 1].y) < threshold) {
        end--
    }

    val trimmed = points.subList(start, end + 1)
    return if (trimmed.size >= 3) trimmed else points
}

/**
 * Interactive Dialog for recording custom motion trajectories or static poses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestureRecordingDialog(
    onDismissRequest: () -> Unit,
    onSaveTemplate: (GestureTemplate) -> Unit
) {
    var recordingMode by remember { mutableStateOf(RecordingMode.MOTION) }
    var recordingState by remember { mutableStateOf(RecordingState.READY) }
    var countdownSeconds by remember { mutableIntStateOf(3) }

    var gestureName by remember { mutableStateOf("") }
    var selectedAction by remember { mutableStateOf(GestureAction.MEDIA_PLAY_PAUSE) }
    var actionMenuExpanded by remember { mutableStateOf(false) }

    // Live frame state
    var currentBundle by remember { mutableStateOf<HandLandmarkResultBundle?>(null) }
    var hasHandInFrame by remember { mutableStateOf(false) }

    // Captured data buffers
    val capturedCentroids = remember { mutableListOf<Point2D>() }
    val capturedStaticLandmarkSets = remember { mutableListOf<List<NormalizedLandmark>>() }

    // Final normalized review data
    var normalizedMotionPoints by remember { mutableStateOf<List<Point2D>>(emptyList()) }
    var motionStats by remember { mutableStateOf<TrajectoryStats?>(null) }
    var normalizedStaticLandmarks by remember { mutableStateOf<List<Point2D>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Countdown Timer logic
    LaunchedEffect(recordingState) {
        if (recordingState == RecordingState.COUNTDOWN) {
            countdownSeconds = 3
            while (countdownSeconds > 0) {
                delay(1000L)
                countdownSeconds--
            }
            // Transition to RECORDING
            capturedCentroids.clear()
            capturedStaticLandmarkSets.clear()
            errorMessage = null
            recordingState = RecordingState.RECORDING
        } else if (recordingState == RecordingState.RECORDING) {
            val recordDurationMs = if (recordingMode == RecordingMode.MOTION) 2500L else 1500L
            val startTime = SystemClock.uptimeMillis()

            while (SystemClock.uptimeMillis() - startTime < recordDurationMs) {
                delay(50L)
            }

            // Finish recording and process
            if (recordingMode == RecordingMode.MOTION) {
                if (capturedCentroids.size >= 4) {
                    val trimmed = trimStillness(capturedCentroids)
                    val norm = TrajectoryNormalizer.normalizeTrajectory(trimmed)
                    if (norm != null && norm.second.totalPathLength >= 0.05f) {
                        normalizedMotionPoints = norm.first
                        motionStats = norm.second
                        if (gestureName.isBlank()) gestureName = "Custom Motion"
                        recordingState = RecordingState.REVIEW
                    } else {
                        errorMessage = "Movement was too subtle. Please try a clearer motion."
                        recordingState = RecordingState.READY
                    }
                } else {
                    errorMessage = "No hand movement captured. Keep hand in frame."
                    recordingState = RecordingState.READY
                }
            } else {
                // Static Pose
                if (capturedStaticLandmarkSets.isNotEmpty()) {
                    val lastSet = capturedStaticLandmarkSets.last()
                    val norm = StaticTemplateMatcher.normalizeLandmarks(lastSet)
                    if (norm != null) {
                        normalizedStaticLandmarks = norm
                        if (gestureName.isBlank()) gestureName = "Custom Pose"
                        recordingState = RecordingState.REVIEW
                    } else {
                        errorMessage = "Could not detect full hand landmarks."
                        recordingState = RecordingState.READY
                    }
                } else {
                    errorMessage = "No hand detected. Please hold hand steady."
                    recordingState = RecordingState.READY
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark),
            color = BackgroundDark
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Record Gesture Template",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Mode Tabs (Motion vs Static)
                if (recordingState == RecordingState.READY) {
                    TabRow(
                        selectedTabIndex = if (recordingMode == RecordingMode.MOTION) 0 else 1,
                        containerColor = CardBackground,
                        contentColor = PrimaryTeal,
                        indicator = { tabPositions ->
                            TabRowDefaults.Indicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[if (recordingMode == RecordingMode.MOTION) 0 else 1]),
                                color = PrimaryTeal
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        Tab(
                            selected = recordingMode == RecordingMode.MOTION,
                            onClick = { recordingMode = RecordingMode.MOTION },
                            text = { Text("Motion Gesture", fontWeight = FontWeight.Bold) },
                            icon = { Icon(Icons.Default.Gesture, contentDescription = null) }
                        )
                        Tab(
                            selected = recordingMode == RecordingMode.STATIC_POSE,
                            onClick = { recordingMode = RecordingMode.STATIC_POSE },
                            text = { Text("Static Pose", fontWeight = FontWeight.Bold) },
                            icon = { Icon(Icons.Default.PanTool, contentDescription = null) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Main Center Area: Live Camera Preview or Review Preview
                if (recordingState != RecordingState.REVIEW) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f)
                            .clip(RoundedCornerShape(18.dp)),
                        colors = CardDefaults.cardColors(containerColor = CardBackground)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            CameraPreview(
                                modifier = Modifier.fillMaxSize(),
                                onLandmarkResults = { bundle ->
                                    currentBundle = bundle
                                    val landmarks = bundle.result?.landmarks()
                                    if (landmarks != null && landmarks.isNotEmpty() && landmarks[0].size >= 21) {
                                        hasHandInFrame = true
                                        val hand = landmarks[0]

                                        if (recordingState == RecordingState.RECORDING) {
                                            if (recordingMode == RecordingMode.MOTION) {
                                                val (cx, cy) = PalmCentroidHelper.calculatePalmCentroid(hand)
                                                capturedCentroids.add(Point2D(cx, cy))
                                            } else {
                                                capturedStaticLandmarkSets.add(hand)
                                            }
                                        }
                                    } else {
                                        hasHandInFrame = false
                                    }
                                }
                            )

                            // Skeletal Overlay
                            HandLandmarkOverlay(
                                resultBundle = currentBundle,
                                modifier = Modifier.fillMaxSize(),
                                isFrontCamera = true
                            )

                            // Status / Countdown Overlay
                            when (recordingState) {
                                RecordingState.COUNTDOWN -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.55f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$countdownSeconds",
                                            fontSize = 72.sp,
                                            fontWeight = FontWeight.Black,
                                            color = PrimaryTeal
                                        )
                                    }
                                }
                                RecordingState.RECORDING -> {
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .padding(top = 12.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        color = StatusRed.copy(alpha = 0.85f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.FiberManualRecord,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (recordingMode == RecordingMode.MOTION) "RECORDING MOTION..." else "HOLD POSE STEADY...",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                                else -> Unit
                            }
                        }
                    }
                } else {
                    // Review Screen: Large Visual Path Preview
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBackground)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (recordingMode == RecordingMode.MOTION && motionStats != null) {
                                val dummyTemplate = remember(normalizedMotionPoints, motionStats) {
                                    MotionGestureTemplate(
                                        id = "preview",
                                        name = gestureName,
                                        action = selectedAction,
                                        normalizedPoints = normalizedMotionPoints,
                                        stats = motionStats!!
                                    )
                                }
                                TrajectoryThumbnail(
                                    template = dummyTemplate,
                                    size = 160.dp
                                )
                            } else if (recordingMode == RecordingMode.STATIC_POSE && normalizedStaticLandmarks.isNotEmpty()) {
                                val dummyTemplate = remember(normalizedStaticLandmarks) {
                                    StaticGestureTemplate(
                                        id = "preview",
                                        name = gestureName,
                                        action = selectedAction,
                                        landmarks = normalizedStaticLandmarks
                                    )
                                }
                                TrajectoryThumbnail(
                                    template = dummyTemplate,
                                    size = 160.dp
                                )
                            }
                        }
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage!!,
                        color = StatusRed,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Control Action Buttons / Forms
                when (recordingState) {
                    RecordingState.READY -> {
                        Text(
                            text = if (recordingMode == RecordingMode.MOTION)
                                "Tap Start, then perform your custom gesture (e.g. circle, wave, flick) in front of the camera."
                            else
                                "Tap Start, then hold your custom hand pose steady for 1.5 seconds.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { recordingState = RecordingState.COUNTDOWN },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                        ) {
                            Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Start Recording", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }

                    RecordingState.RECORDING -> {
                        Button(
                            onClick = {
                                // Manual early stop
                                if (recordingMode == RecordingMode.MOTION && capturedCentroids.size >= 3) {
                                    val norm = TrajectoryNormalizer.normalizeTrajectory(capturedCentroids)
                                    if (norm != null) {
                                        normalizedMotionPoints = norm.first
                                        motionStats = norm.second
                                        recordingState = RecordingState.REVIEW
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Finish Recording", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }

                    RecordingState.REVIEW -> {
                        // Gesture Name Input
                        OutlinedTextField(
                            value = gestureName,
                            onValueChange = { gestureName = it },
                            label = { Text("Gesture Name") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = PrimaryTeal,
                                unfocusedBorderColor = Color.DarkGray,
                                focusedLabelColor = PrimaryTeal
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Target Action Dropdown
                        ExposedDropdownMenuBox(
                            expanded = actionMenuExpanded,
                            onExpandedChange = { actionMenuExpanded = !actionMenuExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedAction.displayName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Trigger Action") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = actionMenuExpanded) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = PrimaryTeal,
                                    unfocusedBorderColor = Color.DarkGray,
                                    focusedLabelColor = PrimaryTeal
                                ),
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )

                            ExposedDropdownMenu(
                                expanded = actionMenuExpanded,
                                onDismissRequest = { actionMenuExpanded = false }
                            ) {
                                GestureAction.values().forEach { action ->
                                    DropdownMenuItem(
                                        text = { Text(action.displayName) },
                                        onClick = {
                                            selectedAction = action
                                            actionMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Action Buttons: Re-record or Save
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    recordingState = RecordingState.READY
                                    capturedCentroids.clear()
                                    capturedStaticLandmarkSets.clear()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Re-record")
                            }

                            Button(
                                onClick = {
                                    val id = UUID.randomUUID().toString()
                                    val name = gestureName.ifBlank { "Custom Gesture" }

                                    val template: GestureTemplate = if (recordingMode == RecordingMode.MOTION && motionStats != null) {
                                        MotionGestureTemplate(
                                            id = id,
                                            name = name,
                                            action = selectedAction,
                                            normalizedPoints = normalizedMotionPoints,
                                            stats = motionStats!!
                                        )
                                    } else {
                                        StaticGestureTemplate(
                                            id = id,
                                            name = name,
                                            action = selectedAction,
                                            landmarks = normalizedStaticLandmarks
                                        )
                                    }

                                    onSaveTemplate(template)
                                    onDismissRequest()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = StatusGreen)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    else -> Unit
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
