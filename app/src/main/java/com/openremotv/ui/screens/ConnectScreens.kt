package com.openremotv.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openremotv.R
import com.openremotv.tv.Tv
import com.openremotv.ui.Ink
import com.openremotv.ui.MangaButton
import com.openremotv.ui.Mascot
import com.openremotv.ui.Panel
import com.openremotv.ui.Shout
import com.openremotv.ui.SpeechBubble
import com.openremotv.ui.speedLines
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The kinds of TV the app knows, with how to switch on network debugging on each. */
internal enum class TvKind(@StringRes val title: Int, @StringRes val blurb: Int, @StringRes val steps: List<Int>) {
    ANDROID_TV(
        R.string.kind_android_tv, R.string.kind_android_tv_blurb,
        listOf(R.string.step_atv_1, R.string.step_atv_2, R.string.step_atv_3, R.string.step_atv_4),
    ),
    GOOGLE_TV(
        R.string.kind_google_tv, R.string.kind_google_tv_blurb,
        listOf(R.string.step_gtv_1, R.string.step_gtv_2, R.string.step_gtv_3, R.string.step_gtv_4),
    ),
    FIRE_TV(
        R.string.kind_fire_tv, R.string.kind_fire_tv_blurb,
        listOf(R.string.step_fire_1, R.string.step_fire_2, R.string.step_fire_3),
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
            SpeechBubble(stringResource(R.string.pick_hello), Modifier.weight(1f))
        }
        Text(stringResource(R.string.pick_title), style = Shout, fontSize = 26.sp)
        TvKind.entries.forEach { kind ->
            val on = open == kind
            Panel(Modifier.fillMaxWidth(), fill = if (on) Ink.ink else Ink.paper, role = Role.RadioButton, onClick = { open = kind }) {
                Column(Modifier.padding(14.dp)) {
                    Text(stringResource(kind.title).uppercase(), style = Shout, fontSize = 20.sp,
                        color = if (on) Ink.paper else Ink.ink)
                    Text(stringResource(kind.blurb), fontSize = 14.sp, color = if (on) Ink.paper else Ink.ink)
                }
            }
        }
        open?.let { kind ->
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.pick_ready_title), style = Shout, fontSize = 16.sp)
                    kind.steps.forEachIndexed { i, s ->
                        Row {
                            Text("${i + 1}", style = Shout, color = Ink.red, fontSize = 18.sp, modifier = Modifier.width(26.dp))
                            Text(stringResource(s), fontSize = 15.sp)
                        }
                    }
                }
            }
            MangaButton(stringResource(R.string.pick_ready_button), Modifier.fillMaxWidth(), loud = true) { onPicked(kind) }
        }
    }
}

/** Connect: type or find the TV's address; the TV then asks to allow this phone once. */
@Composable
internal fun ConnectScreen(tv: Tv, scope: CoroutineScope, onBack: () -> Unit = {}, onConnected: (String) -> Unit) {
    val context = LocalContext.current
    var host by remember { mutableStateOf(tv.host) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val found = remember { mutableStateListOf<String>() }

    fun connect(address: String) {
        if (address.isBlank()) return
        busy = true
        status = context.getString(R.string.connect_connecting)
        scope.launch {
            try {
                onConnected(tv.connect(address))
            } catch (e: Exception) {
                status = context.getString(R.string.connect_failed, address)
            }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.fillMaxWidth().height(170.dp)) {
            Canvas(Modifier.matchParentSize()) { speedLines(count = 60) }
            Mascot(Modifier.size(140.dp).align(Alignment.Center), happy = !busy)
        }
        Heading(stringResource(R.string.connect_title), stringResource(R.string.connect_sub))
        Panel(Modifier.fillMaxWidth()) {
            OutlinedTextField(host, { host = it }, label = { Text(stringResource(R.string.connect_address)) },
                placeholder = { Text("192.168.1.50") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MangaButton(stringResource(R.string.connect_button), loud = true) { if (!busy) connect(host) }
            MangaButton(stringResource(R.string.connect_find)) {
                if (busy) return@MangaButton
                busy = true
                status = context.getString(R.string.connect_searching)
                scope.launch {
                    found.clear()
                    found.addAll(Tv.scan(context))
                    status = context.getString(if (found.isEmpty()) R.string.connect_none else R.string.connect_found)
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
        TextButtonLine(stringResource(R.string.connect_back), onClick = onBack)
    }
}
