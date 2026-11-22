package com.rumput.vision.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.rumput.vision.MainActivity
import com.rumput.vision.data.repository.RepositoryModule
import com.rumput.vision.domain.repository.AppPreferencesRepository
import com.rumput.vision.domain.repository.ServerProfileRepository
import com.rumput.vision.testing.FakeAppPreferencesRepository
import com.rumput.vision.testing.FakeServerProfileRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [RepositoryModule::class])
abstract class FakeRepositoryModule {
    @Binds abstract fun profiles(fake: FakeServerProfileRepository): ServerProfileRepository

    @Binds abstract fun preferences(fake: FakeAppPreferencesRepository): AppPreferencesRepository
}

/** End-to-end navigation through the real activity, Hilt graph and screens, with fake repositories. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class AppNavigationTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun onboarding_addServer_thenNavigateAllTabs() {
        composeRule.onNodeWithTag("onboarding").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_add_server").performClick()

        composeRule.onNodeWithTag("screen_profile_edit").assertIsDisplayed()
        composeRule.onNodeWithTag("field_name").performTextInput("Rumah")
        composeRule.onNodeWithTag("field_local_url").performTextInput("http://frigate.home.arpa")
        composeRule.onNodeWithText("None").performScrollTo().performClick()
        composeRule.onNodeWithTag("profile_save").performScrollTo().performClick()

        // Onboarding is popped: Home is the root and shows the saved server with a cleartext warning.
        composeRule.onNodeWithTag("screen_home").assertIsDisplayed()
        composeRule.onNodeWithText("Rumah").assertIsDisplayed()
        composeRule.onNodeWithText("Unencrypted connection").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_cameras").performClick()
        composeRule.onNodeWithTag("screen_cameras").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_events").performClick()
        composeRule.onNodeWithTag("screen_events").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_recordings").performClick()
        composeRule.onNodeWithTag("screen_recordings").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.onNodeWithTag("screen_settings").assertIsDisplayed()
        composeRule.onNodeWithText("Rumah").assertIsDisplayed()
        composeRule.onNodeWithText("Active").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_home").performClick()
        composeRule.onNodeWithTag("screen_home").assertIsDisplayed()
    }

    @Test
    fun loginOverHttp_isRejectedWithMessage() {
        composeRule.onNodeWithTag("onboarding_add_server").performClick()
        composeRule.onNodeWithTag("field_name").performTextInput("Rumah")
        composeRule.onNodeWithTag("field_local_url").performTextInput("http://192.168.100.22")
        composeRule.onNodeWithTag("field_username").performScrollTo().performTextInput("admin")
        composeRule.onNodeWithTag("profile_save").performScrollTo().performClick()

        composeRule.onNodeWithText("Signing in requires HTTPS so your password is not sent in plain text")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("screen_profile_edit").assertIsDisplayed()
    }
}
