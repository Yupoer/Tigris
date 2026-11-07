package com.leslie.tvhome

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.LinearLayout
import android.view.View
import android.view.Gravity

class WeatherWidget(ctx: Context) : LinearLayout(ctx) {
    private val city = ctx.text(12f, weight = 600).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
    private val temperature = ctx.text(26f, weight = 300)
    private val condition = ctx.text(11f, Color.argb(225, 255, 255, 255), weight = 400).apply { maxLines = 1 }
    private val range = ctx.text(10f, Color.argb(185, 255, 255, 255), weight = 400).apply { maxLines = 1 }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        setPadding(dp(12), dp(9), dp(12), dp(9))
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.argb(78, 25, 35, 48), Color.argb(60, 20, 25, 32))).apply {
            cornerRadius = dp(15).toFloat()
            setStroke(dp(0.5), Color.argb(32, 255, 255, 255))
        }
        addView(temperature, LayoutParams(-2, -2).apply { marginEnd = dp(9) })
        addView(LinearLayout(ctx).apply {
            orientation = VERTICAL
            addView(city, LayoutParams(-1, -2))
            addView(condition, LayoutParams(-1, -2).apply { topMargin = dp(2) })
            addView(range, LayoutParams(-1, -2).apply { topMargin = dp(2) })
        }, LayoutParams(0, -2, 1f))
    }

    fun pending(label: String, automatic: Boolean) {
        city.text = label
        temperature.text = "—°"
        condition.text = I18n.text(if (automatic) R.string.network_weather_locating else R.string.weather_weather_pending)
        range.text = ""
        contentDescription = "$label ${condition.text}"
    }

    fun unavailable() {
        city.text = I18n.text(R.string.weather_settings)
        temperature.text = "—°"
        condition.text = I18n.text(R.string.weather_weather_unavailable)
        range.text = ""
        contentDescription = condition.text
    }

    fun show(reading: Weather.Reading, fahrenheit: Boolean) {
        fun degrees(c: Int): Int = if (fahrenheit) kotlin.math.round(c * 9.0 / 5.0 + 32).toInt() else c
        city.text = reading.city
        temperature.text = "${degrees(reading.temperature)}°"
        condition.text = "${Weather.glyph(reading.code)} ${Weather.label(reading.code)}"
        range.text = listOfNotNull(
            reading.high?.let { I18n.text(R.string.home_weather_high, degrees(it)) },
            reading.low?.let { I18n.text(R.string.home_weather_low, degrees(it)) },
        ).joinToString(" ")
        contentDescription = "${city.text} ${temperature.text} ${condition.text} ${range.text}"
    }
}
