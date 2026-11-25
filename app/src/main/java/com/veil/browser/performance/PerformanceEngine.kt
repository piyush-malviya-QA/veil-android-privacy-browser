package com.veil.browser.performance

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoSession

enum class NetworkTier { WIFI, CELLULAR, SLOW_3G }

class PerformanceEngine(
    private val context: Context,
    private val scope: CoroutineScope
) {
    // PF-1: Render Compose shell immediately; warm GeckoRuntime asynchronously on IO/Default
    fun launchAsyncEngineInit(initBlock: () -> Unit) {
        scope.launch(Dispatchers.Default) { initBlock() }
    }

    // PF-2: Load serialized adblock-rust binary snapshot from disk so page loads never wait on list updates
    fun loadCachedAdblockSnapshotBytes(): ByteArray? {
        val file = context.filesDir.resolve("adblock_engine.dat")
        return if (file.exists()) file.readBytes() else null
    }

    // PF-4: Trim inactive background GeckoSessions when memory exceeds configured cap
    fun trimBackgroundTabs(backgroundSessions: List<GeckoSession>, maxActiveBackground: Int = 2) {
        backgroundSessions.drop(maxActiveBackground).forEach { session ->
            session.setActive(false)
            session.purgeHistory()
        }
    }

    // PF-5: Preconnect / DNS prefetch allowed only on clearnet; strictly disabled in Tor mode
    fun shouldEnablePreconnectAndDnsPrefetch(torModeActive: Boolean, userEnabled: Boolean): Boolean {
        if (torModeActive) return false
        return userEnabled
    }

    // PF-7: Adaptive segmented download stream count by network tier & Tor routing
    fun computeAdaptiveDownloadStreams(
        tier: NetworkTier,
        viaTor: Boolean,
        adaptiveEnabled: Boolean,
        requested: Int = 6
    ): Int {
        if (!adaptiveEnabled) return requested.coerceIn(4, 8)
        if (viaTor) return if (tier == NetworkTier.SLOW_3G) 2 else 4
        return when (tier) {
            NetworkTier.WIFI -> 8
            NetworkTier.CELLULAR -> 4
            NetworkTier.SLOW_3G -> 2
        }
    }
}
