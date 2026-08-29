package com.ydnar.cheironomy.data.template

import com.ydnar.cheironomy.data.GestureAction
import org.json.JSONArray
import org.json.JSONObject

/**
 * 2D point in normalized coordinate space [0, 1].
 */
data class Point2D(
    val x: Float,
    val y: Float
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("x", x.toDouble())
        put("y", y.toDouble())
    }

    companion object {
        fun fromJson(json: JSONObject): Point2D = Point2D(
            x = json.getDouble("x").toFloat(),
            y = json.getDouble("y").toFloat()
        )
    }
}

/**
 * Cheap summary statistics for fast O(1) prefiltering before DTW matching.
 */
data class TrajectoryStats(
    val totalPathLength: Float,
    val boundingBoxWidth: Float,
    val boundingBoxHeight: Float,
    val displacementX: Float,
    val displacementY: Float
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("totalPathLength", totalPathLength.toDouble())
        put("boundingBoxWidth", boundingBoxWidth.toDouble())
        put("boundingBoxHeight", boundingBoxHeight.toDouble())
        put("displacementX", displacementX.toDouble())
        put("displacementY", displacementY.toDouble())
    }

    companion object {
        fun fromJson(json: JSONObject): TrajectoryStats = TrajectoryStats(
            totalPathLength = json.getDouble("totalPathLength").toFloat(),
            boundingBoxWidth = json.getDouble("boundingBoxWidth").toFloat(),
            boundingBoxHeight = json.getDouble("boundingBoxHeight").toFloat(),
            displacementX = json.getDouble("displacementX").toFloat(),
            displacementY = json.getDouble("displacementY").toFloat()
        )
    }
}

/**
 * User-recorded reusable gesture template.
 */
sealed interface GestureTemplate {
    val id: String
    val name: String
    val action: GestureAction
    val createdAt: Long

    data class StaticGestureTemplate(
        override val id: String,
        override val name: String,
        override val action: GestureAction,
        override val createdAt: Long = System.currentTimeMillis(),
        val landmarks: List<Point2D> // 21 normalized landmarks
    ) : GestureTemplate

    data class MotionGestureTemplate(
        override val id: String,
        override val name: String,
        override val action: GestureAction,
        override val createdAt: Long = System.currentTimeMillis(),
        val normalizedPoints: List<Point2D>, // 20 equidistant resampled points
        val stats: TrajectoryStats
    ) : GestureTemplate

    companion object {
        fun toJson(template: GestureTemplate): JSONObject {
            return JSONObject().apply {
                put("id", template.id)
                put("name", template.name)
                put("action", template.action.name)
                put("createdAt", template.createdAt)

                when (template) {
                    is StaticGestureTemplate -> {
                        put("type", "STATIC")
                        val landmarksArray = JSONArray()
                        template.landmarks.forEach { landmarksArray.put(it.toJson()) }
                        put("landmarks", landmarksArray)
                    }
                    is MotionGestureTemplate -> {
                        put("type", "MOTION")
                        val pointsArray = JSONArray()
                        template.normalizedPoints.forEach { pointsArray.put(it.toJson()) }
                        put("points", pointsArray)
                        put("stats", template.stats.toJson())
                    }
                }
            }
        }

        fun fromJson(json: JSONObject): GestureTemplate? {
            return try {
                val id = json.getString("id")
                val name = json.getString("name")
                val actionName = json.getString("action")
                val action = try { GestureAction.valueOf(actionName) } catch (e: Exception) { GestureAction.NONE }
                val createdAt = json.optLong("createdAt", System.currentTimeMillis())
                val type = json.getString("type")

                when (type) {
                    "STATIC" -> {
                        val array = json.getJSONArray("landmarks")
                        val landmarks = mutableListOf<Point2D>()
                        for (i in 0 until array.length()) {
                            landmarks.add(Point2D.fromJson(array.getJSONObject(i)))
                        }
                        StaticGestureTemplate(
                            id = id,
                            name = name,
                            action = action,
                            createdAt = createdAt,
                            landmarks = landmarks
                        )
                    }
                    "MOTION" -> {
                        val array = json.getJSONArray("points")
                        val points = mutableListOf<Point2D>()
                        for (i in 0 until array.length()) {
                            points.add(Point2D.fromJson(array.getJSONObject(i)))
                        }
                        val stats = TrajectoryStats.fromJson(json.getJSONObject("stats"))
                        MotionGestureTemplate(
                            id = id,
                            name = name,
                            action = action,
                            createdAt = createdAt,
                            normalizedPoints = points,
                            stats = stats
                        )
                    }
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }

        fun listToJson(list: List<GestureTemplate>): String {
            val array = JSONArray()
            list.forEach { array.put(toJson(it)) }
            return array.toString()
        }

        fun listFromJson(jsonStr: String?): List<GestureTemplate> {
            if (jsonStr.isNullOrBlank()) return emptyList()
            return try {
                val array = JSONArray(jsonStr)
                val list = mutableListOf<GestureTemplate>()
                for (i in 0 until array.length()) {
                    fromJson(array.getJSONObject(i))?.let { list.add(it) }
                }
                list
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
