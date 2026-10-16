package com.eventfinder.app.ui.screens.auth

import com.eventfinder.app.R
import com.eventfinder.app.data.repository.AuthRepository
import com.eventfinder.app.domain.model.User
import com.eventfinder.app.ui.components.UiMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ViewModel-level tests for the login and the two-step, email-verified
 * password-reset flows. A fake [AuthRepository] keeps the test on the JVM.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeAuthRepository : AuthRepository {
        override val currentUser: Flow<User?> = flowOf(null)
        override val biometricEnrolled: Flow<Boolean> = flowOf(false)
        var resetResult: Result<Unit> = Result.success(Unit)
        var forgotResult: Result<Unit> = Result.success(Unit)
        var lastConfirmReset: Triple<String, String, String>? = null
        var lastForgotEmail: String? = null

        override suspend fun requestPasswordReset(email: String): Result<Unit> {
            lastForgotEmail = email
            return forgotResult
        }

        override suspend fun confirmPasswordReset(
            email: String,
            token: String,
            newPassword: String
        ): Result<Unit> {
            lastConfirmReset = Triple(email, token, newPassword)
            return resetResult
        }

        override suspend fun googleSignIn(idToken: String, language: String): Result<User> =
            error("not used")

        override suspend fun register(
            fullName: String,
            email: String,
            password: String,
            language: String
        ): Result<User> = error("not used")

        override suspend fun login(email: String, password: String): Result<User> = error("not used")
        override suspend fun biometricLogin(): Result<User> = error("not used")
        override suspend fun logout() = error("not used")
        override suspend fun updateProfile(fullName: String, email: String): Result<User> =
            error("not used")

        override suspend fun verifyEmailChange(token: String): Result<Unit> = error("not used")

        override suspend fun setBiometricEnabled(enabled: Boolean) = error("not used")
        override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> =
            error("not used")

        override suspend fun deleteAccount(): Result<Unit> = error("not used")
        override suspend fun isLoggedIn(): Boolean = false
        override suspend fun continueAsGuest(): Result<User> = error("not used")
    }

    @Test
    fun `mismatched confirmation never reaches the repository`() = runTest(dispatcher) {
        val repo = FakeAuthRepository()
        val viewModel = LoginViewModel(repo, biometricAvailable = false)
        viewModel.onEmailChange("user@example.com")
        val messages = mutableListOf<UiMessage>()
        val job = launch { viewModel.messages.collect { messages += it } }

        viewModel.confirmPasswordReset("tok", "NewPass1!", "Different1!")
        advanceUntilIdle()
        job.cancel()

        assertEquals(listOf(UiMessage.Resource(R.string.passwords_mismatch)), messages)
        assertNull(repo.lastConfirmReset)
    }

    @Test
    fun `successful verified reset shows completion`() = runTest(dispatcher) {
        val repo = FakeAuthRepository()
        repo.resetResult = Result.success(Unit)
        val viewModel = LoginViewModel(repo, biometricAvailable = false)
        viewModel.onEmailChange("user@example.com")
        val messages = mutableListOf<UiMessage>()
        val job = launch { viewModel.messages.collect { messages += it } }

        viewModel.confirmPasswordReset("token-123", "NewPass1!", "NewPass1!")
        advanceUntilIdle()
        job.cancel()

        assertTrue(messages.contains(UiMessage.Resource(R.string.reset_success)))
        assertTrue(viewModel.uiState.value.passwordResetComplete)
        assertEquals(Triple("user@example.com", "token-123", "NewPass1!"), repo.lastConfirmReset)
    }

    @Test
    fun `blank reset token is rejected client-side`() = runTest(dispatcher) {
        val repo = FakeAuthRepository()
        val viewModel = LoginViewModel(repo, biometricAvailable = false)
        viewModel.onEmailChange("user@example.com")
        val messages = mutableListOf<UiMessage>()
        val job = launch { viewModel.messages.collect { messages += it } }

        viewModel.confirmPasswordReset("", "NewPass1!", "NewPass1!")
        advanceUntilIdle()
        job.cancel()

        assertTrue(messages.any { it == UiMessage.Resource(R.string.invalid_token) })
        assertNull(repo.lastConfirmReset)
        assertFalse(viewModel.uiState.value.passwordResetComplete)
    }

    @Test
    fun `send reset email with invalid address shows error`() = runTest(dispatcher) {
        val repo = FakeAuthRepository()
        val viewModel = LoginViewModel(repo, biometricAvailable = false)
        viewModel.onEmailChange("bad-email")
        val messages = mutableListOf<UiMessage>()
        val job = launch { viewModel.messages.collect { messages += it } }

        viewModel.sendResetEmail()
        advanceUntilIdle()
        job.cancel()

        assertTrue(messages.any { it == UiMessage.Resource(R.string.invalid_email) })
        assertNull(repo.lastForgotEmail)
        assertFalse(viewModel.uiState.value.resetEmailSent)
    }

    @Test
    fun `send reset email success marks the sent flag`() = runTest(dispatcher) {
        val repo = FakeAuthRepository()
        repo.forgotResult = Result.success(Unit)
        val viewModel = LoginViewModel(repo, biometricAvailable = false)
        viewModel.onEmailChange("user@example.com")
        val messages = mutableListOf<UiMessage>()
        val job = launch { viewModel.messages.collect { messages += it } }

        viewModel.sendResetEmail()
        advanceUntilIdle()
        job.cancel()

        assertEquals("user@example.com", repo.lastForgotEmail)
        assertTrue(viewModel.uiState.value.resetEmailSent)
        assertTrue(messages.contains(UiMessage.Resource(R.string.reset_email_sent)))
    }

    @Test
    fun `reset with weak password shows error`() = runTest(dispatcher) {
        val repo = FakeAuthRepository()
        val viewModel = LoginViewModel(repo, biometricAvailable = false)
        viewModel.onEmailChange("user@example.com")
        val messages = mutableListOf<UiMessage>()
        val job = launch { viewModel.messages.collect { messages += it } }

        viewModel.confirmPasswordReset("tok", "weak", "weak")
        advanceUntilIdle()
        job.cancel()

        // The ViewModel surfaces the specific policy failure client-side.
        assertTrue(messages.any { it == UiMessage.Resource(R.string.password_too_short) })
        assertFalse(viewModel.uiState.value.passwordResetComplete)
    }

    @Test
    fun `invalid token from the server shows the token error`() = runTest(dispatcher) {
        val repo = FakeAuthRepository()
        repo.resetResult = Result.failure(IllegalArgumentException("invalid_token"))
        val viewModel = LoginViewModel(repo, biometricAvailable = false)
        viewModel.onEmailChange("user@example.com")
        val messages = mutableListOf<UiMessage>()
        val job = launch { viewModel.messages.collect { messages += it } }

        viewModel.confirmPasswordReset("expired-token", "NewPass1!", "NewPass1!")
        advanceUntilIdle()
        job.cancel()

        assertTrue(messages.any { it == UiMessage.Resource(R.string.invalid_token) })
        assertFalse(viewModel.uiState.value.passwordResetComplete)
    }

    @Test
    fun `consumePasswordReset clears the completion flag`() = runTest(dispatcher) {
        val repo = FakeAuthRepository()
        val viewModel = LoginViewModel(repo, biometricAvailable = false)

        viewModel.uiState.value.let { state ->
            assertFalse(state.passwordResetComplete)
        }

        viewModel.consumePasswordReset()
        assertFalse(viewModel.uiState.value.passwordResetComplete)
        assertFalse(viewModel.uiState.value.resetEmailSent)
    }
}
