package com.openremotv.tv

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
        get() = prefs.getString("host", "") ?: ""
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
     * shell, then reached over its own TCP port (8766). It answers only this phone's address, and only after a
     * challenge signed with a secret that never crosses the network. Keys land in a few milliseconds, where
     * "input keyevent" started a JVM per key (six seconds on slow TVs), and files stream
     * straight into Download/OpenRemoTV, where ADB's own push left empty files. Nothing stays open inside adb:
     * long-lived adb shell streams left adbd threads spinning.
     */
    private var agent: java.net.Socket? = null
    private var agentIn: java.io.BufferedReader? = null
    private val agentLock = Mutex()

    /** This phone's secret, plus the helper's size: a helper from another app version won't answer to it. */
    private val secret: String
        get() = (prefs.getString("token", null) ?: java.util.UUID.randomUUID().toString().replace("-", "")
            .also { prefs.edit().putString("token", it).apply() }) + "-" + helperSize

    private val helperSize: Int by lazy { context.assets.open("keyserver.dex").use { it.readBytes().size } }

    private fun dial(): Boolean {
        return try {
            val s = java.net.Socket()
            s.connect(InetSocketAddress(host, AGENT_PORT), 1500)
            s.tcpNoDelay = true
            // An old helper never sends a challenge: give up quickly and start a fresh one.
            s.soTimeout = 3_000
            val reader = s.getInputStream().bufferedReader()
            val nonce = reader.readLine()?.removePrefix("nonce ")
            if (nonce == null) {
                s.close()
                return false
            }
            s.getOutputStream().write((sign(nonce) + "\n").toByteArray())
            s.getOutputStream().flush()
            if (reader.readLine() != "ready") {
                s.close()
                return false
            }
            s.soTimeout = 15_000
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
        // Its own line, and a pattern that doesn't match itself: "pkill -f KeyServer" also killed the shell running it.
        shell("pkill -f '[K]eyServer'")
        shell("rm -f $HELPER.b64 $HELPER.key /data/local/tmp/km8keys.*")
        b64.chunked(2000).forEach { shell("echo -n $it >> $HELPER.b64") }
        val size = shell("base64 -d $HELPER.b64 > $HELPER.dex && rm $HELPER.b64 && stat -c %s $HELPER.dex").trim()
        check(size == dex.size.toString()) { "the helper didn't copy to the TV ($size)" }
        // The helper answers this phone's address only; a new address (DHCP) means a fresh start here.
        val phone = ownAddress(context) ?: error("the phone has no Wi-Fi address")
        // The secret goes into a file only the shell user can read; the helper reads it, then deletes it.
        shell("umask 077; echo -n ${shellQuote(secret)} > $HELPER.key")
        shell(
            "setsid sh -c 'CLASSPATH=$HELPER.dex exec app_process / KeyServer $AGENT_PORT $HELPER.key $phone' " +
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

    /**
     * Submits what was typed, as a keyboard's Enter would. With the TV's on-screen keyboard showing, Enter would press
     * whichever key it has selected (an extra "a"), so the keyboard is closed first and Enter then reaches the search box.
     */
    suspend fun submit() {
        val keyboardShown = shell("dumpsys input_method | grep -c 'mInputShown=true'").trim().let { it.isNotEmpty() && it != "0" }
        if (keyboardShown) {
            key(4)
            kotlinx.coroutines.delay(250)
        }
        key(66)
    }

    /**
     * Asks the TV's assistant (Google Assistant, or Gemini on newer Google TV) a question spoken on the phone: the
     * TV's search opens with the words already in. Falls back to the assistant key when the TV has no such search.
     */
    suspend fun askAssistant(question: String) {
        val out = shell("am start -a android.search.action.GLOBAL_SEARCH --es query " + shellQuote(question) + " 2>&1")
        if (out.contains("Error") || out.contains("unable", ignoreCase = true)) key(KEY_ASSIST)
    }

    /** Opens the TV's own assistant, listening through the TV remote's mic. */
    suspend fun assistant() = key(KEY_ASSIST)

    /** Turns an app on, or off for this user, through the helper (instant), else through pm (seconds). */
    suspend fun setAppEnabled(pkg: String, on: Boolean) {
        val answer = runCatching { ask("e:$pkg:${if (on) 1 else 0}") }.getOrNull()
        if (answer == "ok") return
        val out = shell(if (on) "pm enable " + shellQuote(pkg) else "pm disable-user --user 0 " + shellQuote(pkg))
        check(out.contains("new state")) { out.trim().ifEmpty { "the TV refused" } }
    }

    /** Copies a file into the TV's Download/OpenRemoTV folder under {@code name}. */
    suspend fun upload(file: File, name: String) {
        val answer = ask("f:$name:${file.length()}", file)
        check(answer == "ok") { answer }
    }

    private fun sign(nonce: String) = hmacHex(secret, nonce)

    companion object {
        const val AGENT_PORT = 8766
        /** KEYCODE_VOICE_ASSIST: the remote's assistant button. */
        const val KEY_ASSIST = 231
        private const val HELPER = "/data/local/tmp/openremotv-helper"

        /** The answer to the helper's challenge: HMAC-SHA256 of its nonce with the phone's secret, in hex. */
        fun hmacHex(secret: String, message: String): String {
            val mac = javax.crypto.Mac.getInstance("HmacSHA256")
            mac.init(javax.crypto.spec.SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
            return mac.doFinal(message.toByteArray()).joinToString("") { "%02x".format(it) }
        }

        /** This phone's IPv4 address on its current network, or null. */
        fun ownAddress(context: Context): String? {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            return cm?.getLinkProperties(cm.activeNetwork)?.linkAddresses
                ?.map { it.address }?.firstOrNull { it is Inet4Address }?.hostAddress
        }

        fun shellQuote(s: String) = "'" + s.replace("'", "'\\''") + "'"

        /**
         * TVs on the phone's Wi-Fi with ADB's port open: every address of the /24 is tried at once, with a short
         * timeout, which takes a second or two.
         */
        suspend fun scan(context: Context): List<String> = coroutineScope {
            val own = ownAddress(context) ?: return@coroutineScope emptyList()
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
