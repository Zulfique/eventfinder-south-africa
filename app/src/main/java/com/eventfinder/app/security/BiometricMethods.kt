package com.eventfinder.app.security

import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.biometric.BiometricManager

/**
 * What this device can actually authenticate with, so the biometric screen
 * only offers methods that exist. Nothing here is assumed: a method is enabled
 * only when the hardware feature is present and a strong biometric is enrolled.
 */
data class BiometricMethods(
    val hasFingerprint: Boolean,
    val hasFace: Boolean,
    val hasDeviceCredential: Boolean
) {
    val hasAny: Boolean get() = hasFingerprint || hasFace || hasDeviceCredential

    companion object {
        fun detect(context: Context): BiometricMethods {
            val packageManager = context.packageManager
            val strongEnrolled = BiometricManager.from(context)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS
            val keyguard = context.getSystemService(KeyguardManager::class.java)
            return BiometricMethods(
                hasFingerprint = strongEnrolled &&
                    packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT),
                hasFace = strongEnrolled &&
                    packageManager.hasSystemFeature(PackageManager.FEATURE_FACE),
                // The device credential is only a promise on API 30+, where the
                // prompt and the Keystore key both accept it.
                hasDeviceCredential = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    keyguard?.isDeviceSecure == true
            )
        }
    }
}
