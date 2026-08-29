package com.tenmilelabs.chefai.core.data.timer

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.os.Build
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * A bare [Application], not the real (`@HiltAndroidApp`) `ChefAIApplication` — Robolectric
 * otherwise instantiates whatever the manifest declares, which pulls in real Hilt-wired
 * dependencies (`SessionManager` reaching for `AndroidKeyStore`, unavailable under Robolectric)
 * for a test that only needs a plain [android.content.Context].
 *
 * Also pins the SDK below the app's targetSdk (36) — ahead of the newest SDK Robolectric 4.15.1
 * ships shadows for (35).
 */
@Config(application = Application::class, sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
@RunWith(RobolectricTestRunner::class)
class RecipeTimerNotifierTest {

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `creates the notification channel eagerly, on construction`() {
        RecipeTimerNotifier(context)

        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel("recipe_timer")

        assertThat(channel).isNotNull()
        assertThat(channel.importance).isEqualTo(NotificationManager.IMPORTANCE_HIGH)
    }

    @Config(sdk = [Build.VERSION_CODES.S]) // API 31 — below the POST_NOTIFICATIONS requirement
    @Test
    fun `posts a notification without a runtime permission below API 33`() {
        val notifier = RecipeTimerNotifier(context)

        notifier.notifyTimerComplete("Step 2")

        val shadowManager = Shadows.shadowOf(context.getSystemService(NotificationManager::class.java))
        assertThat(shadowManager.allNotifications).hasSize(1)
    }

    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @Test
    fun `does not post when POST_NOTIFICATIONS hasn't been granted on API 33+`() {
        val notifier = RecipeTimerNotifier(context)

        notifier.notifyTimerComplete("Step 2")

        val shadowManager = Shadows.shadowOf(context.getSystemService(NotificationManager::class.java))
        assertThat(shadowManager.allNotifications).isEmpty()
    }

    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @Test
    fun `posts a notification once POST_NOTIFICATIONS is granted on API 33+`() {
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notifier = RecipeTimerNotifier(context)

        notifier.notifyTimerComplete("Step 2")

        val shadowManager = Shadows.shadowOf(context.getSystemService(NotificationManager::class.java))
        assertThat(shadowManager.allNotifications).hasSize(1)
    }
}
