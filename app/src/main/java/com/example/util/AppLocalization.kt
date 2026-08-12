package com.example.util

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

val LocalAppLanguage = staticCompositionLocalOf { "English" }

@Composable
fun String.localized(): String {
    val lang = LocalAppLanguage.current
    return AppLocalization.tr(this, lang)
}

object AppLocalization {

    fun updateLocale(context: Context, language: String): Context {
        val locale = when (language.trim().lowercase()) {
            "hindi", "hi" -> Locale("hi", "IN")
            "odia", "or" -> Locale("or", "IN")
            "marathi", "mr" -> Locale("mr", "IN")
            else -> Locale("en", "US")
        }
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    private val translations: Map<String, Map<String, String>> = mapOf(
        // Navigation & Titles
        "Home" to mapOf(
            "Hindi" to "गृह",
            "Odia" to "ମୁଖ୍ୟ ପୃଷ୍ଠା",
            "Marathi" to "मुख्यपृष्ठ"
        ),
        "Clients" to mapOf(
            "Hindi" to "ग्राहक",
            "Odia" to "ଗ୍ରାହକ",
            "Marathi" to "ग्राहक"
        ),
        "Policies" to mapOf(
            "Hindi" to "पॉलिसियां",
            "Odia" to "ପଲିସି",
            "Marathi" to "पॉलिसी"
        ),
        "Reminders" to mapOf(
            "Hindi" to "अनुस्मारक",
            "Odia" to "ସ୍ମାରକ",
            "Marathi" to "स्मरणपत्रे"
        ),
        "More" to mapOf(
            "Hindi" to "अधिक",
            "Odia" to "ଅଧିକ",
            "Marathi" to "अधिक"
        ),
        "Payments" to mapOf(
            "Hindi" to "भुगतान",
            "Odia" to "ପେମେଣ୍ଟ",
            "Marathi" to "पेमेंट"
        ),
        "Reports" to mapOf(
            "Hindi" to "रिपोर्ट",
            "Odia" to "ରିପୋର୍ଟ",
            "Marathi" to "अहवाल"
        ),
        "Documents" to mapOf(
            "Hindi" to "दस्तावेज़",
            "Odia" to "ଦସ୍ତାବିଜ",
            "Marathi" to "कागदपत्रे"
        ),
        "Profile & Settings" to mapOf(
            "Hindi" to "प्रोफ़ाइल और सेटिंग्स",
            "Odia" to "ପ୍ରୋଫାଇଲ୍ ଏବଂ ସେଟିଂସ",
            "Marathi" to "प्रोफाइल आणि सेटिंग्ज"
        ),
        "Clients Directory" to mapOf(
            "Hindi" to "ग्राहक निर्देशिका",
            "Odia" to "ଗ୍ରାହକ ଡିରେକ୍ଟୋରୀ",
            "Marathi" to "ग्राहक निर्देशिका"
        ),
        "Policy Portfolio" to mapOf(
            "Hindi" to "पॉलिसी पोर्टफोलियो",
            "Odia" to "ପଲିସି ପୋର୍ଟଫୋଲିଓ",
            "Marathi" to "पॉलिसी पोर्टफोलिओ"
        ),
        "Reminders & Dues" to mapOf(
            "Hindi" to "अनुस्मारक और देय",
            "Odia" to "ସ୍ମାରକ ଏବଂ ଦେୟ",
            "Marathi" to "स्मरणपत्रे आणि देय"
        ),
        "Payment History" to mapOf(
            "Hindi" to "भुगतान इतिहास",
            "Odia" to "ପେମେଣ୍ଟ ଇତିହାସ",
            "Marathi" to "पेमेंट इतिहास"
        ),
        "Reports & Analytics" to mapOf(
            "Hindi" to "रिपोर्ट और विश्लेषण",
            "Odia" to "ରିପୋର୍ଟ ଏବଂ ବିଶ୍ଳେଷଣ",
            "Marathi" to "अहवाल आणि विश्लेषण"
        ),
        "Document Locker" to mapOf(
            "Hindi" to "दस्तावेज़ लॉकर",
            "Odia" to "ଦସ୍ତାବିଜ ଲକର",
            "Marathi" to "कागदपत्र लॉकर"
        ),

        // Dashboard Concepts
        "Dashboard" to mapOf(
            "Hindi" to "डैशबोर्ड",
            "Odia" to "ଡ୍ୟାସବୋର୍ଡ",
            "Marathi" to "डॅशबोर्ड"
        ),
        "Quick Actions" to mapOf(
            "Hindi" to "त्वरित कार्रवाई",
            "Odia" to "ଦ୍ରୁତ କାର୍ଯ୍ୟ",
            "Marathi" to "जलद कृती"
        ),
        "Due Today" to mapOf(
            "Hindi" to "आज देय",
            "Odia" to "ଆଜି ଦେୟ",
            "Marathi" to "आज देय"
        ),
        "Tomorrow Dues" to mapOf(
            "Hindi" to "कल देय",
            "Odia" to "କାଲି ଦେୟ",
            "Marathi" to "उद्या देय"
        ),
        "Upcoming Dues" to mapOf(
            "Hindi" to "आगामी देय",
            "Odia" to "ଆଗାମୀ ଦେୟ",
            "Marathi" to "आगामी देय"
        ),
        "Overdue" to mapOf(
            "Hindi" to "अतिदेय",
            "Odia" to "ଅତିଦେୟ",
            "Marathi" to "थकबाकी"
        ),
        "Active Policies" to mapOf(
            "Hindi" to "सक्रिय पॉलिसियां",
            "Odia" to "ସକ୍ରିୟ ପଲିସି",
            "Marathi" to "सक्रिय पॉलिसी"
        ),
        "Lapsed Policies" to mapOf(
            "Hindi" to "व्यपगत पॉलिसियां",
            "Odia" to "ଲାପ୍ସଡ ପଲିସି",
            "Marathi" to "लॅप्स पॉलिसी"
        ),
        "Matured Policies" to mapOf(
            "Hindi" to "परिपक्व पॉलिसियां",
            "Odia" to "ମ୍ୟାଚୁଅର୍ଡ ପଲିସି",
            "Marathi" to "मुदत संपलेली पॉलिसी"
        ),
        "Collect Premium" to mapOf(
            "Hindi" to "प्रीमियम जमा करें",
            "Odia" to "ପ୍ରିମିୟମ୍ ସଂଗ୍ରହ",
            "Marathi" to "प्रीमियम गोळा करा"
        ),
        "Add Client" to mapOf(
            "Hindi" to "ग्राहक जोड़ें",
            "Odia" to "ଗ୍ରାହକ ଯୋଡନ୍ତୁ",
            "Marathi" to "ग्राहक जोडा"
        ),
        "Add Policy" to mapOf(
            "Hindi" to "पॉलिसी जोड़ें",
            "Odia" to "ପଲିସି ଯୋଡନ୍ତୁ",
            "Marathi" to "पॉलिसी जोडा"
        ),

        // Preferences & Settings
        "Agent Profile" to mapOf(
            "Hindi" to "एजेंट प्रोफ़ाइल",
            "Odia" to "ଏଜେଣ୍ଟ ପ୍ରୋଫାଇଲ୍",
            "Marathi" to "एजंट प्रोफाइल"
        ),
        "App Preferences" to mapOf(
            "Hindi" to "ऐप प्राथमिकताएं",
            "Odia" to "ଆପ୍ ପସନ୍ଦ",
            "Marathi" to "ॲप प्राधान्ये"
        ),
        "Dark Theme Mode" to mapOf(
            "Hindi" to "डार्क थीम मोड",
            "Odia" to "ଡାର୍କ ଥିମ୍ ମୋଡ୍",
            "Marathi" to "डार्क थीम मोड"
        ),
        "Follow System Theme" to mapOf(
            "Hindi" to "सिस्टम थीम का पालन करें",
            "Odia" to "ସିଷ୍ଟମ୍ ଥିମ୍ ଅନୁସରଣ କରନ୍ତୁ",
            "Marathi" to "सिस्टम थीमचे अनुसरण करा"
        ),
        "Application Language" to mapOf(
            "Hindi" to "ऐप भाषा",
            "Odia" to "ଆପ୍ ଭାଷା",
            "Marathi" to "ॲप भाषा"
        ),
        "Display Font Size" to mapOf(
            "Hindi" to "डिस्प्ले फ़ॉन्ट आकार",
            "Odia" to "ଫଣ୍ଟ ଆକାର",
            "Marathi" to "डिस्प्ले फॉन्ट आकार"
        ),
        "Small" to mapOf(
            "Hindi" to "छोटा",
            "Odia" to "ଛୋଟ",
            "Marathi" to "लहान"
        ),
        "Medium" to mapOf(
            "Hindi" to "मध्यम",
            "Odia" to "ମଧ୍ୟମ",
            "Marathi" to "मध्यम"
        ),
        "Large" to mapOf(
            "Hindi" to "बड़ा",
            "Odia" to "ବଡ଼",
            "Marathi" to "मोठा"
        ),
        "Notification & Reminders" to mapOf(
            "Hindi" to "अधिसूचना और अनुस्मारक",
            "Odia" to "ବିଜ୍ଞପ୍ତି ଏବଂ ସ୍ମାରକ",
            "Marathi" to "सूचना आणि स्मरणपत्रे"
        ),
        "Receipt & PDF Generator" to mapOf(
            "Hindi" to "रसीद और पीडीएफ",
            "Odia" to "ରସିଦ୍ ଏବଂ PDF",
            "Marathi" to "पावती आणि PDF"
        ),
        "Payment & UPI QR Settings" to mapOf(
            "Hindi" to "भुगतान और यूपीआई क्यूआर",
            "Odia" to "ପେମେଣ୍ଟ ଏବଂ UPI QR",
            "Marathi" to "पेमेंट आणि UPI QR"
        ),
        "Cloud Sync & Backup" to mapOf(
            "Hindi" to "क्लाउड सिंक और बैकअप",
            "Odia" to "କ୍ଲାଉଡ୍ ସିଙ୍କ",
            "Marathi" to "क्लाउड सिंक आणि बॅकअप"
        ),
        "Security & Vault Passcode" to mapOf(
            "Hindi" to "सुरक्षा और पासकोड",
            "Odia" to "ସୁରକ୍ଷା ଏବଂ ଭଲ୍ଟ",
            "Marathi" to "सुरक्षा आणि पासकोड"
        ),
        "Data Management & Storage" to mapOf(
            "Hindi" to "डेटा प्रबंधन",
            "Odia" to "ଡାଟା ପରିଚାଳନା",
            "Marathi" to "डेटा व्यवस्थापन"
        ),
        "Support & Legal" to mapOf(
            "Hindi" to "सहायता और कानूनी",
            "Odia" to "ସହାୟତା ଏବଂ ଆଇନଗତ",
            "Marathi" to "समर्थन आणि कायदेशीर"
        ),
        "Logout" to mapOf(
            "Hindi" to "लॉग आउट",
            "Odia" to "ଲଗ୍ ଆଉଟ୍",
            "Marathi" to "लॉग आउट"
        ),

        // Common Buttons & Dialogs
        "Save" to mapOf(
            "Hindi" to "सहेजें",
            "Odia" to "ସଂରକ୍ଷଣ",
            "Marathi" to "जतन करा"
        ),
        "Cancel" to mapOf(
            "Hindi" to "रद्द करें",
            "Odia" to "ରଦ୍ଦ କରନ୍ତୁ",
            "Marathi" to "रद्द करा"
        ),
        "Search" to mapOf(
            "Hindi" to "खोजें",
            "Odia" to "ଖୋଜନ୍ତୁ",
            "Marathi" to "शोधा"
        ),
        "Filter" to mapOf(
            "Hindi" to "फ़िल्टर",
            "Odia" to "ଫିଲ୍ଟର୍",
            "Marathi" to "फिल्टर"
        )
    )

    fun tr(text: String, language: String): String {
        if (language.equals("English", ignoreCase = true) || language.isBlank()) {
            return text
        }
        val trimmed = text.trim()
        val langKey = when (language.lowercase()) {
            "hindi", "hi" -> "Hindi"
            "odia", "or" -> "Odia"
            "marathi", "mr" -> "Marathi"
            else -> "English"
        }
        if (langKey == "English") return text

        val exactMatch = translations[trimmed]?.get(langKey)
        if (exactMatch != null) return exactMatch

        val foundKey = translations.keys.find { it.equals(trimmed, ignoreCase = true) }
        if (foundKey != null) {
            val match = translations[foundKey]?.get(langKey)
            if (match != null) return match
        }

        return text
    }
}
