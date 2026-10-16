package com.eventfinder.app.data.repository

import android.content.Context
import com.eventfinder.app.data.local.DatabaseTransactionHelper
import com.eventfinder.app.data.local.EventDao
import com.eventfinder.app.data.local.UserDao
import com.eventfinder.app.data.local.UserEntity
import com.eventfinder.app.data.local.toDomain
import com.eventfinder.app.data.remote.EventFinderApiClient
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
 * Authentication boundary.
 *
 * Accounts are backed by the EventFinder REST API (ASP.NET Core, JWT bearer
 * tokens, server-side PBKDF2 hashes, verified Google ID tokens, and an
 * email-verified password-reset flow). A local Room copy of the profile is
 * kept so the UI works offline; guest accounts remain purely local.
 */
interface AuthRepository {
    val currentUser: Flow<User?>
    /**
     * True when a local user has enrolled biometric login and can therefore
     * use biometric authentication from the logged-out Login screen.
     */
    val biometricEnrolled: Flow<Boolean>

    suspend fun register(fullName: String, email: String, password: String, language: String): Result<User>
    suspend fun login(email: String, password: String): Result<User>
    suspend fun biometricLogin(): Result<User>
    suspend fun logout()
    suspend fun updateProfile(fullName: String, email: String): Result<User>
    suspend fun setBiometricEnabled(enabled: Boolean)

    /** Signs in (or provisions) an account from a verified Google ID token. */
    suspend fun googleSignIn(idToken: String, language: String = "en"): Result<User>

    /**
     * Step 1 of password reset: asks the backend to email a single-use,
     * expiring reset token to [email]. Always succeeds when the server is
     * reachable — the API does not reveal whether the account exists.
     */
    suspend fun requestPasswordReset(email: String): Result<Unit>

    /**
     * Step 2 of password reset: consumes the emailed [token] on the backend
     * and updates the local password hash so offline login keeps working.
     */
    suspend fun confirmPasswordReset(email: String, token: String, newPassword: String): Result<Unit>

    /** Changes the signed-in user's password after verifying the current one. */
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit>

    /** Permanently removes the signed-in user's account and clears the session. */
    suspend fun deleteAccount(): Result<Unit>

    suspend fun isLoggedIn(): Boolean

    /** Continues without creating a full account — uses a local device profile. */
    suspend fun continueAsGuest(): Result<User>
}


