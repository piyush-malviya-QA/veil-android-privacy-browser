package com.veil.browser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.veil.browser.privacy.AdBlockerController
import com.veil.browser.privacy.NavigationGuard
import com.veil.browser.privacy.TrackerFilterEngine
import com.veil.browser.tor.TorManager
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView

class MainActivity : ComponentActivity() {

    private lateinit var torManager: TorManager
    private lateinit var adBlocker: AdBlockerController
    private lateinit var runtime: GeckoRuntime
    private lateinit var session: GeckoSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        torManager = TorManager(applicationContext)

        // SH-1: Configure GeckoView with strict Enhanced Tracking Protection (ETP)
        val runtimeSettings = GeckoRuntimeSettings.Builder()
            .contentBlocking(
                ContentBlocking.Settings.Builder()
                    .antiTracking(ContentBlocking.AntiTracking.STRICT)
                    .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
                    .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
                    .build()
            )
            .build()

        runtime = GeckoRuntime.create(this, runtimeSettings)
        adBlocker = AdBlockerController(runtime).also { it.installBuiltInExtension() }

        // SH-4: Open GeckoSession (Tor tabs enforce usePrivateMode(true))
        session = createSession(privateMode = false)

        setContent {
            VeilBrowserScreen(
                session = session,
                adBlocker = adBlocker,
                torManager = torManager,
                onToggleTor = { enabled ->
                    if (enabled) {
                        torManager.startTorDaemon()
                        session.close()
                        session = createSession(privateMode = true)
                    } else {
                        torManager.stopTorDaemon()
                    }
                }
            )
        }
    }

    private fun createSession(privateMode: Boolean): GeckoSession {
        val settings = GeckoSessionSettings.Builder()
            .usePrivateMode(privateMode)
            .build()
        return GeckoSession(settings).apply {
            navigationDelegate = NavigationGuard(TrackerFilterEngine()) { _, _ -> }
            open(runtime)
            loadUri("https://check.torproject.org")
        }
    }
}

@Composable
fun VeilBrowserScreen(
    session: GeckoSession,
    adBlocker: AdBlockerController,
    torManager: TorManager,
    onToggleTor: (Boolean) -> Unit
) {
    val blockedCount by adBlocker.blockedCount.collectAsState()
    val torConnected by torManager.isConnected.collectAsState()
    var urlInput by remember { mutableStateOf("https://check.torproject.org") }

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
            Button(onClick = { onToggleTor(!torConnected) }) {
                Text(if (torConnected) "Tor ON" else "Tor")
            }
        }

        if (torConnected) {
            // TOR-6: Mandatory in-app anonymity honesty notice
            Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    text = TorManager.HONESTY_NOTICE,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { ctx -> GeckoView(ctx).apply { setSession(session) } }
        )
    }
}
