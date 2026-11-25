package com.veil.browser.tor

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.torproject.jni.TorService
import java.io.File

class TorManager(private val context: Context) {

    companion object {
        const val SOCKS_HOST = "127.0.0.1"
        const val SOCKS_PORT = 9050
        const val HONESTY_NOTICE =
            "Tor mode provides IP masking and censorship bypass via 127.0.0.1:9050, " +
                "not Tor Browser-level anonymity. High-threat users should use Tor Browser for Android."
    }

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private var torService: TorService? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            torService = (binder as TorService.LocalBinder).service
            _isConnected.value = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            torService = null
            _isConnected.value = false
        }
    }

    fun startTorDaemon() {
        val intent = Intent(context, TorService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun stopTorDaemon() {
        if (torService != null) {
            context.unbindService(connection)
            torService = null
            _isConnected.value = false
        }
    }

    /**
     * TOR-3 & TOR-4: Writes geckoview-tor-config.yaml and attaches it via configFilePath(...)
     * so DNS, .onion resolution, WebRTC, RFP, and HTTPS-Only are enforced before engine startup.
     */
    fun buildTorRuntimeSettings(): GeckoRuntimeSettings {
        val configFile = writeTorConfigYaml()
        return GeckoRuntimeSettings.Builder()
            .configFilePath(configFile.absolutePath)
            .build()
    }

    fun writeTorConfigYaml(): File {
        val file = File(context.filesDir, "geckoview-tor-config.yaml")
        file.writeText(
            """
            prefs:
              network.proxy.type: 1
              network.proxy.socks: "$SOCKS_HOST"
              network.proxy.socks_port: $SOCKS_PORT
              network.proxy.socks_remote_dns: true
              network.dns.blockDotOnion: false
              network.dns.disablePrefetch: true
              network.http.speculative-parallel-limit: 0
              media.peerconnection.enabled: false
              privacy.resistFingerprinting: true
              dom.security.https_only_mode: true
            """.trimIndent()
        )
        return file
    }
}
