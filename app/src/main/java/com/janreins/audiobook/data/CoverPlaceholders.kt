package com.janreins.audiobook.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.util.Locale

object CoverPlaceholders {
    val PALETTE = intArrayOf(
        0xFF315B70.toInt(), 0xFF665080.toInt(), 0xFF46705A.toInt(), 0xFF8A4F50.toInt(),
        0xFF79602D.toInt(), 0xFF405F91.toInt(), 0xFF79516C.toInt(), 0xFF53676B.toInt()
    )

    fun initials(title: String): String = title.trim().split(Regex("\\s+"))
        .filter { it.firstOrNull()?.isLetterOrDigit() == true }
        .take(2).joinToString("") { it.take(1) }.uppercase(Locale.ROOT).ifEmpty { "?" }

    fun colorIndex(bookId: String, paletteSize: Int): Int {
        require(paletteSize > 0)
        return Math.floorMod(bookId.hashCode(), paletteSize)
    }

    fun placeholderBitmap(title: String, bookId: String, sizePx: Int): Bitmap {
        require(sizePx > 0)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(PALETTE[colorIndex(bookId, PALETTE.size)])
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = sizePx * 0.34f
            textAlign = Paint.Align.CENTER
        }
        val metrics = paint.fontMetrics
        canvas.drawText(initials(title), sizePx / 2f, sizePx / 2f - (metrics.ascent + metrics.descent) / 2f, paint)
        return bitmap
    }
}
