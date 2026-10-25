package com.openremotv.home

import android.content.Intent
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.Navigation
import com.android.resources.ScreenOrientation
import com.android.resources.TouchScreen
import com.android.resources.UiMode
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** MangaHome with the TV set to Hindi: the translation has to fit the comic panels. */
class HindiPagesTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig(
            screenHeight = 1080, screenWidth = 1920, xdpi = 320, ydpi = 320, orientation = ScreenOrientation.LANDSCAPE,
            uiMode = UiMode.TELEVISION, density = Density.XHIGH, touchScreen = TouchScreen.NOTOUCH,
            navigation = Navigation.DPAD, softButtons = false, locale = "hi",
        ),
        theme = "android:Theme.Material.NoActionBar.Fullscreen",
        maxPercentDifference = 1.0,
    )

    @get:Rule
    val tmp = TemporaryFolder()

    @Test fun manga() {
        val src = File("../app/src/main/assets/themes/manga")
        val dir = tmp.newFolder("manga")
        val t = Theme(JSONObject(File(src, "config.json").readText()), dir, "test")
        paparazzi.snapshot(ComicHome(paparazzi.context, t, t.dockApps, object : ComicHome.Host {
            override fun open(intent: Intent) {}
            override fun button(which: String) {}
        }))
    }
}
