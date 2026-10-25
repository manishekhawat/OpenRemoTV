package com.openremotv

import com.openremotv.send.Send
import com.openremotv.tv.Apps
import com.openremotv.tv.Tv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plain JVM checks of the logic the screens rely on: no phone, no TV. */
class LogicTest {

    @Test fun youTubeLinks() {
        assertEquals("dQw4w9WgXcQ", Send.videoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", Send.videoId("Watch this https://youtu.be/dQw4w9WgXcQ?si=abc"))
        assertEquals("dQw4w9WgXcQ", Send.videoId("https://youtube.com/shorts/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", Send.videoId("https://www.youtube.com/watch?list=x&v=dQw4w9WgXcQ"))
        assertNull(Send.videoId("https://example.com/watch?v=dQw4w9WgXcQ"))
        assertNull(Send.videoId("not a link"))
    }

    @Test fun shellQuoting() {
        assertEquals("'plain'", Tv.shellQuote("plain"))
        assertEquals("'it'\\''s'", Tv.shellQuote("it's"))
        assertEquals("'a; rm -rf /'", Tv.shellQuote("a; rm -rf /"))
    }

    /** RFC 4231 test case 2: the phone signs the helper's challenge the same way javax.crypto does everywhere. */
    @Test fun helperSignature() {
        assertEquals(
            "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
            Tv.hmacHex("Jefe", "what do ya want for nothing?"),
        )
    }

    @Test fun appsTheTvNeedsAreLocked() {
        listOf("android", "com.android.systemui", "com.android.vending", "com.google.android.gms",
            "com.android.providers.media", "com.openremotv.home").forEach { assertTrue(it, Apps.isProtected(it)) }
        listOf("com.netflix.ninja", "com.google.android.tvlauncher", "com.android.providers.calendar",
            "in.startv.hotstar").forEach { assertFalse(it, Apps.isProtected(it)) }
    }

    @Test fun readableNames() {
        assertEquals("YouTube", Apps.label("com.google.android.youtube.tv"))
        assertTrue(Apps.label("com.example.some.weather_app").isNotBlank())
    }
}
