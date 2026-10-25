package com.openremotv

import com.openremotv.sweep.Lists
import com.openremotv.sweep.ShareCard
import com.openremotv.sweep.TvInfo
import com.openremotv.tv.Apps
import com.openremotv.tv.Tv
import com.openremotv.tv.TvApp
import com.openremotv.ui.MangaPage
import com.openremotv.ui.MangaTheme
import com.openremotv.ui.screens.AppTile
import com.openremotv.ui.screens.ConnectScreen
import com.openremotv.ui.screens.MangaTabs
import com.openremotv.ui.screens.PickScreen
import com.openremotv.ui.screens.RemoteScreen
import com.openremotv.ui.screens.SweepScreen
import com.openremotv.ui.screens.Tab
import com.openremotv.ui.screens.TvBar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
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
                TvBar(true, "KM8", "192.168.1.50", onChange = {})
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

    @Test fun appTiles() = paparazzi.snapshot {
        MangaTheme {
            MangaPage {
                androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth()) {
                    listOf("in.startv.hotstar", "com.droidlogic.FileBrower", "com.google.android.tv").forEach { pkg ->
                        Box(Modifier.fillMaxWidth(0.5f).padding(8.dp)) { AppTile(TvApp(pkg, true, false, "x")) {} }
                    }
                }
            }
        }
    }
}
