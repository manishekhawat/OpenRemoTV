package com.openremotv

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * The manga look: black ink on paper, screentone dots, speed lines, thick panel borders with a hard offset shadow,
 * speech-bubble messages, sound-effect lettering on presses, and an original chibi mascot ("Remo") drawn in code.
 * No characters from published manga: everything here is drawn for this app.
 */
object Ink {
    val paper = Color(0xFFFBF8F1)
    val ink = Color(0xFF141414)
    val tone = Color(0xFFD9D4C7)
    val red = Color(0xFFE0262B)
    /** Secondary text: black too (it was grey), for reading at arm's length. */
    val muted = Color(0xFF141414)
}

val MangaColors = lightColorScheme(
    primary = Ink.ink, onPrimary = Ink.paper, secondary = Ink.red, onSecondary = Color.White,
    background = Ink.paper, onBackground = Ink.ink, surface = Ink.paper, onSurface = Ink.ink,
    surfaceVariant = Color(0xFFEFEADF), onSurfaceVariant = Ink.ink, outline = Ink.ink,
)

/** Bold comic lettering for headings and sound effects. */
val Shout = TextStyle(fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, letterSpacing = 0.5.sp)

/** A panel: paper inside a thick ink border, with a solid ink shadow down and to the right. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(6.dp),
    fill: Color = Ink.paper,
    shadow: Dp = 5.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val drop = if (pressed) 1.dp else shadow
    // propagateMinConstraints: a panel given a size passes it on, so its content can centre in it.
    Box(modifier, propagateMinConstraints = true) {
        Box(Modifier.matchParentSize().offset(drop, drop).background(Ink.ink, shape))
        Box(
            Modifier.matchParentSize().offset(if (pressed) shadow - 1.dp else 0.dp, if (pressed) shadow - 1.dp else 0.dp)
                .clip(shape).background(fill).border(3.dp, Ink.ink, shape)
        )
        Box(
            Modifier
                .offset(if (pressed) shadow - 1.dp else 0.dp, if (pressed) shadow - 1.dp else 0.dp)
                .clip(shape)
                .then(if (onClick != null) Modifier.clickable(source, null, onClick = onClick) else Modifier)
                .padding(3.dp),
            content = content,
        )
    }
}

/** A comic button: lettering in a panel. {@code loud} makes it ink-black with paper lettering. */
@Composable
fun MangaButton(text: String, modifier: Modifier = Modifier, loud: Boolean = false, onClick: () -> Unit) {
    Panel(modifier, RoundedCornerShape(10.dp), if (loud) Ink.ink else Ink.paper, onClick = onClick) {
        Text(
            text, style = Shout, fontSize = 17.sp, color = if (loud) Ink.paper else Ink.ink,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 18.dp, vertical = 12.dp),
        )
    }
}

/** A round key with an inked icon. */
@Composable
fun InkKey(icon: ImageVector, label: String, size: Dp = 60.dp, tint: Color = Ink.ink, onClick: () -> Unit) {
    Panel(Modifier.size(size), CircleShape, shadow = 4.dp, onClick = onClick) {
        Icon(icon, label, tint = tint, modifier = Modifier.align(Alignment.Center).size(size * 0.48f))
    }
}

/** Screentone: a field of halftone dots, denser towards the bottom, behind a screen. */
@Composable
fun Screentone(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val step = 14.dp.toPx()
        var y = 0f
        var row = 0
        while (y < size.height) {
            val r = (0.6f + 2.2f * (y / size.height)) * density
            var x = if (row % 2 == 0) 0f else step / 2
            while (x < size.width) {
                drawCircle(Ink.tone, r, Offset(x, y))
                x += step
            }
            y += step * 0.87f
            row++
        }
    }
}

/** Speed lines bursting from the centre, the manga sign of something happening. */
fun DrawScope.speedLines(color: Color = Ink.ink.copy(alpha = 0.12f), count: Int = 48) {
    val c = center
    val outer = size.maxDimension
    for (i in 0 until count) {
        val a = (i * 360.0 / count + (i * 37 % 11)) * Math.PI / 180
        val inner = size.minDimension * (0.36f + (i * 13 % 7) / 40f)
        drawLine(
            color, Offset(c.x + cos(a).toFloat() * inner, c.y + sin(a).toFloat() * inner),
            Offset(c.x + cos(a).toFloat() * outer, c.y + sin(a).toFloat() * outer),
            strokeWidth = (1 + i % 3) * density, cap = StrokeCap.Round,
        )
    }
}

/** A speech bubble with a tail, for the app's messages. */
@Composable
fun SpeechBubble(text: String, modifier: Modifier = Modifier) {
    Box(modifier.padding(bottom = 14.dp)) {
        Canvas(Modifier.matchParentSize()) {
            val r = 22.dp.toPx()
            val line = 3.dp.toPx()
            val x1 = size.width * 0.16f
            val x2 = size.width * 0.26f
            val tip = Offset(size.width * 0.10f, size.height + 14.dp.toPx())
            // The bubble, then the tail drawn over its bottom edge so the two read as one outline.
            drawRoundRect(Ink.paper, cornerRadius = CornerRadius(r))
            drawRoundRect(Ink.ink, cornerRadius = CornerRadius(r), style = Stroke(line))
            val tail = Path().apply {
                moveTo(x1, size.height - line)
                lineTo(tip.x, tip.y)
                lineTo(x2, size.height - line)
                close()
            }
            drawPath(tail, Ink.paper)
            drawLine(Ink.ink, Offset(x1, size.height - line / 2), tip, line, StrokeCap.Round)
            drawLine(Ink.ink, Offset(x2, size.height - line / 2), tip, line, StrokeCap.Round)
        }
        Text(text, color = Ink.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp))
    }
}

