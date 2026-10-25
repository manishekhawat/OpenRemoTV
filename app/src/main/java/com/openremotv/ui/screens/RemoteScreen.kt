package com.openremotv.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openremotv.R
import com.openremotv.tv.Tv
import com.openremotv.ui.Ink
import com.openremotv.ui.InkKey
import com.openremotv.ui.MangaButton
import com.openremotv.ui.Panel
import com.openremotv.ui.Shout
import com.openremotv.ui.SoundEffect
import com.openremotv.ui.speedLines

/** The remote: power, home, back, play; the D-pad; volume; and a box for typing on the TV. Fits one screen. */
@Composable
internal fun RemoteScreen(tv: Tv, run: (suspend () -> Unit) -> Unit) {
    var text by remember { mutableStateOf("") }
    var sfx by remember { mutableStateOf("") }
    var sfxKey by remember { mutableIntStateOf(0) }
    val context = androidx.compose.ui.platform.LocalContext.current
    // The phone's own speech recogniser (Google's): no microphone permission needed in this app.
    var forAssistant by remember { mutableStateOf(false) }
    val listen = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val words = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            ?: return@rememberLauncherForActivityResult
        if (forAssistant) run { tv.askAssistant(words) } else text = words
    }
    fun speak(assistant: Boolean) {
        forAssistant = assistant
        val i = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, context.getString(
                if (assistant) R.string.voice_prompt_assistant else R.string.voice_prompt_type))
        try {
            listen.launch(i)
        } catch (e: android.content.ActivityNotFoundException) {
            // No speech recogniser on this phone: the TV's assistant listens through its own remote instead.
            if (assistant) run { tv.assistant() }
        }
    }
    fun key(code: Int, word: String = "PON!") {
        sfx = word
        sfxKey++
        run { tv.key(code) }
    }

    // Everything sized from the room there is, so the whole remote fits on one screen without scrolling.
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
        val k = (maxHeight / 10).coerceIn(48.dp, 60.dp)
        val pad = (maxHeight - k * 2 - 64.dp - 36.dp).coerceAtMost(maxWidth * 0.86f).coerceIn(180.dp, 300.dp)
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                InkKey(Icons.Filled.PowerSettingsNew, stringResource(R.string.key_power), k, tint = Ink.red) { key(26, "BAN!") }
                InkKey(Icons.Filled.Home, stringResource(R.string.key_home), k) { key(3, "TADA!") }
                InkKey(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.key_back), k) { key(4, "SHU!") }
                InkKey(Icons.Filled.PlayArrow, stringResource(R.string.key_play), k) { key(85) }
            }
            // The D-pad inside a burst of speed lines.
            Box(Modifier.size(pad), contentAlignment = Alignment.Center) {
                Canvas(Modifier.matchParentSize()) { speedLines(count = 56) }
                val arrow = pad * 0.28f
                Box(Modifier.align(Alignment.TopCenter)) { InkKey(Icons.Filled.KeyboardArrowUp, stringResource(R.string.key_up), arrow, repeat = true) { key(19) } }
                Box(Modifier.align(Alignment.BottomCenter)) { InkKey(Icons.Filled.KeyboardArrowDown, stringResource(R.string.key_down), arrow, repeat = true) { key(20) } }
                Box(Modifier.align(Alignment.CenterStart)) { InkKey(Icons.Filled.KeyboardArrowLeft, stringResource(R.string.key_left), arrow, repeat = true) { key(21) } }
                Box(Modifier.align(Alignment.CenterEnd)) { InkKey(Icons.Filled.KeyboardArrowRight, stringResource(R.string.key_right), arrow, repeat = true) { key(22) } }
                Panel(Modifier.size(pad * 0.36f).align(Alignment.Center), CircleShape, Ink.ink, onClick = { key(23, "DON!") }) {
                    Text(stringResource(R.string.key_ok), style = Shout, fontSize = 26.sp, color = Ink.paper,
                        modifier = Modifier.align(Alignment.Center))
                }
                SoundEffect(sfx, sfxKey, Modifier.align(Alignment.TopEnd))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                InkKey(Icons.AutoMirrored.Filled.VolumeDown, stringResource(R.string.key_volume_down), k, repeat = true) { key(25) }
                InkKey(Icons.AutoMirrored.Filled.VolumeOff, stringResource(R.string.key_mute), k) { key(164, "SHIIN…") }
                InkKey(Icons.AutoMirrored.Filled.VolumeUp, stringResource(R.string.key_volume_up), k, repeat = true) { key(24) }
                // Ask the TV's Google Assistant or Gemini, speaking into the phone.
                InkKey(Icons.Filled.AutoAwesome, stringResource(R.string.key_assistant), k, tint = Ink.red) { speak(true) }
            }
            // Typing: one compact row (put the TV's cursor in a search box first).
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(text, { text = it }, singleLine = true,
                    placeholder = { Text(stringResource(R.string.remote_type_hint)) }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                // Speak instead of typing: the words land in the box, ready to send.
                InkKey(Icons.Filled.Mic, stringResource(R.string.key_voice_type), 48.dp) { speak(false) }
                Spacer(Modifier.width(8.dp))
                InkKey(Icons.AutoMirrored.Filled.Backspace, stringResource(R.string.key_delete), 48.dp, repeat = true) { key(67) }
                Spacer(Modifier.width(8.dp))
                MangaButton(stringResource(R.string.remote_go), loud = true) {
                    val t = text
                    text = ""
                    sfx = "KAKI!"
                    sfxKey++
                    run { tv.type(t); tv.submit() }
                }
            }
        }
    }
}
