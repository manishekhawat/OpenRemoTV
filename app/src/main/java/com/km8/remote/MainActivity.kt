package com.km8.remote

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * RemoteSweepTV: a phone app for Android TV, Google TV and Fire TV over Wi-Fi, TidyTV-style with a manga look. First
 * Pick my TV and Connect; then five tabs: Remote (arrows, OK, back, home, volume, power, typing), Apps (open anything
 * on the TV), Send (YouTube links, photos, videos, files), Sweep (backup, the tested list for this TV, clean home
 * screen, done, share it, undo anytime; or single apps by hand) and Check (apps a TV update turned back on). Sharing a link or a file to this app from
 * any other app sends it straight to the TV.
 */
class MainActivity : ComponentActivity() {

    private lateinit var tv: Tv
    private val shared = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
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

internal enum class Tab(val label: String, val icon: ImageVector) {
    REMOTE("Remote", Icons.Filled.SettingsRemote),
    APPS("Apps", Icons.Filled.Apps),
    SEND("Send", Icons.Filled.Send),
    SWEEP("Sweep", Icons.Filled.CleaningServices),
    CHECK("Check", Icons.Filled.VerifiedUser),
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
    val prefs = remember { context.getSharedPreferences("tv", android.content.Context.MODE_PRIVATE) }
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
            say("The TV didn't answer: ${e.message ?: e.javaClass.simpleName}")
        }
    }
    LaunchedEffect(messageId) {
        if (message == null) return@LaunchedEffect
        kotlinx.coroutines.delay(3500)
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
                stream != null -> { say("Sending…"); say("Sent ${send.file(stream)} to the TV!") }
                text != null && send.youtube(text) -> say("Playing on the TV!")
                else -> say("Hmm, that isn't a YouTube link, a photo or a file")
            }
        } catch (e: Exception) {
            say("Couldn't send: ${e.message}")
        }
        sharedHandled()
    }

    MangaPage {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (!connected) {
                if (kind == null) PickScreen { k -> kind = k; prefs.edit().putString("kind", k.name).apply() }
                else ConnectScreen(tv, scope, onBack = { kind = null }) { m -> model = m; connected = true }
            } else when (tab) {
                Tab.REMOTE -> RemoteScreen(tv, ::run)
                Tab.APPS -> AppsScreen(apps, ::run)
                Tab.SEND -> SendScreen(send, ::run, ::say)
                Tab.SWEEP -> if (manual) {
                    Column {
                        Text("← BACK TO SWEEP", style = Shout, modifier = Modifier.clickable { manual = false }.padding(16.dp))
                        TidyScreen(apps, ::run, ::say)
                    }
                } else {
                    Column {
                        Box(Modifier.weight(1f)) { SweepScreen(tv, apps, ::run, ::say) }
                        Text("Turn single apps on or off →", color = Ink.ink, fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.End).clickable { manual = true }.padding(12.dp))
                    }
                }
                Tab.CHECK -> CheckScreen(apps, ::run, ::say)
            }
            message?.let {
                SpeechBubble(it, Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
        // A slim bar at the bottom, just above the tabs: who is connected, and a way to switch TVs.
        TvBar(connected, model, tv.host) { tv.disconnect(); connected = false }
        if (connected) MangaTabs(tab) { tab = it }
    }
}

