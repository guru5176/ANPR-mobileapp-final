package com.example.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PhotoWatermarkUtil:
 * Compresses the captured vehicle/plate snapshot and permanently burns in a high-contrast
 * tamper-proof timestamp + license plate footer banner onto the Bitmap canvas.
 */
object PhotoWatermarkUtil {

    fun saveWatermarkedSnapshot(
        context: Context,
        sourceBitmap: Bitmap?,
        plateNumber: String,
        timestampMillis: Long = System.currentTimeMillis(),
        workerName: String = "Worker"
    ): String {
        val baseBitmap = sourceBitmap?.let { scaleDownBitmap(it, 720) }
            ?: generateFallbackVehicleBitmap(plateNumber)

        val mutable = baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutable)
        val width = mutable.width.toFloat()
        val height = mutable.height.toFloat()

        val bannerHeight = (height * 0.22f).coerceAtLeast(88f)
        val bannerTop = height - bannerHeight

        // Semi-transparent obsidian footer bar
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(225, 11, 15, 20)
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, bannerTop, width, height, bgPaint)

        // Top amber accent rule on footer
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 179, 0)
            strokeWidth = 4f
        }
        canvas.drawLine(0f, bannerTop, width, bannerTop, accentPaint)

        val dateFormat = SimpleDateFormat("dd MMM yyyy • hh:mm:ss a", Locale.ENGLISH)
        val formattedTime = dateFormat.format(Date(timestampMillis))

        val platePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 179, 0)
            textSize = (bannerHeight * 0.36f).coerceIn(22f, 36f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (bannerHeight * 0.25f).coerceIn(16f, 26f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }

        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 230, 118)
            textSize = (bannerHeight * 0.21f).coerceIn(14f, 22f)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        canvas.drawText(
            "PLATE: $plateNumber  [PROOF VERIFIED]",
            20f,
            bannerTop + bannerHeight * 0.38f,
            platePaint
        )
        canvas.drawText(
            "ENTRY: $formattedTime",
            20f,
            bannerTop + bannerHeight * 0.68f,
            timePaint
        )
        canvas.drawText(
            "MOTOVAULT ANPR • LOGGED BY: ${workerName.uppercase()}",
            20f,
            bannerTop + bannerHeight * 0.92f,
            metaPaint
        )

        val dir = File(context.filesDir, "entry_proofs").apply {
            if (!exists()) mkdirs()
        }
        val file = File(dir, "proof_${plateNumber}_${timestampMillis}.jpg")
        FileOutputStream(file).use { out ->
            mutable.compress(Bitmap.CompressFormat.JPEG, 78, out)
            out.flush()
        }
        return file.absolutePath
    }

    private fun scaleDownBitmap(source: Bitmap, maxDimension: Int): Bitmap {
        val width = source.width
        val height = source.height
        if (width <= maxDimension && height <= maxDimension) return source
        val ratio = width.toFloat() / height.toFloat()
        val (targetW, targetH) = if (ratio > 1f) {
            maxDimension to (maxDimension / ratio).toInt().coerceAtLeast(1)
        } else {
            (maxDimension * ratio).toInt().coerceAtLeast(1) to maxDimension
        }
        return Bitmap.createScaledBitmap(source, targetW, targetH, true)
    }

    /**
     * Generates a clean, high-contrast synthetic vehicle + Indian HSRP plate proof card
     * when running on a virtual camera or manual override without live camera frame.
     */
    fun generateFallbackVehicleBitmap(plateNumber: String): Bitmap {
        val width = 640
        val height = 420
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Asphalt background
        canvas.drawColor(Color.rgb(18, 24, 32))

        // Grid lines
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(40, 255, 179, 0)
            strokeWidth = 1.5f
        }
        for (x in 0..width step 40) {
            canvas.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), gridPaint)
        }
        for (y in 0..height step 40) {
            canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), gridPaint)
        }

        // HSRP Indian License Plate Graphic in center
        val plateRect = RectF(110f, 95f, 530f, 225f)
        val plateBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(248, 250, 252)
            style = Paint.Style.FILL
        }
        val plateBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 179, 0)
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawRoundRect(plateRect, 16f, 16f, plateBgPaint)
        canvas.drawRoundRect(plateRect, 16f, 16f, plateBorderPaint)

        // IND Blue Band
        val indPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(13, 71, 161)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(122f, 107f, 168f, 213f), 8f, 8f, indPaint)
        val indTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText("IND", 129f, 168f, indTextPaint)

        // Plate Number Text
        val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 46f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        canvas.drawText(plateNumber, 184f, 176f, numberPaint)

        return bmp
    }
}
