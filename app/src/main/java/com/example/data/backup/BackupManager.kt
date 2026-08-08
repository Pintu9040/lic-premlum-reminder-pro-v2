package com.example.data.backup

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.work.*
import com.example.data.local.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

data class BackupHistoryItemData(
    val id: String = "",
    val date: String = "",
    val time: String = "",
    val timestamp: Long = 0L,
    val size: String = "0 KB",
    val sizeBytes: Long = 0L,
    val duration: String = "0 sec",
    val status: String = "Success", // "Success", "Failed"
    val destination: String = "Firebase Cloud", // "Firebase Cloud", "Local Device"
    val totalCustomers: Int = 0,
    val totalPolicies: Int = 0,
    val totalPayments: Int = 0,
    val totalDocuments: Int = 0,
    val agentUid: String = "",
    val localFilePath: String? = null,
    val cloudDownloadUrl: String? = null
)

object BackupManager {
    private const val TAG = "BackupManager"
    private const val BACKUP_DIR_NAME = "LIC Premium Reminder Pro/Reports"
    private const val PREFS_NAME = "lic_backup_prefs"
    private const val KEY_HISTORY_LOG = "backup_history_json"
    private const val KEY_AUTO_BACKUP_FREQ = "auto_backup_frequency"
    private const val KEY_AUTO_BACKUP_ENABLED = "auto_backup_enabled"
    private const val KEY_WIFI_ONLY = "auto_backup_wifi_only"
    private const val KEY_CHARGING_ONLY = "auto_backup_charging_only"

    private fun getAgentUid(): String {
        return try {
            FirebaseAuth.getInstance().currentUser?.uid ?: "local_agent"
        } catch (e: Throwable) {
            "local_agent"
        }
    }

    suspend fun createFullBackup(
        context: Context,
        db: AppDatabase,
        progressCallback: ((Float) -> Unit)? = null
    ): Result<BackupHistoryItemData> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val uid = getAgentUid()
        val backupId = "backup_${uid}_$startTime"

        try {
            progressCallback?.invoke(0.1f)

            // 1. Fetch live Room DB data synchronously
            val agentProfile = db.agentDao().getAgentProfileSync()
            val customers = db.customerDao().getAllCustomersSync()
            val policies = db.policyDao().getAllPoliciesSync()
            val payments = db.paymentDao().getAllPaymentsSync()
            val followUps = db.followUpDao().getAllFollowUpsSync()
            val documents = db.documentDao().getAllDocumentsSync()

            progressCallback?.invoke(0.3f)

            // 2. Build JSON package
            val rootObj = JSONObject()
            rootObj.put("version", 1)
            rootObj.put("backupId", backupId)
            rootObj.put("createdAtTimestamp", startTime)
            rootObj.put("agentUid", uid)

            // Agent Profile JSON
            if (agentProfile != null) {
                val profileObj = JSONObject().apply {
                    put("id", agentProfile.id)
                    put("agentName", agentProfile.agentName)
                    put("agencyCode", agentProfile.agencyCode)
                    put("branchCode", agentProfile.branchCode)
                    put("branchName", agentProfile.branchName)
                    put("licenseNumber", agentProfile.licenseNumber)
                    put("email", agentProfile.email)
                    put("mobile", agentProfile.mobile)
                    put("photoUri", agentProfile.photoUri)
                    put("themeMode", agentProfile.themeMode)
                    put("isDarkMode", agentProfile.isDarkMode)
                    put("pinCode", agentProfile.pinCode)
                    put("isBiometricEnabled", agentProfile.isBiometricEnabled)
                    put("autoLogoutMinutes", agentProfile.autoLogoutMinutes)
                    put("isAutoSyncEnabled", agentProfile.isAutoSyncEnabled)
                    put("lastSyncedTime", agentProfile.lastSyncedTime)
                }
                rootObj.put("agentProfile", profileObj)
            }

            // Customers Array
            val custArray = JSONArray()
            customers.forEach { c ->
                custArray.put(JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("mobile", c.mobile)
                    put("whatsapp", c.whatsapp)
                    put("email", c.email)
                    put("address", c.address)
                    put("dob", c.dob)
                    put("anniversary", c.anniversary)
                    put("aadhaar", c.aadhaar)
                    put("pan", c.pan)
                    put("occupation", c.occupation)
                    put("notes", c.notes)
                    put("photoUri", c.photoUri ?: "")
                    put("createdAt", c.createdAt)
                })
            }
            rootObj.put("customers", custArray)

