package com.leslie.tvhome

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.leslie.tvhome.tv.TclAdapter

object VpnBackground {
    private const val TAG = "VpnBackground"
    private const val PREFS = "vpn-background"
    private const val PACKAGES = "packages"
    private const val GUARD_PACKAGE = "com.tcl.guard"
    private const val GUARD_SERVICE = "$GUARD_PACKAGE.memorymanager.TGuardService"
    private const val DUMP = "dumpsys activity service $GUARD_PACKAGE/.memorymanager.TGuardService"
    private val preferencesLock = Any()
    @Volatile private var lastAttempt: Long? = null

    fun protect(ctx: Context, pkg: String): Boolean = synchronized(preferencesLock) {
        if (!isVpn(ctx, pkg)) return false
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val opted = prefs.getStringSet(PACKAGES, emptySet()).orEmpty()
        if (pkg in opted) return true
        val saved = prefs.edit().putStringSet(PACKAGES, opted + pkg).commit()
        if (saved) lastAttempt = null
        saved
    }

    /** Blocking reconciliation for explicitly opted-in VPNs. Call from a worker thread. */
    @Synchronized
    fun ensure(ctx: Context) {
        val opted = synchronized(preferencesLock) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(PACKAGES, emptySet()).orEmpty().toSet()
        }
        if (opted.isEmpty()) return
        check(Looper.myLooper() != Looper.getMainLooper()) { "Call VpnBackground.ensure from a worker thread" }
        val now = SystemClock.elapsedRealtime()
        if (!VpnBackgroundPolicy.due(lastAttempt, now)) return
        lastAttempt = now
        if (!TclAdapter.isTcl() || !hasGuard(ctx)) return
        val packages = opted.filter { isVpn(ctx, it) }.toSet()
        if (packages.isEmpty()) return
        val dump = LocalAdb.shell(ctx, DUMP) ?: return
        val additions = VpnBackgroundPolicy.additions(dump, packages)
        if (additions == null) {
            Log.w(TAG, "Unrecognized TCL guard whitelist dump; leaving device policy unchanged")
            return
        }
        for (pkg in packages.sorted()) {
            val marker = "TIGRIS_VPN_BACKGROUND_OK"
            val commands = listOf(
                "appops set $pkg RUN_IN_BACKGROUND allow",
                "appops set $pkg RUN_ANY_IN_BACKGROUND allow",
                "dumpsys deviceidle whitelist +$pkg",
                "appops set $pkg APP_AUTO_START allow",
                "appops set $pkg APP_ASSOC_START allow",
                "echo $marker",
            )
            if (!CleanupPolicy.succeeded(LocalAdb.shell(ctx, commands.joinToString(" && ")), marker)) {
                Log.w(TAG, "Could not restore all background permissions for $pkg")
            }
        }
        for ((list, missing) in additions) {
            LocalAdb.shell(ctx, "$DUMP --ma ${list.field} ${missing.joinToString(",")}")
        }
        if (additions.isNotEmpty()) {
            val verified = LocalAdb.shell(ctx, DUMP)?.let { VpnBackgroundPolicy.additions(it, packages) }
            if (verified == null || verified.isNotEmpty()) Log.w(TAG, "TCL VPN whitelist protection is still incomplete")
            else Log.i(TAG, "Restored TCL guard protection for ${packages.joinToString()}")
        }
    }

    private fun isVpn(ctx: Context, pkg: String): Boolean {
        if (!VpnBackgroundPolicy.validPackage(pkg)) return false
        return runCatching {
            ctx.packageManager.getPackageInfo(pkg, PackageManager.GET_SERVICES or PackageManager.MATCH_DISABLED_COMPONENTS)
                .services.orEmpty().any { it.permission == Manifest.permission.BIND_VPN_SERVICE }
        }.getOrDefault(false)
    }

    private fun hasGuard(ctx: Context): Boolean = runCatching {
        val service = ctx.packageManager.getServiceInfo(ComponentName(GUARD_PACKAGE, GUARD_SERVICE), 0)
        service.enabled && service.exported && service.applicationInfo.enabled
    }.getOrDefault(false)
}
