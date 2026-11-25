package com.veil.browser.tor

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.torproject.jni.TorService
import java.io.File

class TorManager(private val context: Context) {

    companion object {
        const val SOCKS_HOST = "127.0.0.1"
        private const val PREFS = "veil_prefs"
        private const val KEY_TOR_MODE = "tor_mode_enabled"
        private const val READY_TIMEOUT_MS = 120_000L

        const val HONESTY_NOTICE =
            "Tor mode provides IP masking and censorship bypass through the embedded Tor daemon, " +
                "not Tor Browser-level anonymity. High-threat users should use Tor Browser for Android."

        /** Actual SOCKS port chosen by the embedded Tor daemon (set once Tor is ready). */
        @Volatile
        var socksPort: Int = 0
            private set

        /** Tor mode is chosen at launch: GeckoView proxy prefs can only be set at engine start. */
        fun isTorModeEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_TOR_MODE, false)

        fun setTorModeEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_TOR_MODE, enabled).apply()
        }
    }

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {}
        override fun onServiceDisconnected(name: ComponentName) {
            _isConnected.value = false
        }
    }

    /**
     * Starts the embedded Tor daemon and suspends until it has built a circuit.
     * Returns the SOCKS port, or null on error/timeout. Callers must NOT fall back to a
     * direct connection when this returns null.
     */
    suspend fun startAndAwaitReady(): Int? {
        val ready = CompletableDeferred<Boolean>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    TorService.ACTION_STATUS ->
                        if (intent.getStringExtra(TorService.EXTRA_STATUS) == TorService.STATUS_ON) {
                            ready.complete(true)
                        }
                    TorService.ACTION_ERROR -> ready.complete(false)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(TorService.ACTION_STATUS)
            addAction(TorService.ACTION_ERROR)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            bound = context.bindService(
                Intent(context, TorService::class.java), connection, Context.BIND_AUTO_CREATE
            )
            if (!bound) return null
            val ok = withTimeoutOrNull(READY_TIMEOUT_MS) { ready.await() } ?: false
            if (!ok || TorService.socksPort <= 0) return null
            socksPort = TorService.socksPort
            _isConnected.value = true
            return socksPort
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    fun stopTorDaemon() {
        if (bound) {
            runCatching { context.unbindService(connection) }
            bound = false
        }
        _isConnected.value = false
    }

    /**
     * TOR-3 & TOR-4: Writes the Tor prefs file and attaches it via configFilePath(...)
     * so proxy, DNS, WebRTC, RFP and HTTPS-Only are enforced before the engine starts.
     */
    fun configureRuntimeForTor(builder: GeckoRuntimeSettings.Builder, port: Int): GeckoRuntimeSettings.Builder =
        builder.configFilePath(writeTorConfigYaml(port).absolutePath)

    fun writeTorConfigYaml(port: Int): File {
        val file = File(context.filesDir, "geckoview-tor-config.yaml")
        file.writeText(
            """
            prefs:
              network.proxy.type: 1
              network.proxy.socks: "$SOCKS_HOST"
              network.proxy.socks_port: $port
              network.proxy.socks_version: 5
              network.proxy.socks_remote_dns: true
              network.proxy.failover_direct: false
              network.proxy.allow_bypass: false
              network.proxy.no_proxies_on: ""
              network.trr.mode: 5
              network.dns.blockDotOnion: false
              network.dns.disablePrefetch: true
              network.http.speculative-parallel-limit: 0
              network.prefetch-next: false
              media.peerconnection.enabled: false
              privacy.resistFingerprinting: true
              dom.security.https_only_mode: true
            """.trimIndent()
        )
        return file
    }
}
