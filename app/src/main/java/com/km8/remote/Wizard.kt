package com.km8.remote

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The kinds of TV the app knows, with how to switch on network debugging on each. */
internal enum class TvKind(val title: String, val blurb: String, val steps: List<String>) {
    ANDROID_TV(
        "Android TV", "Most smart TVs and boxes (Sony, TCL, Mi Box, MECOOL…)",
        listOf(
            "Settings › Device Preferences › About: press OK on \"Build\" 7 times, until it says you're a developer.",
            "Settings › Device Preferences › Developer options: turn on \"USB debugging\".",
            "If there is \"Network debugging\" or \"ADB over network\", turn it on too.",
            "Find the TV's address in Settings › Network (it looks like 192.168.1.20).",
        ),
    ),
    GOOGLE_TV(
        "Google TV", "Chromecast with Google TV and newer TVs with the Google TV home screen",
        listOf(
            "Settings › System › About: press OK on \"Android TV OS build\" 7 times.",
            "Settings › System › Developer options: turn on \"USB debugging\".",
            "Turn on \"Wireless debugging\" if you see it.",
            "Find the TV's address in Settings › Network & Internet.",
        ),
    ),
    FIRE_TV(
        "Fire TV", "Amazon Fire TV sticks and Fire TV edition sets (sweep only)",
        listOf(
            "Settings › My Fire TV › About: press OK on the device name 7 times.",
            "Settings › My Fire TV › Developer options: turn on \"ADB debugging\".",
            "Find the address in Settings › My Fire TV › About › Network.",
        ),
    ),
}

/** Pick my TV: what kind of TV it is, and how to get it ready to connect. */
@Composable
internal fun PickScreen(onPicked: (TvKind) -> Unit) {
    var open by remember { mutableStateOf<TvKind?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Mascot(Modifier.size(96.dp))
            SpeechBubble("Hi, I'm Remo! Which TV are we sweeping today?", Modifier.weight(1f))
        }
        Text("PICK YOUR TV", style = Shout, fontSize = 26.sp)
        TvKind.entries.forEach { kind ->
            Panel(Modifier.fillMaxWidth(), fill = if (open == kind) Ink.ink else Ink.paper, onClick = { open = kind }) {
                Column(Modifier.padding(14.dp)) {
                    Text(kind.title.uppercase(), style = Shout, fontSize = 20.sp,
                        color = if (open == kind) Ink.paper else Ink.ink)
                    Text(kind.blurb, fontSize = 13.sp, color = if (open == kind) Ink.paper else Ink.ink)
                }
            }
        }
        open?.let { kind ->
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("GET IT READY", style = Shout, fontSize = 16.sp)
                    kind.steps.forEachIndexed { i, s ->
                        Row {
                            Text("${i + 1}", style = Shout, color = Ink.red, fontSize = 18.sp, modifier = Modifier.width(26.dp))
                            Text(s, fontSize = 15.sp)
                        }
                    }
                }
            }
            MangaButton("IT'S READY! CONNECT →", Modifier.fillMaxWidth(), loud = true) { onPicked(kind) }
        }
    }
}

private enum class Step { BACKUP, PICK_LIST, SWEEP, SWEEPING, HOME, DONE }

/**
 * Sweep: backup → pick the list → choose apps → sweep → clean home screen → done (share it), with "Undo anytime"
 * always on the first page.
 */
