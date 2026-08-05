package com.example.data.remote

import android.net.Uri
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

class FirebaseStorageManager {
    private val storage: FirebaseStorage?
        get() = try { FirebaseStorage.getInstance() } catch (e: Throwable) { null }

    suspend fun uploadProfilePhoto(uid: String, fileUri: Uri): String? {
        return uploadFile("profile_photos/${uid}_${System.currentTimeMillis()}.jpg", fileUri)
    }

    suspend fun uploadClientPhoto(clientId: Long, fileUri: Uri): String? {
        return uploadFile("client_photos/${clientId}_${System.currentTimeMillis()}.jpg", fileUri)
    }

    suspend fun uploadPolicyDocument(policyId: Long, fileName: String, fileUri: Uri): String? {
        val safeFileName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return uploadFile("policy_documents/${policyId}_${System.currentTimeMillis()}_$safeFileName", fileUri)
    }

    suspend fun uploadReceipt(receiptNo: String, fileUri: Uri): String? {
        val safeReceiptNo = receiptNo.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return uploadFile("receipts/${safeReceiptNo}_${System.currentTimeMillis()}.jpg", fileUri)
    }

    private suspend fun uploadFile(path: String, fileUri: Uri): String? {
        val storageInstance = storage ?: run {
            Log.w("FirebaseStorageManager", "FirebaseStorage instance is null")
            return null
        }
        return try {
            val ref = storageInstance.reference.child(path)
            ref.putFile(fileUri).await()
            val downloadUrl = ref.downloadUrl.await().toString()
            Log.i("FirebaseStorageManager", "Successfully uploaded file to $path: $downloadUrl")
            downloadUrl
        } catch (e: Throwable) {
            Log.e("FirebaseStorageManager", "Failed to upload file to $path: ${e.localizedMessage}", e)
            null
        }
    }
}
