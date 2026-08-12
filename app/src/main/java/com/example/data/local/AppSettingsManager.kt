package com.example.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.data.backup.BackupManager
import com.example.data.remote.FirebaseStorageManager
import com.example.data.remote.FirebaseSyncManager
import com.example.notifications.NotificationEngine
import com.example.whatsapp.WhatsAppAutomation
import com.example.whatsapp.WhatsAppLanguage
import com.example.whatsapp.WhatsAppTemplateType
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class AppSettingsData(
    // Agent Profile
    val agentName: String = "Pintu Ojha",
    val agencyCode: String = "LIC-AG-89421",
    val branchCode: String = "08B",
    val branchName: String = "Bhubaneswar Branch",
    val mobileNumber: String = "+91 98765 43210",
    val emailAddress: String = "pintu.lic.agent@gmail.com",
    val officeAddress: String = "Plot 102, Janpath, Bhubaneswar, Odisha",
    val photoUri: String = "",

    // App Preferences
    val isDarkMode: Boolean = true,
    val isSystemTheme: Boolean = false,
    val selectedLanguage: String = "English",
    val selectedFontSize: String = "Medium",

    // Notification Settings
    val isPremiumReminder: Boolean = true,
    val isDueTodayReminder: Boolean = true,
    val isTomorrowReminder: Boolean = true,
    val isUpcomingReminder: Boolean = true,
    val isOverdueReminder: Boolean = true,
    val isWhatsAppReminder: Boolean = true,
    val selectedReminderTime: String = "09:00 AM",
    val selectedNotificationSound: String = "LIC Chime",
    val isVibrationEnabled: Boolean = true,

    // WhatsApp Settings
    val whatsappDefaultTemplate: String = "TODAY_DUE",
    val whatsappLanguage: String = "ENGLISH",
    val agentSignature: String = "Pintu Ojha (Authorized LIC Agent)",
    val customFooter: String = "LIC India — Your Security, Our Commitment",

    // Backup Settings
    val isAutoBackupEnabled: Boolean = true,
    val isCloudSyncEnabled: Boolean = true,
    val autoBackupFrequency: String = "Daily",
    val isWifiOnlyBackup: Boolean = true,
    val isChargingOnlyBackup: Boolean = false,
    val lastBackupText: String = "Today, 05:30 PM • 14.2 MB",

    // Receipt Settings
    val receiptPrefix: String = "LIC-",
    val selectedReceiptSize: String = "A5",
    val isReceiptHeaderEnabled: Boolean = true,
    val receiptHeaderTitle: String = "LIC Premium Official Receipt",
    val isAgentSignatureOnReceipt: Boolean = true,
    val isQrCodeOnReceipt: Boolean = true,
    val isAutoReceiptNumber: Boolean = true,

    // Payment Settings
    val accountHolderName: String = "GEETANJALI SUTAR",
    val upiVpaId: String = "895412036@lic",

    // Security Settings
    val isAppLockEnabled: Boolean = false,
    val isPinLockEnabled: Boolean = false,
    val pinCode: String = "",
    val isFingerprintEnabled: Boolean = false,
    val isFaceUnlockEnabled: Boolean = false,
    val selectedAutoLockTime: String = "5 Min"
)

object AppSettingsManager {
    private const val TAG = "AppSettingsManager"
    private const val PREFS_SETTINGS = "lic_app_settings_prefs"

    fun getSettings(context: Context, agentProfile: AgentProfileEntity? = null): AppSettingsData {
        val prefs = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)

        val profileName = agentProfile?.agentName ?: prefs.getString("agent_name", "Pintu Ojha") ?: "Pintu Ojha"
        val profileAgencyCode = agentProfile?.agencyCode ?: prefs.getString("agency_code", "LIC-AG-89421") ?: "LIC-AG-89421"
        val profileBranchCode = agentProfile?.branchCode ?: prefs.getString("branch_code", "08B") ?: "08B"
        val profileBranchName = agentProfile?.branchName ?: prefs.getString("branch_name", "Bhubaneswar Branch") ?: "Bhubaneswar Branch"
        val profileMobile = agentProfile?.mobile ?: prefs.getString("mobile_number", "+91 98765 43210") ?: "+91 98765 43210"
        val profileEmail = agentProfile?.email ?: prefs.getString("email_address", "pintu.lic.agent@gmail.com") ?: "pintu.lic.agent@gmail.com"
        val profileAddress = agentProfile?.officeAddress ?: prefs.getString("office_address", "Plot 102, Janpath, Bhubaneswar, Odisha") ?: "Plot 102, Janpath, Bhubaneswar, Odisha"
        val profilePhoto = agentProfile?.photoUri ?: prefs.getString("photo_uri", "") ?: ""

