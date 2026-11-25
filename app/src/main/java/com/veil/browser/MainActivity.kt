package com.veil.browser

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.veil.browser.privacy.AdBlockerController
import com.veil.browser.privacy.NavigationGuard
import com.veil.browser.privacy.TrackerFilterEngine
import com.veil.browser.tor.TorManager
import kotlinx.coroutines.launch
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView

private sealed interface BrowserState {
    data object ConnectingTor : BrowserState
    data class TorFailed(val message: String) : BrowserState
    data class Ready(val session: GeckoSession, val adBlocker: AdBlockerController) : BrowserState
}

class MainActivity : ComponentActivity() {

    private lateinit var torManager: TorManager
    private var runtime: GeckoRuntime? = null
    private var state by mutableStateOf<BrowserState>(BrowserState.ConnectingTor)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        torManager = TorManager(applicationContext)
        val torMode = TorManager.isTorModeEnabled(this)

        setContent {
            VeilBrowserScreen(
                state = state,
                torMode = torMode,
                torManager = torManager,
                onToggleTor = { setTorModeAndRestart(!torMode) },
                onRetry = { startBrowser(torMode) }
            )
        }
        startBrowser(torMode)
    }

    private fun startBrowser(torMode: Boolean) {
        state = BrowserState.ConnectingTor
        lifecycleScope.launch {
            val builder = GeckoRuntimeSettings.Builder()
                .contentBlocking(
                    // SH-1: strict Enhanced Tracking Protection (ETP)
                    ContentBlocking.Settings.Builder()
                        .antiTracking(ContentBlocking.AntiTracking.STRICT)
                        .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
                        .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
                        .build()
                )

            if (torMode) {
                // Fail closed: never start the engine (or load a page) until Tor is up.
                val port = torManager.startAndAwaitReady()
                if (port == null) {
                    state = BrowserState.TorFailed(
                        "Could not connect to Tor. No traffic was sent over your normal connection."
                    )
                    return@launch
                }
                torManager.configureRuntimeForTor(builder, port)
            }

            val rt = runtime ?: GeckoRuntime.create(this@MainActivity, builder.build()).also { runtime = it }
            val adBlocker = AdBlockerController(rt).also { it.installBuiltInExtension() }
            state = BrowserState.Ready(createSession(rt, privateMode = torMode), adBlocker)
        }
    }

    private fun createSession(rt: GeckoRuntime, privateMode: Boolean): GeckoSession {
        val settings = GeckoSessionSettings.Builder()
            .usePrivateMode(privateMode)
            .build()
        return GeckoSession(settings).apply {
            navigationDelegate = NavigationGuard(TrackerFilterEngine()) { _, _ -> }
            open(rt)
            loadUri("https://check.torproject.org")
        }
    }

    /** GeckoView proxy prefs are applied only at engine start, so switching modes restarts the app. */
    private fun setTorModeAndRestart(enabled: Boolean) {
        TorManager.setTorModeEnabled(this, enabled)
        val launch = packageManager.getLaunchIntentForPackage(packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val pending = PendingIntent.getActivity(
            this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        (getSystemService(ALARM_SERVICE) as AlarmManager)
            .set(AlarmManager.RTC, System.currentTimeMillis() + 300, pending)
        finishAffinity()
        Process.killProcess(Process.myPid())
    }
}

@Composable
private fun VeilBrowserScreen(
    state: BrowserState,
    torMode: Boolean,
    torManager: TorManager,
    onToggleTor: () -> Unit,
    onRetry: () -> Unit
) {
    val torConnected by torManager.isConnected.collectAsState()
    var urlInput by remember { mutableStateOf("https://check.torproject.org") }
    val blockedCount = (state as? BrowserState.Ready)?.adBlocker?.blockedCount?.collectAsState()?.value ?: 0

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                singleLine = true,
                modifier = Modifier.weight(1f),
                label = { Text("Omnibox") }
            )
            FilledTonalButton(onClick = { /* Open Shields bottom sheet */ }) {
                Text("Shields $blockedCount")
            }
            Button(onClick = onToggleTor) {
                Text(if (torMode) "Tor ON" else "Tor")
            }
        }

        if (torMode && torConnected) {
            // TOR-6: Mandatory in-app anonymity honesty notice
            Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    text = TorManager.HONESTY_NOTICE,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        when (state) {
            is BrowserState.ConnectingTor -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(if (torMode) "Connecting to Tor…" else "Starting…")
                }
            }
            is BrowserState.TorFailed -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text(state.message)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onRetry) { Text("Retry") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onToggleTor) { Text("Turn Tor off") }
                }
            }
            is BrowserState.Ready -> AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                factory = { ctx -> GeckoView(ctx).apply { setSession(state.session) } }
            )
        }
    }
}
