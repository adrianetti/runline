package com.runline.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.createBitmap

internal object MapMarks {
    private const val SIDE_DP = 14
    private const val RIM_DP = 2
    private const val CORNER_DP = 2

    fun finish(context: Context): Bitmap {
        val side = UiText.dp(context, SIDE_DP)
        val rim = UiText.dp(context, RIM_DP).toFloat()
        val corner = UiText.dp(context, CORNER_DP).toFloat()
        val bitmap = createBitmap(side, side)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        canvas.drawRoundRect(0f, 0f, side.toFloat(), side.toFloat(), corner + rim, corner + rim, paint)
        paint.color = Color.BLACK
        canvas.drawRoundRect(rim, rim, side - rim, side - rim, corner, corner, paint)
        return bitmap
    }
}
