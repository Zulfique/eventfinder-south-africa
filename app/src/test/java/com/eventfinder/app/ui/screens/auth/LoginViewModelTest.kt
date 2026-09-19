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
 * ViewModel-level tests for the local password-reset flow (FR-01). A fake
 * [AuthRepository] keeps the test on the JVM with no Android dependencies.
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
        var resetResult: Result<Unit> = Result.success(Unit)
        var lastReset: Pair<String, String>? = null

        override suspend fun resetPassword(email: String, newPassword: String): Result<Unit> {
            lastReset = email to newPassword
            return resetResult
        }

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

        override suspend fun setBiometricEnabled(enabled: Boolean) = error("not used")
        override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> =
            error("not used")

        override suspend fun deleteAccount(): Result<Unit> = error("not used")
        override suspend fun isLoggedIn(): Boolean = false
    }

    @Test
    fun `mismatched confirmation never reaches the repository`() = runTest(dispatcher) {
        val repo = FakeAuthRepository()
        val viewModel = LoginViewModel(repo, biometricAvailable = false)
        val messages = mutableListOf<UiMessage>()
        val job = launch { viewModel.messages.collect { messages += it } }

        viewModel.requestPasswordReset("NewPass1!", "Different1!")
        advanceUntilIdle()
        job.cancel()

        assertEquals(listOf(UiMessage.Resource(R.string.passwords_mismatch)), messages)
        assertNull(repo.lastReset)
    }

    @Test
    fun `successful reset flags completion and clears it on consume`() = runTest(dispatcher) {
        val repo = FakeAuthRepository()
        val viewModel = LoginViewModel(repo, biometricAvailable = false)
        viewModel.onEmailChange("user@example.com")
        val messages = mutableListOf<UiMessage>()
        val job = launch { viewModel.messages.collect { messages += it } }

        viewModel.requestPasswordReset("NewPass1!", "NewPass1!")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.passwordResetComplete)
        assertEquals("user@example.com" to "NewPass1!", repo.lastReset)
        assertTrue(messages.contains(UiMessage.Resource(R.string.reset_success)))

        viewModel.consumePasswordReset()
        assertFalse(viewModel.uiState.value.passwordResetComplete)
        job.cancel()
    }

    @Test
    fun `unknown email surfaces a specific message and keeps the dialog open`() = runTest(dispatcher) {
        val repo = FakeAuthRepository().apply {
            resetResult = Result.failure(IllegalArgumentException("unknown_email"))
        }
        val viewModel = LoginViewModel(repo, biometricAvailable = false)
        val messages = mutableListOf<UiMessage>()
        val job = launch { viewModel.messages.collect { messages += it } }

        viewModel.requestPasswordReset("NewPass1!", "NewPass1!")
        advanceUntilIdle()
        job.cancel()

        assertTrue(messages.contains(UiMessage.Resource(R.string.unknown_email)))
        assertFalse(viewModel.uiState.value.passwordResetComplete)
    }
}
