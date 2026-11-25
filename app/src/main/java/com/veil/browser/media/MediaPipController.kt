package com.veil.browser.media

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.MediaSession

class MediaPipController(
    private val allowPip: () -> Boolean,
    private val allowBackgroundAudio: () -> Boolean
) : MediaSession.Delegate {

    private var activeMediaSession: MediaSession? = null

    fun enterPictureInPicture(activity: Activity): Boolean {
        if (!allowPip() || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .build()
        return activity.enterPictureInPictureMode(params)
    }

    fun onAppBackgrounded(session: GeckoSession) {
        if (!allowBackgroundAudio()) {
            activeMediaSession?.pause()
            session.setActive(false)
        }
        // When background audio is enabled, keep MediaSession audio stream active
    }

    override fun onActivated(session: GeckoSession, mediaSession: MediaSession) {
        activeMediaSession = mediaSession
    }

    override fun onDeactivated(session: GeckoSession, mediaSession: MediaSession) {
        if (activeMediaSession == mediaSession) activeMediaSession = null
    }
}
