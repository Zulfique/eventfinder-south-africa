package com.eventfinder.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Encodes [value] as a black-on-white QR bitmap using ZXing only — the
 * attendee ticket QR is generated completely offline, so it renders in
 * airplane mode at the gate.
 *
 * Returns null for a blank value or an encoder failure (never throws out of
 * composition).
 */
@Composable
fun rememberQrBitmap(value: String, pixels: Int = 512): Bitmap? =
    remember(value, pixels) {
        if (value.isBlank()) return@remember null
        runCatching {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, pixels, pixels, hints)
            val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
            for (y in 0 until matrix.height) {
                for (x in 0 until matrix.width) {
                    bitmap.setPixel(
                        x, y,
                        if (matrix[x, y]) android.graphics.Color.BLACK
                        else android.graphics.Color.WHITE
                    )
                }
            }
            bitmap
        }.getOrNull()
    }

/** Renders [value] as a QR code image with a meaningful content description. */
@Composable
fun QrImage(
    value: String,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val bitmap = rememberQrBitmap(value) ?: return
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier
    )
}