/** RemoteSweepTV's name and the TV in use, in one slim ink strip. */
@Composable
internal fun TvBar(connected: Boolean, model: String, host: String, onChange: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Ink.ink).padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp).background(Ink.paper, CircleShape), contentAlignment = Alignment.Center) {
            Mascot(Modifier.size(28.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text("REMOTESWEEPTV", style = Shout, fontSize = 15.sp, color = Ink.paper)
        if (connected) {
            Text("  ·  $model · $host", fontSize = 12.sp, color = Ink.tone, maxLines = 1, modifier = Modifier.weight(1f))
            Text("Change TV", color = Ink.paper, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                modifier = Modifier.clickable { onChange() }.padding(horizontal = 6.dp, vertical = 4.dp))
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

/** The tab bar as a strip of comic panels; the chosen one is inked solid. */
@Composable
internal fun MangaTabs(current: Tab, onPick: (Tab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Ink.paper).navigationBarsPadding()
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Tab.entries.forEach { t ->
            val on = t == current
            Column(
                Modifier.weight(1f).padding(horizontal = 3.dp)
                    .background(if (on) Ink.ink else Ink.paper, RoundedCornerShape(8.dp))
                    .border(2.dp, Ink.ink, RoundedCornerShape(8.dp))
                    .clickable { onPick(t) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(t.icon, null, tint = if (on) Ink.paper else Ink.ink)
                Text(t.label.uppercase(), style = Shout, fontSize = 10.sp, color = if (on) Ink.paper else Ink.ink)
            }
        }
    }
}

@Composable
private fun Heading(text: String, sub: String? = null) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(text.uppercase(), style = Shout, fontSize = 26.sp, color = Ink.ink)
        if (sub != null) Text(sub, color = Ink.muted, fontSize = 14.sp)
    }
}

@Composable
internal fun ConnectScreen(tv: Tv, scope: CoroutineScope, onBack: () -> Unit = {}, onConnected: (String) -> Unit) {
    val context = LocalContext.current
    var host by remember { mutableStateOf(tv.host) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val found = remember { mutableStateListOf<String>() }

    fun connect(address: String) {
        busy = true
        status = "Connecting… If the TV asks \"Allow USB debugging?\", tick \"Always allow\" and press OK."
        scope.launch {
            try {
                onConnected(tv.connect(address))
            } catch (e: Exception) {
                status = "Couldn't reach $address. Is the TV on, on the same Wi-Fi, with network debugging on?"
            }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.fillMaxWidth().height(170.dp)) {
            Canvas(Modifier.matchParentSize()) { speedLines(count = 60) }
            Mascot(Modifier.size(140.dp).align(Alignment.Center), happy = !busy)
        }
        Heading("Let's connect!", "Your phone and the TV must be on the same Wi-Fi.")
        Panel(Modifier.fillMaxWidth()) {
            OutlinedTextField(host, { host = it }, label = { Text("TV address") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MangaButton("CONNECT!", loud = true) { if (!busy) connect(host) }
            MangaButton("FIND MY TV") {
                if (busy) return@MangaButton
                busy = true
                status = "Searching your Wi-Fi…"
                scope.launch {
                    found.clear()
                    found.addAll(Tv.scan(context))
                    status = if (found.isEmpty()) "No TV found. Is it on, with network debugging on?" else "Found it!"
                    busy = false
                }
            }
        }
        found.forEach { ip ->
            Panel(Modifier.fillMaxWidth(), onClick = { host = ip; connect(ip) }) {
                Text("📺  $ip", Modifier.padding(14.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (status.isNotEmpty()) SpeechBubble(status)
        Text("← Pick a different kind of TV", color = Ink.muted, modifier = Modifier.clickable { onBack() }.padding(vertical = 8.dp))
    }
}

@Composable
internal fun RemoteScreen(tv: Tv, run: (suspend () -> Unit) -> Unit) {
    var text by remember { mutableStateOf("") }
    var sfx by remember { mutableStateOf("") }
    var sfxKey by remember { mutableIntStateOf(0) }
    fun key(code: Int, word: String = "PON!") {
        sfx = word
        sfxKey++
        run { tv.key(code) }
    }

    // Everything sized from the room there is, so the whole remote fits on one screen without scrolling.
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
        val k = (maxHeight / 10).coerceIn(44.dp, 60.dp)
        val pad = (maxHeight - k * 2 - 64.dp - 36.dp).coerceAtMost(maxWidth * 0.86f).coerceIn(180.dp, 300.dp)
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                InkKey(Icons.Filled.PowerSettingsNew, "Power", k, tint = Ink.red) { key(26, "BAN!") }
                InkKey(Icons.Filled.Home, "Home", k) { key(3, "TADA!") }
                InkKey(Icons.AutoMirrored.Filled.ArrowBack, "Back", k) { key(4, "SHU!") }
                InkKey(Icons.Filled.PlayArrow, "Play/pause", k) { key(85) }
            }
            // The D-pad inside a burst of speed lines.
            Box(Modifier.size(pad), contentAlignment = Alignment.Center) {
                Canvas(Modifier.matchParentSize()) { speedLines(count = 56) }
                val arrow = pad * 0.28f
                Box(Modifier.align(Alignment.TopCenter)) { InkKey(Icons.Filled.KeyboardArrowUp, "Up", arrow) { key(19) } }
                Box(Modifier.align(Alignment.BottomCenter)) { InkKey(Icons.Filled.KeyboardArrowDown, "Down", arrow) { key(20) } }
                Box(Modifier.align(Alignment.CenterStart)) { InkKey(Icons.Filled.KeyboardArrowLeft, "Left", arrow) { key(21) } }
                Box(Modifier.align(Alignment.CenterEnd)) { InkKey(Icons.Filled.KeyboardArrowRight, "Right", arrow) { key(22) } }
                Panel(Modifier.size(pad * 0.36f).align(Alignment.Center), CircleShape, Ink.ink, onClick = { key(23, "DON!") }) {
                    Text("OK", style = Shout, fontSize = 26.sp, color = Ink.paper, modifier = Modifier.align(Alignment.Center))
                }
                SoundEffect(sfx, sfxKey, Modifier.align(Alignment.TopEnd))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                InkKey(Icons.AutoMirrored.Filled.VolumeDown, "Volume down", k) { key(25) }
                InkKey(Icons.AutoMirrored.Filled.VolumeOff, "Mute", k) { key(164, "SHIIN…") }
                InkKey(Icons.AutoMirrored.Filled.VolumeUp, "Volume up", k) { key(24) }
            }
            // Typing: one compact row (put the TV's cursor in a search box first).
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(text, { text = it }, singleLine = true, placeholder = { Text("Type on the TV…") },
                    modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                InkKey(Icons.Filled.Backspace, "Delete", 46.dp) { key(67) }
                Spacer(Modifier.width(8.dp))
                MangaButton("GO", loud = true) { val t = text; text = ""; sfx = "KAKI!"; sfxKey++; run { tv.type(t); tv.key(66) } }
            }
        }
    }
}

/** The remote is taller than small phones: let it scroll. */
@Composable
private fun Modifier.verticalScrollable(): Modifier =
    this.verticalScroll(androidx.compose.foundation.rememberScrollState())

@Composable
internal fun AppsScreen(apps: Apps, run: (suspend () -> Unit) -> Unit) {
    val list = remember { mutableStateListOf<TvApp>() }
    LaunchedEffect(Unit) { run { list.clear(); list.addAll(apps.list().filter { it.launch != null && it.enabled }) } }
    // Two columns of compact panels: a dozen apps fit on one screen.
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
        Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) { Heading("Open on the TV") }
        if (list.isEmpty()) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) { SpeechBubble("Fetching the TV's apps…") }
        items(list.size) { i ->
            val app = list[i]
            Panel(Modifier.fillMaxWidth().height(64.dp), shadow = 4.dp, onClick = { run { apps.open(app) } }) {
                Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp).background(Ink.ink, CircleShape), contentAlignment = Alignment.Center) {
                        Text(app.name.take(1).uppercase(), style = Shout, color = Ink.paper, fontSize = 16.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(app.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                }
            }
        }
    }
}

@Composable
internal fun SendScreen(send: Send, run: (suspend () -> Unit) -> Unit, say: (String) -> Unit) {
    var link by remember { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) run { say("Sending… ZOOM!"); say("Sent ${send.file(uri)} to the TV!") }
    }
    Column(Modifier.fillMaxSize().verticalScrollable().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Heading("Send to the TV", "Tip: in YouTube, Photos or Files, tap Share and pick RemoteSweepTV.")
        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(link, { link = it }, label = { Text("YouTube link") }, modifier = Modifier.fillMaxWidth())
                MangaButton("PLAY ON TV!", loud = true) {
                    run { if (send.youtube(link)) { say("Playing on the TV!"); link = "" } else say("That isn't a YouTube link") }
                }
            }
        }
        MangaButton("📷  SHOW A PHOTO", Modifier.fillMaxWidth()) { pick.launch("image/*") }
        MangaButton("🎬  PLAY A VIDEO", Modifier.fillMaxWidth()) { pick.launch("video/*") }
        MangaButton("📦  SEND ANY FILE", Modifier.fillMaxWidth()) { pick.launch("*/*") }
        Text("Files land in the TV's Download/RemoteSweepTV folder.", color = Ink.muted, fontSize = 13.sp)
    }
}

