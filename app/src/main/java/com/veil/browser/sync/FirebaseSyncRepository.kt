package com.veil.browser.sync

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.veil.browser.BuildConfig
import kotlinx.coroutines.tasks.await

class FirebaseSyncRepository {

    private val auth = Firebase.auth

    // Connects to the exact Cloud Firestore database provisioned for Veil
    private val db: FirebaseFirestore by lazy {
        Firebase.firestore(Firebase.app, BuildConfig.FIRESTORE_DATABASE_ID)
    }

    private fun requireVerifiedUser(): FirebaseUser {
        val user = auth.currentUser ?: error("User must be signed in to sync with Firebase")
        check(user.isEmailVerified) { "Verified email required by Firestore security rules" }
        return user
    }

    /**
     * Master Gate invariant: ensures /users/{uid} exists before writing subcollections.
     */
    suspend fun ensureUserAccountDoc() {
        val user = requireVerifiedUser()
        val docRef = db.collection("users").document(user.uid)
        val snap = docRef.get().await()
        val displayName = (user.displayName ?: "Veil User").take(120).ifBlank { "Veil User" }
        val email = (user.email ?: "user@veil.local").take(254)

        if (!snap.exists()) {
            docRef.set(
                mapOf(
                    "uid" to user.uid,
                    "displayName" to displayName,
                    "email" to email,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else {
            docRef.update(
                mapOf(
                    "displayName" to displayName,
                    "email" to email,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    /**
     * Syncs a non-Tor bookmark to /users/{uid}/bookmarks/{bookmarkId}.
     * NF-3: Never call this for Tor session tabs.
     */
    suspend fun upsertBookmark(
        bookmarkId: String,
        title: String,
        url: String,
        folder: String,
        isTorSession: Boolean
    ) {
        if (isTorSession) return
        val user = requireVerifiedUser()
        val safeId = bookmarkId.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(128)
        val docRef = db.collection("users").document(user.uid)
            .collection("bookmarks").document(safeId)

        val snap = docRef.get().await()
        if (!snap.exists()) {
            docRef.set(
                mapOf(
                    "ownerId" to user.uid,
                    "title" to title.trim().take(200).ifBlank { "Saved Page" },
                    "url" to url.trim().take(2048),
                    "folder" to folder.trim().take(80).ifBlank { "Daily Reading" },
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else {
            docRef.update(
                mapOf(
                    "title" to title.trim().take(200).ifBlank { "Saved Page" },
                    "url" to url.trim().take(2048),
                    "folder" to folder.trim().take(80).ifBlank { "Daily Reading" },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    /**
     * NF-U3: Persists client-side AES-256-GCM ciphertext snapshot to /users/{uid}/syncSnapshots/latest.
     */
    suspend fun uploadEncryptedSnapshot(
        ciphertextBase64: String,
        encryptedBytes: Long,
        deviceCount: Int
    ) {
        val user = requireVerifiedUser()
        val docRef = db.collection("users").document(user.uid)
            .collection("syncSnapshots").document("latest")

        val snap = docRef.get().await()
        val boundedCipher = ciphertextBase64.take(4096)
        val boundedBytes = encryptedBytes.coerceIn(1L, 1_048_576L)
        val boundedDevices = deviceCount.coerceIn(1, 50)

        if (!snap.exists()) {
            docRef.set(
                mapOf(
                    "ownerId" to user.uid,
                    "algorithm" to "AES-256-GCM",
                    "ciphertextPreview" to boundedCipher,
                    "encryptedBytes" to boundedBytes,
                    "deviceCount" to boundedDevices,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else {
            docRef.update(
                mapOf(
                    "ciphertextPreview" to boundedCipher,
                    "encryptedBytes" to boundedBytes,
                    "deviceCount" to boundedDevices,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }
}
