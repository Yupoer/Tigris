package com.leslie.tvhome

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import kotlin.math.max
import kotlin.math.min

/** A moving glass lens over app artwork. Canvas shaders also work on Android 9–12 TVs. */
class TileGlass(ctx: Context) : View(ctx) {
    private val density = resources.displayMetrics.density
    private val radius = dp(11).toFloat()
    private val face = RectF()
    private val rim = Path().apply { fillType = Path.FillType.EVEN_ODD }
    private val outline = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val matrix = Matrix()
    private val lightMatrix = Matrix()
    private var source: Bitmap? = null
    private var refraction: BitmapShader? = null
    private var edge: LinearGradient? = null
    private var light: RadialGradient? = null
    private var wash: LinearGradient? = null

    var offsetX = 0f
        private set
    var offsetY = 0f
        private set
    var energy = 0f
        private set

    init { isFocusable = false; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    fun artwork(bitmap: Bitmap?) {
        if (source === bitmap) return
        source = bitmap
        refraction = bitmap?.let { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        invalidate()
    }

    fun motion(x: Float, y: Float, amount: Float) {
        offsetX = x
        offsetY = y
        energy = amount.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w <= 0 || h <= 0) return
        face.set(0f, 0f, w.toFloat(), h.toFloat())
        val band = min(h * 0.15f, radius * 1.1f)
        rim.rewind()
        rim.addRoundRect(face, radius, radius, Path.Direction.CW)
        outline.set(face)
        outline.inset(band, band)
        rim.addRoundRect(outline, max(0f, radius - band), max(0f, radius - band), Path.Direction.CW)
        val strokeInset = density
        outline.set(strokeInset, strokeInset, w - strokeInset, h - strokeInset)
        edge = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), intArrayOf(
            Color.argb(220, 255, 255, 255), Color.argb(35, 230, 245, 255),
            Color.argb(22, 255, 255, 255), Color.argb(145, 220, 240, 255),
        ), floatArrayOf(0f, 0.36f, 0.62f, 1f), Shader.TileMode.CLAMP)
        light = RadialGradient(0f, 0f, max(w * 0.78f, 1f), intArrayOf(
            Color.argb(62, 255, 255, 255), Color.argb(15, 235, 246, 255), Color.TRANSPARENT,
        ), floatArrayOf(0f, 0.42f, 1f), Shader.TileMode.CLAMP)
        wash = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(
            Color.argb(16, 255, 255, 255), Color.argb(2, 255, 255, 255), Color.argb(18, 20, 35, 55),
        ), null, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        // Only the rounded edge refracts; app names and logos in the centre stay readable.
        source?.let { bitmap ->
            val scale = max(width / bitmap.width.toFloat(), height / bitmap.height.toFloat()) * (1.07f + energy * 0.045f)
            matrix.setScale(scale, scale)
            matrix.postTranslate((width - bitmap.width * scale) / 2f + offsetX * width * 0.025f,
                (height - bitmap.height * scale) / 2f + offsetY * height * 0.035f)
            refraction?.setLocalMatrix(matrix)
            paint.shader = refraction
            paint.alpha = 150
            canvas.drawPath(rim, paint)
        }
        paint.alpha = 255
        paint.shader = wash
        canvas.drawRoundRect(face, radius, radius, paint)

        // Light follows the input direction and flows back to the upper-left as the lens settles.
        lightMatrix.setTranslate(width * (0.32f + offsetX * 0.65f), height * (0.02f + offsetY * 0.65f))
        light?.setLocalMatrix(lightMatrix)
        paint.shader = light
        canvas.drawRoundRect(face, radius, radius, paint)

        lightMatrix.setRotate(offsetX * 18f + offsetY * 12f, width / 2f, height / 2f)
        edge?.setLocalMatrix(lightMatrix)
        paint.shader = edge
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = density * (1.1f + energy * 0.65f)
        canvas.drawRoundRect(outline, radius - density, radius - density, paint)
        paint.shader = null
    }
}
