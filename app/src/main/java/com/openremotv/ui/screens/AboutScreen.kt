package com.openremotv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openremotv.BuildConfig
import com.openremotv.R
import com.openremotv.ui.Ink
import com.openremotv.ui.MangaButton
import com.openremotv.ui.Panel
import com.openremotv.ui.Shout

private const val REPO = "https://github.com/manishekhawat/OpenRemoTV"

/** About: the version, the licence, where the code lives, and the credits the licences ask for. */
@Composable
internal fun AboutScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val links = LocalUriHandler.current
    // The photo credits travel with the theme (assets/themes/india/CREDITS.md); shown here as plain lines.
    val photos = remember {
        runCatching { context.assets.open("themes/india/CREDITS.md").bufferedReader().readLines() }.getOrDefault(emptyList())
            .filter { it.startsWith("- ") }.map { it.removePrefix("- ") }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Heading(stringResource(R.string.about_title), stringResource(R.string.about_version, BuildConfig.VERSION_NAME))
        Text(stringResource(R.string.about_what), fontSize = 15.sp)
        MangaButton(stringResource(R.string.about_source), Modifier.fillMaxWidth()) { links.openUri(REPO) }
        MangaButton(stringResource(R.string.about_privacy), Modifier.fillMaxWidth()) { links.openUri("$REPO/blob/main/PRIVACY.md") }
        Section(stringResource(R.string.about_licence_title), listOf(stringResource(R.string.about_licence)))
        Section(stringResource(R.string.about_credits_title), listOf(
            stringResource(R.string.about_credit_home),
            stringResource(R.string.about_credit_lists),
            stringResource(R.string.about_credit_font),
            stringResource(R.string.about_credit_libraries),
            stringResource(R.string.about_credit_tidytv),
        ))
        Section(stringResource(R.string.about_photos_title), listOf(stringResource(R.string.about_photos_note)) + photos)
        MangaButton(stringResource(R.string.close), Modifier.fillMaxWidth(), loud = true, onClick = onClose)
    }
}

@Composable
private fun Section(title: String, lines: List<String>) {
    Panel(Modifier.fillMaxWidth(), shadow = 3.dp) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title.uppercase(), style = Shout, fontSize = 16.sp)
            lines.forEach { Text(it, fontSize = 13.sp, color = Ink.ink) }
        }
    }
}
