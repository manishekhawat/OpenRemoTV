package com.openremotv.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openremotv.R
import com.openremotv.sweep.HomeTheme
import com.openremotv.sweep.Lists
import com.openremotv.sweep.ShareCard
import com.openremotv.sweep.SweepItem
import com.openremotv.sweep.Sweeper
import com.openremotv.sweep.TvInfo
import com.openremotv.sweep.TvList
import com.openremotv.tv.Apps
import com.openremotv.tv.Tv
import com.openremotv.ui.Ink
import com.openremotv.ui.MangaButton
import com.openremotv.ui.Mascot
import com.openremotv.ui.Panel
import com.openremotv.ui.Shout
import com.openremotv.ui.SpeechBubble
import com.openremotv.ui.speedLines

private enum class Step { BACKUP, PICK_LIST, SWEEP, SWEEPING, HOME, DONE }

/**
 * Sweep: backup → pick the list → choose apps → sweep → clean home screen → done (share it), with "Undo anytime"
 * always on the first page, and the home screen theme changeable on its own.
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
    var homeName by remember { mutableStateOf(context.getString(R.string.home_unchanged)) }
    var search by remember { mutableStateOf("") }
    var confirmUndo by remember { mutableStateOf(false) }
    /** Came straight to the home screen choice from the start page, not through a sweep. */
    var homeOnly by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { run { info = Lists.info(tv) } }

    if (confirmUndo) AlertDialog(
        onDismissRequest = { confirmUndo = false },
        containerColor = Ink.paper,
        title = { Text(stringResource(R.string.restore_title), style = Shout) },
        text = { Text(stringResource(R.string.restore_text)) },
        confirmButton = {
            MangaButton(stringResource(R.string.restore), loud = true) {
                confirmUndo = false
                run {
                    busy = context.getString(R.string.restoring)
                    val n = sweeper.restoreAll(info ?: Lists.info(tv))
                    busy = ""
                    say(context.resources.getQuantityString(R.plurals.restored, n, n))
                }
            }
        },
        dismissButton = { MangaButton(stringResource(R.string.cancel)) { confirmUndo = false } },
    )

    fun choose(list: TvList) = run {
        busy = context.getString(R.string.sweep_reading)
        listName = list.tv
        items.clear()
        items.addAll(sweeper.candidates(list))
        busy = ""
        step = Step.SWEEP
    }

    /** Installs the home screen with one theme; one at a time, however many taps. */
    fun putOn(theme: HomeTheme) {
        if (busy.isNotEmpty()) return
        busy = context.getString(R.string.home_putting_on, theme.title)
        run {
            val ok = try { sweeper.installHome(theme) { busy = it } } finally { busy = "" }
            if (!ok) {
                say(context.getString(R.string.home_kept_old))
            } else {
                homeName = theme.title
                if (homeOnly) {
                    homeOnly = false
                    step = null
                    say(context.getString(R.string.home_is_on, theme.title))
                } else {
                    step = Step.DONE
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(12.dp))
        when (step) {
            null -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.sweep_title), style = Shout, fontSize = 30.sp)
                Box(Modifier.fillMaxWidth().height(150.dp)) {
                    Canvas(Modifier.matchParentSize()) { speedLines(count = 50) }
                    Mascot(Modifier.size(120.dp).align(Alignment.Center))
                }
                SpeechBubble(info?.let { stringResource(R.string.sweep_ready, it.title, it.android) }
                    ?: stringResource(R.string.sweep_looking))
                Steps(listOf(R.string.step_backup, R.string.step_sweep, R.string.step_home, R.string.step_done, R.string.step_share).map { stringResource(it) })
                MangaButton(stringResource(R.string.sweep_start), Modifier.fillMaxWidth(), loud = true) { step = Step.BACKUP }
                MangaButton(stringResource(R.string.sweep_change_theme), Modifier.fillMaxWidth()) { homeOnly = true; step = Step.HOME }
                MangaButton(stringResource(R.string.undo_anytime), Modifier.fillMaxWidth()) { confirmUndo = true }
                Text(stringResource(R.string.sweep_promise), color = Ink.muted, fontSize = 13.sp)
            }

            Step.BACKUP -> Page(stringResource(R.string.backup_title), stringResource(R.string.backup_sub)) {
                LaunchedEffect(Unit) {
                    run {
                        val i = info ?: Lists.info(tv).also { info = it }
                        busy = context.getString(R.string.backing_up)
                        val n = sweeper.backup(i)
                        busy = ""
                        say(context.resources.getQuantityString(R.plurals.backed_up, n, n))
                        matches = Lists.matches(context, i)
                        step = Step.PICK_LIST
                    }
                }
                SpeechBubble(busy.ifEmpty { stringResource(R.string.saving) })
            }

            Step.PICK_LIST -> Page(stringResource(R.string.list_title), stringResource(R.string.list_sub)) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    items(matches) { l ->
                        Panel(Modifier.fillMaxWidth(), onClick = { choose(l) }) {
                            Column(Modifier.padding(12.dp)) {
                                Text(l.tv, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(pluralStringResource(R.plurals.list_apps, l.packages.size, l.os, l.packages.size),
                                    color = Ink.muted, fontSize = 12.sp)
                            }
                        }
                    }
                    item {
                        Panel(Modifier.fillMaxWidth(), fill = Ink.ink, onClick = { choose(Lists.common(context)) }) {
                            Column(Modifier.padding(12.dp)) {
                                Text(stringResource(R.string.list_common), style = Shout, color = Ink.paper)
                                Text(stringResource(R.string.list_common_sub), color = Ink.paper, fontSize = 12.sp)
                            }
                        }
                    }
                    item {
                        OutlinedTextField(search, { search = it },
                            label = { Text(stringResource(R.string.list_search, Lists.all(context).size)) },
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

            Step.SWEEP -> Page(stringResource(R.string.choose_title), stringResource(R.string.choose_sub)) {
                Text(listName, color = Ink.muted, fontSize = 12.sp)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (items.isEmpty()) item { SpeechBubble(stringResource(R.string.choose_nothing)) }
                    items(items, key = { it.pkg }) { item ->
                        SwitchRow(item.name, item.pkg, item.sweep, sweepSwitch()) { on ->
                            val i = items.indexOfFirst { it.pkg == item.pkg }
                            if (i >= 0) items[i] = item.copy(sweep = on)
                        }
                    }
                }
                val chosen = items.filter { it.sweep }
                MangaButton(pluralStringResource(R.plurals.choose_go, chosen.size, chosen.size),
                    Modifier.fillMaxWidth().padding(vertical = 10.dp), loud = true) {
                    step = Step.SWEEPING
                    run {
                        swept = sweeper.sweep(chosen.map { it.pkg }) { i, pkg ->
                            progress = context.getString(R.string.sweeping_progress, i + 1, chosen.size, Apps.label(pkg))
                        }
                        step = Step.HOME
                    }
                }
            }

            Step.SWEEPING -> Page(stringResource(R.string.sweeping_title), stringResource(R.string.sweeping_sub)) {
                Box(Modifier.fillMaxWidth().height(200.dp)) {
                    Canvas(Modifier.matchParentSize()) { speedLines(count = 80) }
                    Mascot(Modifier.size(140.dp).align(Alignment.Center))
                }
                SpeechBubble(progress.ifEmpty { stringResource(R.string.sweeping_start) })
            }

            Step.HOME -> Page(
                stringResource(if (homeOnly) R.string.home_theme_title else R.string.home_title),
                stringResource(if (homeOnly) R.string.home_theme_sub else R.string.home_sub),
            ) {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeChoice(HomeTheme.MANGA.title.uppercase(), stringResource(R.string.home_manga_blurb)) { putOn(HomeTheme.MANGA) }
                    HomeChoice(HomeTheme.INDIA.title.uppercase(), stringResource(R.string.home_india_blurb)) { putOn(HomeTheme.INDIA) }
                    HomeChoice(stringResource(if (homeOnly) R.string.cancel else R.string.home_keep), stringResource(R.string.home_keep_sub)) {
                        if (busy.isNotEmpty()) return@HomeChoice
                        if (homeOnly) { homeOnly = false; step = null } else step = Step.DONE
                    }
                    if (busy.isNotEmpty()) SpeechBubble(busy)
                }
            }

            Step.DONE -> Page(stringResource(R.string.done_title), null) {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.fillMaxWidth().height(170.dp)) {
                        Canvas(Modifier.matchParentSize()) { speedLines(count = 70) }
                        Mascot(Modifier.size(140.dp).align(Alignment.Center))
                    }
                    SpeechBubble(pluralStringResource(R.plurals.done_text, swept, swept, homeName))
                    MangaButton(stringResource(R.string.share_it), Modifier.fillMaxWidth(), loud = true) {
                        ShareCard.share(context, info?.title ?: context.getString(R.string.my_tv), swept, homeName)
                    }
                    MangaButton(stringResource(R.string.undo_anytime), Modifier.fillMaxWidth()) { confirmUndo = true }
                    MangaButton(stringResource(R.string.back_to_start), Modifier.fillMaxWidth()) { step = null }
                }
            }
        }
    }
}

@Composable
private fun HomeChoice(title: String, blurb: String, onClick: () -> Unit) {
    Panel(Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = Shout, fontSize = 18.sp)
            Text(blurb, fontSize = 14.sp, color = Ink.muted)
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
                    .padding(vertical = 6.dp).semantics(mergeDescendants = true) { contentDescription = "${i + 1}. $n" },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("${i + 1}", style = Shout, color = Ink.red, fontSize = 18.sp)
                Text(n.uppercase(), style = Shout, fontSize = 12.sp, maxLines = 1, textAlign = TextAlign.Center)
            }
        }
    }
}
