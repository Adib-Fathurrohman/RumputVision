package com.rumput.vision.domain.usecase

import com.rumput.vision.core.common.Clock
import com.rumput.vision.core.common.IdGenerator
import com.rumput.vision.domain.model.AuthMode
import com.rumput.vision.domain.model.EndpointPreference
import com.rumput.vision.domain.repository.PasswordUpdate
import com.rumput.vision.testing.FakeServerProfileRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveServerProfileUseCaseTest {
    private val repository = FakeServerProfileRepository()
    private var now = 1_000L
    private val useCase = SaveServerProfileUseCase(
        repository = repository,
        validator = ValidateServerProfileUseCase(),
        clock = Clock { now },
        idGenerator = IdGenerator { "generated-id" },
    )

    private val loginDraft = ServerProfileDraft(
        displayName = " Home ",
        localUrl = "https://frigate.home.arpa",
        remoteUrl = "https://nvr.tail1234.ts.net/",
        authMode = AuthMode.FRIGATE_LOGIN,
        username = " admin ",
        password = "correct horse",
    )

    @Test
    fun `saves a valid new profile, trims fields and stores the password`() = runTest {
        val result = useCase(loginDraft)

        assertEquals(SaveProfileResult.Saved("generated-id"), result)
        val saved = repository.getProfile("generated-id")!!
        assertEquals("Home", saved.displayName)
        assertEquals("admin", saved.username)
        assertEquals("https://nvr.tail1234.ts.net", saved.remoteUrl?.value)
        assertTrue("first profile becomes active", saved.isActive)
        assertEquals("correct horse", repository.passwords["generated-id"])
    }

    @Test
    fun `rejects login over http`() = runTest {
        val result = useCase(loginDraft.copy(localUrl = "http://192.168.1.10"))

        result as SaveProfileResult.Invalid
        assertEquals(UrlError.HTTPS_REQUIRED_FOR_LOGIN, result.validation.localUrlError)
        assertTrue(repository.profiles.value.isEmpty())
    }

    @Test
    fun `reports every invalid field at once`() = runTest {
        val result = useCase(ServerProfileDraft(authMode = AuthMode.FRIGATE_LOGIN)) as SaveProfileResult.Invalid

        with(result.validation) {
            assertEquals(NameError.REQUIRED, nameError)
            assertEquals(FormError.AT_LEAST_ONE_URL, formError)
            assertTrue(usernameRequired)
        }
    }

    @Test
    fun `endpoint preference requires the matching URL`() = runTest {
        val draft = loginDraft.copy(localUrl = "", endpointPreference = EndpointPreference.LOCAL_ONLY)
        val result = useCase(draft) as SaveProfileResult.Invalid
        assertEquals(FormError.LOCAL_URL_REQUIRED_FOR_PREFERENCE, result.validation.formError)
    }

    @Test
    fun `no-auth profile over LAN http is allowed with warnings and clears credentials`() = runTest {
        val draft = ServerProfileDraft(
            displayName = "Home",
            localUrl = "http://192.168.1.10:5000",
            authMode = AuthMode.NONE,
            username = "ignored",
            password = "ignored",
        )
        val validation = ValidateServerProfileUseCase()(draft)
        assertTrue(validation.isValid)
        assertTrue(UrlWarning.UNAUTHENTICATED_INTERNAL_PORT in validation.localUrlWarnings)

        useCase(draft)
        val saved = repository.getProfile("generated-id")!!
        assertNull(saved.username)
        assertEquals(PasswordUpdate.ClearAll, repository.lastPasswordUpdate["generated-id"])
        assertFalse(repository.passwords.containsKey("generated-id"))
    }

    @Test
    fun `editing keeps id, creation time, active flag and stored password when password is blank`() = runTest {
        useCase(loginDraft)
        now = 2_000L

        useCase(loginDraft.copy(id = "generated-id", displayName = "Renamed", password = ""))

        val saved = repository.getProfile("generated-id")!!
        assertEquals("Renamed", saved.displayName)
        assertEquals(1_000L, saved.createdAtEpochMillis)
        assertEquals(2_000L, saved.updatedAtEpochMillis)
        assertTrue(saved.isActive)
        assertEquals(PasswordUpdate.Keep, repository.lastPasswordUpdate["generated-id"])
        assertEquals("correct horse", repository.passwords["generated-id"])
    }

    @Test
    fun `changing the username without a new password clears stored secrets`() = runTest {
        useCase(loginDraft)
        useCase(loginDraft.copy(id = "generated-id", username = "someone-else", password = ""))

        assertEquals(PasswordUpdate.ClearAll, repository.lastPasswordUpdate["generated-id"])
        assertFalse(repository.passwords.containsKey("generated-id"))
    }

    @Test
    fun `turning off remember password clears it`() = runTest {
        useCase(loginDraft)
        useCase(loginDraft.copy(id = "generated-id", password = "", rememberPassword = false))

        assertEquals(PasswordUpdate.Clear, repository.lastPasswordUpdate["generated-id"])
    }

    @Test
    fun `draft toString never contains the password`() {
        assertFalse(loginDraft.toString().contains("correct horse"))
        assertFalse(PasswordUpdate.Set("correct horse").toString().contains("correct horse"))
    }
}
