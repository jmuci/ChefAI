package com.tenmilelabs.chefai.core.ui.banner

import com.google.common.truth.Truth.assertThat
import com.tenmilelabs.chefai.R
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class StatusBannerControllerTest {

    private val controller = StatusBannerController()

    @Test
    fun `show publishes the message with its tone and the five-minute default`() {
        controller.show(R.string.status_banner_offline, StatusBannerTone.WARNING)

        val shown = requireNotNull(controller.message.value)
        assertThat(shown.message).isEqualTo(R.string.status_banner_offline)
        assertThat(shown.tone).isEqualTo(StatusBannerTone.WARNING)
        assertThat(shown.displayDuration).isEqualTo(5.minutes)
        assertThat(shown.placement).isEqualTo(StatusBannerPlacement.BOTTOM)
    }

    @Test
    fun `showing the same message twice gives it a new id so its countdown restarts`() {
        controller.show(R.string.status_banner_server_error, StatusBannerTone.ERROR)
        val first = requireNotNull(controller.message.value)

        controller.show(R.string.status_banner_server_error, StatusBannerTone.ERROR)

        assertThat(requireNotNull(controller.message.value).id).isNotEqualTo(first.id)
    }

    @Test
    fun `dismiss hides the message it names`() {
        controller.show(R.string.status_banner_server_error, StatusBannerTone.ERROR)

        controller.dismiss(requireNotNull(controller.message.value).id)

        assertThat(controller.message.value).isNull()
    }

    @Test
    fun `a stale dismiss leaves the newer message up`() {
        // Given: an error replaced by a newer warning before its countdown elapsed
        controller.show(R.string.status_banner_server_error, StatusBannerTone.ERROR)
        val stale = requireNotNull(controller.message.value)
        controller.show(R.string.status_banner_offline, StatusBannerTone.WARNING)

        // When: the old message's countdown fires
        controller.dismiss(stale.id)

        // Then: the warning is still showing
        assertThat(controller.message.value?.message).isEqualTo(R.string.status_banner_offline)
    }
}
