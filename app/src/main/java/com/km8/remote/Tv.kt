package com.km8.remote

import android.content.Context
import android.net.ConnectivityManager
import dadb.AdbKeyPair
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket

/**
 * The one ADB connection to the TV, over Wi-Fi on port 5555. The app's own key is made on first use and kept in
 * its private files; the TV asks "Allow USB debugging?" once for it, and remembers it after "Always allow".
 * Every call runs off the main thread and reconnects once if the TV went to sleep in between.
 */
class Tv(private val context: Context) {

    private val lock = Mutex()
    private var dadb: Dadb? = null
    private val prefs = context.getSharedPreferences("tv", Context.MODE_PRIVATE)

    var host: String
        get() = prefs.getString("host", "192.168.1.50") ?: "192.168.1.50"
        set(value) = prefs.edit().putString("host", value.trim()).apply()

    private fun keyPair(): AdbKeyPair {
        val private = File(context.filesDir, "adbkey")
        val public = File(context.filesDir, "adbkey.pub")
        if (!private.exists() || !public.exists()) AdbKeyPair.generate(private, public)
        return AdbKeyPair.read(private, public)
    }

    /** Connects (the TV may show its "Allow" prompt now) and checks with a harmless command. */
    suspend fun connect(address: String = host): String = withContext(Dispatchers.IO) {
        lock.withLock {
            dadb?.close()
            dadb = null
            val d = Dadb.create(address, 5555, keyPair(), 30_000, 60_000)
            val model = d.shell("getprop ro.product.model").output.trim()
            dadb = d
            host = address
            model
        }
    }

    val connected: Boolean get() = dadb != null

    fun disconnect() {
        try {
            keys?.close()
        } catch (e: Exception) {
            // already closed
        }
        keys = null
        dadb?.close()
        dadb = null
    }

    /** A shell command's output; one reconnect if the old connection is gone. */
    suspend fun shell(command: String): String = withContext(Dispatchers.IO) {
        lock.withLock {
            try {
                (dadb ?: open()).shell(command).allOutput
            } catch (e: Exception) {
                dadb?.close()
                dadb = null
                open().shell(command).allOutput
            }
        }
    }

    suspend fun push(file: File, remotePath: String) = withContext(Dispatchers.IO) {
        lock.withLock {
            try {
                (dadb ?: open()).push(file, remotePath, 420 /* 0644 */, System.currentTimeMillis())
            } catch (e: Exception) {
                dadb?.close()
                dadb = null
                open().push(file, remotePath, 420, System.currentTimeMillis())
            }
        }
    }

    private fun open(): Dadb = Dadb.create(host, 5555, keyPair(), 15_000, 30_000).also { dadb = it }

    /**
     * The key server on the TV (assets/keyserver.dex, see keyserver/): one JVM started once per connection that
     * injects each key the moment it arrives. "input keyevent" starts a new JVM per key, about six seconds on the
     * KM8, so a few presses queued up for half a minute.
     */
    private var keys: dadb.AdbStream? = null
    private val keysLock = Mutex()

    private suspend fun keyStream(): dadb.AdbStream = keysLock.withLock {
        keys ?: withContext(Dispatchers.IO) {
            // Written through the shell rather than pushed: a push racing the other commands left an empty file.
            val dex = context.assets.open("keyserver.dex").use { it.readBytes() }
            val b64 = android.util.Base64.encodeToString(dex, android.util.Base64.NO_WRAP)
            // In pieces small enough for any ADB connection's packet size.
            shell("rm -f /data/local/tmp/km8keys.b64")
            b64.chunked(2000).forEach { shell("echo -n $it >> /data/local/tmp/km8keys.b64") }
            val size = shell(
                "base64 -d /data/local/tmp/km8keys.b64 > /data/local/tmp/km8keys.dex && rm /data/local/tmp/km8keys.b64 && " +
                        "stat -c %s /data/local/tmp/km8keys.dex"
            ).trim()
            check(size == dex.size.toString()) { "key helper not copied ($size)" }
            val d = lock.withLock { dadb ?: open() }
            val stream = d.open("shell:CLASSPATH=/data/local/tmp/km8keys.dex exec app_process / KeyServer")
            // Read its answers as they come so its output never backs up; the stream closing means it ended.
            Thread {
                try {
                    while (!stream.source.exhausted()) stream.source.readUtf8Line()
                } catch (e: Exception) {
                    // closed
                }
                if (keys === stream) keys = null
            }.apply { isDaemon = true }.start()
            keys = stream
            stream
        }
    }

    private suspend fun sendToKeyServer(line: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val s = keyStream()
            s.sink.writeUtf8(line + "\n")
            s.sink.flush()
            true
        } catch (e: Exception) {
            keys = null
            false
        }
    }

    suspend fun key(code: Int) {
        if (!sendToKeyServer(code.toString())) shell("input keyevent $code")
    }

    /** Types into whatever text box has focus on the TV. */
    suspend fun type(text: String) {
        if (text.isEmpty()) return
        val plain = text.all { it.code in 32..126 }
        if (plain && sendToKeyServer("t:$text")) return
        shell("input text " + shellQuote(text.replace(" ", "%s")))
    }

    companion object {
        fun shellQuote(s: String) = "'" + s.replace("'", "'\\''") + "'"

        /**
         * TVs on the phone's Wi-Fi with ADB's port open: every address of the /24 is tried at once, with a short
         * timeout, which takes a second or two.
         */
        suspend fun scan(context: Context): List<String> = coroutineScope {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val own = cm?.getLinkProperties(cm.activeNetwork)?.linkAddresses
                ?.map { it.address }?.firstOrNull { it is Inet4Address }?.hostAddress ?: return@coroutineScope emptyList()
            val prefix = own.substringBeforeLast('.')
            (1..254).map { i ->
                async(Dispatchers.IO) {
                    val ip = "$prefix.$i"
                    if (ip == own) return@async null
                    try {
                        Socket().use { it.connect(InetSocketAddress(ip, 5555), 400) }
                        ip
                    } catch (e: Exception) {
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }
}