        return AppSettingsData(
            agentName = profileName,
            agencyCode = profileAgencyCode,
            branchCode = profileBranchCode,
            branchName = profileBranchName,
            mobileNumber = profileMobile,
            emailAddress = profileEmail,
            officeAddress = profileAddress,
            photoUri = profilePhoto,

            isDarkMode = prefs.getBoolean("is_dark_mode", false),
            isSystemTheme = prefs.getBoolean("is_system_theme", false),
            selectedLanguage = prefs.getString("selected_language", "English") ?: "English",
            selectedFontSize = prefs.getString("selected_font_size", "Medium") ?: "Medium",

            isPremiumReminder = NotificationEngine.isNotificationsEnabled(context),
            isDueTodayReminder = NotificationEngine.isTodayReminderEnabled(context),
            isTomorrowReminder = NotificationEngine.isTomorrowReminderEnabled(context),
            isUpcomingReminder = NotificationEngine.isWeeklyReminderEnabled(context),
            isOverdueReminder = NotificationEngine.isOverdueReminderEnabled(context),
            isWhatsAppReminder = WhatsAppAutomation.isWhatsAppRemindersEnabled(context),
            selectedReminderTime = prefs.getString("reminder_time", "09:00 AM") ?: "09:00 AM",
            selectedNotificationSound = prefs.getString("notification_sound", "LIC Chime") ?: "LIC Chime",
            isVibrationEnabled = NotificationEngine.isVibrationEnabled(context),

            whatsappDefaultTemplate = WhatsAppAutomation.getDefaultTemplate(context).name,
            whatsappLanguage = WhatsAppAutomation.getLanguage(context).name,
            agentSignature = prefs.getString("whatsapp_agent_signature", "Pintu Ojha (Authorized LIC Agent)") ?: "Pintu Ojha (Authorized LIC Agent)",
            customFooter = WhatsAppAutomation.getCustomFooter(context),

            isAutoBackupEnabled = prefs.getBoolean("auto_backup_enabled", true),
            isCloudSyncEnabled = prefs.getBoolean("cloud_sync_enabled", true),
            autoBackupFrequency = prefs.getString("auto_backup_freq", "Daily") ?: "Daily",
            isWifiOnlyBackup = prefs.getBoolean("wifi_only_backup", true),
            isChargingOnlyBackup = prefs.getBoolean("charging_only_backup", false),
            lastBackupText = prefs.getString("last_backup_text", "Today, 05:30 PM • 14.2 MB") ?: "Today, 05:30 PM • 14.2 MB",

            receiptPrefix = prefs.getString("receipt_prefix", "LIC-") ?: "LIC-",
            selectedReceiptSize = prefs.getString("receipt_size", "A5") ?: "A5",
            isReceiptHeaderEnabled = prefs.getBoolean("receipt_header_enabled", true),
            receiptHeaderTitle = prefs.getString("receipt_header_title", "LIC Premium Official Receipt") ?: "LIC Premium Official Receipt",
            isAgentSignatureOnReceipt = prefs.getBoolean("receipt_signature_enabled", true),
            isQrCodeOnReceipt = prefs.getBoolean("receipt_qrcode_enabled", true),
            isAutoReceiptNumber = prefs.getBoolean("receipt_auto_num_enabled", true),

            accountHolderName = prefs.getString("payment_account_holder", "GEETANJALI SUTAR") ?: "GEETANJALI SUTAR",
            upiVpaId = prefs.getString("payment_upi_vpa", "895412036@lic") ?: "895412036@lic",

            isAppLockEnabled = prefs.getBoolean("app_lock_enabled", false),
            isPinLockEnabled = prefs.getBoolean("pin_lock_enabled", false),
            pinCode = com.example.util.SecurePreferences.getSecureToken(context, "encrypted_pin_code").ifBlank {
                val legacy = prefs.getString("pin_code", "") ?: ""
                if (legacy.isNotBlank()) {
                    com.example.util.SecurePreferences.saveSecureToken(context, "encrypted_pin_code", legacy)
                    prefs.edit().remove("pin_code").apply()
                }
                legacy
            },
            isFingerprintEnabled = com.example.util.SecurityUtils.isBiometricEnabled(context),
            isFaceUnlockEnabled = prefs.getBoolean("face_unlock_enabled", false),
            selectedAutoLockTime = prefs.getString("auto_lock_time", "5 Min") ?: "5 Min"
        )
    }

    suspend fun saveSettings(
        context: Context,
        settings: AppSettingsData,
        db: AppDatabase?,
        syncManager: FirebaseSyncManager? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString("agent_name", settings.agentName)
                putString("agency_code", settings.agencyCode)
                putString("branch_code", settings.branchCode)
                putString("branch_name", settings.branchName)
                putString("mobile_number", settings.mobileNumber)
                putString("email_address", settings.emailAddress)
                putString("office_address", settings.officeAddress)
                putString("photo_uri", settings.photoUri)

                putBoolean("is_dark_mode", settings.isDarkMode)
                putBoolean("is_system_theme", settings.isSystemTheme)
                putString("selected_language", settings.selectedLanguage)
                putString("selected_font_size", settings.selectedFontSize)

                putBoolean("is_premium_reminder", settings.isPremiumReminder)
                putBoolean("is_due_today_reminder", settings.isDueTodayReminder)
                putBoolean("is_tomorrow_reminder", settings.isTomorrowReminder)
                putBoolean("is_upcoming_reminder", settings.isUpcomingReminder)
                putBoolean("is_overdue_reminder", settings.isOverdueReminder)
                putBoolean("is_whatsapp_reminder", settings.isWhatsAppReminder)
                putString("reminder_time", settings.selectedReminderTime)
                putString("notification_sound", settings.selectedNotificationSound)
                putBoolean("is_vibration_enabled", settings.isVibrationEnabled)

                putString("whatsapp_agent_signature", settings.agentSignature)

                putBoolean("auto_backup_enabled", settings.isAutoBackupEnabled)
                putBoolean("cloud_sync_enabled", settings.isCloudSyncEnabled)
                putString("auto_backup_freq", settings.autoBackupFrequency)
                putBoolean("wifi_only_backup", settings.isWifiOnlyBackup)
                putBoolean("charging_only_backup", settings.isChargingOnlyBackup)
                putString("last_backup_text", settings.lastBackupText)

                putString("receipt_prefix", settings.receiptPrefix)
                putString("receipt_size", settings.selectedReceiptSize)
                putBoolean("receipt_header_enabled", settings.isReceiptHeaderEnabled)
                putString("receipt_header_title", settings.receiptHeaderTitle)
                putBoolean("receipt_signature_enabled", settings.isAgentSignatureOnReceipt)
                putBoolean("receipt_qrcode_enabled", settings.isQrCodeOnReceipt)
                putBoolean("receipt_auto_num_enabled", settings.isAutoReceiptNumber)

                putString("payment_account_holder", settings.accountHolderName)
                putString("payment_upi_vpa", settings.upiVpaId)

                putBoolean("app_lock_enabled", settings.isAppLockEnabled)
                putBoolean("pin_lock_enabled", settings.isPinLockEnabled)
                remove("pin_code") // Do not store plain text PIN in SharedPreferences
                com.example.util.SecurePreferences.saveSecureToken(context, "encrypted_pin_code", settings.pinCode)
                putBoolean("fingerprint_enabled", settings.isFingerprintEnabled)
                putBoolean("face_unlock_enabled", settings.isFaceUnlockEnabled)
                putString("auto_lock_time", settings.selectedAutoLockTime)
                apply()
            }
            com.example.util.SecurityUtils.setBiometricEnabled(context, settings.isFingerprintEnabled)

            // Sync Notification Engine Settings & Update Workers Immediately
            val notifPrefs = NotificationEngine.getPrefs(context)
            notifPrefs.edit().apply {
                putBoolean("notifications_enabled", settings.isPremiumReminder)
                putBoolean("reminder_today_enabled", settings.isDueTodayReminder)
                putBoolean("reminder_tomorrow_enabled", settings.isTomorrowReminder)
                putBoolean("reminder_weekly_enabled", settings.isUpcomingReminder)
                putBoolean("reminder_overdue_enabled", settings.isOverdueReminder)
                putString("reminder_time", settings.selectedReminderTime)
                putBoolean("vibration_enabled", settings.isVibrationEnabled)
                putBoolean("sound_enabled", settings.selectedNotificationSound != "Silent")
                apply()
            }
            NotificationEngine.createNotificationChannel(context)
            NotificationEngine.scheduleBackgroundWorkers(context)

            // Sync WhatsApp Preferences
            WhatsAppAutomation.setWhatsAppRemindersEnabled(context, settings.isWhatsAppReminder)
            WhatsAppAutomation.setCustomFooter(context, settings.customFooter)
            try {
                WhatsAppAutomation.setLanguage(context, WhatsAppLanguage.valueOf(settings.whatsappLanguage.uppercase()))
            } catch (_: Throwable) {}
            try {
                WhatsAppAutomation.setDefaultTemplate(context, WhatsAppTemplateType.valueOf(settings.whatsappDefaultTemplate.uppercase()))
            } catch (_: Throwable) {}

            // Schedule / Cancel Auto Backup
            if (settings.isAutoBackupEnabled) {
                BackupManager.scheduleAutoBackupWork(
                    context,
                    settings.autoBackupFrequency,
                    settings.isWifiOnlyBackup,
                    settings.isChargingOnlyBackup
                )
            } else {
                BackupManager.cancelAutoBackupWork(context)
            }

            // Update Room AgentProfileEntity
            if (db != null) {
                val existing = db.agentDao().getAgentProfileSync() ?: AgentProfileEntity()
                val updatedProfile = existing.copy(
                    agentName = settings.agentName,
                    agencyCode = settings.agencyCode,
                    branchCode = settings.branchCode,
                    branchName = settings.branchName,
                    email = settings.emailAddress,
                    mobile = settings.mobileNumber,
                    officeAddress = settings.officeAddress,
                    photoUri = settings.photoUri,
                    isDarkMode = settings.isDarkMode,
                    pinCode = settings.pinCode,
                    isBiometricEnabled = settings.isFingerprintEnabled,
                    isAutoSyncEnabled = settings.isCloudSyncEnabled
                )
                db.agentDao().saveAgentProfile(updatedProfile)

                // Sync to Firestore
                val uid = try { FirebaseAuth.getInstance().currentUser?.uid ?: "local_agent" } catch (_: Throwable) { "local_agent" }
                syncManager?.backupAgentProfile(uid, updatedProfile)
            }
            Log.i(TAG, "Settings successfully saved locally and synced to cloud.")
        } catch (e: Throwable) {
            Log.e(TAG, "Error saving settings: ${e.localizedMessage}", e)
        }
    }

    suspend fun compressAndSaveProfilePhoto(context: Context, sourceUri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(sourceUri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            if (bitmap == null) return@withContext sourceUri.toString()

            val photoFile = File(context.filesDir, "agent_profile_photo.jpg")
            FileOutputStream(photoFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            val localUri = Uri.fromFile(photoFile).toString()

            // Background upload to Firebase Storage if available
            val uid = try { FirebaseAuth.getInstance().currentUser?.uid ?: "local_agent" } catch (_: Throwable) { "local_agent" }
            val storageManager = FirebaseStorageManager()
            val cloudUrl = storageManager.uploadProfilePhoto(uid, Uri.parse(localUri))
            val finalPhotoUri = cloudUrl ?: localUri

            Log.i(TAG, "Profile photo saved and compressed: $finalPhotoUri")
            finalPhotoUri
        } catch (e: Throwable) {
            Log.e(TAG, "Failed compressing profile photo: ${e.localizedMessage}", e)
            sourceUri.toString()
        }
    }

    fun clearCache(context: Context): Long {
        var bytesCleared = 0L
        try {
            val cacheDir = context.cacheDir
            cacheDir.listFiles()?.forEach { file ->
                bytesCleared += file.length()
                file.deleteRecursively()
            }
            val externalCache = context.externalCacheDir
            externalCache?.listFiles()?.forEach { file ->
                bytesCleared += file.length()
                file.deleteRecursively()
            }
            Log.i(TAG, "Cache cleared: $bytesCleared bytes removed")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed clearing cache: ${e.localizedMessage}")
        }
        return bytesCleared
    }

    suspend fun optimizeDatabase(db: AppDatabase): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            db.openHelper.writableDatabase.execSQL("VACUUM;")
            db.openHelper.writableDatabase.execSQL("PRAGMA optimize;")
            Log.i(TAG, "Database optimization completed successfully.")
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Database optimization notice: ${e.localizedMessage}")
            false
        }
    }

    fun savePaymentAccountHolder(context: Context, accountHolderName: String) {
        if (accountHolderName.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        prefs.edit().putString("payment_account_holder", accountHolderName.trim()).apply()
    }

    fun savePaymentUpiVpa(context: Context, upiVpaId: String) {
        if (upiVpaId.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        prefs.edit().putString("payment_upi_vpa", upiVpaId.trim()).apply()
    }
}
