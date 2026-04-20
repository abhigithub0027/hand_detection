package com.example.fingerdetector.vision

import android.graphics.PointF
import com.example.fingerdetector.data.model.FingerReference
import com.example.fingerdetector.data.model.FingerType
import com.example.fingerdetector.data.model.HandSide
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt

data class LandmarkPoint(val x: Float, val y: Float, val z: Float)

data class HandObservation(
    val handSide: HandSide,
    val fingerCount: Int,
    val palmFacing: Boolean,
    val centeredFinger: FingerType,
    val featureMap: Map<FingerType, List<Float>>
)

object HandFeatureMath {

    private val palmFingerTypes = listOf(
        FingerType.THUMB,
        FingerType.INDEX,
        FingerType.MIDDLE,
        FingerType.RING,
        FingerType.LITTLE
    )

    fun fromResult(result: HandLandmarkerResult): HandObservation? {
        val landmarks = result.landmarks().firstOrNull()?.map { it.toPoint() } ?: return null
        val handedness = result.handedness().firstOrNull()?.firstOrNull()?.categoryName() ?: "Unknown"
        val handSide = when (handedness.uppercase()) {
            "LEFT" -> HandSide.LEFT
            "RIGHT" -> HandSide.RIGHT
            else -> HandSide.UNKNOWN
        }
        val featureMap = palmFingerTypes.associateWith { extractFingerVector(landmarks, it) }
        return HandObservation(
            handSide = handSide,
            fingerCount = countExtendedFingers(landmarks, handSide),
            palmFacing = isPalmFacing(landmarks),
            centeredFinger = detectCenteredFinger(landmarks),
            featureMap = featureMap
        )
    }

    fun toReferences(featureMap: Map<FingerType, List<Float>>): List<FingerReference> =
        palmFingerTypes.mapNotNull { finger ->
            featureMap[finger]?.let { FingerReference(finger, it) }
        }

    fun detectCenteredFinger(landmarks: List<LandmarkPoint>, center: PointF = PointF(0.5f, 0.5f)): FingerType {
        return palmFingerTypes.minByOrNull { finger ->
            val tip = landmarks[finger.tipIndex]
            distance(tip.x, tip.y, center.x, center.y)
        } ?: FingerType.UNKNOWN
    }

    fun bestFingerMatch(currentVector: List<Float>, references: List<FingerReference>): Pair<FingerType, Double> {
        val best = references.minByOrNull { euclidean(currentVector, it.featureVector) }
            ?: return FingerType.UNKNOWN to 0.0
        val distance = euclidean(currentVector, best.featureVector)
        val score = 1.0 / (1.0 + distance)
        return best.fingerType to score
    }

    fun similarityScore(currentVector: List<Float>, referenceVector: List<Float>): Double {
        val distance = euclidean(currentVector, referenceVector)
        return 1.0 / (1.0 + distance)
    }

    private fun NormalizedLandmark.toPoint(): LandmarkPoint = LandmarkPoint(x(), y(), z())

    private fun isPalmFacing(landmarks: List<LandmarkPoint>): Boolean {
        val fingertipAverageZ = listOf(4, 8, 12, 16, 20).map { landmarks[it].z }.average()
        val baseAverageZ = listOf(2, 5, 9, 13, 17).map { landmarks[it].z }.average()
        return fingertipAverageZ <= baseAverageZ
    }

    private fun countExtendedFingers(landmarks: List<LandmarkPoint>, handSide: HandSide): Int {
        var count = 0
        if (isThumbExtended(landmarks, handSide)) count++
        if (landmarks[8].y < landmarks[6].y) count++
        if (landmarks[12].y < landmarks[10].y) count++
        if (landmarks[16].y < landmarks[14].y) count++
        if (landmarks[20].y < landmarks[18].y) count++
        return count
    }

    private fun isThumbExtended(landmarks: List<LandmarkPoint>, handSide: HandSide): Boolean {
        return when (handSide) {
            HandSide.LEFT -> landmarks[4].x > landmarks[3].x
            HandSide.RIGHT -> landmarks[4].x < landmarks[3].x
            HandSide.UNKNOWN -> abs(landmarks[4].x - landmarks[3].x) > 0.04f
        }
    }

    private fun extractFingerVector(landmarks: List<LandmarkPoint>, fingerType: FingerType): List<Float> {
        val indices = when (fingerType) {
            FingerType.THUMB -> listOf(1, 2, 3, 4)
            FingerType.INDEX -> listOf(5, 6, 7, 8)
            FingerType.MIDDLE -> listOf(9, 10, 11, 12)
            FingerType.RING -> listOf(13, 14, 15, 16)
            FingerType.LITTLE -> listOf(17, 18, 19, 20)
            FingerType.UNKNOWN -> listOf(0, 0, 0, 0)
        }
        val mcp = landmarks[indices[0]]
        val pip = landmarks[indices[1]]
        val dip = landmarks[indices[2]]
        val tip = landmarks[indices[3]]
        val wrist = landmarks[0]
        val palmCenter = averageOf(landmarks[0], landmarks[5], landmarks[9], landmarks[13], landmarks[17])
        val palmScale = maxOf(distance(landmarks[5], landmarks[17]), 0.001f)

        val segmentAngle1 = angleBetween(mcp, pip)
        val segmentAngle2 = angleBetween(pip, dip)
        val segmentAngle3 = angleBetween(dip, tip)

        return listOf(
            distance(mcp, tip) / palmScale,
            distance(mcp, pip) / palmScale,
            distance(pip, dip) / palmScale,
            distance(dip, tip) / palmScale,
            distance(wrist, tip) / palmScale,
            ((tip.x - palmCenter.x) / palmScale),
            ((tip.y - palmCenter.y) / palmScale),
            segmentAngle1,
            segmentAngle2,
            segmentAngle3
        )
    }

    private fun averageOf(vararg points: LandmarkPoint): LandmarkPoint = LandmarkPoint(
        x = points.map { it.x }.average().toFloat(),
        y = points.map { it.y }.average().toFloat(),
        z = points.map { it.z }.average().toFloat()
    )

    private fun angleBetween(a: LandmarkPoint, b: LandmarkPoint): Float =
        atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble()).toFloat()

    private fun distance(a: LandmarkPoint, b: LandmarkPoint): Float = distance(a.x, a.y, b.x, b.y)

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float =
        sqrt((x1 - x2).pow(2) + (y1 - y2).pow(2))

    private fun euclidean(a: List<Float>, b: List<Float>): Double =
        sqrt(a.zip(b).sumOf { (x, y) -> (x - y).toDouble().pow(2) })
}
