package com.veil.browser.privacy

import android.net.Uri

class TrackerFilterEngine {

    private val exactTrackingParams = setOf(
        "fbclid", "gclid", "dclid", "mc_eid", "mc_cid", "yclid", "msclkid", "igshid"
    )

    private val knownRedirectTrackers = setOf(
        "doubleclick.net",
        "adservice.google.com",
        "clickserve.dartsearch.net",
        "trk.mail-tracker.example"
    )

    fun isKnownTrackerHost(url: String): Boolean {
        val host = Uri.parse(url).host?.lowercase() ?: return false
        return knownRedirectTrackers.any { host == it || host.endsWith(".$it") }
    }

    fun sanitizeUrl(rawUrl: String): String {
        val uri = Uri.parse(rawUrl)
        val paramNames = uri.queryParameterNames
        if (paramNames.isEmpty()) return rawUrl

        val builder = uri.buildUpon().clearQuery()
        var modified = false

        for (name in paramNames) {
            val lower = name.lowercase()
            if (lower.startsWith("utm_") || lower in exactTrackingParams) {
                modified = true
                continue
            }
            for (value in uri.getQueryParameters(name)) {
                builder.appendQueryParameter(name, value)
            }
        }

        return if (modified) builder.build().toString() else rawUrl
    }
}
