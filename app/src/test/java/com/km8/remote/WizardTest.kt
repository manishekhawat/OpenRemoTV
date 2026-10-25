package com.km8.remote

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import java.io.File

class WizardTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, maxPercentDifference = 1.0)

    @Test fun pick() = paparazzi.snapshot {
        MangaTheme { MangaPage { Box(Modifier.weight(1f).fillMaxWidth()) { PickScreen {} } } }
    }

    @Test fun sweepStart() {
        val tv = Tv(paparazzi.context)
        paparazzi.snapshot {
            MangaTheme {
                MangaPage {
                    Box(Modifier.weight(1f).fillMaxWidth()) { SweepScreen(tv, Apps(paparazzi.context, tv), {}, {}) }
                    MangaTabs(Tab.SWEEP) {}
                }
            }
        }
    }

    @Test fun matchingAndCard() {
        val km8 = TvInfo("KM8", "MECOOL", "MECOOL", "p212", "8.0.0")
        println("KM8 matches: " + Lists.matches(paparazzi.context, km8).map { it.id })
        val sony = TvInfo("BRAVIA 4K VH2", "Sony", "Sony", "BRAVIA_VH2", "10")
        println("Sony matches: " + Lists.matches(paparazzi.context, sony).map { it.id })
        println("common: " + Lists.common(paparazzi.context).packages.size)
        val out = File("build/share-card.png")
        out.outputStream().use { ShareCard.draw("MECOOL KM8", 21, "Manga Home").compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
