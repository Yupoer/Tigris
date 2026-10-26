package com.leslie.tvhome

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.concurrent.Executors

/** Browse each cached picture inside its category; focus previews it as the full background. */
class WallpaperActivity : Activity() {
    private val prefs by lazy { Prefs(this) }
    private val main = Handler(Looper.getMainLooper())
    private val downloads = Executors.newSingleThreadExecutor()
    private val previews = Executors.newSingleThreadExecutor()
    private val rows = LinkedHashMap<String, LinearLayout>()
    private val cards = LinkedHashMap<String, Card>()
    private lateinit var backdrop: ImageView
    private lateinit var qr: ImageView
    private lateinit var address: TextView
    private var previewVersion = 0

    private inner class Card(
        val id: String, val source: String?, val photo: File?, val theme: String?,
        title: String, thumbnail: Bitmap?, action: () -> Unit,
    ) : LinearLayout(this@WallpaperActivity) {
        private val check = text(14f, Color.WHITE, bold = true).apply {
            text = "✓"
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(10, 132, 255), dp(11).toFloat())
        }
        init {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isFocusable = true
            isClickable = true
            clipChildren = false
            val frame = FrameLayout(context).apply { clipChildren = false }
            frame.addView(ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(thumbnail)
                background = rounded(Color.argb(45, 255, 255, 255), dp(12).toFloat())
                clipRounded(dp(12).toFloat())
            }, FrameLayout.LayoutParams(-1, -1))
            val ring = View(context)
            frame.addView(ring, FrameLayout.LayoutParams(-1, -1))
            frame.addView(check, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP or Gravity.END).apply {
                setMargins(0, dp(6), dp(6), 0)
            })
            addView(frame, LayoutParams(dp(174), dp(98)))
            addView(text(14f, Color.WHITE, weight = 500).apply {
                text = title
                gravity = Gravity.CENTER
                maxLines = 1
            }, LayoutParams(-1, -2).apply { topMargin = dp(7) })
            setOnFocusChangeListener { _, focused ->
                ring.background = if (focused) GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(3), Color.WHITE)
                } else null
                animate().scaleX(if (focused) 1.07f else 1f).scaleY(if (focused) 1.07f else 1f)
                    .translationZ(if (focused) dp(10).toFloat() else 0f)
                    .setDuration(160).setInterpolator(Motion.ease).start()
                if (focused) preview(photo, theme)
            }
            setOnClickListener { action() }
            selected()
        }
        fun selected() {
            check.visibility = if (theme == prefs.background ||
                (photo != null && source != null && source == prefs.background &&
                    Wallpapers(this@WallpaperActivity, source).current()?.name == photo.name)) VISIBLE else GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(15, 20, 30)) }
        backdrop = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        root.addView(backdrop, FrameLayout.LayoutParams(-1, -1))
        root.addView(View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(Color.argb(220, 0, 0, 0), Color.argb(100, 0, 0, 0)))
        }, FrameLayout.LayoutParams(-1, -1))

        val categories = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
        categories.addView(text(29f, bold = true).apply { text = I18n.text(R.string.i18n_wallpaperactivity_001) },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) })
        for (source in Wallpapers.SOURCES) {
            categories.addView(text(19f, bold = true).apply { text = Wallpapers.label(source) },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); bottomMargin = dp(9) })
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
            rows[source] = row
            categories.addView(HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                clipChildren = false
                addView(row)
            }, LinearLayout.LayoutParams(-1, dp(143)))
            renderSource(source)
        }
        categories.addView(text(19f, bold = true).apply { text = I18n.text(R.string.wallpaper_colors) },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); bottomMargin = dp(9) })
        val themes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
        for ((key, value) in Backgrounds.THEMES) {
            addCard(themes, Card("theme:" + key, null, null, key, value.first, Backgrounds.render(key)) {
                prefs.background = key
                updateChecks()
                preview(null, key)
            })
        }
        categories.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            addView(themes)
        }, LinearLayout.LayoutParams(-1, dp(143)))

        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            clipChildren = false
            addView(categories)
        }
        qr = ImageView(this).apply {
            background = rounded(Color.WHITE, dp(14).toFloat())
            setPadding(dp(12), dp(12), dp(12), dp(12))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        address = text(13f, Color.WHITE)
        val phone = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(18f, bold = true).apply { text = I18n.text(R.string.i18n_wallpaperactivity_003) })
            addView(qr, LinearLayout.LayoutParams(dp(180), dp(180)).apply { topMargin = dp(12) })
            addView(address, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        }
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            setPadding(dp(60), dp(36), dp(45), dp(28))
            addView(scroll, LinearLayout.LayoutParams(0, -1, 1f))
            addView(phone, LinearLayout.LayoutParams(dp(205), -2).apply { marginStart = dp(24) })
        }, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        (cards.values.firstOrNull { it.theme == prefs.background ||
            (it.source == prefs.background && it.photo?.name == Wallpapers(this, prefs.background).current()?.name) }
            ?: cards.values.firstOrNull())?.requestFocus()
    }

    private fun addCard(row: LinearLayout, card: Card) {
        cards[card.id] = card
        row.addView(card, LinearLayout.LayoutParams(dp(174), -2).apply { marginEnd = dp(14) })
    }

    private fun renderSource(source: String) {
        val row = rows[source] ?: return
        val files = Wallpapers(this, source).files()
        val stamp = files.joinToString("|") { it.name }
        if (row.tag == stamp) return
        val hadFocus = row.hasFocus()
        cards.keys.filter { it.startsWith("photo:" + source + ":") || it == "more:" + source }
            .forEach { cards.remove(it) }
        row.removeAllViews()
        row.tag = stamp
        files.forEachIndexed { index, file ->
            val thumb = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = 8 })
            addCard(row, Card("photo:" + source + ":" + file.name, source, file, null,
                I18n.text(R.string.wallpaper_photo_number, index + 1), thumb) {
                if (Wallpapers(this, source).select(file)) {
                    prefs.background = source
                    updateChecks()
                }
            })
        }
        if (source !in setOf(Wallpapers.MINE, Wallpapers.CUSTOM) && files.size < Wallpapers.MAX_FILES) {
            addCard(row, Card("more:" + source, source, null, null,
                I18n.text(R.string.wallpaper_download_more), null) {
                downloads.execute {
                    Wallpapers(this, source).prefetch((files.size + 4).coerceAtMost(Wallpapers.MAX_FILES))
                    main.post { if (!isDestroyed) renderSource(source) }
                }
            })
        } else if (files.isEmpty()) {
            addCard(row, Card("more:" + source, source, null, null,
                I18n.text(R.string.wallpaper_phone_upload), null) {
                Toast.makeText(this, I18n.text(R.string.i18n_wallpaperactivity_010), Toast.LENGTH_LONG).show()
            })
        }
        if (hadFocus) row.getChildAt(0)?.requestFocus()
    }

    private fun preview(photo: File?, theme: String?) {
        if (photo == null && theme == null) return
        val version = ++previewVersion
        previews.execute {
            val bitmap = when {
                photo != null -> BitmapFactory.decodeFile(photo.path, BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                })
                theme != null -> Backgrounds.render(theme)
                else -> null
            }
            main.post { if (version == previewVersion && !isDestroyed) backdrop.setImageBitmap(bitmap) }
        }
    }

    private fun updateChecks() = cards.values.forEach { it.selected() }

    override fun onStart() {
        super.onStart()
        stopLater.removeCallbacks(stop)
        address.text = I18n.text(R.string.i18n_wallpaperactivity_005)
        downloads.execute {
            val url = PhoneServer.start(this)
            val bitmap = url?.let { PhoneServer.qr(it) }
            main.post {
                if (!isDestroyed) {
                    address.text = url ?: I18n.text(R.string.i18n_wallpaperactivity_006)
                    qr.setImageBitmap(bitmap)
                }
            }
            for (source in Wallpapers.SOURCES) {
                if (source != Wallpapers.MINE && source != Wallpapers.CUSTOM) {
                    runCatching { Wallpapers(this, source).prefetch() }
                }
                main.post { if (!isDestroyed) { renderSource(source); updateChecks() } }
            }
        }
        poll.run()
    }

    private val poll = object : Runnable {
        override fun run() {
            rows.keys.forEach(::renderSource)
            updateChecks()
            main.postDelayed(this, 3000)
        }
    }

    override fun onStop() {
        main.removeCallbacks(poll)
        stopLater.postDelayed(stop, 5 * 60_000L)
        super.onStop()
    }

    override fun onDestroy() {
        downloads.shutdown()
        previews.shutdown()
        super.onDestroy()
    }

    companion object {
        private val stopLater = Handler(Looper.getMainLooper())
        private val stop = Runnable { Thread { PhoneServer.stop() }.start() }
    }
}
