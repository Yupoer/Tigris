package com.leslie.tvhome

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executors

/** One-time phone-assisted pairing; the pairing dialog must remain visible on the TV. */
class LocalAdbSetupActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var qr: ImageView
    private lateinit var status: TextView

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        qr = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        status = text(17f, Color.WHITE).apply { gravity = Gravity.CENTER }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(100), dp(35), dp(100), dp(35))
            addView(text(30f, bold = true).apply { text = I18n.text(R.string.local_adb_title) })
            addView(text(17f).apply {
                text = I18n.text(R.string.local_adb_instructions)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
            addView(qr, LinearLayout.LayoutParams(dp(235), dp(235)).apply { topMargin = dp(20) })
            addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            addView(button(R.string.local_adb_open_settings) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            }, LinearLayout.LayoutParams(dp(350), dp(54)).apply { topMargin = dp(20) })
            addView(button(R.string.local_adb_test) {
                status.text = I18n.text(R.string.local_adb_testing)
                worker.execute {
                    val ok = LocalAdb.shell(this@LocalAdbSetupActivity, "echo TIGRIS_ADB_OK", 8000)
                        ?.contains("TIGRIS_ADB_OK") == true
                    main.post {
                        if (!isDestroyed) status.text = I18n.text(if (ok)
                            R.string.local_adb_connected else R.string.local_adb_not_connected)
                    }
                }
            }, LinearLayout.LayoutParams(dp(350), dp(54)).apply { topMargin = dp(10) })
        }
        setContentView(body.apply { setBackgroundColor(Color.rgb(18, 23, 32)) })
    }

    private fun button(label: Int, action: () -> Unit): TextView = text(17f, Color.WHITE).apply {
        text = I18n.text(label)
        gravity = Gravity.CENTER
        background = rounded(Color.argb(80, 255, 255, 255), dp(18).toFloat())
        isFocusable = true
        isClickable = true
        setOnClickListener { action() }
    }

    override fun onStart() {
        super.onStart()
        stopLater.removeCallbacks(stop)
        status.text = I18n.text(R.string.local_adb_starting)
        worker.execute {
            val url = PhoneServer.start(this)?.plus("adb")
            val bitmap = url?.let(PhoneServer::qr)
            main.post {
                if (!isDestroyed) {
                    qr.setImageBitmap(bitmap)
                    status.text = url ?: I18n.text(R.string.local_adb_offline)
                }
            }
        }
    }

    override fun onStop() {
        // The phone must still reach the pairing form while Android Settings is in front.
        stopLater.postDelayed(stop, 10 * 60_000L)
        super.onStop()
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }

    companion object {
        private val stopLater = Handler(Looper.getMainLooper())
        private val stop = Runnable { Thread { PhoneServer.stop() }.start() }
    }
}
