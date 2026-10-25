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
        dropAgent()
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

    private fun open(): Dadb = Dadb.create(host, 5555, keyPair(), 15_000, 30_000).also { dadb = it }

    /**
     * The helper on the TV (assets/keyserver.dex, source in keyserver/): started once in the background by the adb
     * shell, then reached over its own TCP port (8766) with a token only this phone knows. Keys land in a few
     * milliseconds, where "input keyevent" started a JVM per key (six seconds on the KM8), and files stream
     * straight into Download/KM8Remote, where ADB's own push left empty files. Nothing stays open inside adb:
     * long-lived adb shell streams left adbd threads spinning.
     */
    private var agent: java.net.Socket? = null
    private var agentIn: java.io.BufferedReader? = null
    private val agentLock = Mutex()

    private val token: String
        get() = prefs.getString("token", null) ?: java.util.UUID.randomUUID().toString().replace("-", "")
            .also { prefs.edit().putString("token", it).apply() }

    private fun dial(): Boolean {
        return try {
            val s = java.net.Socket()
            s.connect(InetSocketAddress(host, AGENT_PORT), 1500)
            s.tcpNoDelay = true
            s.soTimeout = 15_000
            val reader = s.getInputStream().bufferedReader()
            s.getOutputStream().write("$token\n".toByteArray())
            s.getOutputStream().flush()
            if (reader.readLine() != "ready") {
                s.close()
                return false
            }
            agent = s
            agentIn = reader
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Connects to the helper, starting it on the TV first when it isn't running. */
    private suspend fun ensureAgent() {
        if (agent != null) return
        if (withContext(Dispatchers.IO) { dial() }) return
        val dex = context.assets.open("keyserver.dex").use { it.readBytes() }
        val b64 = android.util.Base64.encodeToString(dex, android.util.Base64.NO_WRAP)
        // A helper left from an older version (another token) would hold the port.
        shell("pkill -f KeyServer; rm -f /data/local/tmp/km8keys.b64")
        b64.chunked(2000).forEach { shell("echo -n $it >> /data/local/tmp/km8keys.b64") }
        val size = shell(
            "base64 -d /data/local/tmp/km8keys.b64 > /data/local/tmp/km8keys.dex && rm /data/local/tmp/km8keys.b64 && " +
                    "stat -c %s /data/local/tmp/km8keys.dex"
        ).trim()
        check(size == dex.size.toString()) { "the helper didn't copy to the TV ($size)" }
        shell(
            "setsid sh -c 'CLASSPATH=/data/local/tmp/km8keys.dex exec app_process / KeyServer $AGENT_PORT $token' " +
                    "</dev/null >/dev/null 2>&1 &"
        )
        // The TV needs a few seconds to start it.
        withContext(Dispatchers.IO) {
            val until = System.currentTimeMillis() + 30_000
            while (!dial()) {
                check(System.currentTimeMillis() < until) { "the helper didn't start on the TV" }
                Thread.sleep(500)
            }
        }
    }

    private fun dropAgent() {
        try {
            agent?.close()
        } catch (e: Exception) {
            // already closed
        }
        agent = null
        agentIn = null
    }

    /** Sends one command line (and optional bytes) to the helper and returns its answer; one retry on a fresh link. */
    private suspend fun ask(line: String, bytes: File? = null): String = agentLock.withLock {
        for (attempt in 0..1) {
            try {
                ensureAgent()
                return@withLock withContext(Dispatchers.IO) {
                    val out = agent!!.getOutputStream()
                    out.write((line + "\n").toByteArray())
                    bytes?.inputStream()?.use { it.copyTo(out, 64 * 1024) }
                    out.flush()
                    agentIn!!.readLine() ?: throw java.io.EOFException("the helper closed")
                }
            } catch (e: Exception) {
                dropAgent()
                if (attempt == 1) throw e
            }
        }
        error("unreachable")
    }

    suspend fun key(code: Int) {
        try {
            ask(code.toString())
        } catch (e: Exception) {
            shell("input keyevent $code")
        }
    }

    /** Types into whatever text box has focus on the TV. */
    suspend fun type(text: String) {
        if (text.isEmpty()) return
        val plain = text.all { it.code in 32..126 }
        if (plain && runCatching { ask("t:$text") }.getOrNull() == "ok") return
        shell("input text " + shellQuote(text.replace(" ", "%s")))
    }

    /** Copies a file into the TV's Download/KM8Remote folder under {@code name}. */
    suspend fun upload(file: File, name: String) {
        val answer = ask("f:$name:${file.length()}", file)
        check(answer == "ok") { answer }
    }

    companion object {
        const val AGENT_PORT = 8766

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
