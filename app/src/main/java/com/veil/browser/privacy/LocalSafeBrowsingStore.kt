package com.veil.browser.privacy

import android.net.Uri
import java.security.MessageDigest

data class LocalThreatMatch(
    val host: String,
    val threatType: String,
    val hashPrefixHex: String
)

class LocalSafeBrowsingStore {

    // Populated by periodic offline delta downloads (never queried live per URL)
    private val localHashPrefixes = mutableMapOf<String, String>()

    fun seedOfflineBlocklist(entries: Map<String, String>) {
        localHashPrefixes.clear()
        localHashPrefixes.putAll(entries)
    }

    fun checkUrlLocally(url: String): LocalThreatMatch? {
        val host = Uri.parse(url).host?.lowercase() ?: return null
        val prefix = sha256Prefix12(host)
        val threatType = localHashPrefixes[prefix] ?: return null
        return LocalThreatMatch(host = host, threatType = threatType, hashPrefixHex = prefix)
    }

    private fun sha256Prefix12(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.take(6).joinToString("") { "%02x".format(it) }
    }
}
