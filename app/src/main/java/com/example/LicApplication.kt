package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class LicApplication : Application() {

    companion object {
        var isFirebaseInitialized = false
            private set
        var firebaseInitializationError: String? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        initializeFirebase()
    }

    private fun initializeFirebase() {
        try {
            if (FirebaseApp.getApps(this).isNotEmpty()) {
                Log.i("LicApplication", "FirebaseApp already initialized")
                isFirebaseInitialized = true
                firebaseInitializationError = null
                return
            }

            var app = FirebaseApp.initializeApp(this)
            if (app == null) {
                Log.w("LicApplication", "FirebaseApp.initializeApp(this) returned null. Constructing FirebaseOptions from resources...")
                try {
                    val appId = getString(R.string.google_app_id)
                    val apiKey = getString(R.string.google_api_key)
                    val projectId = getString(R.string.project_id)
                    val storageBucket = getString(R.string.google_storage_bucket)
                    val senderId = getString(R.string.gcm_defaultSenderId)

                    val options = FirebaseOptions.Builder()
                        .setApplicationId(appId)
                        .setApiKey(apiKey)
                        .setProjectId(projectId)
                        .setStorageBucket(storageBucket)
                        .setGcmSenderId(senderId)
                        .build()

                    app = FirebaseApp.initializeApp(this, options)
                } catch (e: Exception) {
                    Log.e("LicApplication", "Failed to initialize Firebase with explicit options from resources", e)
                }
            }

            if (app != null) {
                isFirebaseInitialized = true
                firebaseInitializationError = null
                Log.i("LicApplication", "FirebaseApp successfully initialized: ${app.name}")
            } else {
                isFirebaseInitialized = false
                firebaseInitializationError = "FirebaseApp.initializeApp returned null"
                Log.e("LicApplication", "FirebaseApp.initializeApp returned null")
            }
        } catch (e: Throwable) {
            isFirebaseInitialized = false
            firebaseInitializationError = e.localizedMessage ?: e.message ?: "Firebase initialization failed"
            Log.e("LicApplication", "Failed to initialize FirebaseApp: ${e.localizedMessage}", e)
        }
    }
}




