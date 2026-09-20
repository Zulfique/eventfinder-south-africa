package com.eventfinder.app.data.repository

import android.content.Context
import com.eventfinder.app.data.local.DatabaseTransactionHelper
import com.eventfinder.app.data.local.EventDao
import com.eventfinder.app.data.local.UserDao
import com.eventfinder.app.data.local.UserEntity
import com.eventfinder.app.data.local.toDomain
import com.eventfinder.app.data.store.SessionProvider
import com.eventfinder.app.domain.model.User
import com.eventfinder.app.notifications.ReminderHelper
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
 * Authentication boundary. Performs local registration/login with PBKDF2-hashed
 * passwords (see [PasswordHasher]). Password reset is a local email-lookup that
 * updates the Room hash — no cloud backend is required.
 *
 * **Security limitation:** password reset performs no proof of email ownership.
 * Anyone who knows a registered email can reset that account's password. This is
 * acceptable for a single-device prototype but must be replaced with email/SMS
 * verification before production use.
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
     * Resets a password by looking up the account by email, validating the new
     * password, and updating the Room hash. Returns an error if the email is
     * unknown, the password is weak, or it matches the current password.
     */
    suspend fun resetPassword(email: String, newPassword: String): Result<Unit>

    /** Changes the signed-in user's password after verifying the current one. */
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit>

    /** Permanently removes the signed-in user's account and clears the session. */
    suspend fun deleteAccount(): Result<Unit>

    suspend fun isLoggedIn(): Boolean
}

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryImpl(
    private val database: DatabaseTransactionHelper,
    private val userDao: UserDao,
    private val eventDao: EventDao,
    private val context: Context,
    private val preferences: SessionProvider
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
        preferences.setLanguage(language)
        ReminderHelper.restoreReminders(context, eventDao, user.id)
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
        preferences.setLanguage(user.preferredLanguage)
        ReminderHelper.restoreReminders(context, eventDao, user.id)
        AppLogger.i("AuthRepository", "User signed in: ${user.email}")
        return Result.success(user.toDomain())
    }

    override suspend fun biometricLogin(): Result<User> {
        val userId = preferences.biometricUserId.first()
            ?: return Result.failure(IllegalStateException("no_biometric_user"))
        val user = userDao.findById(userId)
            ?: return Result.failure(IllegalStateException("biometric_user_missing"))
        if (!user.biometricEnabled) {
            return Result.failure(IllegalStateException("biometric_disabled"))
        }
        preferences.setSessionUserId(user.id)
        preferences.setLanguage(user.preferredLanguage)
        ReminderHelper.restoreReminders(context, eventDao, user.id)
        AppLogger.i("AuthRepository", "Biometric login for ${user.email}")
        return Result.success(user.toDomain())
    }

    override suspend fun logout() {
        val userId = preferences.sessionUserId.first()
        if (userId != null) {
            ReminderHelper.cancelReminders(context, eventDao, userId)
        }
        preferences.setSessionUserId(null)
        AppLogger.i("AuthRepository", "User logged out - session cleared")
    }

    override suspend fun updateProfile(fullName: String, email: String): Result<User> {
        val current = currentUser.first() ?: return Result.failure(IllegalStateException("no_session"))
        if (fullName.isBlank()) return Result.failure(IllegalArgumentException("name_required"))
        if (!EmailValidator.isValid(email)) return Result.failure(IllegalArgumentException("invalid_email"))

        val normalizedEmail = email.trim().lowercase()
        val existing = userDao.findByEmail(normalizedEmail)
        if (existing != null && existing.id != current.id) {
            return Result.failure(IllegalArgumentException("email_in_use"))
        }

        val updated = current.copy(
            fullName = fullName.trim(),
            email = normalizedEmail
        )
        val entity = userDao.findById(current.id) ?: return Result.failure(IllegalStateException("user_missing"))
        userDao.upsert(entity.copy(fullName = updated.fullName, email = updated.email))
        AppLogger.i("AuthRepository", "Profile updated for ${updated.id}")
        return Result.success(updated)
    }

    override suspend fun setBiometricEnabled(enabled: Boolean) {
        val user = currentUser.first() ?: return
        if (enabled) {
            preferences.setBiometricUserId(user.id)
        } else {
            preferences.setBiometricUserId(null)
        }
        userDao.findById(user.id)?.let {
            userDao.upsert(it.copy(biometricEnabled = enabled))
        }
        AppLogger.i("AuthRepository", "Biometric preference updated: $enabled")
    }

    override suspend fun resetPassword(email: String, newPassword: String): Result<Unit> {
        val normalizedEmail = email.trim().lowercase()
        if (!EmailValidator.isValid(normalizedEmail)) {
            return Result.failure(IllegalArgumentException("invalid_email"))
        }
        val user = userDao.findByEmail(normalizedEmail)
            ?: return Result.failure(IllegalArgumentException("unknown_email"))
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

        ReminderHelper.cancelReminders(context, eventDao, current.id)
        database.deleteAccountData(current.id)
        preferences.clearUserPreferences(current.id)

        AppLogger.i("AuthRepository", "Account deleted for ${current.email}")
        return Result.success(Unit)
    }

    override suspend fun isLoggedIn(): Boolean =
        preferences.sessionUserId.first() != null
}