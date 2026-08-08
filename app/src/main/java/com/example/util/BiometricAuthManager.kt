package com.example.util

import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricAuthManager {
    private const val TAG = "BiometricAuthManager"

    fun findFragmentActivity(context: Context): FragmentActivity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is FragmentActivity) {
                return ctx
            }
            ctx = ctx.baseContext
        }
        return null
    }

    fun isBiometricAvailable(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        return biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun canAuthenticateBiometricOnly(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        return biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun getBiometricStatusMessage(context: Context): String {
        val biometricManager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        return when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> "Biometric authentication is ready."
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "No biometric hardware found on this device."
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Biometric hardware is currently unavailable."
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "No biometric credentials enrolled in device settings."
            else -> "Biometric authentication is unavailable on this device."
        }
    }

    fun showBiometricPrompt(
        context: Context,
        title: String = "LIC Vault Security",
        subtitle: String = "Scan fingerprint or face unlock to access",
        negativeButtonText: String = "Use Security PIN",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val activity = findFragmentActivity(context)
        if (activity == null) {
            Log.e(TAG, "FragmentActivity not found for BiometricPrompt")
            onError("Biometric authentication error: Activity context missing.")
            return
        }

        if (!isBiometricAvailable(context)) {
            val status = getBiometricStatusMessage(context)
            onError(status)
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                Log.i(TAG, "Biometric authentication succeeded.")
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Log.w(TAG, "Biometric authentication error [$errorCode]: $errString")
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    onError("PIN_FALLBACK")
                } else if (errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                    onError("Cancelled by user")
                } else {
                    onError(errString.toString())
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Log.w(TAG, "Biometric verification failed.")
                onError("Biometric verification failed. Please try again.")
            }
        }

        try {
            val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)

            if (canAuthenticateBiometricOnly(context)) {
                promptInfoBuilder.setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
                )
                promptInfoBuilder.setNegativeButtonText(negativeButtonText)
            } else {
                promptInfoBuilder.setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                            BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
            }

            val biometricPrompt = BiometricPrompt(activity, executor, callback)
            biometricPrompt.authenticate(promptInfoBuilder.build())
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to launch BiometricPrompt: ${e.localizedMessage}", e)
            onError("Could not launch biometric prompt: ${e.localizedMessage}")
        }
    }
}
