package com.eventfinder.app.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.eventfinder.app.R
import com.eventfinder.app.utils.AppLogger
import java.util.concurrent.Executor

/** Why a biometric prompt did not unlock the app. */
enum class BiometricFailure {
    /** The user dismissed the prompt; nothing is wrong. */
    CANCELLED,

    /** Too many attempts (temporarily or permanently); password is the way back in. */
    LOCKED_OUT,

    /** Enrolment changed, so the Keystore key was invalidated: re-enrol required. */
    INVALIDATED,

    /** No usable lock screen, hardware or key on this device right now. */
    UNAVAILABLE,

    /** The prompt ran but the result could not be verified. */
    FAILED
}

/**
 * Biometric authentication wrapper (FR-01).
 *
 * The prompt is bound to a Keystore key ([BiometricCrypto]) so a successful
 * result is proof that the current biometric enrolment authorised the key, not
 * merely that some fingerprint on the phone was accepted. If the user adds a
 * fingerprint after enrolling, Android invalidates the key and this class
 * reports [BiometricFailure.INVALIDATED] so the caller can drop the enrolment
 * and ask for the password.
 */
class BiometricAuth(
    private val activity: FragmentActivity,
    private val onAuthenticated: () -> Unit,
    private val onError: (BiometricFailure) -> Unit
) {

    private val executor: Executor = ContextCompat.getMainExecutor(activity)
    private val title: String = activity.getString(R.string.login_with_biometrics)
    private val negativeButtonText: String = activity.getString(R.string.cancel)

    companion object {
        private const val TAG = "BiometricAuth"

        /** True when a strong biometric (fingerprint or face) is enrolled. */
        fun isAvailable(context: Context): Boolean {
            val manager = BiometricManager.from(context)
            val result = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            AppLogger.d(TAG, "Biometric availability: $result")
            return result == BiometricManager.BIOMETRIC_SUCCESS
        }

        /**
         * Drops the Keystore key. Called when the user turns biometric login off
         * or when enrolment changed, so the next enrolment starts from a fresh
         * key bound to the new biometrics.
         */
        fun clearKey() = BiometricCrypto.deleteKey()
    }

    @Suppress("MissingPermission")
    fun authenticate() {
        val cipher = try {
            BiometricCrypto.newCipher()
        } catch (throwable: Throwable) {
            // Keystore may wrap the invalidation inside another exception, so the
            // whole cause chain is checked rather than just the outer type.
            val invalidated = generateSequence(throwable) { it.cause }
                .any { it is KeyPermanentlyInvalidatedException }
            AppLogger.w(TAG, "Biometric unavailable: ${throwable.message} (invalidated=$invalidated)")
            if (invalidated) {
                BiometricCrypto.deleteKey()
                onError(BiometricFailure.INVALIDATED)
            } else {
                // Typically no secure lock screen, so user authentication is impossible.
                onError(BiometricFailure.UNAVAILABLE)
            }
            return
        }

        val promptInfo = buildPromptInfo()
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val verified = BiometricCrypto.verify(result.cryptoObject?.cipher)
                    if (verified) {
                        AppLogger.i(TAG, "Biometric authentication succeeded")
                        onAuthenticated()
                    } else {
                        // The key was revoked between the prompt and completion.
                        AppLogger.w(TAG, "Biometric result failed key verification")
                        BiometricCrypto.deleteKey()
                        onError(BiometricFailure.INVALIDATED)
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    val failure = when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_CANCELED -> BiometricFailure.CANCELLED

                        BiometricPrompt.ERROR_LOCKOUT,
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> BiometricFailure.LOCKED_OUT

                        else -> BiometricFailure.FAILED
                    }
                    AppLogger.w(TAG, "Biometric error $errorCode ($errString) -> $failure")
                    onError(failure)
                }

                override fun onAuthenticationFailed() {
                    // Not fatal: the prompt stays up and lets the user retry.
                    AppLogger.w(TAG, "Biometric attempt not recognised (retry allowed)")
                }
            }
        )

        prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    }

    private fun buildPromptInfo(): BiometricPrompt.PromptInfo {
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(activity.getString(R.string.biometric_hint))
            .setConfirmationRequired(false)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // The system blocks the negative button when the device credential is
            // allowed, so it is omitted; the key accepts PIN/pattern fallback too.
            builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            builder.build()
        } else {
            builder.setNegativeButtonText(negativeButtonText)
            builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            builder.build()
        }
    }

}
