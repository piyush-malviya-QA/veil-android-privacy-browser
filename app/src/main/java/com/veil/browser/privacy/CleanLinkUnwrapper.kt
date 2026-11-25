package com.veil.browser.privacy

import android.net.Uri

data class CleanLinkResult(
    val cleanUrl: String,
    val unwrappedFrom: String?,
    val strippedParams: List<String>
)

class CleanLinkUnwrapper(private val trackerFilter: TrackerFilterEngine) {

    private val redirectParamMap = mapOf(
        "l.facebook.com" to "u",
        "lm.facebook.com" to "u",
        "www.google.com" to "q",
        "google.com" to "url",
        "out.reddit.com" to "url"
    )

    fun cleanAndUnwrap(rawUrl: String, unwrapAmpAndRedirects: Boolean = true): CleanLinkResult {
        var working = rawUrl
        var unwrappedFrom: String? = null

        if (unwrapAmpAndRedirects) {
            val uri = Uri.parse(working)
            val host = uri.host?.lowercase().orEmpty()
            val path = uri.path.orEmpty()

            // Unwrap Google AMP cache & amp.* subdomains
            when {
                (host == "www.google.com" || host == "google.com") && path.startsWith("/amp/s/") -> {
                    unwrappedFrom = working
                    working = "https://" + path.removePrefix("/amp/s/")
                }
                host.endsWith(".cdn.ampproject.org") && path.startsWith("/c/s/") -> {
                    unwrappedFrom = working
                    working = "https://" + path.removePrefix("/c/s/")
                }
                host.startsWith("amp.") -> {
                    unwrappedFrom = working
                    working = uri.buildUpon().authority(host.removePrefix("amp.")).build().toString()
                }
            }

            // Unwrap redirect tracker wrappers
            val afterAmp = Uri.parse(working)
            val paramKey = redirectParamMap[afterAmp.host?.lowercase()]
            val nested = if (paramKey != null) afterAmp.getQueryParameter(paramKey) else null
            if (!nested.isNullOrBlank() && (nested.startsWith("https://") || nested.startsWith("http://"))) {
                unwrappedFrom = unwrappedFrom ?: working
                working = nested
            }
        }

        val sanitized = trackerFilter.sanitizeUrl(working)
        return CleanLinkResult(
            cleanUrl = sanitized,
            unwrappedFrom = unwrappedFrom,
            strippedParams = if (sanitized != working) listOf("tracking-params-removed") else emptyList()
        )
    }
}
