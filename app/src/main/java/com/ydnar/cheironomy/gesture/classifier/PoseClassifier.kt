package com.ydnar.cheironomy.gesture.classifier

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.ydnar.cheironomy.gesture.model.PoseType
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Classifies static hand poses using scale-invariant geometric joint rules.
 */
object PoseClassifier {

    /**
     * Classifies the hand pose from the 21 normalized landmarks.
     */
    fun classifyPose(landmarks: List<NormalizedLandmark>): PoseType {
        if (landmarks.size < 21) return PoseType.UNKNOWN

        val wrist = landmarks[0]
        val middleMcp = landmarks[9]

        // Palm scale reference: distance from wrist to middle finger MCP
        val palmScale = distance(wrist, middleMcp)
        if (palmScale < 0.04f) {
            // Hand too far or invalid scale
            return PoseType.UNKNOWN
        }

        val isIndexExtended = isFingerExtended(
            wrist = wrist,
            mcp = landmarks[5],
            pip = landmarks[6],
            dip = landmarks[7],
            tip = landmarks[8]
        )

        val isMiddleExtended = isFingerExtended(
            wrist = wrist,
            mcp = landmarks[9],
            pip = landmarks[10],
            dip = landmarks[11],
            tip = landmarks[12]
        )

        val isRingExtended = isFingerExtended(
            wrist = wrist,
            mcp = landmarks[13],
            pip = landmarks[14],
            dip = landmarks[15],
            tip = landmarks[16]
        )

        val isPinkyExtended = isFingerExtended(
            wrist = wrist,
            mcp = landmarks[17],
            pip = landmarks[18],
            dip = landmarks[19],
            tip = landmarks[20]
        )

        val isThumbExtended = isThumbExtended(
            wrist = wrist,
            cmc = landmarks[1],
            mcp = landmarks[2],
            ip = landmarks[3],
            tip = landmarks[4],
            pinkyMcp = landmarks[17]
        )

        val extendedFingerCount = listOf(
            isIndexExtended,
            isMiddleExtended,
            isRingExtended,
            isPinkyExtended
        ).count { it }

        // Open Palm: All 4 main fingers extended + thumb extended (or at least 4 total extended)
        if (extendedFingerCount == 4 && isThumbExtended) {
            return PoseType.OPEN_PALM
        }

        // Fist: All fingers curled
        if (extendedFingerCount == 0 && !isThumbExtended) {
            return PoseType.FIST
        }

        // Peace sign: Index + Middle extended, Ring + Pinky curled
        if (isIndexExtended && isMiddleExtended && !isRingExtended && !isPinkyExtended) {
            return PoseType.PEACE_SIGN
        }

        return PoseType.UNKNOWN
    }

    /**
     * Calculates the palm centroid (geometric center of palm base).
     */
    fun calculatePalmCentroid(landmarks: List<NormalizedLandmark>): Pair<Float, Float> {
        if (landmarks.size < 21) return Pair(0f, 0f)

        // Cluster: Wrist (0), Index MCP (5), Middle MCP (9), Ring MCP (13), Pinky MCP (17)
        val palmIndices = listOf(0, 5, 9, 13, 17)
        var sumX = 0f
        var sumY = 0f

        for (idx in palmIndices) {
            sumX += landmarks[idx].x()
            sumY += landmarks[idx].y()
        }

        return Pair(sumX / palmIndices.size, sumY / palmIndices.size)
    }

    private fun isFingerExtended(
        wrist: NormalizedLandmark,
        mcp: NormalizedLandmark,
        pip: NormalizedLandmark,
        dip: NormalizedLandmark,
        tip: NormalizedLandmark
    ): Boolean {
        val distTipWrist = distance(tip, wrist)
        val distPipWrist = distance(pip, wrist)
        val distTipMcp = distance(tip, mcp)
        val distPipMcp = distance(pip, mcp)

        return (distTipWrist > distPipWrist * 1.1f) && (distTipMcp > distPipMcp * 1.15f)
    }

    private fun isThumbExtended(
        wrist: NormalizedLandmark,
        cmc: NormalizedLandmark,
        mcp: NormalizedLandmark,
        ip: NormalizedLandmark,
        tip: NormalizedLandmark,
        pinkyMcp: NormalizedLandmark
    ): Boolean {
        val distTipWrist = distance(tip, wrist)
        val distMcpWrist = distance(mcp, wrist)
        val distTipPinky = distance(tip, pinkyMcp)
        val distIpPinky = distance(ip, pinkyMcp)

        return (distTipWrist > distMcpWrist * 1.1f) || (distTipPinky > distIpPinky * 1.1f)
    }

    fun distance(a: NormalizedLandmark, b: NormalizedLandmark): Float {
        val dx = a.x() - b.x()
        val dy = a.y() - b.y()
        return sqrt((dx * dx) + (dy * dy))
    }
}
