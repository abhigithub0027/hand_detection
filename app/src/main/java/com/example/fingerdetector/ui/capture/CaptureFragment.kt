package com.example.fingerdetector.ui.capture

import android.Manifest
import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.fingerdetector.base.BaseActivity
import com.example.fingerdetector.base.PermissionAware
import com.example.fingerdetector.data.model.CameraTelemetry
import com.example.fingerdetector.data.model.CaptureStage
import com.example.fingerdetector.data.model.CaptureUiState
import com.example.fingerdetector.data.model.FingerType
import com.example.fingerdetector.data.model.HandSide
import com.example.fingerdetector.data.model.LiveAnalysis
import com.example.fingerdetector.databinding.FragmentCaptureBinding
import com.example.fingerdetector.ui.result.ResultFragment
import com.example.fingerdetector.ui.widget.DetectionOverlayView
import com.example.fingerdetector.vision.HandLandmarkerEngine
import com.example.fingerdetector.vision.ImageUtils
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.launch

class CaptureFragment : Fragment(), PermissionAware {

    private var _binding: FragmentCaptureBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CaptureViewModel by activityViewModels()

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var detector: HandLandmarkerEngine

    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var lastLiveAnalysisAt = 0L
    private var isLiveAnalysisBusy = false
    private var appliedExposureIndex: Int? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCaptureBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (activity as? BaseActivity)?.configureToolbar("Capture Flow", true)
        cameraExecutor = Executors.newSingleThreadExecutor()
        detector = HandLandmarkerEngine(requireContext())

        binding.switchCameraButton.setOnClickListener {
            viewModel.toggleLensFacing()
            startCamera()
        }
        binding.captureButton.setOnClickListener { captureCurrentStage() }
        binding.finishButton.setOnClickListener {
            (activity as? BaseActivity)?.navigate(ResultFragment())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }

