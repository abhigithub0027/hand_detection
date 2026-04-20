package com.example.fingerdetector.vision

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.camera.core.ImageProxy
import com.example.fingerdetector.data.model.CameraTelemetry
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object ImageUtils {

    fun bitmapFromRgbaImageProxy(imageProxy: ImageProxy, mirror: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(imageProxy.planes[0].buffer)
        return rotateBitmap(bitmap, imageProxy.imageInfo.rotationDegrees.toFloat(), mirror)
    }

    fun rotateBitmap(source: Bitmap, degrees: Float, mirror: Boolean): Bitmap {
        val matrix = Matrix().apply {
            postRotate(degrees)
            if (mirror) {
                postScale(-1f, 1f, source.width / 2f, source.height / 2f)
            }
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    fun decodeBitmapWithExif(file: File): Bitmap? {
        val rawBitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val orientation = ExifInterface(file.absolutePath)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        return if (degrees == 0f) rawBitmap else rotateBitmap(rawBitmap, degrees, false)
    }

    fun computeBrightness(bitmap: Bitmap): Double {
        val scaled = Bitmap.createScaledBitmap(bitmap, 96, 96, true)
        val pixels = IntArray(scaled.width * scaled.height)
        scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        val average = pixels.map { color ->
            val r = color shr 16 and 0xFF
            val g = color shr 8 and 0xFF
            val b = color and 0xFF
            (0.299 * r) + (0.587 * g) + (0.114 * b)
        }.average()
        return average
    }

    fun computeBlurScore(bitmap: Bitmap): Double {
        val scaled = Bitmap.createScaledBitmap(bitmap, 128, 128, true)
        val width = scaled.width
        val height = scaled.height
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        val gray = IntArray(width * height) { index ->
            val color = pixels[index]
            val r = color shr 16 and 0xFF
            val g = color shr 8 and 0xFF
            val b = color and 0xFF
            (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        }

        val laplacian = mutableListOf<Int>()
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val center = gray[y * width + x]
                val value = (4 * center) -
                    gray[(y - 1) * width + x] -
                    gray[(y + 1) * width + x] -
                    gray[y * width + x - 1] -
                    gray[y * width + x + 1]
                laplacian += value
            }
        }
        val mean = laplacian.average()
        return laplacian.map { (it - mean) * (it - mean) }.average()
    }

    fun saveTempFileToFingerData(context: Context, sourceFile: File, displayName: String): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Finger Data")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { output ->
                sourceFile.inputStream().use { input -> input.copyTo(output) }
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } else {
            val directory = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "Finger Data"
            ).apply { mkdirs() }
            val destination = File(directory, displayName)
            sourceFile.copyTo(destination, overwrite = true)
            Uri.fromFile(destination)
        }
    }

    fun appendTelemetryLog(
        context: Context,
        stageLabel: String,
        fileName: String,
        handLabel: String,
        fingerLabel: String,
        telemetry: CameraTelemetry
    ) {
        val logText = buildString {
            appendLine("stage=$stageLabel")
            appendLine("file=$fileName")
            appendLine("hand=$handLabel")
            appendLine("finger=$fingerLabel")
            appendLine("deviceId=${telemetry.deviceId}")
            appendLine("brightness=${"%.2f".format(Locale.US, telemetry.brightnessScore)}")
            appendLine("lighting=${telemetry.lightingCondition}")
            appendLine("camera=${telemetry.cameraType}")
            appendLine("focalLength=${telemetry.focalLength ?: "n/a"}")
            appendLine("aperture=${telemetry.aperture ?: "n/a"}")
            appendLine("focusDistance=${telemetry.focusDistance ?: "n/a"}")
            appendLine("blurScore=${"%.2f".format(Locale.US, telemetry.blurScore)}")
            appendLine("timestamp=${timestamp()}")
            appendLine("---")
        }
        val file = File(context.getExternalFilesDir(null), "device_${telemetry.deviceId}_telemetry_log.txt")
        file.parentFile?.mkdirs()
        FileOutputStream(file, true).use { output ->
            output.write(logText.toByteArray())
        }
    }

    fun deviceId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"

    fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())

    fun blurThresholdViolated(blurScore: Double): Boolean = blurScore < 45.0

    fun classifyLighting(brightnessScore: Double) = when {
        brightnessScore < 80 -> com.example.fingerdetector.data.model.LightingCondition.LOW
        brightnessScore > 170 -> com.example.fingerdetector.data.model.LightingCondition.BRIGHT
        else -> com.example.fingerdetector.data.model.LightingCondition.NORMAL
    }

    fun isNearlySame(a: Double, b: Double): Boolean = abs(a - b) < 0.001
}
