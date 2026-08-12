package com.example.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.io.FileOutputStream

object QrCodeGenerator {
    private const val TAG = "QrCodeGenerator"

    /**
     * Generates a raw QR Code bitmap from a UPI URI or any string content.
     */
    fun generateQrBitmap(
        content: String,
        size: Int = 512,
        foregroundColor: Int = Color.BLACK,
        backgroundColor: Int = Color.WHITE
    ): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val hints = mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
            )
            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) foregroundColor else backgroundColor
                }
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error generating QR bitmap: ${e.localizedMessage}")
            null
        }
    }

    /**
     * Generates a styled branded QR Card Bitmap containing Agent Name, UPI ID, Amount, Policy & Customer info.
     * Perfect for downloading and sharing as a clean PNG image.
     */
    fun createBrandedQrCardBitmap(
        accountHolderName: String,
        upiId: String,
        amount: String,
        policyNumber: String? = null,
        customerName: String? = null
    ): Bitmap {
        val width = 640
        val height = 820
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Dark background canvas
        val bgPaint = Paint().apply {
            color = Color.parseColor("#0F172A") // Dark Navy
            isAntiAlias = true
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Header Card Container
        val headerPaint = Paint().apply {
            color = Color.parseColor("#1E293B")
            isAntiAlias = true
        }
        val headerRect = RectF(30f, 30f, (width - 30).toFloat(), 150f)
        canvas.drawRoundRect(headerRect, 24f, 24f, headerPaint)

        // Header Title
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 30f
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("LIC PREMIUM PAYMENT QR", (width / 2).toFloat(), 80f, titlePaint)

        // Subtitle Account Holder Name
        val subTitlePaint = Paint().apply {
            color = Color.parseColor("#38BDF8") // Sky Blue
            textSize = 22f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Agent: $accountHolderName", (width / 2).toFloat(), 122f, subTitlePaint)

        // QR Code Box
        val pNo = if (!policyNumber.isNullOrBlank()) policyNumber else "LIC-POL"
        val cleanAmount = amount.ifEmpty { "0" }
        val upiUri = "upi://pay?pa=$upiId&pn=${Uri.encode(accountHolderName)}&am=$cleanAmount&tn=${Uri.encode("LIC Policy $pNo")}&cu=INR"
        val rawQr = generateQrBitmap(upiUri, size = 420)

        val qrContainerRect = RectF(100f, 175f, 540f, 615f)
        val qrBgPaint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
        }
        canvas.drawRoundRect(qrContainerRect, 28f, 28f, qrBgPaint)

        if (rawQr != null) {
            canvas.drawBitmap(rawQr, null, RectF(120f, 195f, 520f, 595f), Paint(Paint.FILTER_BITMAP_FLAG))
        }

        // Details Container Box
        val infoPaint = Paint().apply {
            color = Color.parseColor("#1E293B")
            isAntiAlias = true
        }
        val infoRect = RectF(30f, 640f, (width - 30).toFloat(), 790f)
        canvas.drawRoundRect(infoRect, 24f, 24f, infoPaint)

        // Amount Text
        val amountPaint = Paint().apply {
            color = Color.parseColor("#22C55E") // Emerald Green
            textSize = 36f
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Payment Amount: ₹$cleanAmount", (width / 2).toFloat(), 690f, amountPaint)

        // Info Text
        val infoTextPaint = Paint().apply {
            color = Color.parseColor("#94A3B8") // Muted Slate
            textSize = 21f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val customerInfo = if (!customerName.isNullOrBlank()) "Customer: $customerName • Policy: $pNo" else "Policy No: $pNo"
        canvas.drawText(customerInfo, (width / 2).toFloat(), 732f, infoTextPaint)

        val vpaPaint = Paint().apply {
            color = Color.parseColor("#F97316") // Orange
            textSize = 21f
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("UPI VPA: $upiId", (width / 2).toFloat(), 766f, vpaPaint)

        return bitmap
    }

    /**
     * Saves the QR Bitmap into device Downloads or Pictures folder and returns Uri.
     */
    fun saveQrBitmapToGallery(context: Context, bitmap: Bitmap, fileName: String = "LIC_Payment_QR"): Uri? {
        val filename = "${fileName}_${System.currentTimeMillis()}.png"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/LIC_QR")
                }
                val resolver = context.contentResolver
                val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (imageUri != null) {
                    resolver.openOutputStream(imageUri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                }
                imageUri
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val licDir = File(imagesDir, "LIC_QR")
                if (!licDir.exists()) licDir.mkdirs()
                val imageFile = File(licDir, filename)
                FileOutputStream(imageFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                Uri.fromFile(imageFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving QR to gallery: ${e.localizedMessage}")
            null
        }
    }

    /**
     * Saves QR Bitmap to cache directory for sharing via Android FileProvider Intent.
     */
    fun saveQrBitmapToCache(context: Context, bitmap: Bitmap): Uri? {
        return try {
            val cachePath = File(context.cacheDir, "images")
            if (!cachePath.exists()) cachePath.mkdirs()
            val imageFile = File(cachePath, "shared_payment_qr.png")
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error saving QR to cache: ${e.localizedMessage}")
            null
        }
    }

    /**
     * Helper to validate standard UPI VPA ID format (e.g., name@oksbi, name@ybl).
     */
    fun isValidUpiId(upiId: String): Boolean {
        val trimmed = upiId.trim()
        if (trimmed.isEmpty() || !trimmed.contains("@")) return false
        val parts = trimmed.split("@")
        if (parts.size != 2) return false
        val username = parts[0]
        val bank = parts[1]
        if (username.length < 2 || bank.length < 2) return false
        val validUser = username.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }
        val validBank = bank.all { it.isLetterOrDigit() }
        return validUser && validBank
    }
}
