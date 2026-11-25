package com.veil.browser.privacy

import android.net.Uri
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

enum class SitePermission { CAMERA, MICROPHONE, LOCATION, NOTIFICATIONS }

class SitePermissionManager : GeckoSession.PermissionDelegate {

    // Per-host overrides; absent entries default to DENY
    private val allowedPermissions = mutableMapOf<String, MutableSet<SitePermission>>()

    fun setPermission(host: String, permission: SitePermission, allow: Boolean) {
        val set = allowedPermissions.getOrPut(host.lowercase()) { mutableSetOf() }
        if (allow) set.add(permission) else set.remove(permission)
    }

    fun isAllowed(host: String, permission: SitePermission): Boolean =
        allowedPermissions[host.lowercase()]?.contains(permission) == true

    fun resetAllToDeny() {
        allowedPermissions.clear()
    }

    override fun onContentPermissionRequest(
        session: GeckoSession,
        perm: GeckoSession.PermissionDelegate.ContentPermission
    ): GeckoResult<Int> {
        val host = Uri.parse(perm.uri).host?.lowercase().orEmpty()
        val mapped = when (perm.permission) {
            GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> SitePermission.LOCATION
            GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> SitePermission.NOTIFICATIONS
            else -> null
        }
        val granted = mapped != null && isAllowed(host, mapped)
        return GeckoResult.fromValue(
            if (granted) {
                GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
            } else {
                GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
            }
        )
    }
}
