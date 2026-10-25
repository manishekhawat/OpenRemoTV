package com.openremotv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.openremotv.tv.Tv
import com.openremotv.ui.MangaPage
import com.openremotv.ui.MangaTheme
import com.openremotv.ui.screens.MangaTabs
import com.openremotv.ui.screens.RemoteScreen
import com.openremotv.ui.screens.Tab
import com.openremotv.ui.screens.TvBar
import org.junit.Rule
import org.junit.Test

/** The remote in Hindi: checks the translation fits the one-screen layout. */
class HindiTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6.copy(locale = "hi"), maxPercentDifference = 1.0)

    @Test fun remote() {
        val tv = Tv(paparazzi.context)
        paparazzi.snapshot {
            MangaTheme {
                MangaPage {
                    Box(Modifier.weight(1f).fillMaxWidth()) { WithResults { RemoteScreen(tv) {} } }
                    TvBar(true, "KM8", "192.168.1.50", onChange = {})
                    MangaTabs(Tab.REMOTE) {}
                }
            }
        }
    }
}
