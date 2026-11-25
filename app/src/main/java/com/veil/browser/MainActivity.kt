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
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView

private const val HOME_URL = "https://duckduckgo.com"

class MainActivity : ComponentActivity() {

    private lateinit var adBlocker: AdBlockerController
    private lateinit var runtime: GeckoRuntime
    private lateinit var session: GeckoSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        session = createSession()

        setContent {
            VeilBrowserScreen(session = session, adBlocker = adBlocker)
        }
    }

    private fun createSession(): GeckoSession {
        val settings = GeckoSessionSettings.Builder().build()
        return GeckoSession(settings).apply {
            navigationDelegate = NavigationGuard(TrackerFilterEngine()) { _, _ -> }
            open(runtime)
            loadUri(HOME_URL)
        }
    }
}

@Composable
private fun VeilBrowserScreen(
    session: GeckoSession,
    adBlocker: AdBlockerController
) {
    val blockedCount by adBlocker.blockedCount.collectAsState()
    var urlInput by remember { mutableStateOf(HOME_URL) }

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
        }

        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { ctx -> GeckoView(ctx).apply { setSession(session) } }
        )
    }
}
