package com.example.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.backup.BackupManager
import com.example.data.local.AppDatabase

class AutoBackupWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.i("AutoBackupWorker", "Starting scheduled background backup execution...")
        val db = AppDatabase.getDatabase(applicationContext)
        val result = BackupManager.createFullBackup(applicationContext, db)
        return if (result.isSuccess) {
            Log.i("AutoBackupWorker", "Scheduled auto backup completed successfully.")
            Result.success()
        } else {
            Log.e("AutoBackupWorker", "Scheduled auto backup failed: ${result.exceptionOrNull()?.localizedMessage}")
            Result.retry()
        }
    }
}
