package com.km8.remote

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test

/** Renders the screens to PNGs (app/src/test/snapshots) to check the manga look without a phone. */
class ScreensTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, maxPercentDifference = 1.0)

    private fun page(tab: Tab, body: @androidx.compose.runtime.Composable () -> Unit) = paparazzi.snapshot {
        MangaTheme {
            MangaPage {
                Box(Modifier.weight(1f).fillMaxWidth()) { body() }
                TvBar(true, "KM8", "192.168.1.50") {}
                MangaTabs(tab) {}
            }
        }
    }

    @Test fun remote() {
        val tv = Tv(paparazzi.context)
        page(Tab.REMOTE) { RemoteScreen(tv) {} }
    }

    @Test fun connect() {
        val tv = Tv(paparazzi.context)
        page(Tab.REMOTE) { ConnectScreen(tv, CoroutineScope(Dispatchers.Unconfined)) {} }
    }
}
