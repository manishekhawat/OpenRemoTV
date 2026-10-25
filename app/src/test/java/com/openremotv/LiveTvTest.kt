package com.openremotv

import dadb.AdbKeyPair
import dadb.Dadb
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Talks to a real TV with the Mac's already-trusted key, to check the library and the commands the app uses.
 * Skipped unless OPENREMOTV_TV_HOST is set: OPENREMOTV_TV_HOST=192.168.1.50 ./gradlew testReleaseUnitTest
 */
class LiveTvTest {
    @Test
    fun shellPushAndParse() {
        val host = System.getenv("OPENREMOTV_TV_HOST")
        assumeTrue(host != null)
        val home = System.getProperty("user.home")
        val key = AdbKeyPair.read(File("$home/.android_tvclean/adbkey"), File("$home/.android_tvclean/adbkey.pub"))
        Dadb.create(host!!, 5555, key).use { d ->
            println("model=" + d.shell("getprop ro.product.model").output.trim())
            val acts = d.shell("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER").output
            val launch = acts.lines().map { it.trim() }.filter { it.contains('/') && !it.contains(' ') }
            println("launchable=" + launch.size + " " + launch.take(3))
            val tmp = File.createTempFile("openremotv", ".txt").apply { writeText("hello from the phone app test") }
            d.shell("mkdir -p /sdcard/Download/OpenRemoTV")
            d.push(tmp, "/sdcard/Download/OpenRemoTV/test.txt", 420, System.currentTimeMillis())
            println("pushed=" + d.shell("cat /sdcard/Download/OpenRemoTV/test.txt; rm /sdcard/Download/OpenRemoTV/test.txt").output.trim())
            println("protected settings=" + Apps.isProtected("com.android.tv.settings") + " jio=" + Apps.isProtected("com.jio.web.androidtv"))
            println("label=" + Apps.label("com.droidlogic.FileBrower") + " yt=" + Send.videoId("https://youtu.be/dQw4w9WgXcQ?si=x"))
        }
    }
}
