package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class LicApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initializeFirebase()
    }

    private fun initializeFirebase() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val app = FirebaseApp.initializeApp(this)
                if (app == null) {
                    initFirebaseWithOptions()
                } else {
                    Log.i("LicApplication", "FirebaseApp initialized via default resources")
                }
            }
        } catch (e: Throwable) {
            Log.w("LicApplication", "Default FirebaseApp init failed: ${e.localizedMessage}. Trying explicit options.")
            initFirebaseWithOptions()
        }
    }

    private fun initFirebaseWithOptions() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApiKey("AIzaSyLicReminderProKey2026SecureBuild")
                    .setApplicationId("1:250618018880:android:a1b2c3d4e5f67890")
                    .setProjectId("lic-reminder-pro")
                    .setStorageBucket("lic-reminder-pro.appspot.com")
                    .build()
                FirebaseApp.initializeApp(this, options)
                Log.i("LicApplication", "FirebaseApp initialized via explicit FirebaseOptions")
            }
        } catch (e: Throwable) {
            Log.e("LicApplication", "Critical error initializing Firebase options: ${e.localizedMessage}")
        }
    }
}
