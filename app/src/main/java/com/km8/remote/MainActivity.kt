package com.km8.remote

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * KM8 Remote: a phone app for the KM8 TV box over Wi-Fi. Five tabs: Remote (arrows, OK, back, home, volume,
 * power, typing), Apps (open anything on the TV), Send (YouTube links, photos, videos, files), Tidy (turn apps off
 * and on, undo everything) and Check (apps a TV update turned back on). Sharing a link or a file to this app from
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
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFFFD54F), secondary = Color(0xFF9FA8FF))) {
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

private enum class Tab(val label: String, val icon: ImageVector) {
    REMOTE("Remote", Icons.Filled.SettingsRemote),
    APPS("Apps", Icons.Filled.Apps),
    SEND("Send", Icons.Filled.Send),
    TIDY("Tidy", Icons.Filled.CleaningServices),
    CHECK("Check", Icons.Filled.VerifiedUser),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(tv: Tv, shared: Intent?, sharedHandled: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var connected by remember { mutableStateOf(tv.connected) }
    var model by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(Tab.REMOTE) }
    val apps = remember { Apps(context, tv) }
    val send = remember { Send(context, tv) }

    fun say(text: String) = scope.launch { snack.showSnackbar(text) }
    fun run(block: suspend () -> Unit) = scope.launch {
        try {
            block()
        } catch (e: Exception) {
            say("The TV didn't answer: ${e.message ?: e.javaClass.simpleName}")
        }
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
                stream != null -> say("Sent ${send.file(stream)} to the TV")
                text != null && send.youtube(text) -> say("Playing on the TV")
                else -> say("That isn't a YouTube link, a photo or a file")
            }
        } catch (e: Exception) {
            say("Couldn't send: ${e.message}")
        }
        sharedHandled()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("KM8 Remote", fontWeight = FontWeight.SemiBold)
                    if (connected) Text("$model · ${tv.host}", fontSize = 12.sp, color = Color.Gray)
                }
            }, actions = {
                if (connected) TextButton(onClick = { tv.disconnect(); connected = false }) { Text("Change TV") }
            })
        },
        bottomBar = {
            if (connected) NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(selected = tab == t, onClick = { tab = t },
                        icon = { Icon(t.icon, null) }, label = { Text(t.label) })
                }
            }
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (!connected) {
                ConnectScreen(tv, scope) { m -> model = m; connected = true }
            } else when (tab) {
                Tab.REMOTE -> RemoteScreen(tv, ::run)
                Tab.APPS -> AppsScreen(apps, ::run)
                Tab.SEND -> SendScreen(send, ::run, ::say)
                Tab.TIDY -> TidyScreen(apps, ::run, ::say)
                Tab.CHECK -> CheckScreen(apps, ::run, ::say)
            }
        }
    }
}

@Composable
private fun ConnectScreen(tv: Tv, scope: CoroutineScope, onConnected: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
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
                status = "Couldn't connect to $address. Is the TV on, on the same Wi-Fi, with network debugging on?"
            }
            busy = false
        }
    }

    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Connect to your TV", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text("Your phone and the TV must be on the same Wi-Fi.", color = Color.Gray)
        OutlinedTextField(host, { host = it }, label = { Text("TV address") }, singleLine = true,
            modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { connect(host) }, enabled = !busy) { Text("Connect") }
            OutlinedButton(onClick = {
                busy = true
                status = "Looking for TVs on your Wi-Fi…"
                scope.launch {
                    found.clear()
                    found.addAll(Tv.scan(context))
                    status = if (found.isEmpty()) "No TV found. Is it on, with network debugging on?" else "Found:"
                    busy = false
                }
            }, enabled = !busy) { Text("Find my TV") }
        }
        found.forEach { ip ->
            Card(onClick = { host = ip; connect(ip) }, modifier = Modifier.fillMaxWidth()) {
                Text(ip, Modifier.padding(16.dp), fontSize = 18.sp)
            }
        }
        if (status.isNotEmpty()) Text(status)
    }
}

@Composable
private fun RemoteScreen(tv: Tv, run: (suspend () -> Unit) -> Unit) {
    var text by remember { mutableStateOf("") }
    fun key(code: Int) = run { tv.key(code) }

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            RoundKey(Icons.Filled.PowerSettingsNew, "Power", Color(0xFFE57373)) { key(26) }
            RoundKey(Icons.Filled.Home, "Home") { key(3) }
            RoundKey(Icons.AutoMirrored.Filled.ArrowBack, "Back") { key(4) }
            RoundKey(Icons.Filled.PlayArrow, "Play/pause") { key(85) }
        }
        // The D-pad: four arrows round a big OK.
        Box(Modifier.size(260.dp)) {
            PadKey(Icons.Filled.KeyboardArrowUp, Modifier.align(Alignment.TopCenter)) { key(19) }
            PadKey(Icons.Filled.KeyboardArrowDown, Modifier.align(Alignment.BottomCenter)) { key(20) }
            PadKey(Icons.Filled.KeyboardArrowLeft, Modifier.align(Alignment.CenterStart)) { key(21) }
            PadKey(Icons.Filled.KeyboardArrowRight, Modifier.align(Alignment.CenterEnd)) { key(22) }
            Button(onClick = { key(23) }, shape = CircleShape, modifier = Modifier.size(104.dp).align(Alignment.Center)) {
                Text("OK", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            RoundKey(Icons.AutoMirrored.Filled.VolumeDown, "Volume down") { key(25) }
            RoundKey(Icons.AutoMirrored.Filled.VolumeOff, "Mute") { key(164) }
            RoundKey(Icons.AutoMirrored.Filled.VolumeUp, "Volume up") { key(24) }
        }
        Spacer(Modifier.height(4.dp))
        Text("Type on the TV (put the TV's cursor in a search box first)", color = Color.Gray, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(onClick = { key(67) }) { Icon(Icons.Filled.Backspace, "Delete") }
            Spacer(Modifier.width(4.dp))
            Button(onClick = { val t = text; text = ""; run { tv.type(t); tv.key(66) } }) { Text("Send") }
        }
    }
}

