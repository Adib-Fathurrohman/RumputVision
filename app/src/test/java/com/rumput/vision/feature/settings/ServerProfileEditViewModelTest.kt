package com.rumput.vision.feature.settings

import androidx.lifecycle.SavedStateHandle
import com.rumput.vision.core.common.Clock
import com.rumput.vision.core.common.IdGenerator
import com.rumput.vision.domain.model.AuthMode
import com.rumput.vision.domain.repository.PasswordUpdate
import com.rumput.vision.domain.usecase.NameError
import com.rumput.vision.domain.usecase.SaveServerProfileUseCase
import com.rumput.vision.domain.usecase.UrlError
import com.rumput.vision.domain.usecase.UrlWarning
import com.rumput.vision.domain.usecase.ValidateServerProfileUseCase
import com.rumput.vision.testing.FakeConnectionTester
import com.rumput.vision.testing.FakeServerProfileRepository
import com.rumput.vision.testing.MainDispatcherRule
import com.rumput.vision.testing.profile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ServerProfileEditViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeServerProfileRepository()

    private fun viewModel(profileId: String? = null) = ServerProfileEditViewModel(
        savedStateHandle = SavedStateHandle(if (profileId != null) mapOf("profileId" to profileId) else emptyMap()),
        repository = repository,
        validate = ValidateServerProfileUseCase(),
        saveProfile = SaveServerProfileUseCase(repository, ValidateServerProfileUseCase(), Clock { 0 }, IdGenerator { "new" }),
        connectionTester = tester,
    )

    private val tester = FakeConnectionTester()

    @Test
    fun `new profile starts empty with errors hidden`() {
        val state = viewModel().uiState.value
        assertTrue(state.isNew)
        assertFalse(state.isLoading)
        assertFalse(state.showErrors)
        assertEquals(NameError.REQUIRED, state.validation.nameError)
    }

    @Test
    fun `warnings update live while typing`() {
        val vm = viewModel()
        vm.onAuthModeChange(AuthMode.NONE)
        vm.onLocalUrlChange("http://192.168.100.22:5000")

        val warnings = vm.uiState.value.validation.localUrlWarnings
        assertTrue(UrlWarning.CLEARTEXT in warnings)
        assertTrue(UrlWarning.UNAUTHENTICATED_INTERNAL_PORT in warnings)
    }

    @Test
    fun `saving an invalid draft shows errors and does not finish`() {
        val vm = viewModel()
        vm.onDisplayNameChange("Home")
        vm.onLocalUrlChange("http://frigate.home.arpa")
        vm.onUsernameChange("admin")

        vm.save()

        val state = vm.uiState.value
        assertTrue(state.showErrors)
        assertFalse(state.finished)
        assertEquals(UrlError.HTTPS_REQUIRED_FOR_LOGIN, state.validation.localUrlError)
        assertTrue(repository.profiles.value.isEmpty())
    }

    @Test
    fun `saving a valid draft persists it, finishes and clears the password from state`() {
        val vm = viewModel()
        vm.onDisplayNameChange("Home")
        vm.onLocalUrlChange("https://frigate.home.arpa")
        vm.onUsernameChange("admin")
        vm.onPasswordChange("hunter2")

        vm.save()

        val state = vm.uiState.value
        assertTrue(state.finished)
        assertEquals("", state.draft.password)
        assertEquals("hunter2", repository.passwords["new"])
        assertTrue(repository.profiles.value.single().isActive)
    }

    @Test
    fun `editing loads the existing profile without exposing the password`() = runTest {
        repository.saveProfile(
            profile(id = "p1", name = "Home", local = "https://frigate.home.arpa", authMode = AuthMode.FRIGATE_LOGIN, username = "admin"),
            PasswordUpdate.Set("hunter2"),
        )

        val state = viewModel("p1").uiState.value

        assertFalse(state.isNew)
        assertEquals("Home", state.draft.displayName)
        assertEquals("admin", state.draft.username)
        assertEquals("", state.draft.password)
        assertTrue(state.hasStoredPassword)
        assertTrue(state.draft.rememberPassword)
    }

    @Test
    fun `missing profile shows not found`() {
        assertTrue(viewModel("ghost").uiState.value.notFound)
    }

    @Test
    fun `delete removes the profile and finishes`() = runTest {
        repository.saveProfile(profile(id = "p1"), PasswordUpdate.Keep)
        val vm = viewModel("p1")

        vm.delete()

        assertTrue(vm.uiState.value.finished)
        assertTrue(repository.profiles.value.isEmpty())
    }

    @Test
    fun `test connection validates first, runs the tester, and results reset on edit`() {
        val vm = viewModel()
        vm.onDisplayNameChange("Home")
        vm.onLocalUrlChange("http://frigate.home.arpa")
        vm.onUsernameChange("admin")

        vm.testConnection() // login over http is invalid: nothing is contacted
        assertTrue(tester.tested.isEmpty())
        assertTrue(vm.uiState.value.showErrors)

        tester.report = com.rumput.vision.domain.model.ConnectionTestReport(
            listOf(
                com.rumput.vision.domain.model.EndpointTestResult(
                    com.rumput.vision.domain.model.ConnectionRoute.LOCAL,
                    "https://frigate.home.arpa",
                    com.rumput.vision.domain.model.EndpointTestOutcome.NotFrigate,
                ),
            ),
        )
        vm.onLocalUrlChange("https://frigate.home.arpa")
        vm.testConnection()
        assertEquals(1, tester.tested.size)
        assertEquals(tester.report, vm.uiState.value.testReport)

        vm.onLocalUrlChange("https://other.home.arpa")
        assertEquals(null, vm.uiState.value.testReport)
    }
}
