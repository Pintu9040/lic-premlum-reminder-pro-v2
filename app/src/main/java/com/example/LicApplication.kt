package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp

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

            val app = FirebaseApp.initializeApp(this)
            if (app != null) {
                isFirebaseInitialized = true
                firebaseInitializationError = null
                Log.i("LicApplication", "FirebaseApp successfully initialized with google-services.json")
            } else {
                isFirebaseInitialized = false
                firebaseInitializationError = "FirebaseApp.initializeApp(this) returned null"
                Log.e("LicApplication", "FirebaseApp.initializeApp(this) returned null")
            }
        } catch (e: Throwable) {
            isFirebaseInitialized = false
            firebaseInitializationError = e.localizedMessage ?: e.message ?: "Firebase initialization failed"
            Log.e("LicApplication", "Failed to initialize FirebaseApp with google-services.json: ${e.localizedMessage}", e)
        }
    }
}