@Composable
private fun TidyScreen(apps: Apps, run: (suspend () -> Unit) -> Unit, say: (String) -> Unit) {
    val list = remember { mutableStateListOf<TvApp>() }
    var showSystem by remember { mutableStateOf(true) }
    var confirmUndo by remember { mutableStateOf(false) }
    fun reload() = run { val l = apps.list(); list.clear(); list.addAll(l.sortedBy { it.name.lowercase() }) }
    LaunchedEffect(Unit) { reload() }

    if (confirmUndo) AlertDialog(
        onDismissRequest = { confirmUndo = false },
        containerColor = Ink.paper,
        title = { Text("UNDO EVERYTHING?", style = Shout) },
        text = { Text("Turns back on every app this phone has turned off.") },
        confirmButton = { MangaButton("UNDO", loud = true) { confirmUndo = false; run { say("Turned ${apps.undoAll()} apps back on"); reload() } } },
        dismissButton = { MangaButton("CANCEL") { confirmUndo = false } },
    )

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Heading("Tidy up", "Off means disabled, never uninstalled. Apps the TV needs are locked.")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Show built-in apps", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Switch(showSystem, { showSystem = it }, colors = inkSwitch())
                }
                MangaButton("UNDO EVERYTHING") { confirmUndo = true }
            }
        }
        if (list.isEmpty()) item { SpeechBubble("Fetching the TV's apps…") }
        items(list.filter { showSystem || !it.system }, key = { it.pkg }) { app ->
            Panel(Modifier.fillMaxWidth(), shadow = 3.dp) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(app.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(app.pkg, fontSize = 11.sp, color = Ink.muted)
                    }
                    if (app.protected) {
                        Icon(Icons.Filled.Lock, "Needed by the TV", tint = Ink.muted)
                    } else {
                        Switch(app.enabled, { on ->
                            // Optimistic: the switch moves at once and moves back only if the TV says no.
                            fun show(state: Boolean) {
                                val i = list.indexOfFirst { it.pkg == app.pkg }
                                if (i >= 0) list[i] = list[i].copy(enabled = state)
                            }
                            show(on)
                            run {
                                try {
                                    apps.setEnabled(app.pkg, on)
                                } catch (e: Exception) {
                                    show(!on)
                                    say("Couldn't switch ${app.name}: ${e.message}")
                                }
                            }
                        }, colors = inkSwitch())
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun inkSwitch() = SwitchDefaults.colors(
    checkedThumbColor = Ink.paper, checkedTrackColor = Ink.ink, checkedBorderColor = Ink.ink,
    uncheckedThumbColor = Ink.ink, uncheckedTrackColor = Ink.paper, uncheckedBorderColor = Ink.ink,
)

@Composable
internal fun CheckScreen(apps: Apps, run: (suspend () -> Unit) -> Unit, say: (String) -> Unit) {
    val back = remember { mutableStateListOf<TvApp>() }
    var checked by remember { mutableStateOf(false) }
    fun check() = run {
        val off = apps.meantToBeOff()
        val now = apps.list()
        back.clear()
        back.addAll(now.filter { it.pkg in off && it.enabled && !it.protected })
        checked = true
    }
    LaunchedEffect(Unit) { check() }
    Column(Modifier.fillMaxSize().verticalScrollable().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Heading("Check my TV", "TV updates sometimes switch cleaned-up apps back on. Remo will find them.")
        Box(Modifier.fillMaxWidth().height(150.dp)) {
            Canvas(Modifier.matchParentSize()) { speedLines(count = 40) }
            Mascot(Modifier.size(120.dp).align(Alignment.Center), happy = checked && back.isEmpty())
        }
        when {
            !checked -> SpeechBubble("Checking… hmm hmm…")
            back.isEmpty() -> SpeechBubble("All clear! Everything you turned off is still off.")
            else -> {
                SpeechBubble("Uh-oh! ${back.size} app(s) came back on:")
                back.forEach { app ->
                    Panel(Modifier.fillMaxWidth(), shadow = 3.dp) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(app.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            MangaButton("OFF") {
                                back.remove(app)
                                run {
                                    try {
                                        apps.setEnabled(app.pkg, false)
                                    } catch (e: Exception) {
                                        back.add(app)
                                        say("Couldn't turn ${app.name} off: ${e.message}")
                                    }
                                }
                            }
                        }
                    }
                }
                MangaButton("TURN THEM ALL OFF!", loud = true) {
                    run { back.toList().forEach { apps.setEnabled(it.pkg, false) }; back.clear(); say("All off again. Mission complete!") }
                }
            }
        }
        MangaButton("CHECK AGAIN") { checked = false; check() }
    }
}
