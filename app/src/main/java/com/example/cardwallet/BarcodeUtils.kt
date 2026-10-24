package com.example.cardwallet

import android.graphics.Bitmap
import android.graphics.Color
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter

object BarcodeUtils {

    // Превращает код формата из ML Kit в формат для ZXing
    private fun mapFormat(mlKitFormat: Int): BarcodeFormat {
        return when (mlKitFormat) {
            Barcode.FORMAT_QR_CODE -> BarcodeFormat.QR_CODE
            Barcode.FORMAT_EAN_13 -> BarcodeFormat.EAN_13
            Barcode.FORMAT_EAN_8 -> BarcodeFormat.EAN_8
            Barcode.FORMAT_CODE_128 -> BarcodeFormat.CODE_128
            Barcode.FORMAT_CODE_39 -> BarcodeFormat.CODE_39
            Barcode.FORMAT_UPC_A -> BarcodeFormat.UPC_A
            Barcode.FORMAT_UPC_E -> BarcodeFormat.UPC_E
            else -> BarcodeFormat.CODE_128 // Запасной вариант по умолчанию
        }
    }

    // Создаёт изображение (Bitmap) штрих-кода из текста
    fun generateBarcodeBitmap(
        content: String,
        format: Int,
        width: Int = 800,
        height: Int = 300
    ): Bitmap? {
        return try {
            val zxingFormat = mapFormat(format)
            // Если это квадратный QR-код, делаем высоту равной ширине
            val actualHeight = if (zxingFormat == BarcodeFormat.QR_CODE) width else height

            val bitMatrix = MultiFormatWriter().encode(content, zxingFormat, width, actualHeight)
            val matrixWidth = bitMatrix.width
            val matrixHeight = bitMatrix.height
            val pixels = IntArray(matrixWidth * matrixHeight)

            for (y in 0 until matrixHeight) {
                val offset = y * matrixWidth
                for (x in 0 until matrixWidth) {
                    // Чёрная полоска или белая
                    pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }

            val bitmap = Bitmap.createBitmap(matrixWidth, matrixHeight, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, matrixWidth, 0, 0, matrixWidth, matrixHeight)
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}