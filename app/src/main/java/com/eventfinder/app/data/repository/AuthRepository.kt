package com.eventfinder.app.data.repository

import com.eventfinder.app.data.local.UserDao
import com.eventfinder.app.data.local.UserEntity
import com.eventfinder.app.data.local.toDomain
import com.eventfinder.app.data.store.UserPreferences
import com.eventfinder.app.domain.model.User
import com.eventfinder.app.utils.AppLogger
import com.eventfinder.app.utils.EmailValidator
import com.eventfinder.app.utils.PasswordHasher
import com.eventfinder.app.utils.PasswordValidator
import com.eventfinder.app.utils.ValidationResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Authentication boundary. The prototype performs local registration/login with
 * PBKDF2-hashed passwords (see [PasswordHasher]); Firebase Authentication and
 * real password-reset emailing are explicitly deferred to the final POE phase.
 *
 * References:
 *  - Firebase (2026a) Firebase Authentication: https://firebase.google.com/docs/auth
 */
interface AuthRepository {
    val currentUser: Flow<User?>
    suspend fun register(fullName: String, email: String, password: String, language: String): Result<User>
    suspend fun login(email: String, password: String): Result<User>
    suspend fun biometricLogin(): Result<User>
    suspend fun logout()
    suspend fun updateProfile(fullName: String, email: String): Result<User>
    suspend fun setBiometricEnabled(enabled: Boolean)

    /**
     * Resets a forgotten password. The prototype runs entirely on-device, so no
     * email is sent: the call validates that [email] belongs to a local account
     * and stores a fresh PBKDF2 hash for [newPassword].
     */
    suspend fun resetPassword(email: String, newPassword: String): Result<Unit>

    /** Changes the signed-in user's password after verifying the current one. */
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit>

    /** Permanently removes the signed-in user's account and clears the session. */
    suspend fun deleteAccount(): Result<Unit>