@Composable
internal fun SweepScreen(tv: Tv, apps: Apps, run: (suspend () -> Unit) -> Unit, say: (String) -> Unit) {
    val context = LocalContext.current
    val sweeper = remember { Sweeper(context, tv, apps) }
    var step by remember { mutableStateOf<Step?>(null) }
    var info by remember { mutableStateOf<TvInfo?>(null) }
    var busy by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<TvList>>(emptyList()) }
    val items = remember { mutableStateListOf<SweepItem>() }
    var listName by remember { mutableStateOf("") }
    var swept by remember { mutableIntStateOf(0) }
    var progress by remember { mutableStateOf("") }
    var homeName by remember { mutableStateOf("Unchanged") }
    var search by remember { mutableStateOf("") }
    var confirmUndo by remember { mutableStateOf(false) }
    /** Came straight to the home screen choice from the start page, not through a sweep. */
    var homeOnly by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { run { info = Lists.info(tv) } }

    if (confirmUndo) AlertDialog(
        onDismissRequest = { confirmUndo = false },
        containerColor = Ink.paper,
        title = { Text("RESTORE EVERYTHING?", style = Shout) },
        text = { Text("Every app comes back on and the original home screen returns, as in your first backup.") },
        confirmButton = {
            MangaButton("RESTORE", loud = true) {
                confirmUndo = false
                run { busy = "Restoring…"; val n = sweeper.restoreAll(info ?: Lists.info(tv)); busy = ""; say("Restored! $n app(s) back on.") }
            }
        },
        dismissButton = { MangaButton("CANCEL") { confirmUndo = false } },
    )

    fun choose(list: TvList) = run {
        busy = "Reading what's on the TV…"
        listName = list.tv
        items.clear()
        items.addAll(sweeper.candidates(list))
        busy = ""
        step = Step.SWEEP
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(12.dp))
        when (step) {
            null -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("SWEEP", style = Shout, fontSize = 30.sp)
                Box(Modifier.fillMaxWidth().height(150.dp)) {
                    Canvas(Modifier.matchParentSize()) { speedLines(count = 50) }
                    Mascot(Modifier.size(120.dp).align(Alignment.Center))
                }
                SpeechBubble(info?.let { "${it.title} (Android ${it.android}) is connected. Ready to sweep away the clutter?" }
                    ?: "Looking at your TV…")
                Steps(listOf("Backup", "Sweep", "Home", "Done", "Share"))
                MangaButton("START THE SWEEP!", Modifier.fillMaxWidth(), loud = true) { step = Step.BACKUP }
                MangaButton("🎨  CHANGE HOME SCREEN THEME", Modifier.fillMaxWidth()) { homeOnly = true; step = Step.HOME }
                MangaButton("↩  UNDO ANYTIME", Modifier.fillMaxWidth()) { confirmUndo = true }
                Text("Apps are turned off, never deleted. Your first backup is kept forever.", color = Ink.muted, fontSize = 13.sp)
            }

            Step.BACKUP -> Page("1 · BACKUP", "First, Remo saves a backup of your apps, settings and home screen.") {
                LaunchedEffect(Unit) {
                    run {
                        val i = info ?: Lists.info(tv).also { info = it }
                        busy = "Backing up…"
                        val n = sweeper.backup(i)
                        busy = ""
                        say("Backed up $n apps, on your phone and on the TV.")
                        matches = Lists.matches(context, i)
                        step = Step.PICK_LIST
                    }
                }
                SpeechBubble(busy.ifEmpty { "Saving…" })
            }

            Step.PICK_LIST -> Page("2 · WHICH LIST?", "These tested TVs look like yours. Pick the closest, or the common list.") {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    items(matches) { l ->
                        Panel(Modifier.fillMaxWidth(), onClick = { choose(l) }) {
                            Column(Modifier.padding(12.dp)) {
                                Text(l.tv, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("${l.os} · ${l.packages.size} apps in its list", color = Ink.muted, fontSize = 12.sp)
                            }
                        }
                    }
                    item {
                        Panel(Modifier.fillMaxWidth(), fill = Ink.ink, onClick = { choose(Lists.common(context)) }) {
                            Column(Modifier.padding(12.dp)) {
                                Text("COMMON LIST", style = Shout, color = Ink.paper)
                                Text("Apps most tested TVs turn off. Good for a TV nobody has tested.", color = Ink.paper, fontSize = 12.sp)
                            }
                        }
                    }
                    item {
                        OutlinedTextField(search, { search = it }, label = { Text("Search all ${Lists.all(context).size} tested TVs") },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    if (search.length >= 2) {
                        items(Lists.all(context).filter { it.tv.contains(search, true) || it.id.contains(search, true) }.take(20)) { l ->
                            Panel(Modifier.fillMaxWidth(), shadow = 3.dp, onClick = { choose(l) }) {
                                Text(l.tv, Modifier.padding(10.dp), fontSize = 14.sp)
                            }
                        }
                    }
                }
                if (busy.isNotEmpty()) SpeechBubble(busy)
            }

            Step.SWEEP -> Page("3 · SWEEP", "Switched on = goes away. Apps families often keep start switched off; flip any you like.") {
                Text(listName, color = Ink.muted, fontSize = 12.sp)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (items.isEmpty()) item { SpeechBubble("Nothing from this list is on your TV. It's already swept clean!") }
                    items(items, key = { it.pkg }) { item ->
                        Panel(Modifier.fillMaxWidth(), shadow = 3.dp) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, fontWeight = FontWeight.Bold)
                                    Text(item.pkg, fontSize = 11.sp, color = Ink.muted)
                                }
                                Switch(item.sweep, { on ->
                                    val i = items.indexOfFirst { it.pkg == item.pkg }
                                    if (i >= 0) items[i] = item.copy(sweep = on)
                                }, colors = SwitchDefaults.colors(checkedTrackColor = Ink.red, checkedBorderColor = Ink.ink,
                                    uncheckedTrackColor = Ink.paper, uncheckedThumbColor = Ink.ink, uncheckedBorderColor = Ink.ink))
                            }
                        }
                    }
                }
                val chosen = items.filter { it.sweep }
                MangaButton("SWEEP ${chosen.size} APPS! SHAAA!", Modifier.fillMaxWidth().padding(vertical = 10.dp), loud = true) {
                    step = Step.SWEEPING
                    run {
                        swept = sweeper.sweep(chosen.map { it.pkg }) { i, pkg -> progress = "${i + 1} / ${chosen.size}: ${Apps.label(pkg)}" }
                        step = Step.HOME
                    }
                }
            }

            Step.SWEEPING -> Page("SWEEPING…", "One app at a time, each one checked and logged first.") {
                Box(Modifier.fillMaxWidth().height(200.dp)) {
                    Canvas(Modifier.matchParentSize()) { speedLines(count = 80) }
                    Mascot(Modifier.size(140.dp).align(Alignment.Center))
                }
                SpeechBubble(progress.ifEmpty { "Here we go!" })
            }

            Step.HOME -> Page(if (homeOnly) "HOME SCREEN THEME" else "4 · CLEAN HOME SCREEN",
                if (homeOnly) "Pick a look for the TV's home screen. It only stays if it really takes over Home."
                else "Optional. A new home screen only stays if it really takes over Home.") {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeChoice("MANGAHOME", "Ink-black manga pages, comic-panel cards and Remo, on the clean tile home screen.") {
                        run {
                            val ok = sweeper.installHome("manga") { busy = it }
                            busy = ""
                            if (ok) { homeName = "MangaHome"; if (homeOnly) { homeOnly = false; step = null; say("MangaHome is on your TV!") } else step = Step.DONE } else say("Your TV kept its old home screen; nothing changed.")
                        }
                    }
                    HomeChoice("INDIAMINIMALIST", "Monuments of India and the world, the Panchang, festivals and glass cards.") {
                        run {
                            val ok = sweeper.installHome("india") { busy = it }
                            busy = ""
                            if (ok) { homeName = "IndiaMinimalist"; if (homeOnly) { homeOnly = false; step = null; say("IndiaMinimalist is on your TV!") } else step = Step.DONE } else say("Your TV kept its old home screen; nothing changed.")
                        }
                    }
                    HomeChoice(if (homeOnly) "CANCEL" else "KEEP MY HOME SCREEN", "Leave it as it is.") {
                        if (homeOnly) { homeOnly = false; step = null } else step = Step.DONE
                    }
                    if (busy.isNotEmpty()) SpeechBubble(busy)
                }
            }

            Step.DONE -> Page("5 · DONE!", null) {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.fillMaxWidth().height(170.dp)) {
                        Canvas(Modifier.matchParentSize()) { speedLines(count = 70) }
                        Mascot(Modifier.size(140.dp).align(Alignment.Center))
                    }
                    SpeechBubble("Mission complete! $swept app(s) swept away. Home screen: $homeName.")
                    MangaButton("SHARE IT!", Modifier.fillMaxWidth(), loud = true) {
                        ShareCard.share(context, info?.title ?: "My TV", swept, homeName)
                    }
                    MangaButton("↩  UNDO ANYTIME", Modifier.fillMaxWidth()) { confirmUndo = true }
                    MangaButton("BACK TO START", Modifier.fillMaxWidth()) { step = null }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.Page(title: String, sub: String?, content: @Composable ColumnScope.() -> Unit) {
    Text(title, style = Shout, fontSize = 26.sp)
    if (sub != null) Text(sub, color = Ink.muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 10.dp))
    content()
}

@Composable
private fun HomeChoice(title: String, blurb: String, onClick: () -> Unit) {
    Panel(Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = Shout, fontSize = 18.sp)
            Text(blurb, fontSize = 13.sp, color = Ink.muted)
        }
    }
}

/** The steps as a strip of numbered comic frames. */
@Composable
private fun Steps(names: List<String>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        names.forEachIndexed { i, n ->
            Column(
                Modifier.weight(1f).border(2.dp, Ink.ink, RoundedCornerShape(6.dp)).background(Ink.paper, RoundedCornerShape(6.dp))
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("${i + 1}", style = Shout, color = Ink.red, fontSize = 18.sp)
                Text(n.uppercase(), style = Shout, fontSize = 8.sp, maxLines = 2,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 9.sp)
            }
        }
    }
}
