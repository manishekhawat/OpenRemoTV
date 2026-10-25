package com.openremotv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openremotv.send.Send
import com.openremotv.tv.Apps
import com.openremotv.tv.Tv
import com.openremotv.ui.MangaPage
import com.openremotv.ui.MangaTheme
import com.openremotv.ui.SpeechBubble
import com.openremotv.ui.screens.AboutScreen
import com.openremotv.ui.screens.AppsScreen
import com.openremotv.ui.screens.CheckScreen
import com.openremotv.ui.screens.ConnectScreen
import com.openremotv.ui.screens.MangaTabs
import com.openremotv.ui.screens.PickScreen
import com.openremotv.ui.screens.RemoteScreen
import com.openremotv.ui.screens.SendScreen
import com.openremotv.ui.screens.SweepByHandScreen
import com.openremotv.ui.screens.SweepScreen
import com.openremotv.ui.screens.Tab
import com.openremotv.ui.screens.TextButtonLine
import com.openremotv.ui.screens.TvBar
import com.openremotv.ui.screens.TvKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * OpenRemoTV: a phone app for Android TV, Google TV and Fire TV over Wi-Fi, with a manga look. First Pick my TV and
 * Connect; then five tabs: Remote (arrows, OK, back, home, volume, power, typing), Apps (open anything on the TV),
 * Send (YouTube links, photos, videos, files), Sweep (backup, the tested list for this TV, clean home screen, done,
 * share it, undo anytime; or single apps by hand) and Check (apps a TV update turned back on). Sharing a link or a
 * file to this app from any other app sends it straight to the TV.
 */
class MainActivity : ComponentActivity() {

    private lateinit var tv: Tv
    private val shared = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        tv = Tv(applicationContext)
        shared.value = intent?.takeIf { it.action == Intent.ACTION_SEND }
        setContent {
            MangaTheme {
                App(tv, shared.value) { shared.value = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_SEND) shared.value = intent
    }

    override fun onDestroy() {
        if (isFinishing) tv.disconnect()
        super.onDestroy()
    }
}

@Composable
private fun App(tv: Tv, shared: Intent?, sharedHandled: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var messageId by remember { mutableIntStateOf(0) }
    var connected by remember { mutableStateOf(tv.connected) }
    var model by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(Tab.REMOTE) }
    var about by remember { mutableStateOf(false) }
    val prefs = remember { context.getSharedPreferences("tv", Context.MODE_PRIVATE) }
    var kind by remember { mutableStateOf(prefs.getString("kind", null)?.let { runCatching { TvKind.valueOf(it) }.getOrNull() }) }
    var manual by remember { mutableStateOf(false) }
    val apps = remember { Apps(context, tv) }
    val send = remember { Send(context, tv) }

    fun say(text: String) {
        message = text
        messageId++
    }
    fun run(block: suspend () -> Unit) = scope.launch {
        try {
            block()
        } catch (e: Exception) {
            android.util.Log.w("OpenRemoTV", "TV call failed", e)
            say(context.getString(R.string.tv_no_answer, e.message ?: e.javaClass.simpleName))
        }
    }
    LaunchedEffect(messageId) {
        if (message == null) return@LaunchedEffect
        delay(3500)
        message = null
    }

    // Something shared to the app: send it once connected.
    LaunchedEffect(shared, connected) {
        val intent = shared ?: return@LaunchedEffect
        if (!connected) return@LaunchedEffect
        tab = Tab.SEND
        try {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            val stream: Uri? = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            when {
                stream != null -> {
                    say(context.getString(R.string.send_sending))
                    say(context.getString(R.string.send_sent, send.file(stream)))
                }
                text != null && send.youtube(text) -> say(context.getString(R.string.send_playing))
                else -> say(context.getString(R.string.send_unknown))
            }
        } catch (e: Exception) {
            say(context.getString(R.string.send_failed, e.message ?: ""))
        }
        sharedHandled()
    }

    MangaPage {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                about -> AboutScreen { about = false }
                !connected && kind == null -> PickScreen { k -> kind = k; prefs.edit().putString("kind", k.name).apply() }
                !connected -> ConnectScreen(tv, scope, onBack = { kind = null }) { m -> model = m; connected = true }
                else -> when (tab) {
                    Tab.REMOTE -> RemoteScreen(tv, ::run)
                    Tab.APPS -> AppsScreen(apps, ::run)
                    Tab.SEND -> SendScreen(send, ::run, ::say)
                    Tab.SWEEP -> if (manual) {
                        Column {
                            TextButtonLine(stringResource(R.string.back_to_sweep), Modifier.padding(horizontal = 12.dp), bold = true) { manual = false }
                            SweepByHandScreen(apps, ::run, ::say)
                        }
                    } else {
                        Column {
                            Box(Modifier.weight(1f)) { SweepScreen(tv, apps, ::run, ::say) }
                            TextButtonLine(stringResource(R.string.by_hand_link), Modifier.align(Alignment.End).padding(horizontal = 12.dp),
                                bold = true) { manual = true }
                        }
                    }
                    Tab.CHECK -> CheckScreen(apps, ::run, ::say)
                }
            }
            message?.let {
                SpeechBubble(it, Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
        // A slim bar at the bottom, just above the tabs: who is connected, and a way to switch TVs.
        TvBar(connected, model, tv.host, onAbout = { about = true }) { tv.disconnect(); connected = false }
        if (connected && !about) MangaTabs(tab) { tab = it }
    }
}