    fun isLoggedIn(): Boolean
}

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryImpl(
    private val userDao: UserDao,
    private val preferences: UserPreferences
) : AuthRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val currentUser: Flow<User?> =
        preferences.sessionUserId.flatMapLatest { sessionId ->
            if (sessionId == null) {
                flowOf(null)
            } else {
                userDao.observeUser(sessionId).map { it?.toDomain() }
            }
        }

    override suspend fun register(
        fullName: String,
        email: String,
        password: String,
        language: String
    ): Result<User> {
        val normalizedEmail = email.trim().lowercase()
        if (userDao.findByEmail(normalizedEmail) != null) {
            AppLogger.w("AuthRepository", "Registration rejected - email already used: $normalizedEmail")
            return Result.failure(IllegalArgumentException("email_in_use"))
        }
        val user = UserEntity(
            id = UUID.randomUUID().toString(),
            fullName = fullName.trim(),
            email = normalizedEmail,
            passwordHash = PasswordHasher.hash(password),
            preferredLanguage = language,
            defaultCity = "Johannesburg",
            defaultRadiusKm = 50,
            biometricEnabled = false,
            createdAt = System.currentTimeMillis()
        )
        userDao.upsert(user)
        preferences.setSessionUserId(user.id)
        AppLogger.i("AuthRepository", "New account created for ${user.email}")
        return Result.success(user.toDomain())
    }

    override suspend fun login(email: String, password: String): Result<User> {
        val normalizedEmail = email.trim().lowercase()
        val user = userDao.findByEmail(normalizedEmail)
        if (user == null || !PasswordHasher.verify(password, user.passwordHash)) {
            AppLogger.w("AuthRepository", "Login rejected for $normalizedEmail")
            return Result.failure(IllegalArgumentException("login_invalid_credentials"))
        }
        preferences.setSessionUserId(user.id)
        AppLogger.i("AuthRepository", "User signed in: ${user.email}")
        return Result.success(user.toDomain())
    }

    override suspend fun biometricLogin(): Result<User> {
        // Prototype: the device typically holds one local user. When biometrics
        // are enabled for them, a successful prompt signs that user straight in.
        val candidate = userDao.observeAll().first().firstOrNull { it.biometricEnabled }
        return if (candidate != null) {
            preferences.setSessionUserId(candidate.id)
            AppLogger.i("AuthRepository", "Biometric login for ${candidate.email}")
            Result.success(candidate.toDomain())
        } else {
            AppLogger.w("AuthRepository", "Biometric login requested but no biometric-enabled user found")
            Result.failure(IllegalStateException("no_biometric_user"))
        }
    }

    override suspend fun logout() {
        preferences.setSessionUserId(null)
        AppLogger.i("AuthRepository", "User logged out - session cleared")
    }

    override suspend fun updateProfile(fullName: String, email: String): Result<User> {
        val current = currentUser.first() ?: return Result.failure(IllegalStateException("no_session"))
        if (fullName.isBlank()) return Result.failure(IllegalArgumentException("name_required"))
        if (!EmailValidator.isValid(email)) return Result.failure(IllegalArgumentException("invalid_email"))

        val updated = current.copy(
            fullName = fullName.trim(),
            email = email.trim().lowercase()
        )
        val existing = userDao.findById(current.id) ?: return Result.failure(IllegalStateException("user_missing"))
        userDao.upsert(existing.copy(fullName = updated.fullName, email = updated.email))
        AppLogger.i("AuthRepository", "Profile updated for ${updated.id}")
        return Result.success(updated)
    }

    override suspend fun setBiometricEnabled(enabled: Boolean) {
        preferences.setBiometricEnabled(enabled)
        // Keep the user row in-sync for future API payloads.
        currentUser.first()?.let { user ->
            userDao.findById(user.id)?.let {
                userDao.upsert(it.copy(biometricEnabled = enabled))
            }
        }
        AppLogger.i("AuthRepository", "Biometric preference updated: $enabled")
    }

    override suspend fun resetPassword(email: String, newPassword: String): Result<Unit> {
        if (!EmailValidator.isValid(email)) {
            return Result.failure(IllegalArgumentException("invalid_email"))
        }
        val normalizedEmail = email.trim().lowercase()
        val user = userDao.findByEmail(normalizedEmail)
        if (user == null) {
            AppLogger.w("AuthRepository", "Password reset requested for unknown email: $normalizedEmail")
            return Result.failure(IllegalArgumentException("unknown_email"))
        }
        if (PasswordValidator.validate(newPassword) is ValidationResult.Invalid) {
            return Result.failure(IllegalArgumentException("weak_password"))
        }
        if (PasswordHasher.verify(newPassword, user.passwordHash)) {
            return Result.failure(IllegalArgumentException("same_password"))
        }
        userDao.upsert(user.copy(passwordHash = PasswordHasher.hash(newPassword)))
        AppLogger.i("AuthRepository", "Local password reset completed for ${user.email}")
        return Result.success(Unit)
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> {
        val current = currentUser.first() ?: return Result.failure(IllegalStateException("no_session"))
        val entity = userDao.findById(current.id)
            ?: return Result.failure(IllegalStateException("user_missing"))

        if (!PasswordHasher.verify(currentPassword, entity.passwordHash)) {
            AppLogger.w("AuthRepository", "Password change rejected - current password incorrect")
            return Result.failure(IllegalArgumentException("wrong_password"))
        }
        if (PasswordValidator.validate(newPassword) is ValidationResult.Invalid) {
            return Result.failure(IllegalArgumentException("weak_password"))
        }
        if (PasswordHasher.verify(newPassword, entity.passwordHash)) {
            return Result.failure(IllegalArgumentException("same_password"))
        }

        userDao.upsert(entity.copy(passwordHash = PasswordHasher.hash(newPassword)))
        AppLogger.i("AuthRepository", "Password changed for ${entity.email}")
        return Result.success(Unit)
    }

    override suspend fun deleteAccount(): Result<Unit> {
        val current = currentUser.first() ?: return Result.failure(IllegalStateException("no_session"))
        userDao.deleteById(current.id)
        preferences.clearAll()
        AppLogger.i("AuthRepository", "Account deleted for ${current.email}")
        return Result.success(Unit)
    }

    override fun isLoggedIn(): Boolean = preferences.isLoggedIn()
}