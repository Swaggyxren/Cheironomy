package com.ydnar.cheironomy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ydnar.cheironomy.data.template.GestureTemplate
import com.ydnar.cheironomy.data.template.GestureTemplate.MotionGestureTemplate
import com.ydnar.cheironomy.data.template.GestureTemplate.StaticGestureTemplate
import com.ydnar.cheironomy.ui.theme.BackgroundDark
import com.ydnar.cheironomy.ui.theme.PrimaryTeal
import com.ydnar.cheironomy.ui.theme.StatusGreen

// Standard 21-point skeleton connections for static thumbnail
private val STATIC_SKELETON_CONNECTIONS = listOf(
    0 to 1, 1 to 2, 2 to 3, 3 to 4,
    0 to 5, 5 to 6, 6 to 7, 7 to 8,
    0 to 9, 9 to 10, 10 to 11, 11 to 12,
    0 to 13, 13 to 14, 14 to 15, 15 to 16,
    0 to 17, 17 to 18, 18 to 19, 19 to 20,
    5 to 9, 9 to 13, 13 to 17
)

/**
 * Thumbnail rendering of recorded gesture templates (motion trajectory or static hand pose).
 */
@Composable
fun TrajectoryThumbnail(
    template: GestureTemplate,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(BackgroundDark)
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val canvasW = this.size.width
            val canvasH = this.size.height
            val padding = 10f

            when (template) {
                is MotionGestureTemplate -> {
                    val points = template.normalizedPoints
                    if (points.size < 2) return@Canvas

                    var minX = points[0].x
                    var maxX = points[0].x
                    var minY = points[0].y
                    var maxY = points[0].y

                    points.forEach {
                        minX = minOf(minX, it.x)
                        maxX = maxOf(maxX, it.x)
                        minY = minOf(minY, it.y)
                        maxY = maxOf(maxY, it.y)
                    }

                    val spanX = (maxX - minX).coerceAtLeast(0.01f)
                    val spanY = (maxY - minY).coerceAtLeast(0.01f)
                    val scale = minOf((canvasW - padding * 2) / spanX, (canvasH - padding * 2) / spanY)

                    val midX = (minX + maxX) / 2f
                    val midY = (minY + maxY) / 2f

                    fun mapPoint(x: Float, y: Float): Offset {
                        val px = canvasW / 2f + (x - midX) * scale
                        val py = canvasH / 2f + (y - midY) * scale
                        return Offset(px, py)
                    }

                    val path = Path()
                    val startOffset = mapPoint(points[0].x, points[0].y)
                    path.moveTo(startOffset.x, startOffset.y)

                    for (i in 1 until points.size) {
                        val pt = mapPoint(points[i].x, points[i].y)
                        path.lineTo(pt.x, pt.y)
                    }

                    drawPath(
                        path = path,
                        color = PrimaryTeal,
                        style = Stroke(
                            width = 3.5f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    // Start dot (Cyan)
                    drawCircle(
                        color = Color(0xFF00E5FF),
                        radius = 4f,
                        center = startOffset
                    )

                    // End dot (Green)
                    val endOffset = mapPoint(points.last().x, points.last().y)
                    drawCircle(
                        color = StatusGreen,
                        radius = 4.5f,
                        center = endOffset
                    )
                }

                is StaticGestureTemplate -> {
                    val landmarks = template.landmarks
                    if (landmarks.size < 21) return@Canvas

                    var minX = landmarks[0].x
                    var maxX = landmarks[0].x
                    var minY = landmarks[0].y
                    var maxY = landmarks[0].y

                    landmarks.forEach {
                        minX = minOf(minX, it.x)
                        maxX = maxOf(maxX, it.x)
                        minY = minOf(minY, it.y)
                        maxY = maxOf(maxY, it.y)
                    }

                    val spanX = (maxX - minX).coerceAtLeast(0.01f)
                    val spanY = (maxY - minY).coerceAtLeast(0.01f)
                    val scale = minOf((canvasW - padding * 2) / spanX, (canvasH - padding * 2) / spanY)

                    val midX = (minX + maxX) / 2f
                    val midY = (minY + maxY) / 2f

                    fun mapPoint(x: Float, y: Float): Offset {
                        val px = canvasW / 2f + (x - midX) * scale
                        val py = canvasH / 2f + (y - midY) * scale
                        return Offset(px, py)
                    }

                    // Draw bones
                    for ((s, e) in STATIC_SKELETON_CONNECTIONS) {
                        val p1 = mapPoint(landmarks[s].x, landmarks[s].y)
                        val p2 = mapPoint(landmarks[e].x, landmarks[e].y)
                        drawLine(
                            color = PrimaryTeal.copy(alpha = 0.7f),
                            start = p1,
                            end = p2,
                            strokeWidth = 2.5f,
                            cap = StrokeCap.Round
                        )
                    }

                    // Draw joints
                    landmarks.forEach {
                        val pt = mapPoint(it.x, it.y)
                        drawCircle(
                            color = StatusGreen,
                            radius = 2.5f,
                            center = pt
                        )
                    }
                }
            }
        }
    }
}