@Composable
private fun RoundKey(icon: ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(60.dp)) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun PadKey(icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = modifier.size(76.dp)) {
        Icon(icon, null, modifier = Modifier.size(44.dp))
    }
}

@Composable
private fun AppsScreen(apps: Apps, run: (suspend () -> Unit) -> Unit) {
    val list = remember { mutableStateListOf<TvApp>() }
    LaunchedEffect(Unit) { run { list.clear(); list.addAll(apps.list().filter { it.launch != null && it.enabled }) } }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Open on the TV", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 12.dp)) }
        if (list.isEmpty()) item { Text("Loading the TV's apps…", color = Color.Gray) }
        items(list, key = { it.pkg }) { app ->
            Card(onClick = { run { apps.open(app) } }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(app.name, fontSize = 18.sp)
                    Text(app.pkg, fontSize = 12.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun SendScreen(send: Send, run: (suspend () -> Unit) -> Unit, say: (String) -> Unit) {
    var link by remember { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) run { say("Sending…"); say("Sent ${send.file(uri)} to the TV") }
    }
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Send to the TV", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text("Tip: in YouTube, Photos or Files, tap Share and choose KM8 Remote.", color = Color.Gray)
        OutlinedTextField(link, { link = it }, label = { Text("YouTube link") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            run { if (send.youtube(link)) { say("Playing on the TV"); link = "" } else say("That isn't a YouTube link") }
        }) { Text("Play on TV") }
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(onClick = { pick.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text("Show a photo") }
        FilledTonalButton(onClick = { pick.launch("video/*") }, modifier = Modifier.fillMaxWidth()) { Text("Play a video") }
        FilledTonalButton(onClick = { pick.launch("*/*") }, modifier = Modifier.fillMaxWidth()) { Text("Send any file") }
        Text("Files go to the TV's Download/KM8Remote folder.", color = Color.Gray, fontSize = 13.sp)
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
        title = { Text("Undo everything?") },
        text = { Text("Turns back on every app this phone has turned off.") },
        confirmButton = { TextButton(onClick = { confirmUndo = false; run { say("Turned ${apps.undoAll()} apps back on"); reload() } }) { Text("Undo") } },
        dismissButton = { TextButton(onClick = { confirmUndo = false }) { Text("Cancel") } },
    )

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Turn apps off and on", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("Off means disabled, not uninstalled: switch it back on any time. Apps the TV needs have a lock.",
                    color = Color.Gray, fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Show built-in apps", Modifier.weight(1f))
                    Switch(showSystem, { showSystem = it })
                }
                OutlinedButton(onClick = { confirmUndo = true }) { Text("Undo everything") }
            }
        }
        if (list.isEmpty()) item { Text("Loading the TV's apps…", color = Color.Gray) }
        items(list.filter { showSystem || !it.system }, key = { it.pkg }) { app ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(app.name, fontSize = 16.sp)
                    Text(app.pkg, fontSize = 11.sp, color = Color.Gray)
                }
                if (app.protected) {
                    Icon(Icons.Filled.Lock, "Needed by the TV", tint = Color.Gray)
                } else {
                    Switch(app.enabled, { on ->
                        run {
                            apps.setEnabled(app.pkg, on)
                            val i = list.indexOfFirst { it.pkg == app.pkg }
                            if (i >= 0) list[i] = app.copy(enabled = on)
                            say(if (on) "${app.name} is on" else "${app.name} is off")
                        }
                    })
                }
            }
        }
    }
}

@Composable
private fun CheckScreen(apps: Apps, run: (suspend () -> Unit) -> Unit, say: (String) -> Unit) {
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
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Check my TV", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text("TV updates sometimes switch cleaned-up apps back on. This finds them.", color = Color.Gray)
        when {
            !checked -> Text("Checking…", color = Color.Gray)
            back.isEmpty() -> Text("All good: everything you turned off is still off.", fontSize = 18.sp)
            else -> {
                Text("${back.size} app(s) came back on:", fontSize = 18.sp)
                back.forEach { app ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(app.name, Modifier.weight(1f))
                        TextButton(onClick = { run { apps.setEnabled(app.pkg, false); back.remove(app); say("${app.name} is off again") } }) {
                            Text("Turn off")
                        }
                    }
                }
                Button(onClick = { run { back.toList().forEach { apps.setEnabled(it.pkg, false) }; back.clear(); say("All turned off again") } }) {
                    Text("Turn them all off")
                }
            }
        }
        OutlinedButton(onClick = { checked = false; check() }) { Text("Check again") }
    }
}
