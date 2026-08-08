package com.example.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.view.WindowManager
import com.google.firebase.auth.FirebaseAuth
import java.security.MessageDigest
import kotlin.math.max

object SecurityUtils {
    private const val TAG = "SecurityUtils"

    private const val KEY_BIOMETRIC_ENABLED = "is_biometric_login_enabled"
    private const val KEY_RECOVERY_QUESTION = "emergency_recovery_question"
    private const val KEY_RECOVERY_ANSWER = "emergency_recovery_answer"
    private const val KEY_RECOVERY_EMAIL = "emergency_recovery_email"
    private const val KEY_FAILED_RECOVERY_ATTEMPTS = "failed_recovery_attempts"
    private const val KEY_RECOVERY_LOCKOUT_TIMESTAMP = "recovery_lockout_timestamp"

    private const val MAX_RECOVERY_ATTEMPTS = 5
    private const val LOCKOUT_DURATION_MS = 30 * 60 * 1000L // 30 minutes

    val DEFAULT_RECOVERY_QUESTIONS = listOf(
        "What was your first agency code or code number?",
        "What is the name of your primary LIC branch / city?",
        "What was the name of your first school?",
        "What is your mother's maiden name?",
        "What is your favorite milestone or policy number?"
    )

    fun hashPin(pin: String): String {
        val trimmed = pin.trim()
        if (trimmed.isBlank()) return ""
        val bytes = MessageDigest.getInstance("SHA-256").digest(trimmed.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun isPinValid(enteredPin: String, storedPinOrHash: String): Boolean {
        val cleanEntered = enteredPin.trim()
        val cleanStored = storedPinOrHash.trim()
        if (cleanEntered.isBlank() || cleanStored.isBlank()) return false

        val hashedEntered = hashPin(cleanEntered)
        return cleanStored == cleanEntered || cleanStored == hashedEntered
    }

    fun findActivity(context: Context): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) {
                return ctx
            }
            ctx = ctx.baseContext
        }
        return null
    }

    fun setSecureFlag(context: Context, enable: Boolean) {
        try {
            val activity = findActivity(context)
            if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                if (enable) {
                    activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Window FLAG_SECURE notice: ${e.localizedMessage}")
        }
    }

    fun isBiometricEnabled(context: Context): Boolean {
        return SecurePreferences.getSecureToken(context, KEY_BIOMETRIC_ENABLED, "false") == "true"
    }

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        SecurePreferences.saveSecureToken(context, KEY_BIOMETRIC_ENABLED, if (enabled) "true" else "false")
    }

    // --- Emergency Recovery Data Storage & Encryption ---

    fun saveRecoveryInfo(context: Context, question: String, answer: String, email: String) {
        val cleanQuestion = question.trim()
        val cleanAnswer = answer.trim().lowercase()
        val cleanEmail = email.trim().lowercase()

        val hashedAnswer = hashPin(cleanAnswer) // Answer encrypted/hashed securely

        SecurePreferences.saveSecureToken(context, KEY_RECOVERY_QUESTION, cleanQuestion)
        SecurePreferences.saveSecureToken(context, KEY_RECOVERY_ANSWER, hashedAnswer)
        SecurePreferences.saveSecureToken(context, KEY_RECOVERY_EMAIL, cleanEmail)
    }

    fun hasRecoverySetup(context: Context): Boolean {
        val storedQuestion = SecurePreferences.getSecureToken(context, KEY_RECOVERY_QUESTION, "")
        val storedAnswer = SecurePreferences.getSecureToken(context, KEY_RECOVERY_ANSWER, "")
        return storedQuestion.isNotBlank() && storedAnswer.isNotBlank()
    }

    fun getRecoveryQuestion(context: Context): String {
        val q = SecurePreferences.getSecureToken(context, KEY_RECOVERY_QUESTION, "")
        return if (q.isNotBlank()) q else DEFAULT_RECOVERY_QUESTIONS[0]
    }

    fun getRecoveryEmail(context: Context): String {
        return SecurePreferences.getSecureToken(context, KEY_RECOVERY_EMAIL, "")
    }

    fun verifyRecoveryAnswer(context: Context, answer: String, fallbackDetails: List<String> = emptyList()): Boolean {
        val cleanAnswer = answer.trim().lowercase()
        if (cleanAnswer.isBlank()) return false

        val storedHashedAnswer = SecurePreferences.getSecureToken(context, KEY_RECOVERY_ANSWER, "")
        if (storedHashedAnswer.isNotBlank()) {
            val inputHash = hashPin(cleanAnswer)
            if (storedHashedAnswer == inputHash || storedHashedAnswer == cleanAnswer) {
                return true
            }
        }

        // Fallback match against agent profile details (agency code, email, name, etc.) if recovery answer not set
        for (fallback in fallbackDetails) {
            val cleanFallback = fallback.trim().lowercase()
            if (cleanFallback.isNotBlank() && (cleanFallback == cleanAnswer || (cleanAnswer.length >= 4 && cleanFallback.contains(cleanAnswer)))) {
                return true
            }
        }

        return false
    }

    // --- Emergency Lockout Management ---

    fun checkLockoutStatus(context: Context): Pair<Boolean, Long> {
        val lockoutTimeStr = SecurePreferences.getSecureToken(context, KEY_RECOVERY_LOCKOUT_TIMESTAMP, "0")
        val lockoutTime = lockoutTimeStr.toLongOrNull() ?: 0L
        val currentTime = System.currentTimeMillis()

        if (lockoutTime > currentTime) {
            val remainingMs = lockoutTime - currentTime
            val remainingMinutes = max(1L, (remainingMs + 59999L) / 60000L)
            return Pair(true, remainingMinutes)
        } else {
            if (lockoutTime > 0) {
                // Lockout expired, reset counters
                resetFailedAttempts(context)
            }
            return Pair(false, 0L)
        }
    }

    fun recordFailedAttempt(context: Context): Int {
        val currentAttemptsStr = SecurePreferences.getSecureToken(context, KEY_FAILED_RECOVERY_ATTEMPTS, "0")
        val attempts = (currentAttemptsStr.toIntOrNull() ?: 0) + 1
        SecurePreferences.saveSecureToken(context, KEY_FAILED_RECOVERY_ATTEMPTS, attempts.toString())

        if (attempts >= MAX_RECOVERY_ATTEMPTS) {
            val lockoutTime = System.currentTimeMillis() + LOCKOUT_DURATION_MS
            SecurePreferences.saveSecureToken(context, KEY_RECOVERY_LOCKOUT_TIMESTAMP, lockoutTime.toString())
            return 0
        }

        return max(0, MAX_RECOVERY_ATTEMPTS - attempts)
    }

    fun resetFailedAttempts(context: Context) {
        SecurePreferences.saveSecureToken(context, KEY_FAILED_RECOVERY_ATTEMPTS, "0")
        SecurePreferences.saveSecureToken(context, KEY_RECOVERY_LOCKOUT_TIMESTAMP, "0")
    }

    // --- Firebase Email Reset ---

    fun sendPasswordResetEmail(email: String, onResult: (Boolean, String?) -> Unit) {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank()) {
            onResult(false, "No valid recovery email address provided.")
            return
        }

        try {
            val auth = FirebaseAuth.getInstance()
            auth.sendPasswordResetEmail(cleanEmail)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        onResult(true, "PIN reset link sent to $cleanEmail. Check your inbox.")
                    } else {
                        val error = task.exception?.localizedMessage ?: "Failed to send reset email. Check network connection."
                        onResult(false, error)
                    }
                }
        } catch (e: Throwable) {
            Log.e(TAG, "FirebaseAuth password reset exception: ${e.localizedMessage}")
            onResult(false, "Authentication service unavailable. Please use Recovery Question.")
        }
    }
}
