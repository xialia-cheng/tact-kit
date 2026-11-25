package com.xialia.tactkit

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.xialia.tactkit.data.datastore.SettingsRepository
import com.xialia.tactkit.data.repository.AppUpdateRepository
import com.xialia.tactkit.data.repository.ReleaseInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppUpdateAndOnboardingTest {

  @Test
  fun `release info constructs official, fastest mirror, and stable mirror download URLs`() {
    val info =
      ReleaseInfo(
        tagName = "v1.2.0",
        title = "TactKit v1.2.0",
        body = "Release notes",
        htmlUrl = "https://github.com/xialia-cheng/tact-kit/releases/tag/v1.2.0",
        apkDownloadUrl =
          "https://github.com/xialia-cheng/tact-kit/releases/download/v1.2.0/TactKit-v1.2.0.apk",
        isUpdateAvailable = true,
      )

    assertEquals(
      "https://github.com/xialia-cheng/tact-kit/releases/download/v1.2.0/TactKit-v1.2.0.apk",
      info.officialDownloadUrl,
    )
    assertEquals(
      "https://ghfast.top/https://github.com/xialia-cheng/tact-kit/releases/download/v1.2.0/TactKit-v1.2.0.apk",
      info.fastestMirrorDownloadUrl,
    )
    assertEquals(
      "https://gh-proxy.com/https://github.com/xialia-cheng/tact-kit/releases/download/v1.2.0/TactKit-v1.2.0.apk",
      info.stableMirrorDownloadUrl,
    )
    assertTrue(AppUpdateRepository.isNewerVersion("0.6.0", "v1.2.0"))
    assertFalse(AppUpdateRepository.isNewerVersion("1.2.0", "v1.2.0"))
  }

  @Test
  fun `settings repository defaults autoCheckUpdates true and hasCompletedOnboarding false`() =
    runTest {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val repo = SettingsRepository(context)

      val initial = repo.preferencesFlow.first()
      assertTrue(initial.autoCheckUpdates)
      assertFalse(initial.hasCompletedOnboarding)

      repo.setAutoCheckUpdates(false)
      repo.setHasCompletedOnboarding(true)

      val updated = repo.preferencesFlow.first()
      assertFalse(updated.autoCheckUpdates)
      assertTrue(updated.hasCompletedOnboarding)
    }
}
