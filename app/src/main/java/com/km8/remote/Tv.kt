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

    suspend fun key(code: Int) {
        shell("input keyevent $code")
    }

    /** Types into whatever text box has focus on the TV. */
    suspend fun type(text: String) {
        if (text.isEmpty()) return
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
