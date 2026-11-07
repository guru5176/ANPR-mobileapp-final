package com.example.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.YuvImage
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.data.local.INDIAN_PLATE_REGEX
import com.example.data.repository.BillingRepository
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min
import org.tensorflow.lite.Interpreter

/**
 * Detected plate result from the TFLite + ML Kit ANPR pipeline.
 */
data class AnprDetectionResult(
    val normalizedPlate: String,
    val rawOcrText: String,
    val boundingBoxNormalized: RectF,
    val averageLuminosity: Double,
    val isTfliteModelActive: Boolean,
    val rtoDistrictHint: String,
    val frameSnapshot: Bitmap?
)

/**
 * YoloPlateDetector:
 * Loads a custom YOLOv8 `.tflite` license plate detector (`yolov8_plate.tflite`) from `assets/`
 * using TensorFlow Lite Interpreter with NNAPI acceleration.
 * If no `.tflite` asset is bundled yet, gracefully falls back to a targeted plate ROI bounding box
 * so ML Kit Text Recognition v2 operates continuously without crashing.
 */
class YoloPlateDetector(context: Context) {
    private var interpreter: Interpreter? = null
    val isModelLoaded: Boolean

    init {
        var loaded = false
        try {
            val assetList = context.assets.list("")?.toList().orEmpty()
            val modelName = assetList.firstOrNull { it.endsWith(".tflite", ignoreCase = true) }
                ?: "yolov8_plate.tflite"
            if (assetList.contains(modelName)) {
                val afd = context.assets.openFd(modelName)
                val inputStream = FileInputStream(afd.fileDescriptor)
                val modelBuffer = inputStream.channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    afd.startOffset,
                    afd.declaredLength
                )
                val options = Interpreter.Options().apply {
                    setNumThreads(4)
                    setUseNNAPI(true)
                }
                interpreter = Interpreter(modelBuffer, options)
                loaded = true
                Log.d("YoloPlateDetector", "Loaded custom YOLOv8 TFLite model: $modelName")
            }
        } catch (e: Exception) {
            Log.i("YoloPlateDetector", "Custom .tflite not found in assets; using smart ROI plate cropper.")
        }
        isModelLoaded = loaded
    }

    /**
     * Detects the normalized [0..1] license plate bounding box inside the camera frame.
     */
    fun detectPlateBoundingBox(frameBitmap: Bitmap): RectF {
        val tflite = interpreter
        if (tflite != null) {
            try {
                val inputSize = 320
                val resized = Bitmap.createScaledBitmap(frameBitmap, inputSize, inputSize, true)
                val inputBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4).apply {
                    order(ByteOrder.nativeOrder())
                }
                val pixels = IntArray(inputSize * inputSize)
                resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
                for (pixel in pixels) {
                    inputBuffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f)
                    inputBuffer.putFloat(((pixel shr 8) and 0xFF) / 255.0f)
                    inputBuffer.putFloat((pixel and 0xFF) / 255.0f)
                }
                inputBuffer.rewind()

                // Standard single-box detection output [1, 5] -> [cx, cy, w, h, conf]
                val output = Array(1) { FloatArray(5) }
                tflite.run(inputBuffer, output)
                val det = output[0]
                if (det[4] > 0.35f) {
                    val cx = det[0].coerceIn(0.15f, 0.85f)
                    val cy = det[1].coerceIn(0.15f, 0.85f)
                    val w = det[2].coerceIn(0.25f, 0.85f)
                    val h = det[3].coerceIn(0.12f, 0.45f)
                    return RectF(
                        (cx - w / 2f).coerceIn(0.05f, 0.9f),
                        (cy - h / 2f).coerceIn(0.05f, 0.9f),
                        (cx + w / 2f).coerceIn(0.1f, 0.95f),
                        (cy + h / 2f).coerceIn(0.1f, 0.95f)
                    )
                }
            } catch (e: Exception) {
                Log.w("YoloPlateDetector", "TFLite inference fallback to ROI", e)
            }
        }
        // Default high-accuracy reticle region for two-wheeler front/rear number plates
        return RectF(0.10f, 0.32f, 0.90f, 0.68f)
    }

    fun close() {
        interpreter?.close()
    }
}

/**
 * AnprFrameAnalyzer:
 * 1. Computes average pixel luminosity from Y-plane of YUV_420_888.
 * 2. Triggers automatic Torch in low light when luminosity < lowLightThreshold.
 * 3. Crops plate region via YoloPlateDetector and runs Google ML Kit Text Recognition v2 (Offline).
 * 4. Validates against Indian License Plate Regex (^[A-Z]{2}[0-9]{1,2}[A-Z]{1,3}[0-9]{1,4}$).
 */
