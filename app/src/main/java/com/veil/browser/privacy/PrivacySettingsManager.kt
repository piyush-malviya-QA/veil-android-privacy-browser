package com.veil.browser.privacy

import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.StorageController

enum class DohResolver(val uri: String) {
    QUAD9("https://dns.quad9.net/dns-query"),
    MULLVAD("https://dns.mullvad.net/dns-query"),
    CLOUDFLARE("https://mozilla.cloudflare-dns.com/dns-query"),
    NEXTDNS("https://dns.nextdns.io/dns-query")
}

class PrivacySettingsManager(private val runtime: GeckoRuntime) {

    fun applyClearnetPrivacyPrefs(
        httpsOnly: Boolean,
        dohResolver: DohResolver?,
        resistFingerprinting: Boolean
    ) {
        // NF-P1, NF-P2, NF-P3 applied via GeckoRuntimeSettings / pref YAML on startup
        runtime.settings.apply {
            preferredColorScheme = GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM
        }
    }

    // NF-P4: Auto-clear cookies, caches, and site storage on exit
    fun clearSiteDataOnExit() {
        runtime.storageController.clearData(
            StorageController.ClearFlags.COOKIES or
                StorageController.ClearFlags.ALL_CACHES or
                StorageController.ClearFlags.DOM_STORAGES
        )
    }
}
