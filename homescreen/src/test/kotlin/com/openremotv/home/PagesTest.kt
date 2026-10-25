package com.openremotv.home

import android.content.Intent
import android.view.View
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

/** Renders both pages at 1080p (960×540 dp, like a TV) with the bundled themes, to check the layout without a TV. */
class PagesTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig(
            screenHeight = 1080, screenWidth = 1920, xdpi = 320, ydpi = 320, orientation = ScreenOrientation.LANDSCAPE,
            uiMode = UiMode.TELEVISION, density = Density.XHIGH, touchScreen = TouchScreen.NOTOUCH,
            navigation = Navigation.DPAD, softButtons = false,
        ),
        theme = "android:Theme.Material.NoActionBar.Fullscreen",
        maxPercentDifference = 1.0,
    )

    @get:Rule
    val tmp = TemporaryFolder()

    private fun theme(name: String): Theme {
        val src = File("../app/src/main/assets/themes/$name")
        val dir = tmp.newFolder(name)
        File(src, Theme.WALLPAPERS).copyRecursively(File(dir, Theme.WALLPAPERS))
        return Theme(JSONObject(File(src, "config.json").readText()), dir, "test")
    }

    @Test fun india() {
        val page = IndiaHome(paparazzi.context, theme("india"), object : IndiaHome.Host {
            override fun openApp(app: AppList.App) {}
            override fun button(which: String, anchor: View) {}
        })
        page.cards.setGreeting("Good afternoon")
        page.setToday("Ashwin Shukla Chaturdashi\nPurnima tomorrow\nRahu kaal 4:18 PM – 5:43 PM")
        page.cards.setWeather(JSONObject("""{"current":{"temperature_2m":29,"apparent_temperature":31,
            "relative_humidity_2m":46,"wind_speed_10m":6,"weather_code":0},"aqi":153,"daily":{
            "time":["2026-10-25","2026-10-26","2026-10-27","2026-10-28","2026-10-29"],
            "temperature_2m_max":[29,30,30,30,28],"temperature_2m_min":[18,18,18,17,19],
            "sunrise":["2026-10-25T06:28"],"sunset":["2026-10-25T17:42"]}}"""))
        paparazzi.snapshot(page)
    }

    @Test fun manga() {
        val t = theme("manga")
        val page = ComicHome(paparazzi.context, t, t.dockApps, object : ComicHome.Host {
            override fun open(intent: Intent) {}
            override fun button(which: String) {}
        })
        paparazzi.snapshot(page)
    }
}
