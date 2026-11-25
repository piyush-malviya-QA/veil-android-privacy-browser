package com.veil.browser.utilities

import org.mozilla.geckoview.GeckoSession
import java.io.File
import java.io.FileOutputStream

class PdfPrintExporter {

    // NF-U2: Export current GeckoSession viewport & document as PDF
    fun savePageAsPdf(session: GeckoSession, outputFile: File, onComplete: (Boolean) -> Unit) {
        session.saveAsPdf().accept(
            { inputStream ->
                if (inputStream == null) {
                    onComplete(false)
                    return@accept
                }
                inputStream.use { input ->
                    FileOutputStream(outputFile).use { output ->
                        input.copyTo(output)
                    }
                }
                onComplete(true)
            },
            { onComplete(false) }
        )
    }
}
