package com.example.fingerdetector.ui.capture

import androidx.lifecycle.ViewModel
import com.example.fingerdetector.data.model.CameraTelemetry
import com.example.fingerdetector.data.model.CaptureStage
import com.example.fingerdetector.data.model.CaptureUiState
import com.example.fingerdetector.data.model.FingerCaptureRecord
import com.example.fingerdetector.data.model.FingerType
import com.example.fingerdetector.data.model.LiveAnalysis
import com.example.fingerdetector.data.model.PalmRecord
import com.example.fingerdetector.data.repository.CaptureSessionRepository
import com.example.fingerdetector.vision.HandFeatureMath
import com.example.fingerdetector.vision.HandObservation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

sealed class CaptureValidation {
    data class PalmReady(
        val record: PalmRecord
    ) : CaptureValidation()

    data class FingerReady(
        val record: FingerCaptureRecord,
        val isComplete: Boolean
    ) : CaptureValidation()

    data class Error(val message: String) : CaptureValidation()
}

class CaptureViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState

    private var lastLiveAnalysis: LiveAnalysis? = null

    init {
        resetSession()
    }

    fun resetSession() {
        CaptureSessionRepository.reset()
        lastLiveAnalysis = null
        _uiState.value = CaptureUiState()
    }

    fun onLiveAnalysis(analysis: LiveAnalysis) {
        lastLiveAnalysis = analysis
        val warning = when (_uiState.value.stage) {
            CaptureStage.PALM ->
                if (!analysis.palmFacing) {
                    "We can see the back of your hand. Please turn your hand so your palm lines face the camera."
                } else {
                    null
                }

            CaptureStage.FINGER ->
                if (!analysis.palmFacing) {
                    "We can see the back of your finger. Turn your hand so the palm side faces the camera, then try again."
                } else {
                    null
                }

            CaptureStage.RESULT -> null
        }
        _uiState.update {
            it.copy(
                liveStatusText = analysis.statusText,
                warningText = warning
            )
        }
    }

    fun toggleLensFacing() {
        _uiState.update {
            it.copy(
                lensFacing = if (it.lensFacing == androidx.camera.core.CameraSelector.LENS_FACING_BACK) {
                    androidx.camera.core.CameraSelector.LENS_FACING_FRONT
                } else {
                    androidx.camera.core.CameraSelector.LENS_FACING_BACK
                }
            )
        }
    }

    fun validatePalmCapture(
        observation: HandObservation?,
        telemetry: CameraTelemetry,
        fileName: String,
        fileUri: String?
    ): CaptureValidation {
        if (observation == null) {
            return CaptureValidation.Error(
                "Palm not found.\nTry this: show your full hand inside the box, keep fingers open, and keep the palm facing the camera."
            )
        }
        if (!observation.palmFacing) {
            return CaptureValidation.Error(
                "This looks like the back of your hand.\nPlease turn to the palm side so your palm lines face the camera."
            )
        }
        val record = PalmRecord(
            handSide = observation.handSide,
            fileName = fileName,
            fileUri = fileUri,
            telemetry = telemetry,
            fingerReferences = HandFeatureMath.toReferences(observation.featureMap)
        )
        CaptureSessionRepository.savePalm(record)
        _uiState.value = CaptureUiState(
            stage = CaptureStage.FINGER,
            progressText = "0/5 fingers scanned",
            stageTitle = "Finger Detection",
            instructionText = "Keep your full hand visible and place one finger in the center oval.\nExample: one finger centered, other fingers still visible, palm side facing the camera.",
            liveStatusText = "Palm saved. Start with your thumb, then move finger by finger.",
            warningText = null,
            lensFacing = _uiState.value.lensFacing,
            fingerCountCaptured = 0
        )
        return CaptureValidation.PalmReady(record)
    }

    fun validateFingerCapture(
        observation: HandObservation?,
        telemetry: CameraTelemetry,
        fileNameFactory: (FingerType) -> String,
        fileUriFactory: (String) -> String?
    ): CaptureValidation {
        val snapshot = CaptureSessionRepository.snapshot()
        val palmRecord = snapshot.palmRecord
            ?: return CaptureValidation.Error(
                "Palm step is missing.\nPlease capture your full palm first, then continue to finger scanning."
            )

        if (observation == null) {
            return CaptureValidation.Error(
                "Hand not found.\nKeep your full hand visible and move only one finger into the center oval."
            )
        }
        if (!observation.palmFacing) {
            return CaptureValidation.Error(
                "We are seeing the back of your finger.\nTurn your hand so the palm side faces the camera and try again."
            )
        }
        if (
            palmRecord.handSide != com.example.fingerdetector.data.model.HandSide.UNKNOWN &&
            observation.handSide != com.example.fingerdetector.data.model.HandSide.UNKNOWN &&
            observation.handSide != palmRecord.handSide
        ) {
            return CaptureValidation.Error(
                "This looks like the other hand.\nPlease continue with the same ${palmRecord.handSide.title.lowercase()} hand you used for the palm capture."
            )
        }

        val centeredFinger = observation.centeredFinger
        if (centeredFinger == FingerType.UNKNOWN) {
            return CaptureValidation.Error(
                "I couldn't tell which finger is in the center.\nMove one finger fully into the oval and keep the rest of the hand steady."
            )
        }

        val candidateVector = observation.featureMap[centeredFinger]
            ?: return CaptureValidation.Error(
                "That finger was not clear enough to read.\nHold steady for a moment and capture again."
            )
        val reference = palmRecord.fingerReferences.firstOrNull { it.fingerType == centeredFinger }
            ?: return CaptureValidation.Error(
                "We don't have a saved reference for your ${centeredFinger.title.lowercase()} finger yet.\nPlease restart the session and capture the palm again."
            )
        val matchedFinger = centeredFinger
        val score = HandFeatureMath.similarityScore(candidateVector, reference.featureVector)

        if (score < 0.52) {
            return CaptureValidation.Error(
                "That finger didn't match your saved palm clearly enough.\nUse the same hand, keep the full hand visible, and center only your ${centeredFinger.title.lowercase()} finger."
            )
        }
        if (snapshot.fingerRecords.any { it.fingerType == matchedFinger }) {
            return CaptureValidation.Error(
                "${matchedFinger.title} finger is already saved.\nPlease move the next finger into the oval."
            )
        }

        val fileName = fileNameFactory(matchedFinger)
        val record = FingerCaptureRecord(
            fingerType = matchedFinger,
            fileName = fileName,
            fileUri = fileUriFactory(fileName),
            telemetry = telemetry,
            matchScore = score
        )
        CaptureSessionRepository.saveFinger(record)

        val count = CaptureSessionRepository.snapshot().fingerRecords.size
        val isComplete = count == 5
        _uiState.update {
            it.copy(
                stage = if (isComplete) CaptureStage.RESULT else CaptureStage.FINGER,
                progressText = "$count/5 fingers scanned",
                liveStatusText = if (isComplete) {
                    "Great job. All 5 fingers are captured."
                } else {
                    "${matchedFinger.title} finger saved. Now place the next finger in the oval."
                },
                warningText = null,
                fingerCountCaptured = count
            )
        }
        return CaptureValidation.FingerReady(record, isComplete)
    }

    fun resultReady() {
        _uiState.update { it.copy(stage = CaptureStage.RESULT) }
    }

    fun snapshot() = CaptureSessionRepository.snapshot()
}
