package com.veil.browser.privacy

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension

class AdBlockerController(
    private val runtime: GeckoRuntime
) {
    private var nativePort: WebExtension.Port? = null

    private val _blockedCount = MutableStateFlow(0)
    val blockedCount: StateFlow<Int> = _blockedCount.asStateFlow()

    private val _disabledHosts = MutableStateFlow<Set<String>>(emptySet())
    val disabledHosts: StateFlow<Set<String>> = _disabledHosts.asStateFlow()

    fun installBuiltInExtension() {
        runtime.webExtensionController
            .ensureBuiltIn("resource://android/assets/adblocker/", "adblocker@veil.browser")
            .accept { extension ->
                extension?.setMessageDelegate(
                    object : WebExtension.MessageDelegate {
                        override fun onConnect(port: WebExtension.Port) {
                            nativePort = port
                            port.setDelegate(object : WebExtension.PortDelegate {
                                override fun onPortMessage(message: Any, port: WebExtension.Port) {
                                    val json = message as? JSONObject ?: return
                                    if (json.optString("type") == "BLOCKED_EVENT") {
                                        _blockedCount.value += 1
                                    }
                                }
                            })
                        }
                    },
                    "veil_adblocker"
                )
            }
    }

    fun setSiteShieldsEnabled(host: String, enabled: Boolean) {
        _disabledHosts.value = if (enabled) {
            _disabledHosts.value - host
        } else {
            _disabledHosts.value + host
        }
        nativePort?.postMessage(
            JSONObject().apply {
                put("type", "SET_SITE_EXCEPTION")
                put("host", host)
                put("disabled", !enabled)
            }
        )
    }

    fun resetPageCounter() {
        _blockedCount.value = 0
    }
}
