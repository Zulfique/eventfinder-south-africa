package com.eventfinder.app.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import com.eventfinder.app.utils.AppLogger
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Keystore key that backs biometric authentication.
 *
 * The key can only be used after the user authenticates with a strong biometric
 * (or, on Android 11+, the device credential), and it is invalidated when a new
 * fingerprint or face is enrolled. That is what stops someone who can add a
 * fingerprint to an already-unlocked phone from unlocking the stored account:
 * the hardware throws [android.security.keystore.KeyPermanentlyInvalidatedException]
 * and the app falls back to the password.
 */
internal object BiometricCrypto {

    private const val TAG = "BiometricCrypto"
    private const val KEY_ALIAS = "eventfinder_biometric_key"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    /** Verification payload; only the successful encryption result matters. */
    private val CHALLENGE = "eventfinder-biometric-check".toByteArray()

    /**
     * Builds a fresh encrypt-mode cipher for the biometric key, creating the key
     * on first use. Throws [android.security.keystore.KeyPermanentlyInvalidatedException]
     * when enrolment changed and [java.security.GeneralSecurityException] when the
     * device has no secure lock screen.
     */
    fun newCipher(): Cipher {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val key = (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: generateKey()
        return Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    /** Runs the authenticated cipher so a prompt that returned a stale key fails closed. */
    fun verify(cipher: Cipher?): Boolean {
        if (cipher == null) return false
        return runCatching { cipher.doFinal(CHALLENGE) }
            .onFailure { AppLogger.w(TAG, "Biometric cipher verification failed: ${it.message}") }
            .isSuccess
    }

    fun deleteKey() {
        runCatching {
            KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS)
            AppLogger.i(TAG, "Biometric key removed")
        }.onFailure { AppLogger.w(TAG, "Could not delete biometric key: ${it.message}") }
    }

    private fun generateKey(): SecretKey =
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(buildSpec())
        }.generateKey()

    private fun buildSpec(): KeyGenParameterSpec {
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            authenticatorsForApi30(builder)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
            builder.build()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun authenticatorsForApi30(builder: KeyGenParameterSpec.Builder): KeyGenParameterSpec {
        builder.setUserAuthenticationParameters(
            0,
            KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
        )
        return builder.build()
    }
}
