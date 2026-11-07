package com.leslie.tvhome

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow

class AppContextMenu(
    private val host: View,
    private val anchor: View,
    private val actions: List<Action>,
    private val dismissed: () -> Unit,
) {
    data class Action(val label: String, val destructive: Boolean = false, val run: () -> Unit)

    private val ctx = host.context
    private val body = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(6), dp(6), dp(6), dp(6))
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.argb(247, 56, 60, 68), Color.argb(247, 27, 30, 36))).apply {
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), Color.argb(78, 255, 255, 255))
        }
    }
    private val popup = PopupWindow(body, ctx.dp(178), -2, true).apply {
        setBackgroundDrawable(rounded(Color.TRANSPARENT, ctx.dp(18).toFloat()))
        isOutsideTouchable = true
        elevation = ctx.dp(12).toFloat()
        setOnDismissListener { dismissed() }
    }

    fun show() {
        actions.forEachIndexed { index, action ->
            val row = ctx.text(16f, weight = 600).apply {
                text = action.label
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), 0, dp(14), 0)
                isFocusable = true
                isFocusableInTouchMode = true
                isSoundEffectsEnabled = false
                fun paint(focused: Boolean) {
                    setTextColor(if (action.destructive) Color.rgb(255, 69, 58)
                        else if (focused) Color.rgb(27, 30, 36) else Color.WHITE)
                    background = rounded(if (focused) Color.argb(232, 255, 255, 255) else Color.TRANSPARENT,
                        dp(12).toFloat())
                }
                paint(false)
                setOnFocusChangeListener { _, focused -> paint(focused) }
                setOnClickListener { popup.dismiss(); action.run() }
                setOnKeyListener { _, code, event ->
                    if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_MENU) {
                        if (event.action == KeyEvent.ACTION_UP) popup.dismiss()
                        true
                    } else if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT ||
                        (index == 0 && code == KeyEvent.KEYCODE_DPAD_UP) ||
                        (index == actions.lastIndex && code == KeyEvent.KEYCODE_DPAD_DOWN)) true
                    else false
                }
            }
            body.addView(row, LinearLayout.LayoutParams(-1, ctx.dp(45)))
        }
        val at = IntArray(2).also(anchor::getLocationOnScreen)
        val hostAt = IntArray(2).also(host::getLocationOnScreen)
        body.measure(View.MeasureSpec.makeMeasureSpec(ctx.dp(178), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val margin = ctx.dp(16)
        val right = at[0] + anchor.width + ctx.dp(12)
        val x = (if (right + ctx.dp(178) < hostAt[0] + host.width - margin) right
            else at[0] - ctx.dp(178) - ctx.dp(12))
            .coerceIn(hostAt[0] + margin, hostAt[0] + host.width - ctx.dp(178) - margin)
        val y = (at[1] + anchor.height / 2 - body.measuredHeight / 2)
            .coerceIn(hostAt[1] + margin, hostAt[1] + host.height - body.measuredHeight - margin)
        popup.showAtLocation(host, Gravity.TOP or Gravity.LEFT, x, y)
        body.getChildAt(0)?.requestFocus()
    }

    fun dismiss() = popup.dismiss()
}
