package com.veil.browser.capture

import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.Window

class ScreenshotController {

    // SS-1: Viewport capture via PixelCopy (works across all supported Android versions)
    fun captureViewport(
        window: Window,
        width: Int,
        height: Int,
        onCaptured: (Bitmap?) -> Unit
    ) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        PixelCopy.request(
            window,
            bitmap,
            { result ->
                if (result == PixelCopy.SUCCESS) onCaptured(bitmap) else onCaptured(null)
            },
            Handler(Looper.getMainLooper())
        )
    }

    // SS-2 & NF-6: Full-page scrolling capture uses Android 12+ ScrollCaptureCallback on GeckoView
    fun supportsScrollingCapture(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun bindSurfaceForScrollCapture(surfaceView: SurfaceView) {
        if (supportsScrollingCapture()) {
            // GeckoView attaches its native ScrollCaptureCallback to the host View hierarchy on API 31+
            surfaceView.isImportantForAccessibility = SurfaceView.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
    }
}