/** A sound effect that pops up, tilts and fades: "DON!" on OK, "PON!" on other keys. */
@Composable
fun SoundEffect(word: String, key: Int, modifier: Modifier = Modifier) {
    val pop = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        if (key == 0) return@LaunchedEffect
        pop.snapTo(1f)
        pop.animateTo(0f, tween(650))
    }
    if (pop.value > 0f) {
        Text(
            word, style = Shout, fontSize = (34 + 14 * pop.value).sp, color = Ink.red.copy(alpha = pop.value),
            modifier = modifier.rotate(-12f),
        )
    }
}

/**
 * Remo, the app's own chibi mascot: a round face with big shining eyes, spiky ink hair and a little TV antenna,
 * drawn with paths. {@code happy} switches the mouth from a small "o" to a grin.
 */
@Composable
fun Mascot(modifier: Modifier = Modifier, happy: Boolean = true) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(w * 0.035f, cap = StrokeCap.Round)
        val face = Offset(w / 2, h * 0.58f)
        val fr = w * 0.34f
        // antenna
        drawLine(Ink.ink, Offset(w * 0.5f, h * 0.2f), Offset(w * 0.62f, h * 0.04f), w * 0.03f, StrokeCap.Round)
        drawCircle(Ink.red, w * 0.04f, Offset(w * 0.63f, h * 0.04f))
        // spiky hair behind the face
        val hair = Path().apply {
            moveTo(w * 0.14f, h * 0.55f)
            lineTo(w * 0.08f, h * 0.30f); lineTo(w * 0.24f, h * 0.36f)
            lineTo(w * 0.26f, h * 0.14f); lineTo(w * 0.40f, h * 0.28f)
            lineTo(w * 0.50f, h * 0.10f); lineTo(w * 0.60f, h * 0.28f)
            lineTo(w * 0.76f, h * 0.15f); lineTo(w * 0.77f, h * 0.36f)
            lineTo(w * 0.93f, h * 0.31f); lineTo(w * 0.86f, h * 0.55f)
            close()
        }
        drawPath(hair, Ink.ink)
        // face
        drawCircle(Ink.paper, fr, face)
        drawCircle(Ink.ink, fr, face, style = stroke)
        // fringe over the forehead
        val fringe = Path().apply {
            moveTo(face.x - fr * 0.95f, face.y - fr * 0.25f)
            lineTo(face.x - fr * 0.55f, face.y - fr * 0.55f); lineTo(face.x - fr * 0.35f, face.y - fr * 0.18f)
            lineTo(face.x - fr * 0.05f, face.y - fr * 0.62f); lineTo(face.x + fr * 0.15f, face.y - fr * 0.2f)
            lineTo(face.x + fr * 0.45f, face.y - fr * 0.6f); lineTo(face.x + fr * 0.6f, face.y - fr * 0.22f)
            lineTo(face.x + fr * 0.95f, face.y - fr * 0.3f); lineTo(face.x + fr, face.y - fr * 0.9f)
            lineTo(face.x - fr, face.y - fr * 0.9f)
            close()
        }
        drawPath(fringe, Ink.ink)
        // big eyes with two shines each
        for (side in listOf(-1, 1)) {
            val e = Offset(face.x + side * fr * 0.42f, face.y + fr * 0.12f)
            drawOval(Ink.ink, Offset(e.x - fr * 0.17f, e.y - fr * 0.26f), Size(fr * 0.34f, fr * 0.52f))
            drawCircle(Ink.paper, fr * 0.08f, Offset(e.x + fr * 0.05f, e.y - fr * 0.1f))
            drawCircle(Ink.paper, fr * 0.04f, Offset(e.x - fr * 0.06f, e.y + fr * 0.12f))
            // blush
            drawOval(Ink.red.copy(alpha = 0.35f), Offset(e.x - fr * 0.2f, e.y + fr * 0.32f), Size(fr * 0.3f, fr * 0.12f))
        }
        // mouth
        if (happy) {
            val m = Path().apply {
                moveTo(face.x - fr * 0.16f, face.y + fr * 0.5f)
                quadraticBezierTo(face.x, face.y + fr * 0.72f, face.x + fr * 0.16f, face.y + fr * 0.5f)
            }
            drawPath(m, Ink.ink, style = stroke)
        } else {
            drawCircle(Ink.ink, fr * 0.07f, Offset(face.x, face.y + fr * 0.58f), style = stroke)
        }
    }
}

/** A page background: paper with screentone and the content on top. */
@Composable
fun MangaPage(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Ink.paper)) {
        Screentone(Modifier.fillMaxSize())
        // Android 15 draws apps edge to edge: keep the page clear of the status bar (the bottom bar pads itself).
        Column(Modifier.fillMaxSize().statusBarsPadding(), content = content)
    }
}

@Composable
fun MangaTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = MangaColors, content = content)
