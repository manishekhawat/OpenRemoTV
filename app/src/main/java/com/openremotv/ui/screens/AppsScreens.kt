package com.openremotv.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openremotv.R
import com.openremotv.tv.Apps
import com.openremotv.tv.TvApp
import com.openremotv.ui.Ink
import com.openremotv.ui.MangaButton
import com.openremotv.ui.Mascot
import com.openremotv.ui.Panel
import com.openremotv.ui.Shout
import com.openremotv.ui.SpeechBubble
import com.openremotv.ui.speedLines

/** One app in the Apps grid: its initial in an ink circle, then its name. */
@Composable
internal fun AppTile(app: TvApp, onClick: () -> Unit) {
    Panel(Modifier.fillMaxWidth().height(64.dp), shadow = 4.dp, onClick = onClick) {
        // Fills the panel, so the circle and the name centre on the same line however long the name is.
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).background(Ink.ink, CircleShape).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
                Text(app.name.take(1).uppercase(), color = Ink.paper, fontSize = 16.sp, fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)))
            }
            Spacer(Modifier.width(8.dp))
            Text(app.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, lineHeight = 17.sp)
        }
    }
}

/** Apps: everything on the TV's home screen, two to a row; tap one to open it on the TV. */
@Composable
internal fun AppsScreen(apps: Apps, run: (suspend () -> Unit) -> Unit) {
    val list = remember { mutableStateListOf<TvApp>() }
    LaunchedEffect(Unit) { run { list.clear(); list.addAll(apps.list().filter { it.launch != null && it.enabled }) } }
    LazyVerticalGrid(
        GridCells.Fixed(2),
        Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
    ) {
        item(span = { GridItemSpan(2) }) { Heading(stringResource(R.string.apps_title)) }
        if (list.isEmpty()) item(span = { GridItemSpan(2) }) { SpeechBubble(stringResource(R.string.apps_fetching)) }
        items(list.size) { i ->
            val app = list[i]
            AppTile(app) { run { apps.open(app) } }
        }
    }
}

/** Sweep by hand: every app with a switch; the ones the TV needs are locked. */
@Composable
internal fun SweepByHandScreen(apps: Apps, run: (suspend () -> Unit) -> Unit, say: (String) -> Unit) {
    val context = LocalContext.current
    val list = remember { mutableStateListOf<TvApp>() }
    var showSystem by remember { mutableStateOf(true) }
    var confirmUndo by remember { mutableStateOf(false) }
    fun reload() = run { val l = apps.list(); list.clear(); list.addAll(l.sortedBy { it.name.lowercase() }) }
    LaunchedEffect(Unit) { reload() }

    if (confirmUndo) AlertDialog(
        onDismissRequest = { confirmUndo = false },
        containerColor = Ink.paper,
        title = { Text(stringResource(R.string.undo_all_title), style = Shout) },
        text = { Text(stringResource(R.string.undo_all_text)) },
        confirmButton = {
            MangaButton(stringResource(R.string.undo), loud = true) {
                confirmUndo = false
                run { val n = apps.undoAll(); say(context.resources.getQuantityString(R.plurals.undo_done, n, n)); reload() }
            }
        },
        dismissButton = { MangaButton(stringResource(R.string.cancel)) { confirmUndo = false } },
    )

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Heading(stringResource(R.string.by_hand_title), stringResource(R.string.by_hand_sub))
                SwitchRow(stringResource(R.string.by_hand_show_system), null, showSystem) { showSystem = it }
                MangaButton(stringResource(R.string.undo_all)) { confirmUndo = true }
            }
        }
        if (list.isEmpty()) item { SpeechBubble(stringResource(R.string.apps_fetching)) }
        items(list.filter { showSystem || !it.system }, key = { it.pkg }) { app ->
            if (app.protected) {
                Panel(Modifier.fillMaxWidth(), shadow = 3.dp) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(app.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(app.pkg, fontSize = 12.sp, color = Ink.muted)
                        }
                        Icon(Icons.Filled.Lock, stringResource(R.string.by_hand_locked), tint = Ink.muted)
                    }
                }
            } else {
                SwitchRow(app.name, app.pkg, app.enabled) { on ->
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
                            say(context.getString(R.string.switch_failed, app.name, e.message ?: ""))
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

/** Check: apps a TV update switched back on since the last backup or sweep. */
@Composable
internal fun CheckScreen(apps: Apps, run: (suspend () -> Unit) -> Unit, say: (String) -> Unit) {
    val context = LocalContext.current
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Heading(stringResource(R.string.check_title), stringResource(R.string.check_sub))
        Box(Modifier.fillMaxWidth().height(150.dp)) {
            Canvas(Modifier.matchParentSize()) { speedLines(count = 40) }
            Mascot(Modifier.size(120.dp).align(Alignment.Center), happy = checked && back.isEmpty())
        }
        when {
            !checked -> SpeechBubble(stringResource(R.string.check_checking))
            back.isEmpty() -> SpeechBubble(stringResource(R.string.check_clear))
            else -> {
                SpeechBubble(pluralStringResource(R.plurals.check_back, back.size, back.size))
                back.forEach { app ->
                    Panel(Modifier.fillMaxWidth(), shadow = 3.dp) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(app.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            MangaButton(stringResource(R.string.check_off)) {
                                back.remove(app)
                                run {
                                    try {
                                        apps.setEnabled(app.pkg, false)
                                    } catch (e: Exception) {
                                        back.add(app)
                                        say(context.getString(R.string.switch_failed, app.name, e.message ?: ""))
                                    }
                                }
                            }
                        }
                    }
                }
                MangaButton(stringResource(R.string.check_all_off), loud = true) {
                    run {
                        back.toList().forEach { apps.setEnabled(it.pkg, false) }
                        back.clear()
                        say(context.getString(R.string.check_all_off_done))
                    }
                }
            }
        }
        MangaButton(stringResource(R.string.check_again)) { checked = false; check() }
    }
}
