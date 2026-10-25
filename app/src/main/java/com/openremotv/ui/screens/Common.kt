package com.openremotv.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openremotv.R
import com.openremotv.ui.Ink
import com.openremotv.ui.Mascot
import com.openremotv.ui.Panel
import com.openremotv.ui.Shout

/** The five tabs once a TV is connected. */
internal enum class Tab(@StringRes val label: Int, val icon: ImageVector) {
    REMOTE(R.string.tab_remote, Icons.Filled.SettingsRemote),
    APPS(R.string.tab_apps, Icons.Filled.Apps),
    SEND(R.string.tab_send, Icons.AutoMirrored.Filled.Send),
    SWEEP(R.string.tab_sweep, Icons.Filled.CleaningServices),
    CHECK(R.string.tab_check, Icons.Filled.VerifiedUser),
}

/** A screen's title in comic lettering, with an optional line under it. */
@Composable
internal fun Heading(text: String, sub: String? = null) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(text.uppercase(), style = Shout, fontSize = 26.sp, color = Ink.ink)
        if (sub != null) Text(sub, color = Ink.muted, fontSize = 14.sp)
    }
}

/** A wizard page: its title, an optional line, then the page. */
@Composable
internal fun ColumnScope.Page(title: String, sub: String?, content: @Composable ColumnScope.() -> Unit) {
    Text(title, style = Shout, fontSize = 26.sp)
    if (sub != null) Text(sub, color = Ink.muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 10.dp))
    content()
}

/** Plain text that acts as a button: says so to a screen reader. */
@Composable
internal fun TextButtonLine(text: String, modifier: Modifier = Modifier, bold: Boolean = false, onClick: () -> Unit) {
    Text(text, color = Ink.ink, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        modifier = modifier.clickable(role = Role.Button, onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp))
}

/**
 * One app with a switch. The whole row is the switch for a screen reader ("JioHotstar, on, switch"), so the name
 * and the state are read together.
 */
@Composable
internal fun SwitchRow(title: String, sub: String?, on: Boolean, colors: SwitchColors = inkSwitch(), onChange: (Boolean) -> Unit) {
    Panel(Modifier.fillMaxWidth(), shadow = 3.dp) {
        Row(
            Modifier.toggleable(on, role = Role.Switch, onValueChange = onChange)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (sub != null) Text(sub, fontSize = 12.sp, color = Ink.muted)
            }
            Switch(on, onCheckedChange = null, colors = colors)
        }
    }
}

@Composable
internal fun inkSwitch() = SwitchDefaults.colors(
    checkedThumbColor = Ink.paper, checkedTrackColor = Ink.ink, checkedBorderColor = Ink.ink,
    uncheckedThumbColor = Ink.ink, uncheckedTrackColor = Ink.paper, uncheckedBorderColor = Ink.ink,
)

/** The red "goes away" switch of the sweep list. */
@Composable
internal fun sweepSwitch() = SwitchDefaults.colors(
    checkedThumbColor = Ink.paper, checkedTrackColor = Ink.red, checkedBorderColor = Ink.ink,
    uncheckedThumbColor = Ink.ink, uncheckedTrackColor = Ink.paper, uncheckedBorderColor = Ink.ink,
)

/** OpenRemoTV's name and the TV in use, in one slim ink strip; the name opens About. */
@Composable
internal fun TvBar(connected: Boolean, model: String, host: String, onAbout: () -> Unit = {}, onChange: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Ink.ink)
            .then(if (connected) Modifier else Modifier.navigationBarsPadding())
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.clickable(role = Role.Button, onClickLabel = stringResource(R.string.about_open), onClick = onAbout),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(30.dp).background(Ink.paper, CircleShape), contentAlignment = Alignment.Center) {
                Mascot(Modifier.size(28.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.app_name).uppercase(), style = Shout, fontSize = 15.sp, color = Ink.paper)
        }
        if (connected) {
            Text("  ·  $model · $host", fontSize = 12.sp, color = Ink.paper, maxLines = 1, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.change_tv), color = Ink.paper, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                modifier = Modifier.clickable(role = Role.Button, onClick = onChange).padding(horizontal = 6.dp, vertical = 8.dp))
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
                    .selectable(on, role = Role.Tab, onClick = { onPick(t) }).padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(t.icon, null, tint = if (on) Ink.paper else Ink.ink)
                Text(stringResource(t.label).uppercase(), style = Shout, fontSize = 12.sp, maxLines = 1,
                    color = if (on) Ink.paper else Ink.ink)
            }
        }
    }
}
