package com.example.data.remote

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.data.local.AppDatabase
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.TimeUnit

class CloudSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting background CloudSyncWorker execution...")
        val syncManager = FirebaseSyncManager(applicationContext)

        if (!syncManager.isOnline()) {
            Log.w(TAG, "Device is offline. Retrying CloudSyncWorker later...")
            return Result.retry()
        }

        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            val auth = FirebaseAuth.getInstance()
            val user = auth.currentUser
            val uid = user?.uid ?: syncManager.getOrEnsureUid()

            if (uid.isNotBlank()) {
                Log.i(TAG, "Executing automatic background sync for UID: $uid")
                syncManager.autoRestoreAndSync(uid, db)
                Log.i(TAG, "Background sync completed successfully for UID: $uid")
                Result.success()
            } else {
                Log.w(TAG, "No valid UID available for background sync.")
                Result.failure()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error in CloudSyncWorker: ${e.localizedMessage}", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        private const val TAG = "CloudSyncWorker"
        const val WORK_NAME = "lic_cloud_sync_worker"

        fun schedulePeriodicSync(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val syncRequest = PeriodicWorkRequestBuilder<CloudSyncWorker>(3, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    syncRequest
                )
                Log.i(TAG, "Periodic CloudSyncWorker scheduled every 3 hours with CONNECTED constraint.")
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to schedule periodic CloudSyncWorker: ${e.localizedMessage}")
            }
        }

        fun triggerImmediateSync(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val immediateRequest = OneTimeWorkRequestBuilder<CloudSyncWorker>()
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueue(immediateRequest)
                Log.i(TAG, "Immediate CloudSyncWorker enqueued.")
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to trigger immediate CloudSyncWorker: ${e.localizedMessage}")
            }
        }
    }
}
