package com.example.whatsapp

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.example.data.local.AppDatabase
import com.example.data.local.FollowUpEntity
import com.example.data.remote.FirebaseSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class WhatsAppLanguage(val displayName: String) {
    ENGLISH("English"),
    HINDI("Hindi"),
    ODIA("Odia")
}

enum class WhatsAppTemplateType(val displayName: String) {
    TODAY_DUE("Today Due"),
    TOMORROW_DUE("Tomorrow Due"),
    WEEKLY_DUE("7 Days Before Due"),
    OVERDUE("Overdue Notice"),
    PAYMENT_THANK_YOU("Payment Thank You")
}

object WhatsAppAutomation {
    private const val TAG = "WhatsAppAutomation"
    private const val PREFS_NAME = "lic_whatsapp_prefs"

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isWhatsAppRemindersEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean("is_enabled", true)
    }

    fun setWhatsAppRemindersEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean("is_enabled", enabled).apply()
    }

    fun getLanguage(context: Context): WhatsAppLanguage {
        val langName = getPrefs(context).getString("language", WhatsAppLanguage.ENGLISH.name) ?: WhatsAppLanguage.ENGLISH.name
        return try {
            WhatsAppLanguage.valueOf(langName)
        } catch (_: Exception) {
            WhatsAppLanguage.ENGLISH
        }
    }

    fun setLanguage(context: Context, language: WhatsAppLanguage) {
        getPrefs(context).edit().putString("language", language.name).apply()
    }

    fun getDefaultTemplate(context: Context): WhatsAppTemplateType {
        val templateName = getPrefs(context).getString("default_template", WhatsAppTemplateType.TODAY_DUE.name) ?: WhatsAppTemplateType.TODAY_DUE.name
        return try {
            WhatsAppTemplateType.valueOf(templateName)
        } catch (_: Exception) {
            WhatsAppTemplateType.TODAY_DUE
        }
    }

    fun setDefaultTemplate(context: Context, templateType: WhatsAppTemplateType) {
        getPrefs(context).edit().putString("default_template", templateType.name).apply()
    }

    fun getCustomFooter(context: Context): String {
        return getPrefs(context).getString("custom_footer", "LIC India — Your Security, Our Commitment") ?: "LIC India — Your Security, Our Commitment"
    }

    fun setCustomFooter(context: Context, footer: String) {
        getPrefs(context).edit().putString("custom_footer", footer).apply()
    }

    /**
     * Cleans and formats phone number for WhatsApp URL scheme.
     * Ensures country code (defaults to +91 for India if 10-digit).
     */
    fun formatPhoneNumber(rawNumber: String): String {
        val digitsOnly = rawNumber.replace(Regex("[^0-9]"), "")
        return when {
            digitsOnly.length == 10 -> "91$digitsOnly"
            digitsOnly.length > 10 && digitsOnly.startsWith("91") -> digitsOnly
            digitsOnly.length > 10 -> digitsOnly
            else -> digitsOnly
        }
    }

    /**
     * Generates dynamic message based on template, language, policy data, agent info, and custom signature.
     */
    fun generateMessage(
        context: Context,
        templateType: WhatsAppTemplateType,
        customerName: String,
        policyNumber: String,
        planName: String,
        premiumAmount: Double,
        dueDate: String,
        outstandingBalance: Double = premiumAmount,
        agentName: String = "LIC Agent",
        agentMobile: String = "",
        overrideLanguage: WhatsAppLanguage? = null
    ): String {
        val lang = overrideLanguage ?: getLanguage(context)
        val footer = getCustomFooter(context)
        val formattedAmount = String.format("%,.0f", premiumAmount)
        val formattedOutstanding = String.format("%,.0f", outstandingBalance)

        return when (lang) {
            WhatsAppLanguage.ENGLISH -> generateEnglishMessage(
                templateType, customerName, policyNumber, planName,
                formattedAmount, dueDate, formattedOutstanding, agentName, agentMobile, footer
            )
            WhatsAppLanguage.HINDI -> generateHindiMessage(
                templateType, customerName, policyNumber, planName,
                formattedAmount, dueDate, formattedOutstanding, agentName, agentMobile, footer
            )
            WhatsAppLanguage.ODIA -> generateOdiaMessage(
                templateType, customerName, policyNumber, planName,
                formattedAmount, dueDate, formattedOutstanding, agentName, agentMobile, footer
            )
        }
    }

    private fun generateEnglishMessage(
        templateType: WhatsAppTemplateType,
        customerName: String,
        policyNumber: String,
        planName: String,
        amount: String,
        dueDate: String,
        outstanding: String,
        agentName: String,
        agentMobile: String,
        footer: String
    ): String {
        val agentContact = if (agentMobile.isNotBlank()) "$agentName ($agentMobile)" else agentName
        return when (templateType) {
            WhatsAppTemplateType.TODAY_DUE ->
                "Dear $customerName,\n\n" +
                        "🚨 *LIC PREMIUM DUE TODAY*\n" +
                        "This is a friendly reminder that your LIC Policy #*$policyNumber* ($planName) premium of *₹$amount* is DUE TODAY (*$dueDate*).\n\n" +
                        "• Outstanding Balance: ₹$outstanding\n" +
                        "• Please pay today to maintain continuous life insurance coverage.\n\n" +
                        "Best regards,\n" +
                        "Agent: $agentContact\n" +
                        "_${footer}_"

            WhatsAppTemplateType.TOMORROW_DUE ->
                "Dear $customerName,\n\n" +
                        "⏰ *LIC PREMIUM DUE TOMORROW*\n" +
                        "Your LIC Policy #*$policyNumber* ($planName) premium of *₹$amount* is due TOMORROW (*$dueDate*).\n\n" +
                        "• Outstanding Amount: ₹$outstanding\n" +
                        "• Keep your family protected without interruption.\n\n" +
                        "Best regards,\n" +
                        "Agent: $agentContact\n" +
                        "_${footer}_"

            WhatsAppTemplateType.WEEKLY_DUE ->
                "Dear $customerName,\n\n" +
                        "📅 *UPCOMING LIC PREMIUM REMINDER*\n" +
                        "Your LIC Policy #*$policyNumber* ($planName) premium of *₹$amount* is due in 7 days on *$dueDate*.\n\n" +
                        "• Outstanding Amount: ₹$outstanding\n" +
                        "• Plan ahead for hassle-free payment.\n\n" +
                        "Best regards,\n" +
                        "Agent: $agentContact\n" +
                        "_${footer}_"

            WhatsAppTemplateType.OVERDUE ->
                "Dear $customerName,\n\n" +
                        "⚠️ *URGENT: OVERDUE LIC PREMIUM NOTICE*\n" +
                        "Your LIC Policy #*$policyNumber* ($planName) premium of *₹$amount* due on *$dueDate* is currently *OVERDUE*.\n\n" +
                        "• Outstanding Balance: ₹$outstanding\n" +
                        "• Please clear payment immediately to avoid policy lapse and late fees.\n\n" +
                        "Contact me if you need assistance:\n" +
                        "Agent: $agentContact\n" +
                        "_${footer}_"

            WhatsAppTemplateType.PAYMENT_THANK_YOU ->
                "Dear $customerName,\n\n" +
                        "✅ *PAYMENT RECEIVED — THANK YOU!*\n" +
                        "We have successfully received your premium payment of *₹$amount* for LIC Policy #*$policyNumber* ($planName).\n\n" +
                        "• Next Due Date: *$dueDate*\n" +
                        "• Your life cover remains fully active.\n\n" +
                        "Thank you for trusting LIC India!\n" +
                        "Agent: $agentContact\n" +
                        "_${footer}_"
        }
    }

    private fun generateHindiMessage(
        templateType: WhatsAppTemplateType,
        customerName: String,
        policyNumber: String,
        planName: String,
        amount: String,
        dueDate: String,
        outstanding: String,
        agentName: String,
        agentMobile: String,
        footer: String
    ): String {
        val agentContact = if (agentMobile.isNotBlank()) "$agentName ($agentMobile)" else agentName
        return when (templateType) {
            WhatsAppTemplateType.TODAY_DUE ->
                "प्रिय $customerName जी,\n\n" +
                        "🚨 *एलआईसी प्रीमियम आज देय है*\n" +
                        "आपकी एलआईसी पॉलिसी संख्या #*$policyNumber* ($planName) का प्रीमियम *₹$amount* आज (*$dueDate*) देय है।\n\n" +
                        "• बकाया राशि: ₹$outstanding\n" +
                        "• बिना किसी रुकावट बीमा सुरक्षा बनाए रखने के लिए आज ही भुगतान करें।\n\n" +
                        "शुभकामनाएं,\n" +
                        "एजेंट: $agentContact\n" +
                        "_${footer}_"

            WhatsAppTemplateType.TOMORROW_DUE ->
                "प्रिय $customerName जी,\n\n" +
                        "⏰ *एलआईसी प्रीमियम कल देय है*\n" +
                        "आपकी एलआईसी पॉलिसी संख्या #*$policyNumber* ($planName) का प्रीमियम *₹$amount* कल (*$dueDate*) देय है।\n\n" +
                        "• बकाया राशि: ₹$outstanding\n" +
                        "• परिवार की सुरक्षा निरंतर बनाए रखें।\n\n" +
                        "एजेंट: $agentContact\n" +
                        "_${footer}_"

            WhatsAppTemplateType.WEEKLY_DUE ->
                "प्रिय $customerName जी,\n\n" +
                        "📅 *आगामी एलआईसी प्रीमियम रिमाइंडर*\n" +
                        "आपकी एलआईसी पॉलिसी संख्या #*$policyNumber* ($planName) का प्रीमियम *₹$amount* 7 दिनों में (*$dueDate*) देय है।\n\n" +
                        "• बकाया राशि: ₹$outstanding\n\n" +
                        "एजेंट: $agentContact\n" +
                        "_${footer}_"

            WhatsAppTemplateType.OVERDUE ->
                "प्रिय $customerName जी,\n\n" +
                        "⚠️ *अति आवश्यक: एलआईसी बकाया प्रीमियम सूचना*\n" +
                        "आपकी एलआईसी पॉलिसी संख्या #*$policyNumber* ($planName) का दिनांक *$dueDate* का प्रीमियम *₹$amount* अभी तक *बकाया (Overdue)* है।\n\n" +
                        "• कुल बकाया राशि: ₹$outstanding\n" +
                        "• पॉलिसी लैप्स से बचने के लिए कृपया तुरंत भुगतान करें।\n\n" +
                        "संपर्क करें:\n" +
                        "एजेंट: $agentContact\n" +
                        "_${footer}_"

            WhatsAppTemplateType.PAYMENT_THANK_YOU ->
                "प्रिय $customerName जी,\n\n" +
                        "✅ *भुगतान प्राप्त हुआ — धन्यवाद!*\n" +
                        "आपकी एलआईसी पॉलिसी #*$policyNumber* ($planName) का *₹$amount* का प्रीमियम भुगतान प्राप्त हो गया है।\n\n" +
                        "• अगली देय तिथि: *$dueDate*\n" +
                        "• आपकी पॉलिसी पूर्णतः सुरक्षित है।\n\n" +
                        "एलआईसी पर विश्वास के लिए धन्यवाद!\n" +
                        "एजेंट: $agentContact\n" +
                        "_${footer}_"
        }
    }

    private fun generateOdiaMessage(
        templateType: WhatsAppTemplateType,
        customerName: String,
        policyNumber: String,
        planName: String,
        amount: String,
        dueDate: String,
        outstanding: String,
        agentName: String,
        agentMobile: String,
        footer: String
    ): String {
        val agentContact = if (agentMobile.isNotBlank()) "$agentName ($agentMobile)" else agentName
        return when (templateType) {
            WhatsAppTemplateType.TODAY_DUE ->
                "ପ୍ରିୟ $customerName,\n\n" +
                        "🚨 *LIC ପ୍ରିମିୟମ୍ ଆଜି ଦେୟ*\n" +
                        "ଆପଣଙ୍କ LIC ପଲିସି #*$policyNumber* ($planName) ର ପ୍ରିମିୟମ୍ *₹$amount* ଆଜି (*$dueDate*) ଦେୟ ଅଟେ।\n\n" +
                        "• ବକେୟା ରାଶି: ₹$outstanding\n" +
                        "• ନିରବଚ୍ଛିନ୍ନ ଜୀବନ ବୀମା ପାଇଁ ଆଜି ହିଁ ପରିଶୋଧ କରନ୍ତୁ।\n\n" +
                        "ଏଜେଣ୍ଟ: $agentContact\n" +
                        "_${footer}_"

            WhatsAppTemplateType.TOMORROW_DUE ->
                "ପ୍ରିୟ $customerName,\n\n" +
                        "⏰ *LIC ପ୍ରିମିୟମ୍ ଆସନ୍ତାକାଲି ଦେୟ*\n" +
                        "ଆପଣଙ୍କ LIC ପଲିସି #*$policyNumber* ($planName) ର ପ୍ରିମିୟମ୍ *₹$amount* ଆସନ୍ତାକାଲି (*$dueDate*) ଦେୟ ଅଟେ।\n\n" +
                        "• ବକେୟା ରାଶି: ₹$outstanding\n\n" +
                        "ଏଜେଣ୍ଟ: $agentContact\n" +
                        "_${footer}_"

            WhatsAppTemplateType.WEEKLY_DUE ->
                "ପ୍ରିୟ $customerName,\n\n" +
                        "📅 *LIC ପ୍ରିମିୟମ୍ ସୂଚନା (7 ଦିନ)*\n" +
                        "ଆପଣଙ୍କ LIC ପଲିସି #*$policyNumber* ($planName) ର ପ୍ରିମିୟମ୍ *₹$amount* 7 ଦିନ ମଧ୍ୟରେ (*$dueDate*) ଦେୟ ଅଟେ।\n\n" +
                        "• ବକେୟା ରାଶି: ₹$outstanding\n\n" +
                        "ଏଜେଣ୍ଟ: $agentContact\n" +
                        "_${footer}_"

            WhatsAppTemplateType.OVERDUE ->
                "ପ୍ରିୟ $customerName,\n\n" +
                        "⚠️ *ଜରୁରୀ: LIC ବକେୟା ପ୍ରିମିୟମ୍ ସୂଚନା*\n" +
                        "ଆପଣଙ୍କ LIC ପଲିସି #*$policyNumber* ($planName) ର ତାରିଖ *$dueDate* ର ପ୍ରିମିୟମ୍ *₹$amount* ବର୍ତ୍ତମାନ *ବକେୟା (Overdue)* ଅଛି।\n\n" +
                        "• ସମୁଦାୟ ବକେୟା: ₹$outstanding\n" +
                        "• ପଲିସି ଚାଲୁ ରଖିବା ପାଇଁ ତୁରନ୍ତ ପରିଶୋଧ କରନ୍ତୁ।\n\n" +
                        "ଏଜେଣ୍ଟ: $agentContact\n" +
                        "_${footer}_"

            WhatsAppTemplateType.PAYMENT_THANK_YOU ->
                "ପ୍ରିୟ $customerName,\n\n" +
                        "✅ *ଟଙ୍କା ମିଳିଲା — ଧନ୍ୟବାଦ!*\n" +
                        "ଆପଣଙ୍କ LIC ପଲିସି #*$policyNumber* ($planName) ର *₹$amount* ପ୍ରିମିୟମ୍ ସଫଳତାର ସହ ଗ୍ରହଣ କରାଗଲା।\n\n" +
                        "• ପରବର୍ତ୍ତୀ ଦେୟ ତାରିଖ: *$dueDate*\n" +
                        "• ଆପଣଙ୍କ ବୀମା ସୁରକ୍ଷିତ ଅଛି।\n\n" +
                        "ଏଜେଣ୍ଟ: $agentContact\n" +
                        "_${footer}_"
        }
    }

    /**
     * Launches WhatsApp Intent with pre-filled message for user review.
     * Safety Rule: NEVER sends automatically in background without user interaction.
     * Logs reminder in Room & Firestore.
     */
    fun sendWhatsAppReminder(
        context: Context,
        phoneNumber: String,
        message: String,
        customerId: Long = 0L,
        customerName: String = "",
        policyId: Long = 0L,
        policyNumber: String = "",
        templateUsed: String = "MANUAL_REMINDER"
    ): Boolean {
        if (!isWhatsAppRemindersEnabled(context)) {
            Toast.makeText(context, "WhatsApp reminders disabled in settings.", Toast.LENGTH_SHORT).show()
            Log.i(TAG, "WhatsApp reminder skipped: Disabled in settings.")
            return false
        }

        val cleanPhone = formatPhoneNumber(phoneNumber)
        if (cleanPhone.length < 10) {
            Toast.makeText(context, "Invalid phone number: '$phoneNumber'", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Failed to launch WhatsApp: Invalid phone number '$phoneNumber'")
            logReminderHistory(context, customerId, customerName, cleanPhone, policyNumber, templateUsed, message, "Failed: Invalid Phone")
            return false
        }

        if (message.isBlank()) {
            Toast.makeText(context, "Cannot send empty WhatsApp message.", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Failed to launch WhatsApp: Message is blank")
            logReminderHistory(context, customerId, customerName, cleanPhone, policyNumber, templateUsed, message, "Failed: Empty Message")
            return false
        }

        return try {
            val url = "https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(message)}"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            context.startActivity(intent)
            Log.i(TAG, "Opened WhatsApp successfully for customer: '$customerName', phone: '$cleanPhone'")
            logReminderHistory(context, customerId, customerName, cleanPhone, policyNumber, templateUsed, message, "Opened WhatsApp")
            true
        } catch (e: Exception) {
            Log.e(TAG, "WhatsApp app not installed or error launching intent: ${e.localizedMessage}", e)
            Toast.makeText(
                context,
                "WhatsApp is not installed on this device.",
                Toast.LENGTH_LONG
            ).show()
            logReminderHistory(context, customerId, customerName, cleanPhone, policyNumber, templateUsed, message, "Failed: WhatsApp Not Installed")
            false
        }
    }

    /**
     * Stores reminder log into Room database and syncs to Firestore.
     */
    private fun logReminderHistory(
        context: Context,
        customerId: Long,
        customerName: String,
        customerMobile: String,
        policyNumber: String,
        templateUsed: String,
        message: String,
        deliveryStatus: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val syncManager = FirebaseSyncManager(context)

                val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val sdfTime = SimpleDateFormat("hh:mm a", Locale.getDefault())
                val now = Date()

                val followUp = FollowUpEntity(
                    customerId = customerId,
                    customerName = customerName,
                    customerMobile = customerMobile,
                    date = sdfDate.format(now),
                    time = sdfTime.format(now),
                    notes = "WhatsApp Reminder [$templateUsed] - Policy: $policyNumber | Status: $deliveryStatus",
                    status = if (deliveryStatus.startsWith("Opened")) "Completed" else "Failed",
                    createdAt = System.currentTimeMillis()
                )

                val newId = db.followUpDao().insertFollowUp(followUp)
                Log.i(TAG, "Saved WhatsApp reminder log to Room DB with FollowUp ID: $newId")

                try {
                    syncManager.backupReminders("", db)
                } catch (e: Exception) {
                    Log.w(TAG, "Firestore sync error for reminder history: ${e.localizedMessage}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log reminder history to Room DB: ${e.localizedMessage}", e)
            }
        }
    }
}
