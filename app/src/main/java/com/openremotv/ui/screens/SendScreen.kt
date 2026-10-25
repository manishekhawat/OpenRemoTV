package com.openremotv.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openremotv.R
import com.openremotv.send.Send
import com.openremotv.ui.Ink
import com.openremotv.ui.MangaButton
import com.openremotv.ui.Panel

/** Send: play a YouTube link, show a photo, play a video, or drop any file on the TV. */
@Composable
internal fun SendScreen(send: Send, run: (suspend () -> Unit) -> Unit, say: (String) -> Unit) {
    val context = LocalContext.current
    var link by remember { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) run {
            say(context.getString(R.string.send_sending))
            say(context.getString(R.string.send_sent, send.file(uri)))
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Heading(stringResource(R.string.send_title), stringResource(R.string.send_tip))
        Panel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(link, { link = it }, label = { Text(stringResource(R.string.send_link)) }, modifier = Modifier.fillMaxWidth())
                MangaButton(stringResource(R.string.send_play), loud = true) {
                    run {
                        if (send.youtube(link)) {
                            say(context.getString(R.string.send_playing))
                            link = ""
                        } else {
                            say(context.getString(R.string.send_not_youtube))
                        }
                    }
                }
            }
        }
        MangaButton(stringResource(R.string.send_photo), Modifier.fillMaxWidth()) { pick.launch("image/*") }
        MangaButton(stringResource(R.string.send_video), Modifier.fillMaxWidth()) { pick.launch("video/*") }
        MangaButton(stringResource(R.string.send_file), Modifier.fillMaxWidth()) { pick.launch("*/*") }
        Text(stringResource(R.string.send_where), color = Ink.muted, fontSize = 13.sp)
    }
}
