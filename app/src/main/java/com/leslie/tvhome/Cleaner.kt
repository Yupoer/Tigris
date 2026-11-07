package com.leslie.tvhome

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.util.Log
import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Shell access through paired Wireless debugging, with legacy 5555 for older TVs. */
object LocalAdb {
    private const val TAG = "LocalAdb"

    private fun keys(ctx: Context): AdbKeyPair {
        val priv = File(ctx.filesDir, "adbkey")
        val pub = File(ctx.filesDir, "adbkey.pub")
        if (!priv.exists() || !pub.exists()) AdbKeyPair.generate(priv, pub)
        return AdbKeyPair.read(priv, pub)
    }

    /**
     * Runs [cmd] in the adb shell. Returns null when adbd is unreachable or
     * the key is not authorized yet. [waitAuthMs] is how long to wait for the
     * user to accept the authorization prompt.
     */
    fun shell(ctx: Context, cmd: String, waitAuthMs: Int = 4000): String? {
        val prefs = Prefs(ctx)
        runCatching { LocalAdbTls.shell(ctx, cmd) }.getOrNull()?.let {
            prefs.adbAuthorized = true
            return it
        }
        return try {
            Dadb.create("127.0.0.1", 5555, keys(ctx), connectTimeout = 3000, socketTimeout = waitAuthMs).use { d ->
                d.shell(cmd).allOutput.also { prefs.adbAuthorized = true }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "adb shell failed: $cmd", e)
            prefs.adbAuthorized = false
            null
        }
    }

    /** User-initiated check. Never turn Wireless debugging on just to probe it. */
    fun authorize(ctx: Context): Boolean {
        val waitMs = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) 60_000 else 8_000
        val ok = shell(ctx, "echo TIGRIS_ADB_OK", waitMs)?.contains("TIGRIS_ADB_OK") == true
        Prefs(ctx).adbAuthorized = ok
        return ok
    }

    /** Fire-and-forget variant for commands that kill the connection (shutdown). */
    fun shellAsync(ctx: Context, cmd: String) {
        Thread { shell(ctx.applicationContext, cmd, 60_000) }.start()
    }
}

object Cleaner {
    data class Result(val beforeMb: Long, val afterMb: Long, val stopped: Int, val viaAdb: Boolean, val failed: Int = 0, val skipped: Boolean = false) {
        val freedMb get() = (afterMb - beforeMb).coerceAtLeast(0)
        fun description(): String = when {
            skipped -> I18n.text(R.string.i18n_cleaner_003)
            viaAdb -> I18n.text(R.string.i18n_cleaner_004, freedMb, stopped, failed)
            else -> I18n.text(R.string.i18n_cleaner_005, freedMb)
        }
    }

    fun availMb(ctx: Context): Long = memInfo(ctx).availMem / 1024 / 1024
    fun totalMb(ctx: Context): Long = memInfo(ctx).totalMem / 1024 / 1024

    private fun memInfo(ctx: Context) = ActivityManager.MemoryInfo().also {
        (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it)
    }

    /**
     * Manual cleanup stops unprotected third-party apps. Automatic cleanup requires a known
     * foreground app and only requests cleanup of observed cached processes. An unavailable
     * service inventory skips all cleanup.
     * Blocking; call off the main thread.
     */
    fun clean(ctx: Context, spare: String? = null, automatic: Boolean = false): Result {
        val prefs = Prefs(ctx)
        val before = availMb(ctx)
        val foreground = spare ?: foregroundPackage(ctx)
        // Automatic cleanup never force-stops apps: downloads/casting may be active without a visible window.
        val mode = CleanupPolicy.mode(automatic, foreground)
        if (mode == CleanupPolicy.Mode.SKIP) return Result(before, before, 0, false, skipped = true)
        val services = runCatching { protectedServices(ctx) }.getOrElse {
            Log.w("Cleaner", "Cannot inventory protected services; skipping cleanup", it)
            return Result(before, before, 0, false, skipped = true)
        }
        val keep = prefs.keep + setOfNotNull(ctx.packageName, foreground) + services
        var stopped = 0
        var failed = 0
        var viaAdb = false
        val list = if (mode != CleanupPolicy.Mode.DEEP) null else LocalAdb.shell(ctx, "pm list packages -3")
        if (list != null) {
            val targets = CleanupPolicy.targets(list.lineSequence()
                .filter { it.startsWith("package:") }.map { it.removePrefix("package:").trim() }.toList(), keep)
            viaAdb = true
            for (pkg in targets) {
                val marker = "TIGRIS_STOP_OK"
                val out = LocalAdb.shell(ctx, "am force-stop $pkg && echo $marker", 4000)
                if (CleanupPolicy.succeeded(out, marker)) stopped++ else failed++
            }
        } else {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val packages = ctx.packageManager.getInstalledApplications(0)
                .filter { it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0 }
                .map { it.packageName }.toSet()
            val processes = am.runningAppProcesses.orEmpty()
            val cached = processes.filter { it.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED }
                .flatMap { it.pkgList.orEmpty().toList() }.filter { it in packages }
            val active = processes.filter { it.importance < ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED }
                .flatMap { it.pkgList.orEmpty().toList() }.toSet()
            for (pkg in CleanupPolicy.cachedTargets(cached, active, keep)) am.killBackgroundProcesses(pkg)
        }
        Thread.sleep(1500)
        val r = Result(before, availMb(ctx), stopped, viaAdb, failed)
        prefs.lastClean = SimpleDateFormat(I18n.text(R.string.i18n_cleaner_006), Locale.CHINA).format(Date()) +
            "，${r.description()}"
        return r
    }

    private fun protectedServices(ctx: Context): Set<String> {
        val r = ctx.contentResolver
        val names = listOf("enabled_accessibility_services", "enabled_notification_listeners", "default_input_method")
        val keep = names.flatMap { android.provider.Settings.Secure.getString(r, it).orEmpty().split(':') }
            .mapNotNull { android.content.ComponentName.unflattenFromString(it)?.packageName }.toMutableSet()
        val pm = ctx.packageManager
        val advertised = pm.queryIntentServices(Intent(VpnService.SERVICE_INTERFACE), PackageManager.MATCH_DISABLED_COMPONENTS)
            .map { it.serviceInfo.packageName }
        val declared = pm.getInstalledPackages(PackageManager.GET_SERVICES or PackageManager.MATCH_DISABLED_COMPONENTS)
            .flatMap { pkg -> pkg.services.orEmpty().map { CleanupPolicy.Service(pkg.packageName, it.permission) } }
        keep.addAll(CleanupPolicy.vpnPackages(advertised, declared))
        val am = ctx.getSystemService(ActivityManager::class.java)
        am.runningAppProcesses.orEmpty().filter { it.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE }
            .forEach { keep.addAll(it.pkgList.orEmpty()) }
        ctx.getSystemService(android.media.session.MediaSessionManager::class.java)
            .getActiveSessions(android.content.ComponentName(ctx, RevivalListener::class.java))
            .forEach { keep.add(it.packageName) }
        return keep
    }

    fun foregroundPackage(ctx: Context): String? =
        LocalAdb.shell(ctx, "dumpsys activity activities")?.let(CleanupPolicy::foreground)

    fun shutdown(ctx: Context) = LocalAdb.shellAsync(ctx, "svc power shutdown")
    fun reboot(ctx: Context) = LocalAdb.shellAsync(ctx, "svc power reboot")
    fun sleep(ctx: Context) = LocalAdb.shellAsync(ctx, "input keyevent KEYCODE_SLEEP")
}
