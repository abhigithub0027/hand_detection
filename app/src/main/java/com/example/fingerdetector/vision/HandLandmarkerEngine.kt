package com.example.fingerdetector.vision

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker

class HandLandmarkerEngine(context: Context) {

    private val handLandmarker: HandLandmarker = HandLandmarker.createFromOptions(
        context,
        HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_PATH).build())
            .setMinHandDetectionConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setNumHands(1)
            .setRunningMode(RunningMode.IMAGE)
            .build()
    )

    fun detect(bitmap: Bitmap): HandObservation? {
        val result = handLandmarker.detect(BitmapImageBuilder(bitmap).build()) ?: return null
        return HandFeatureMath.fromResult(result)
    }

    fun close() {
        handLandmarker.close()
    }

    private companion object {
        const val MODEL_PATH = "hand_landmarker.task"
    }
}