        ensurePermissionsAndCamera()
    }

    private fun render(state: CaptureUiState) {
        binding.stageLabel.text = state.stageTitle
        binding.instructionLabel.text = state.instructionText
        binding.liveStatusLabel.text = state.liveStatusText
        binding.progressLabel.text = state.progressText
        binding.overlayView.overlayMode = if (state.stage == CaptureStage.PALM) {
            DetectionOverlayView.OverlayMode.PALM
        } else {
            DetectionOverlayView.OverlayMode.FINGER
        }
        if (state.warningText.isNullOrBlank()) {
            binding.warningLabel.visibility = View.GONE
        } else {
            binding.warningLabel.visibility = View.VISIBLE
            binding.warningLabel.text = state.warningText
        }
    }

    private fun ensurePermissionsAndCamera() {
        if (requiredPermissions().all { permission ->
                ContextCompat.checkSelfPermission(requireContext(), permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        ) {
            startCamera()
        } else {
            (activity as? BaseActivity)?.requestAppPermissions(this, requiredPermissions())
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(requireContext())
        future.addListener(
            {
                val provider = future.get()
                bindCamera(provider)
            },
            ContextCompat.getMainExecutor(requireContext())
        )
    }

    private fun bindCamera(provider: ProcessCameraProvider) {
        provider.unbindAll()
        val lensFacing = viewModel.uiState.value.lensFacing
        val selector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()

        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    handleLiveFrame(imageProxy, lensFacing == CameraSelector.LENS_FACING_FRONT)
                }
            }

        val preview = Preview.Builder().build().also {
            it.surfaceProvider = binding.previewView.surfaceProvider
        }

        try {
            camera = provider.bindToLifecycle(
                viewLifecycleOwner,
                selector,
                preview,
                imageCapture,
                imageAnalysis
            )
        } catch (_: Exception) {
            if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                viewModel.toggleLensFacing()
                bindCamera(provider)
            } else {
                Toast.makeText(requireContext(), "Unable to open camera.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleLiveFrame(imageProxy: ImageProxy, mirror: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (isLiveAnalysisBusy || now - lastLiveAnalysisAt < 350) {
            imageProxy.close()
            return
        }
        lastLiveAnalysisAt = now
        isLiveAnalysisBusy = true

        val bitmap = try {
            ImageUtils.bitmapFromRgbaImageProxy(imageProxy, mirror)
        } finally {
            imageProxy.close()
        }

        cameraExecutor.execute {
            val brightness = ImageUtils.computeBrightness(bitmap)
            val lighting = ImageUtils.classifyLighting(brightness)
            val observation = detector.detect(bitmap)
            val liveAnalysis = LiveAnalysis(
                brightnessScore = brightness,
                lightingCondition = lighting,
                fingerCount = observation?.fingerCount ?: 0,
                handSide = observation?.handSide ?: HandSide.UNKNOWN,
                palmFacing = observation?.palmFacing ?: true,
                centeredFinger = observation?.centeredFinger ?: FingerType.UNKNOWN,
                statusText = buildLiveStatus(observation?.handSide ?: HandSide.UNKNOWN, observation?.fingerCount ?: 0, brightness, lighting, observation?.centeredFinger ?: FingerType.UNKNOWN)
            )
            activity?.runOnUiThread {
                viewModel.onLiveAnalysis(liveAnalysis)
                adjustExposureIfNeeded(lighting)
            }
            isLiveAnalysisBusy = false
        }
    }

    private fun buildLiveStatus(
        handSide: HandSide,
        fingerCount: Int,
        brightness: Double,
        lighting: com.example.fingerdetector.data.model.LightingCondition,
        centeredFinger: FingerType
    ): String = buildString {
        append("Hand: ${handSide.title}")
        append("  |  Fingers: $fingerCount")
        append("  |  Center: ${centeredFinger.title}")
        append("  |  Brightness: ${"%.1f".format(Locale.US, brightness)}")
        append("  |  Lighting: $lighting")
    }

    private fun adjustExposureIfNeeded(lighting: com.example.fingerdetector.data.model.LightingCondition) {
        val activeCamera = camera ?: return
        val exposureState = activeCamera.cameraInfo.exposureState
        val range = exposureState.exposureCompensationRange
        if (range.lower == 0 && range.upper == 0) return

        val desired = when (lighting) {
            com.example.fingerdetector.data.model.LightingCondition.LOW -> 1
            com.example.fingerdetector.data.model.LightingCondition.BRIGHT -> -1
            com.example.fingerdetector.data.model.LightingCondition.NORMAL -> 0
        }.coerceIn(range.lower, range.upper)

        if (appliedExposureIndex != desired) {
            appliedExposureIndex = desired
            activeCamera.cameraControl.setExposureCompensationIndex(desired)
        }
    }

    private fun captureCurrentStage() {
        val capture = imageCapture ?: return
        binding.captureButton.isEnabled = false
        triggerCenterAutoFocus()
        binding.previewView.postDelayed(
            {
                val tempFile = File(requireContext().cacheDir, "capture_${System.currentTimeMillis()}.jpg")
                val options = ImageCapture.OutputFileOptions.Builder(tempFile).build()
                capture.takePicture(
                    options,
                    cameraExecutor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            processCapturedFile(tempFile)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            activity?.runOnUiThread {
                                binding.captureButton.isEnabled = true
                                Toast.makeText(requireContext(), exception.message ?: "Capture failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            },
            250
        )
    }

    private fun triggerCenterAutoFocus() {
        val activeCamera = camera ?: return
        val factory: MeteringPointFactory = binding.previewView.meteringPointFactory
        val point = factory.createPoint(0.5f, 0.5f)
        val action = FocusMeteringAction.Builder(point).build()
        activeCamera.cameraControl.startFocusAndMetering(action)
    }

    private fun processCapturedFile(tempFile: File) {
        val bitmap = ImageUtils.decodeBitmapWithExif(tempFile)
        if (bitmap == null) {
            tempFile.delete()
            activity?.runOnUiThread {
                binding.captureButton.isEnabled = true
                Toast.makeText(requireContext(), "Could not decode image", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val observation = detector.detect(bitmap)
        val brightness = ImageUtils.computeBrightness(bitmap)
        val blurScore = ImageUtils.computeBlurScore(bitmap)
        val telemetry = buildTelemetry(brightness, blurScore)

        val stage = viewModel.uiState.value.stage
        val validation = if (stage == CaptureStage.PALM) {
            val handSide = observation?.handSide ?: HandSide.UNKNOWN
            val fileName = "${handSide.title}_Hand_${ImageUtils.timestamp()}.jpg"
            viewModel.validatePalmCapture(observation, telemetry, fileName, null)
        } else {
            if (ImageUtils.blurThresholdViolated(blurScore)) {
                CaptureValidation.Error(
                    "That photo looks a little blurry.\nHold your hand still for a moment and capture the finger again."
                )
            } else {
                viewModel.validateFingerCapture(
                    observation = observation,
                    telemetry = telemetry,
                    fileNameFactory = { finger ->
                        "${viewModel.snapshot().palmRecord?.handSide?.title ?: "Unknown"}_Hand_${finger.fileToken}_${ImageUtils.timestamp()}.jpg"
                    },
                    fileUriFactory = { null }
                )
            }
        }

        when (validation) {
            is CaptureValidation.Error -> {
                tempFile.delete()
                activity?.runOnUiThread {
                    binding.captureButton.isEnabled = true
                    Toast.makeText(requireContext(), validation.message, Toast.LENGTH_LONG).show()
                }
            }

            is CaptureValidation.PalmReady -> {
                ImageUtils.saveTempFileToFingerData(requireContext(), tempFile, validation.record.fileName)
                ImageUtils.appendTelemetryLog(
                    requireContext(),
                    stageLabel = "PALM",
                    fileName = validation.record.fileName,
                    handLabel = validation.record.handSide.title,
                    fingerLabel = "Palm",
                    telemetry = telemetry
                )
                tempFile.delete()
                activity?.runOnUiThread {
                    binding.captureButton.isEnabled = true
                    Toast.makeText(
                        requireContext(),
                        "Palm saved for your ${validation.record.handSide.title.lowercase()} hand. Next, place one finger in the oval.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            is CaptureValidation.FingerReady -> {
                ImageUtils.saveTempFileToFingerData(requireContext(), tempFile, validation.record.fileName)
                ImageUtils.appendTelemetryLog(
                    requireContext(),
                    stageLabel = "FINGER",
                    fileName = validation.record.fileName,
                    handLabel = viewModel.snapshot().palmRecord?.handSide?.title ?: "Unknown",
                    fingerLabel = validation.record.fingerType.title,
                    telemetry = telemetry
                )
                tempFile.delete()
                activity?.runOnUiThread {
                    binding.captureButton.isEnabled = true
                    Toast.makeText(
                        requireContext(),
                        "${validation.record.fingerType.title} finger captured successfully.",
                        Toast.LENGTH_SHORT
                    ).show()
                    if (validation.isComplete) {
                        viewModel.resultReady()
                        (activity as? BaseActivity)?.navigate(ResultFragment())
                    }
                }
            }
        }
    }

    private fun buildTelemetry(brightness: Double, blurScore: Double): CameraTelemetry {
        val activeCamera = camera
        val deviceId = ImageUtils.deviceId(requireContext())
        val info = activeCamera?.cameraInfo?.let { Camera2CameraInfo.from(it) }
        val focalLength = info?.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
        val aperture = info?.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.firstOrNull()
        val focusDistance = info?.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
        return CameraTelemetry(
            deviceId = deviceId,
            brightnessScore = brightness,
            lightingCondition = ImageUtils.classifyLighting(brightness),
            cameraType = if (viewModel.uiState.value.lensFacing == CameraSelector.LENS_FACING_BACK) "rear" else "front",
            focalLength = focalLength,
            aperture = aperture,
            focusDistance = focusDistance,
            blurScore = blurScore
        )
    }

    private fun requiredPermissions(): Array<String> {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.READ_MEDIA_IMAGES
        } else {
            permissions += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissions += Manifest.permission.WRITE_EXTERNAL_STORAGE
        }
        return permissions.toTypedArray()
    }

    override fun onPermissionResult(allGranted: Boolean, results: Map<String, Boolean>) {
        if (allGranted) {
            startCamera()
        } else {
            Toast.makeText(requireContext(), "Camera and storage permissions are required.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        imageAnalysis?.clearAnalyzer()
        detector.close()
        cameraExecutor.shutdown()
        _binding = null
    }
}