            // Policies Array
            val polArray = JSONArray()
            policies.forEach { p ->
                polArray.put(JSONObject().apply {
                    put("id", p.id)
                    put("policyNumber", p.policyNumber)
                    put("customerId", p.customerId)
                    put("customerName", p.customerName)
                    put("planName", p.planName)
                    put("premiumAmount", p.premiumAmount)
                    put("sumAssured", p.sumAssured)
                    put("premiumMode", p.premiumMode)
                    put("dueDate", p.dueDate)
                    put("maturityDate", p.maturityDate)
                    put("status", p.status)
                    put("nominee", p.nominee)
                    put("policyTerm", p.policyTerm)
                    put("premiumPayingTerm", p.premiumPayingTerm)
                    put("issueDate", p.issueDate)
                    put("gracePeriodDays", p.gracePeriodDays)
                    put("createdAt", p.createdAt)
                })
            }
            rootObj.put("policies", polArray)

            // Payments Array
            val payArray = JSONArray()
            payments.forEach { pay ->
                payArray.put(JSONObject().apply {
                    put("id", pay.id)
                    put("policyId", pay.policyId)
                    put("policyNumber", pay.policyNumber)
                    put("customerId", pay.customerId)
                    put("customerName", pay.customerName)
                    put("paidAmount", pay.paidAmount)
                    put("lateFee", pay.lateFee)
                    put("paymentDate", pay.paymentDate)
                    put("paymentMode", pay.paymentMode)
                    put("receiptNumber", pay.receiptNumber)
                    put("notes", pay.notes)
                    put("createdAt", pay.createdAt)
                })
            }
            rootObj.put("payments", payArray)

            // FollowUps Array
            val followArray = JSONArray()
            followUps.forEach { f ->
                followArray.put(JSONObject().apply {
                    put("id", f.id)
                    put("customerId", f.customerId)
                    put("customerName", f.customerName)
                    put("customerMobile", f.customerMobile)
                    put("date", f.date)
                    put("time", f.time)
                    put("notes", f.notes)
                    put("status", f.status)
                    put("createdAt", f.createdAt)
                })
            }
            rootObj.put("followUps", followArray)

            // Documents Array
            val docArray = JSONArray()
            documents.forEach { d ->
                docArray.put(JSONObject().apply {
                    put("id", d.id)
                    put("customerId", d.customerId ?: -1L)
                    put("customerName", d.customerName)
                    put("policyId", d.policyId ?: -1L)
                    put("docType", d.docType)
                    put("title", d.title)
                    put("fileUri", d.fileUri)
                    put("fileSize", d.fileSize)
                    put("uploadDate", d.uploadDate)
                    put("createdAt", d.createdAt)
                })
            }
            rootObj.put("documents", docArray)

            progressCallback?.invoke(0.6f)

            val jsonString = rootObj.toString(2)
            val jsonBytes = jsonString.toByteArray(Charsets.UTF_8)
            val sizeKb = jsonBytes.size / 1024L
            val sizeFormatted = if (sizeKb > 1024) String.format(Locale.US, "%.1f MB", sizeKb / 1024.0) else "$sizeKb KB"

            // 3. Save to local storage
            val localDir = getLocalBackupDir(context)
            val backupFile = File(localDir, "$backupId.json")
            FileOutputStream(backupFile).use { fos ->
                fos.write(jsonBytes)
            }

            progressCallback?.invoke(0.8f)

            // 4. Upload to Firebase Storage & Save to Firestore if available
            var cloudUrl: String? = null
            var destination = "Local Device"

