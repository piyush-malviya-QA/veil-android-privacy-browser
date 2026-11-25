package com.veil.browser.privacy

import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

class NavigationGuard(
    private val filterEngine: TrackerFilterEngine,
    private val onBlockedEvent: (reason: String, url: String) -> Unit
) : GeckoSession.NavigationDelegate {

    override fun onLoadRequest(
        session: GeckoSession,
        request: GeckoSession.NavigationDelegate.LoadRequest
    ): GeckoResult<AllowOrDeny> {
        val uri = request.uri

        // NG-1: Reject TARGET_WINDOW_NEW popups & cross-origin redirects lacking a user gesture
        val isNewWindow = request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW
        if (isNewWindow && !request.hasUserGesture) {
            onBlockedEvent("Denied non-gesture popup (TARGET_WINDOW_NEW)", uri)
            return GeckoResult.fromValue(AllowOrDeny.DENY)
        }

        if (request.isRedirect && !request.hasUserGesture && filterEngine.isKnownTrackerHost(uri)) {
            onBlockedEvent("Denied cross-origin tracker redirect", uri)
            return GeckoResult.fromValue(AllowOrDeny.DENY)
        }

        // NG-2: Strip tracking query parameters (utm_*, fbclid, gclid, mc_eid) before navigation
        val sanitized = filterEngine.sanitizeUrl(uri)
        if (sanitized != uri) {
            onBlockedEvent("Stripped tracking query parameters", uri)
            session.loadUri(sanitized)
            return GeckoResult.fromValue(AllowOrDeny.DENY)
        }

        return GeckoResult.fromValue(AllowOrDeny.ALLOW)
    }

    override fun onNewSession(
        session: GeckoSession,
        uri: String
    ): GeckoResult<GeckoSession> {
        // NG-1: Deny automatic popup sessions by returning null
        onBlockedEvent("Denied automatic popup in onNewSession", uri)
        return GeckoResult.fromValue(null)
    }
}