@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryImpl(
    private val database: DatabaseTransactionHelper,
    private val userDao: UserDao,
    private val eventDao: EventDao,
    private val context: Context,
    private val preferences: SessionProvider,
    private val api: EventFinderApiClient? = null
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

    override val biometricEnrolled: Flow<Boolean>
        get() = preferences.biometricUserId.flatMapLatest { userId ->
            if (userId == null) {
                flowOf(false)
            } else {
                userDao.observeUser(userId).map { user ->
                    user?.biometricEnabled == true
                }
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

        val remote = api?.register(fullName.trim(), normalizedEmail, password, language)
        if (remote != null) {
            var fallThrough = false
            remote.onSuccess { auth ->
                val user = UserEntity(
                    id = auth.user.id,
                    fullName = auth.user.fullName,
                    email = auth.user.email,
                    passwordHash = PasswordHasher.hash(password),
                    preferredLanguage = auth.user.preferredLanguage.ifBlank { language },
                    defaultCity = "South Africa",
                    defaultRadiusKm = 50,
                    biometricEnabled = false,
                    createdAt = System.currentTimeMillis(),
                    serverBacked = true
                )
                userDao.upsert(user)
                preferences.setAuthToken(auth.token)
                preferences.setSessionUserId(user.id)
                preferences.setLanguage(user.preferredLanguage)
                ReminderHelper.restoreReminders(context, eventDao, user.id)
                AppLogger.i("AuthRepository", "Account created via API for ${user.email}")
                return Result.success(user.toDomain())
            }.onFailure { error ->
                when (error) {
                    is EventFinderApiClient.NetworkException -> {
                        AppLogger.w("AuthRepository", "API unreachable during register - falling back to local")
                        fallThrough = true
                    }
                    is EventFinderApiClient.ApiException -> {
                        if (error.message?.contains("already", ignoreCase = true) == true || error.code == 409) {
                            return Result.failure(IllegalArgumentException("email_in_use"))
                        }
                        AppLogger.w("AuthRepository", "API register rejected: ${error.message}")
                        return Result.failure(IllegalArgumentException(error.message ?: "registration_failed"))
                    }
                    else -> {
                        AppLogger.w("AuthRepository", "API register failed: ${error.message}")
                        return Result.failure(error)
                    }
                }
            }
            if (!fallThrough) {
                // Should not reach here, but guard anyway.
            }
        }

        // Offline fallback: purely local account (works with no backend).
        val user = UserEntity(
            id = UUID.randomUUID().toString(),
            fullName = fullName.trim(),
            email = normalizedEmail,
            passwordHash = PasswordHasher.hash(password),
            preferredLanguage = language,
            defaultCity = "South Africa",
            defaultRadiusKm = 50,
                    biometricEnabled = false,
                    createdAt = System.currentTimeMillis(),
                    serverBacked = false
                )
                userDao.upsert(user)
                preferences.setSessionUserId(user.id)
                preferences.setLanguage(language)
        ReminderHelper.restoreReminders(context, eventDao, user.id)
        AppLogger.i("AuthRepository", "New local account created for ${user.email}")
        return Result.success(user.toDomain())
    }

    override suspend fun login(email: String, password: String): Result<User> {
        val normalizedEmail = email.trim().lowercase()

        val remote = api?.login(normalizedEmail, password)
        if (remote != null) {
            remote.onSuccess { auth ->
                val user = upsertServerUser(auth.user.id, auth.user.email, auth.user.fullName,
                    auth.user.preferredLanguage, PasswordHasher.hash(password))
                preferences.setAuthToken(auth.token)
                preferences.setSessionUserId(user.id)
                preferences.setLanguage(user.preferredLanguage)
                ReminderHelper.restoreReminders(context, eventDao, user.id)
                AppLogger.i("AuthRepository", "User signed in via API: ${user.email}")
                return Result.success(user.toDomain())
            }.onFailure { error ->
                when (error) {
                    is EventFinderApiClient.ApiException -> {
                        val localUser = userDao.findByEmail(normalizedEmail)
                        if (localUser?.serverBacked == true) {
                            AppLogger.w("AuthRepository", "API login rejected for server-backed account - not falling back to stale local hash")
                            return Result.failure(IllegalArgumentException("login_invalid_credentials"))
                        }
                        AppLogger.w("AuthRepository", "API login rejected - trying local hash for local-only account")
                    }
                    is EventFinderApiClient.NetworkException ->
                        AppLogger.w("AuthRepository", "API unreachable during login - trying local hash")
                    else -> AppLogger.w("AuthRepository", "API login failed - trying local hash")
                }
            }
        }

        val user = userDao.findByEmail(normalizedEmail)
        if (user == null || !PasswordHasher.verify(password, user.passwordHash)) {
            AppLogger.w("AuthRepository", "Login rejected for $normalizedEmail")
            return Result.failure(IllegalArgumentException("login_invalid_credentials"))
        }
        preferences.setSessionUserId(user.id)
        preferences.setLanguage(user.preferredLanguage)
        ReminderHelper.restoreReminders(context, eventDao, user.id)
        AppLogger.i("AuthRepository", "User signed in locally: ${user.email}")
        return Result.success(user.toDomain())
    }

    override suspend fun googleSignIn(idToken: String, language: String): Result<User> {
        val apiClient = api ?: return Result.failure(IllegalStateException("password_reset_requires_authenticated_backend"))
        return apiClient.googleSignIn(idToken).map { auth ->
            val user = upsertServerUser(auth.user.id, auth.user.email, auth.user.fullName,
                auth.user.preferredLanguage.ifBlank { language }, PasswordHasher.hash(UUID.randomUUID().toString()))
            preferences.setAuthToken(auth.token)
            preferences.setSessionUserId(user.id)
            preferences.setLanguage(user.preferredLanguage)
            ReminderHelper.restoreReminders(context, eventDao, user.id)
            AppLogger.i("AuthRepository", "Google sign-in for ${user.email}")
            user.toDomain()
        }
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
        preferences.setAuthToken(null)
        AppLogger.i("AuthRepository", "User logged out - session cleared")
    }

    override suspend fun updateProfile(fullName: String, email: String): Result<User> {
        val current = currentUser.first() ?: return Result.failure(IllegalStateException("no_session"))
        if (fullName.isBlank()) return Result.failure(IllegalArgumentException("name_required"))
        if (!EmailValidator.isValid(email)) return Result.failure(IllegalArgumentException("invalid_email"))

        val normalizedEmail = email.trim().lowercase()
        val normalizedFullName = fullName.trim()

        val token = preferences.authToken.first()
        val client = api
        if (client != null && !token.isNullOrBlank()) {
            // Name is updated immediately; email changes go through a verified
            // flow so a new address is not trusted until the user confirms it.
            val nameResult = client.updateProfile("Bearer $token", normalizedFullName, current.email)
            nameResult.onFailure { error ->
                if (error is EventFinderApiClient.ApiException && (error.message?.contains("already", ignoreCase = true) == true || error.code == 409)) {
                    return Result.failure(IllegalArgumentException("email_in_use"))
                }
                AppLogger.w("AuthRepository", "Server profile update failed: ${error.message}")
                return Result.failure(error)
            }
            val updatedUser = nameResult.getOrNull()
            val entity = userDao.findById(current.id) ?: return Result.failure(IllegalStateException("user_missing"))
            userDao.upsert(entity.copy(
                fullName = updatedUser?.fullName ?: normalizedFullName
            ))
            var updated = current.copy(fullName = updatedUser?.fullName ?: normalizedFullName)

            if (normalizedEmail != current.email) {
                val changeResult = client.requestEmailChange("Bearer $token", normalizedEmail)
                if (changeResult.isFailure) {
                    val error = changeResult.exceptionOrNull()
                    if (error is EventFinderApiClient.ApiException && (error.message?.contains("already", ignoreCase = true) == true || error.code == 409)) {
                        return Result.failure(IllegalArgumentException("email_in_use"))
                    }
                    AppLogger.w("AuthRepository", "Email-change request failed: ${error?.message}")
                    return Result.failure(error ?: IllegalStateException("email_change_failed"))
                }
                AppLogger.i("AuthRepository", "Email-change verification sent for ${updated.id}")
                return Result.failure(IllegalArgumentException("email_verification_sent"))
            }

            AppLogger.i("AuthRepository", "Profile updated for ${updated.id}")
            return Result.success(updated)
        }

        val existing = userDao.findByEmail(normalizedEmail)
        if (existing != null && existing.id != current.id) {
            return Result.failure(IllegalArgumentException("email_in_use"))
        }
        val entity = userDao.findById(current.id) ?: return Result.failure(IllegalStateException("user_missing"))
        userDao.upsert(entity.copy(fullName = normalizedFullName, email = normalizedEmail))
        val updated = current.copy(fullName = normalizedFullName, email = normalizedEmail)
        AppLogger.i("AuthRepository", "Profile updated locally for ${updated.id}")
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

    override suspend fun requestPasswordReset(email: String): Result<Unit> {
        val normalizedEmail = email.trim().lowercase()
        if (!EmailValidator.isValid(normalizedEmail)) {
            return Result.failure(IllegalArgumentException("invalid_email"))
        }
        val apiClient = api ?: return Result.failure(
            IllegalStateException("password_reset_requires_authenticated_backend")
        )
        return apiClient.forgotPassword(normalizedEmail).onSuccess {
            AppLogger.i("AuthRepository", "Reset token requested for $normalizedEmail")
        }
    }

    override suspend fun confirmPasswordReset(
        email: String,
        token: String,
        newPassword: String
    ): Result<Unit> {
        val normalizedEmail = email.trim().lowercase()
        if (PasswordValidator.validate(newPassword) is ValidationResult.Invalid) {
            return Result.failure(IllegalArgumentException("weak_password"))
        }
        if (token.isBlank()) {
            return Result.failure(IllegalArgumentException("invalid_token"))
        }
        val apiClient = api ?: return Result.failure(
            IllegalStateException("password_reset_requires_authenticated_backend")
        )
        return apiClient.resetPassword(normalizedEmail, token.trim(), newPassword).map {
            // Keep the offline copy of the hash in step with the server.
            userDao.findByEmail(normalizedEmail)?.let { user ->
                userDao.upsert(user.copy(passwordHash = PasswordHasher.hash(newPassword)))
            }
            AppLogger.i("AuthRepository", "Password reset completed via API for $normalizedEmail")
        }
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

        // Mirror the change to the backend when we hold a token. The local hash
        // is updated only after the server confirms success.
        val token = preferences.authToken.first()
        val client = api
        if (client != null && !token.isNullOrBlank()) {
            val result = client.changePassword("Bearer $token", currentPassword, newPassword)
            if (result.isFailure) {
                val error = result.exceptionOrNull()
                AppLogger.w("AuthRepository", "Server password change failed: ${error?.message}")
                if (error is EventFinderApiClient.ApiException) {
                    return when {
                        error.message?.contains("incorrect", ignoreCase = true) == true || error.code == 400 ->
                            Result.failure(IllegalArgumentException("wrong_password"))
                        else -> Result.failure(IllegalArgumentException("password_change_failed"))
                    }
                }
                // Network failure: keep local change pending? For now report failure.
                return Result.failure(IllegalArgumentException("password_change_failed"))
            }
        }

        userDao.upsert(entity.copy(passwordHash = PasswordHasher.hash(newPassword)))
        AppLogger.i("AuthRepository", "Password changed for ${entity.email}")
        return Result.success(Unit)
    }

    override suspend fun deleteAccount(): Result<Unit> {
        val current = currentUser.first() ?: return Result.failure(IllegalStateException("no_session"))

        val token = preferences.authToken.first()
        val client = api
        if (client != null && !token.isNullOrBlank()) {
            val result = client.deleteAccount("Bearer $token")
            if (result.isFailure) {
                AppLogger.w("AuthRepository", "Server account deletion failed: ${result.exceptionOrNull()?.message}")
                return Result.failure(result.exceptionOrNull() ?: IllegalStateException("account_delete_failed"))
            }
        }

        ReminderHelper.cancelReminders(context, eventDao, current.id)
        database.deleteAccountData(current.id)
        preferences.clearUserPreferences(current.id)
        preferences.setAuthToken(null)

        AppLogger.i("AuthRepository", "Account deleted for ${current.email}")
        return Result.success(Unit)
    }

    override suspend fun isLoggedIn(): Boolean =
        preferences.sessionUserId.first() != null

    override suspend fun continueAsGuest(): Result<User> {
        val guestId = preferences.sessionUserId.first()
        if (guestId != null) {
            val existingUser = userDao.findById(guestId)
            if (existingUser != null) {
                return Result.success(existingUser.toDomain())
            }
        }

        val user = UserEntity(
            id = UUID.randomUUID().toString(),
            fullName = "Guest",
            email = "",
            passwordHash = "",
            preferredLanguage = "en",
            defaultCity = "South Africa",
            defaultRadiusKm = 50,
            biometricEnabled = false,
            createdAt = System.currentTimeMillis()
        )
        userDao.upsert(user)
        preferences.setSessionUserId(user.id)
        AppLogger.i("AuthRepository", "Guest session created: ${user.id}")
        return Result.success(user.toDomain())
    }

    /** Creates or refreshes the local Room mirror of a server-side account. */
    private suspend fun upsertServerUser(
        id: String,
        email: String,
        fullName: String,
        language: String,
        passwordHash: String
    ): UserEntity {
        val existing = userDao.findById(id)
        val entity = existing ?: userDao.findByEmail(email)?.let { local ->
            // A pre-existing local account adopted the server id so the session
            // and all user-scoped preferences follow the account to the backend.
            userDao.upsert(local.copy(id = id))
            userDao.findById(id)
        } ?: UserEntity(
            id = id,
            fullName = fullName,
            email = email,
            passwordHash = passwordHash,
            preferredLanguage = language,
            defaultCity = "South Africa",
            defaultRadiusKm = 50,
                    biometricEnabled = false,
                    createdAt = System.currentTimeMillis(),
                    serverBacked = true
                )
                val refreshed = entity.copy(
            fullName = fullName.ifBlank { entity.fullName },
            email = email.ifBlank { entity.email },
            preferredLanguage = language.ifBlank { entity.preferredLanguage },
            passwordHash = passwordHash,
            serverBacked = true
        )
        userDao.upsert(refreshed)
        return refreshed
    }
}
