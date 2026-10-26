package com.leslie.tvhome

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigInteger
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.security.auth.x500.X500Principal

/**
 * Android 11+ wireless debugging has a changing TLS port, separate from legacy 5555.
 * Only discover this device's own advertised service; never run shell on another TV.
 * The app's RSA key lives in Android Keystore and is paired once through Android's code dialog.
 */
object LocalAdbTls {
    private const val TAG = "TigrisLocalAdb"
    private const val KEY_ALIAS = "tigris-local-adb"
    private const val TLS_CONNECT = "_adb-tls-connect._tcp."
    private const val TLS_PAIRING = "_adb-tls-pairing._tcp."

    private class Connection : AbsAdbConnectionManager() {
        private val entry: KeyStore.PrivateKeyEntry by lazy {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!store.containsAlias(KEY_ALIAS)) {
                val now = System.currentTimeMillis()
                val spec = KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setKeySize(2048)
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setCertificateSubject(X500Principal("CN=Tigris Local ADB"))
                    .setCertificateSerialNumber(BigInteger.valueOf(now))
                    .setCertificateNotBefore(Date(now - 60_000))
                    .setCertificateNotAfter(Date(now + 10L * 365 * 86_400_000))
                    .build()
                KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
                    .apply { initialize(spec) }.generateKeyPair()
            }
            store.getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
        }
        override fun getPrivateKey(): PrivateKey = entry.privateKey
        override fun getCertificate(): Certificate = entry.certificate
        override fun getDeviceName(): String = "Tigris@" + Build.MODEL
    }

    /** Called off the main thread. Returns null when Wireless debugging is off or unpaired. */
    fun shell(ctx: Context, command: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        if (android.provider.Settings.Global.getInt(ctx.contentResolver, "adb_wifi_enabled", 0) != 1) return null
        val port = discoverPort(ctx, TLS_CONNECT, 3500) ?: return null
        repeat(3) { attempt ->
            val connection = Connection().apply { setTimeout(5000, TimeUnit.MILLISECONDS) }
            try {
                if (!connection.connect("127.0.0.1", port)) return@repeat
                connection.openStream("shell:" + command).use { stream ->
                    val input = stream.openInputStream()
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (true) {
                        val read = try { input.read(buffer) }
                        catch (e: IOException) {
                            // libadb 3.1.1 reports normal remote shell close as an IOException.
                            if (!stream.isClosed) throw e
                            break
                        }
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                    }
                    return out.toString(Charsets.UTF_8.name())
                }
            } catch (e: Exception) {
                Log.w(TAG, "TLS shell attempt " + (attempt + 1) + " failed", e)
            } finally {
                runCatching { connection.disconnect() }
            }
        }
        return null
    }

    /** Pair while Android's six-digit pairing dialog remains open on the TV. */
    fun pair(ctx: Context, code: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !code.matches(Regex("[0-9]{6}"))) return false
        val port = discoverPort(ctx, TLS_PAIRING, 10_000) ?: return false
        return runCatching {
            Connection().apply { setTimeout(10_000, TimeUnit.MILLISECONDS) }
                .pair("127.0.0.1", port, code)
        }.onFailure { Log.w(TAG, "pairing failed", it) }.getOrDefault(false)
    }

    private fun discoverPort(ctx: Context, serviceType: String, timeoutMs: Long): Int? {
        val nsd = ctx.getSystemService(NsdManager::class.java) ?: return null
        val addresses = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .map { it.hostAddress }.toSet()
        val found = AtomicInteger(0)
        val ready = CountDownLatch(1)
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, code: Int) { ready.countDown() }
            override fun onStopDiscoveryFailed(type: String, code: Int) = Unit
            override fun onServiceLost(info: NsdServiceInfo) = Unit
            override fun onServiceFound(info: NsdServiceInfo) {
                if (!info.serviceType.startsWith(serviceType.removeSuffix("."))) return
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, code: Int) = Unit
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        if (info.host?.hostAddress in addresses && info.port in 1..65535) {
                            found.set(info.port)
                            ready.countDown()
                        }
                    }
                })
            }
        }
        return try {
            nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            ready.await(timeoutMs, TimeUnit.MILLISECONDS)
            found.get().takeIf { it > 0 }
        } catch (e: Exception) {
            Log.w(TAG, "service discovery failed", e)
            null
        } finally {
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
    }
}
