package com.example.fingerdetector.data.model

import androidx.camera.core.CameraSelector

enum class CaptureStage {
    PALM,
    FINGER,
    RESULT
}

enum class HandSide(val title: String) {
    LEFT("Left"),
    RIGHT("Right"),
    UNKNOWN("Unknown")
}

enum class FingerType(val title: String, val fileToken: String, val tipIndex: Int) {
    THUMB("Thumb", "Thumb_Finger", 4),
    INDEX("Index", "Index_Finger", 8),
    MIDDLE("Middle", "Middle_Finger", 12),
    RING("Ring", "Ring_Finger", 16),
    LITTLE("Little", "Little_Finger", 20),
    UNKNOWN("Unknown", "Unknown_Finger", -1)
}

enum class LightingCondition {
    LOW,
    NORMAL,
    BRIGHT
}

data class CameraTelemetry(
    val deviceId: String,
    val brightnessScore: Double,
    val lightingCondition: LightingCondition,
    val cameraType: String,
    val focalLength: Float?,
    val aperture: Float?,
    val focusDistance: Float?,
    val blurScore: Double
)

data class FingerReference(
    val fingerType: FingerType,
    val featureVector: List<Float>
)

data class PalmRecord(
    val handSide: HandSide,
    val fileName: String,
    val fileUri: String?,
    val telemetry: CameraTelemetry,
    val fingerReferences: List<FingerReference>
)

data class FingerCaptureRecord(
    val fingerType: FingerType,
    val fileName: String,
    val fileUri: String?,
    val telemetry: CameraTelemetry,
    val matchScore: Double
)

data class SessionSnapshot(
    val palmRecord: PalmRecord?,
    val fingerRecords: List<FingerCaptureRecord>
)

data class LiveAnalysis(
    val brightnessScore: Double,
    val lightingCondition: LightingCondition,
    val fingerCount: Int,
    val handSide: HandSide,
    val palmFacing: Boolean,
    val centeredFinger: FingerType,
    val statusText: String
)

data class CaptureUiState(
    val stage: CaptureStage = CaptureStage.PALM,
    val progressText: String = "Palm capture pending",
    val stageTitle: String = "Palm Detection",
    val instructionText: String = "Show your full palm inside the box.\nExample: fingers open, palm facing the camera, wrist visible.",
    val liveStatusText: String = "Waiting for camera... Hold your hand steady in the guide.",
    val warningText: String? = null,
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val fingerCountCaptured: Int = 0
)