            try {
                val storage = FirebaseStorage.getInstance()
                val storageRef = storage.reference.child("backups/$uid/$backupId.json")
                storageRef.putBytes(jsonBytes).await()
                cloudUrl = storageRef.downloadUrl.await().toString()
                destination = "Firebase Cloud"
            } catch (e: Throwable) {
                Log.w(TAG, "Firebase Storage upload skipped/failed: ${e.localizedMessage}")
            }

            val endTime = System.currentTimeMillis()
            val durationSec = Math.max(1, ((endTime - startTime) / 1000)).toString() + " sec"

            val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val dateStr = dateFormat.format(Date(startTime))
            val timeStr = timeFormat.format(Date(startTime))

            val historyItem = BackupHistoryItemData(
                id = backupId,
                date = dateStr,
                time = timeStr,
                timestamp = startTime,
                size = sizeFormatted,
                sizeBytes = jsonBytes.size.toLong(),
                duration = durationSec,
                status = "Success",
                destination = destination,
                totalCustomers = customers.size,
                totalPolicies = policies.size,
                totalPayments = payments.size,
                totalDocuments = documents.size,
                agentUid = uid,
                localFilePath = backupFile.absolutePath,
                cloudDownloadUrl = cloudUrl
            )

            // Save metadata to Firestore
            try {
                if (uid != "local_agent") {
                    val firestore = FirebaseFirestore.getInstance()
                    val metaMap = mapOf(
                        "id" to historyItem.id,
                        "date" to historyItem.date,
                        "time" to historyItem.time,
                        "timestamp" to historyItem.timestamp,
                        "size" to historyItem.size,
                        "sizeBytes" to historyItem.sizeBytes,
                        "duration" to historyItem.duration,
                        "status" to historyItem.status,
                        "destination" to historyItem.destination,
                        "totalCustomers" to historyItem.totalCustomers,
                        "totalPolicies" to historyItem.totalPolicies,
                        "totalPayments" to historyItem.totalPayments,
                        "totalDocuments" to historyItem.totalDocuments,
                        "agentUid" to historyItem.agentUid,
                        "cloudDownloadUrl" to historyItem.cloudDownloadUrl
                    )
                    firestore.collection("agent_backups")
                        .document(uid)
                        .collection("history")
                        .document(backupId)
                        .set(metaMap)
                        .await()
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Firestore metadata write failed: ${e.localizedMessage}")
            }

            // Save to local history log
            saveHistoryToLocalPrefs(context, historyItem)

            progressCallback?.invoke(1.0f)
            Log.i(TAG, "Full backup created successfully: $backupId")
            Result.success(historyItem)
        } catch (e: Throwable) {
            Log.e(TAG, "Error creating full backup: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }

    suspend fun restoreBackup(
        context: Context,
        db: AppDatabase,
        backupItem: BackupHistoryItemData,
        replaceExisting: Boolean = true,
        progressCallback: ((Float) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            progressCallback?.invoke(0.1f)

            // 1. Obtain JSON Content
            var jsonContent: String? = null

            if (!backupItem.localFilePath.isNullOrBlank()) {
                val file = File(backupItem.localFilePath)
                if (file.exists()) {
                    jsonContent = file.readText(Charsets.UTF_8)
                }
            }

            if (jsonContent.isNullOrBlank() && !backupItem.cloudDownloadUrl.isNullOrBlank()) {
                try {
                    val storageRef = FirebaseStorage.getInstance().getReferenceFromUrl(backupItem.cloudDownloadUrl)
                    val maxBytes = 50L * 1024L * 1024L // 50MB
                    val bytes = storageRef.getBytes(maxBytes).await()
                    jsonContent = String(bytes, Charsets.UTF_8)
                } catch (e: Throwable) {
                    Log.w(TAG, "Cloud download failed: ${e.localizedMessage}")
                }
            }

            if (jsonContent.isNullOrBlank()) {
                return@withContext Result.failure(Exception("Backup file content not found or inaccessible."))
            }

            progressCallback?.invoke(0.3f)

            // 2. Parse JSON Package
            val rootObj = JSONObject(jsonContent)
            val backupAgentUid = rootObj.optString("agentUid", "")
            val currentUid = getAgentUid()

            if (backupAgentUid.isNotBlank() && currentUid != "local_agent" && backupAgentUid != currentUid) {
                Log.w(TAG, "Restoring backup belonging to another UID ($backupAgentUid vs $currentUid)")
            }

            // Parse Agent Profile
            val profileObj = rootObj.optJSONObject("agentProfile")
            var restoredProfile: AgentProfileEntity? = null
            if (profileObj != null) {
                restoredProfile = AgentProfileEntity(
                    id = profileObj.optInt("id", 1),
                    agentName = profileObj.optString("agentName", "Agent"),
                    agencyCode = profileObj.optString("agencyCode", ""),
                    branchCode = profileObj.optString("branchCode", ""),
                    branchName = profileObj.optString("branchName", ""),
                    licenseNumber = profileObj.optString("licenseNumber", ""),
                    email = profileObj.optString("email", ""),
                    mobile = profileObj.optString("mobile", ""),
                    photoUri = profileObj.optString("photoUri", ""),
                    themeMode = profileObj.optString("themeMode", "System"),
                    isDarkMode = profileObj.optBoolean("isDarkMode", false),
                    pinCode = profileObj.optString("pinCode", ""),
                    isBiometricEnabled = profileObj.optBoolean("isBiometricEnabled", false),
                    autoLogoutMinutes = profileObj.optInt("autoLogoutMinutes", 15),
                    isAutoSyncEnabled = profileObj.optBoolean("isAutoSyncEnabled", true),
                    lastSyncedTime = profileObj.optString("lastSyncedTime", "Just now")
                )
            }

            // Parse Customers
            val custArray = rootObj.optJSONArray("customers") ?: JSONArray()
            val restoredCustomers = mutableListOf<CustomerEntity>()
            for (i in 0 until custArray.length()) {
                val obj = custArray.getJSONObject(i)
                restoredCustomers.add(
                    CustomerEntity(
                        id = obj.optLong("id", 0L),
                        name = obj.optString("name", "Unknown"),
                        mobile = obj.optString("mobile", ""),
                        whatsapp = obj.optString("whatsapp", ""),
                        email = obj.optString("email", ""),
                        address = obj.optString("address", ""),
                        dob = obj.optString("dob", ""),
                        anniversary = obj.optString("anniversary", ""),
                        aadhaar = obj.optString("aadhaar", ""),
                        pan = obj.optString("pan", ""),
                        occupation = obj.optString("occupation", ""),
                        notes = obj.optString("notes", ""),
                        photoUri = obj.optString("photoUri", "").ifBlank { null },
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }

            // Parse Policies
            val polArray = rootObj.optJSONArray("policies") ?: JSONArray()
            val restoredPolicies = mutableListOf<PolicyEntity>()
            for (i in 0 until polArray.length()) {
                val obj = polArray.getJSONObject(i)
                restoredPolicies.add(
                    PolicyEntity(
                        id = obj.optLong("id", 0L),
                        policyNumber = obj.optString("policyNumber", ""),
                        customerId = obj.optLong("customerId", 0L),
                        customerName = obj.optString("customerName", ""),
                        planName = obj.optString("planName", ""),
                        premiumAmount = obj.optDouble("premiumAmount", 0.0),
                        sumAssured = obj.optDouble("sumAssured", 0.0),
                        premiumMode = obj.optString("premiumMode", "Yearly"),
                        dueDate = obj.optString("dueDate", ""),
                        maturityDate = obj.optString("maturityDate", ""),
                        status = obj.optString("status", "Active"),
                        nominee = obj.optString("nominee", ""),
                        policyTerm = obj.optInt("policyTerm", 20),
                        premiumPayingTerm = obj.optInt("premiumPayingTerm", 16),
                        issueDate = obj.optString("issueDate", ""),
                        gracePeriodDays = obj.optInt("gracePeriodDays", 30),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }

            // Parse Payments
            val payArray = rootObj.optJSONArray("payments") ?: JSONArray()
            val restoredPayments = mutableListOf<PaymentEntity>()
            for (i in 0 until payArray.length()) {
                val obj = payArray.getJSONObject(i)
                restoredPayments.add(
                    PaymentEntity(
                        id = obj.optLong("id", 0L),
                        policyId = obj.optLong("policyId", 0L),
                        policyNumber = obj.optString("policyNumber", ""),
                        customerId = obj.optLong("customerId", 0L),
                        customerName = obj.optString("customerName", ""),
                        paidAmount = obj.optDouble("paidAmount", 0.0),
                        lateFee = obj.optDouble("lateFee", 0.0),
                        paymentDate = obj.optString("paymentDate", ""),
                        paymentMode = obj.optString("paymentMode", "Cash"),
                        receiptNumber = obj.optString("receiptNumber", ""),
                        notes = obj.optString("notes", ""),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }

            // Parse FollowUps
            val followArray = rootObj.optJSONArray("followUps") ?: JSONArray()
            val restoredFollowUps = mutableListOf<FollowUpEntity>()
            for (i in 0 until followArray.length()) {
                val obj = followArray.getJSONObject(i)
                restoredFollowUps.add(
                    FollowUpEntity(
                        id = obj.optLong("id", 0L),
                        customerId = obj.optLong("customerId", 0L),
                        customerName = obj.optString("customerName", ""),
                        customerMobile = obj.optString("customerMobile", ""),
                        date = obj.optString("date", ""),
                        time = obj.optString("time", "10:00 AM"),
                        notes = obj.optString("notes", ""),
                        status = obj.optString("status", "Pending"),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }

            // Parse Documents
            val docArray = rootObj.optJSONArray("documents") ?: JSONArray()
            val restoredDocs = mutableListOf<DocumentEntity>()
            for (i in 0 until docArray.length()) {
                val obj = docArray.getJSONObject(i)
                val cId = obj.optLong("customerId", -1L).let { if (it <= 0) null else it }
                val pId = obj.optLong("policyId", -1L).let { if (it <= 0) null else it }
                restoredDocs.add(
                    DocumentEntity(
                        id = obj.optLong("id", 0L),
                        customerId = cId,
                        customerName = obj.optString("customerName", ""),
                        policyId = pId,
                        docType = obj.optString("docType", "Other"),
                        title = obj.optString("title", "Document"),
                        fileUri = obj.optString("fileUri", ""),
                        fileSize = obj.optString("fileSize", "1.2 MB"),
                        uploadDate = obj.optString("uploadDate", ""),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }

            progressCallback?.invoke(0.7f)

            // 3. Write into Room DB inside transaction
            db.runInTransaction {
                kotlinx.coroutines.runBlocking {
                    if (restoredProfile != null) {
                        db.agentDao().saveAgentProfile(restoredProfile)
                    }

                    restoredCustomers.forEach { c ->
                        db.customerDao().insertCustomer(c)
                    }

                    restoredPolicies.forEach { p ->
                        db.policyDao().insertPolicy(p)
                    }

                    restoredPayments.forEach { pay ->
                        db.paymentDao().insertPayment(pay)
                    }

                    restoredFollowUps.forEach { f ->
                        db.followUpDao().insertFollowUp(f)
                    }

                    restoredDocs.forEach { d ->
                        db.documentDao().insertDocument(d)
                    }
                }
            }

            progressCallback?.invoke(1.0f)
            val msg = "Restored ${restoredCustomers.size} customers, ${restoredPolicies.size} policies, and ${restoredPayments.size} receipts successfully."
            Log.i(TAG, msg)
            Result.success(msg)
        } catch (e: Throwable) {
            Log.e(TAG, "Error restoring backup: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }

    private fun getLocalBackupDir(context: Context): File {
        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val backupDir = File(docsDir, BACKUP_DIR_NAME)
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }
        return if (backupDir.canWrite()) backupDir else context.filesDir
    }

    private fun saveHistoryToLocalPrefs(context: Context, item: BackupHistoryItemData) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingJson = prefs.getString(KEY_HISTORY_LOG, "[]") ?: "[]"
        val array = JSONArray(existingJson)

        val newObj = JSONObject().apply {
            put("id", item.id)
            put("date", item.date)
            put("time", item.time)
            put("timestamp", item.timestamp)
            put("size", item.size)
            put("sizeBytes", item.sizeBytes)
            put("duration", item.duration)
            put("status", item.status)
            put("destination", item.destination)
            put("totalCustomers", item.totalCustomers)
            put("totalPolicies", item.totalPolicies)
            put("totalPayments", item.totalPayments)
            put("totalDocuments", item.totalDocuments)
            put("agentUid", item.agentUid)
            put("localFilePath", item.localFilePath ?: "")
            put("cloudDownloadUrl", item.cloudDownloadUrl ?: "")
        }

        val updatedArray = JSONArray()
        updatedArray.put(newObj)
        for (i in 0 until Math.min(15, array.length())) {
            updatedArray.put(array.getJSONObject(i))
        }

        prefs.edit().putString(KEY_HISTORY_LOG, updatedArray.toString()).apply()
    }

    fun getLocalHistory(context: Context): List<BackupHistoryItemData> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingJson = prefs.getString(KEY_HISTORY_LOG, "[]") ?: "[]"
        val list = mutableListOf<BackupHistoryItemData>()

        try {
            val array = JSONArray(existingJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    BackupHistoryItemData(
                        id = obj.optString("id", ""),
                        date = obj.optString("date", ""),
                        time = obj.optString("time", ""),
                        timestamp = obj.optLong("timestamp", 0L),
                        size = obj.optString("size", "0 KB"),
                        sizeBytes = obj.optLong("sizeBytes", 0L),
                        duration = obj.optString("duration", "0 sec"),
                        status = obj.optString("status", "Success"),
                        destination = obj.optString("destination", "Firebase Cloud"),
                        totalCustomers = obj.optInt("totalCustomers", 0),
                        totalPolicies = obj.optInt("totalPolicies", 0),
                        totalPayments = obj.optInt("totalPayments", 0),
                        totalDocuments = obj.optInt("totalDocuments", 0),
                        agentUid = obj.optString("agentUid", ""),
                        localFilePath = obj.optString("localFilePath", "").ifBlank { null },
                        cloudDownloadUrl = obj.optString("cloudDownloadUrl", "").ifBlank { null }
                    )
                )
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to parse local history: ${e.localizedMessage}")
        }
        return list
    }

    fun scheduleAutoBackupWork(context: Context, frequency: String, wifiOnly: Boolean, chargingOnly: Boolean) {
        val workManager = WorkManager.getInstance(context)

        val repeatIntervalDays = when (frequency.lowercase(Locale.ROOT)) {
            "weekly" -> 7L
            "monthly" -> 30L
            else -> 1L
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(chargingOnly)
            .build()

        val autoBackupRequest = PeriodicWorkRequestBuilder<com.example.notifications.AutoBackupWorker>(
            repeatIntervalDays, TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "LIC_AUTO_BACKUP_WORK",
            ExistingPeriodicWorkPolicy.UPDATE,
            autoBackupRequest
        )

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_AUTO_BACKUP_ENABLED, true)
            .putString(KEY_AUTO_BACKUP_FREQ, frequency)
            .putBoolean(KEY_WIFI_ONLY, wifiOnly)
            .putBoolean(KEY_CHARGING_ONLY, chargingOnly)
            .apply()
    }

    fun cancelAutoBackupWork(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork("LIC_AUTO_BACKUP_WORK")
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_BACKUP_ENABLED, false).apply()
    }

    suspend fun restoreFromUri(
        context: Context,
        db: AppDatabase,
        uri: android.net.Uri
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val jsonContent = contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            } ?: return@withContext Result.failure(Exception("Failed to read backup file from selected location."))

            val dummyItem = BackupHistoryItemData(
                id = "imported_${System.currentTimeMillis()}",
                localFilePath = ""
            )
            val tempFile = File(context.cacheDir, "imported_temp_backup.json")
            tempFile.writeText(jsonContent, Charsets.UTF_8)
            val itemWithFile = dummyItem.copy(localFilePath = tempFile.absolutePath)
            restoreBackup(context, db, itemWithFile)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed restoring from Uri: ${e.localizedMessage}", e)
            Result.failure(e)
        }
    }
}