class AnprFrameAnalyzer(
    context: Context,
    private val getLowLightThreshold: () -> Double,
    private val onLuminosityMeasured: (averageLuminosity: Double, shouldAutoEnableTorch: Boolean) -> Unit,
    private val onPlateDetected: (AnprDetectionResult) -> Unit
) : ImageAnalysis.Analyzer {

    private val yoloDetector = YoloPlateDetector(context.applicationContext)
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val isProcessingOcr = AtomicBoolean(false)
    private var lastOcrTimestamp = 0L

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        try {
            // 1. Calculate Average Luminosity from Y-plane of YUV_420_888
            val yPlane = imageProxy.planes.firstOrNull()
            if (yPlane == null) {
                imageProxy.close()
                return
            }
            val averageLuma = computeAverageLuminosity(yPlane.buffer)
            val threshold = getLowLightThreshold()
            onLuminosityMeasured(averageLuma, averageLuma < threshold)

            // Throttle OCR to ~3 frames per second for smooth viewfinder performance
            val now = System.currentTimeMillis()
            if (now - lastOcrTimestamp < 320L || !isProcessingOcr.compareAndSet(false, true)) {
                imageProxy.close()
                return
            }
            lastOcrTimestamp = now

            val frameBitmap = imageProxyToBitmap(imageProxy)
            if (frameBitmap == null) {
                isProcessingOcr.set(false)
                imageProxy.close()
                return
            }

            // 2. Detect plate bounding box via TFLite YOLOv8 hook / ROI
            val normBox = yoloDetector.detectPlateBoundingBox(frameBitmap)
            val croppedPlate = cropBitmap(frameBitmap, normBox)

            // 3. Pass cropped bitmap to Google ML Kit Text Recognition v2 (Offline)
            val inputImage = InputImage.fromBitmap(croppedPlate, 0)
            textRecognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val candidate = extractValidIndianPlate(visionText.text)
                    if (candidate != null) {
                        onPlateDetected(
                            AnprDetectionResult(
                                normalizedPlate = candidate,
                                rawOcrText = visionText.text.replace("\n", " ").trim(),
                                boundingBoxNormalized = normBox,
                                averageLuminosity = averageLuma,
                                isTfliteModelActive = yoloDetector.isModelLoaded,
                                rtoDistrictHint = lookupRtoDistrict(candidate),
                                frameSnapshot = frameBitmap
                            )
                        )
                    }
                }
                .addOnCompleteListener {
                    isProcessingOcr.set(false)
                    imageProxy.close()
                }
        } catch (e: Exception) {
            isProcessingOcr.set(false)
            imageProxy.close()
        }
    }

    private fun computeAverageLuminosity(buffer: ByteBuffer): Double {
        buffer.rewind()
        val limit = buffer.remaining()
        if (limit <= 0) return 128.0
        var sum = 0L
        var count = 0
        // Sample every 8th pixel for sub-millisecond performance
        var idx = 0
        while (idx < limit) {
            val pixel = buffer.get(idx).toInt() and 0xFF
            sum += pixel
            count++
            idx += 8
        }
        return if (count > 0) sum.toDouble() / count.toDouble() else 128.0
    }

    private fun cropBitmap(source: Bitmap, normRect: RectF): Bitmap {
        val left = (normRect.left * source.width).toInt().coerceIn(0, max(0, source.width - 10))
        val top = (normRect.top * source.height).toInt().coerceIn(0, max(0, source.height - 10))
        val right = (normRect.right * source.width).toInt().coerceIn(left + 10, source.width)
        val bottom = (normRect.bottom * source.height).toInt().coerceIn(top + 10, source.height)
        return Bitmap.createBitmap(source, left, top, right - left, bottom - top)
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val yBuffer = imageProxy.planes[0].buffer
            val uBuffer = imageProxy.planes[1].buffer
            val vBuffer = imageProxy.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 75, out)
            val imageBytes = out.toByteArray()
            val rawBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return null

            val rotation = imageProxy.imageInfo.rotationDegrees
            if (rotation != 0) {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
            } else {
                rawBitmap
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        /**
         * Strips special characters and noise like "IND", normalizes OCR confusions,
         * and validates against the strict Indian License Plate Regex:
         * ^[A-Z]{2}[0-9]{1,2}[A-Z]{1,3}[0-9]{1,4}$
         */
        fun extractValidIndianPlate(rawText: String): String? {
            if (rawText.isBlank()) return null
            val lines = rawText.uppercase()
                .replace("IND", "")
                .split(Regex("[\\r\\n]+"))
                .map { it.replace(Regex("[^A-Z0-9]"), "") }
                .filter { it.isNotBlank() }

            // Check individual lines first
            for (line in lines) {
                val normalized = BillingRepository.normalizePlateNumber(line)
                if (INDIAN_PLATE_REGEX.matches(normalized)) {
                    return normalized
                }
            }

            // Two-wheeler rear plates are often 2 lines (e.g. "TN39AB" on line 1, "1234" on line 2)
            for (i in 0 until lines.size - 1) {
                val combined = BillingRepository.normalizePlateNumber(lines[i] + lines[i + 1])
                if (INDIAN_PLATE_REGEX.matches(combined)) {
                    return combined
                }
            }

            val fullCombined = BillingRepository.normalizePlateNumber(lines.joinToString(""))
            if (INDIAN_PLATE_REGEX.matches(fullCombined)) {
                return fullCombined
            }
            return null
        }

        fun lookupRtoDistrict(plate: String): String {
            val prefix = plate.uppercase().take(4)
            return when {
                prefix.startsWith("TN39") -> "Tiruppur North RTO"
                prefix.startsWith("TN42") -> "Tiruppur South RTO"
                prefix.startsWith("TN33") -> "Erode East RTO"
                prefix.startsWith("TN37") -> "Coimbatore South RTO"
                prefix.startsWith("TN38") -> "Coimbatore North RTO"
                prefix.startsWith("TN66") -> "Coimbatore Central RTO"
                prefix.startsWith("TN86") -> "Erode West RTO"
                prefix.startsWith("TN78") -> "Dharapuram / Udumalpet RTO"
                prefix.startsWith("TN") -> "Tamil Nadu Transport"
                prefix.startsWith("KA") -> "Karnataka Transport"
                prefix.startsWith("KL") -> "Kerala Transport"
                prefix.startsWith("AP") || prefix.startsWith("TS") -> "AP / Telangana RTO"
                else -> "Indian Standard HSRP"
            }
        }
    }
}
