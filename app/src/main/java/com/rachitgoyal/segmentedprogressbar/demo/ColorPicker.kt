package com.rachitgoyal.segmentedprogressbar.demo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * A colour picker in a dialog.
 *
 * A dialog rather than an inline panel for two reasons: it keeps the controls
 * list short, and it takes the picker out of the scrolling column, where a drag
 * across the saturation panel was being claimed by the scroll container instead
 * of the picker.
 */
@Composable
fun ColorPickerDialog(
    initialColor: Color,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit,
) {
    var working by remember { mutableStateOf(initialColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                ColorPickerBody(color = working, onColorChange = { working = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(working) }) { Text("Use colour") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** The saturation and value panel, the hue strip, and a hex readout. */
@Composable
private fun ColorPickerBody(color: Color, onColorChange: (Color) -> Unit) {
    // A fully desaturated or black colour carries no hue of its own, so the last
    // real hue is remembered; otherwise the panel snaps back to red as soon as a
    // drag reaches an edge.
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    val lastHue = remember { floatArrayOf(hsv[0]) }
    if (hsv[1] > 0.01f && hsv[2] > 0.01f) lastHue[0] = hsv[0]
    val hue = lastHue[0]

    Column {
        SaturationValuePanel(
            hue = hue,
            saturation = hsv[1],
            value = hsv[2],
            onChange = { s, v ->
                onColorChange(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, s, v))))
            },
        )

        Spacer(Modifier.height(14.dp))

        HueStrip(
            hue = hue,
            onChange = { newHue ->
                onColorChange(
                    Color(
                        android.graphics.Color.HSVToColor(
                            floatArrayOf(
                                newHue,
                                hsv[1].coerceAtLeast(0.05f),
                                hsv[2].coerceAtLeast(0.05f),
                            ),
                        ),
                    ),
                )
            },
        )

        Spacer(Modifier.height(14.dp))

        HexField(color = color, onColorChange = onColorChange)
    }
}

/**
 * A hex entry field that stays in step with the panel above it.
 *
 * The text is local state rather than derived from [color], so a half-typed value
 * survives keystrokes. It is pushed back into sync only when the colour changes
 * from somewhere else, which is what stops the field fighting the panel while you
 * drag.
 */
@Composable
private fun HexField(color: Color, onColorChange: (Color) -> Unit) {
    var text by remember { mutableStateOf(color.toHexString()) }

    LaunchedEffect(color) {
        if (parseHexColor(text) != color) text = color.toHexString()
    }

    val parsed = parseHexColor(text)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(color)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(10.dp),
                ),
        )
        Spacer(Modifier.size(12.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { raw ->
                text = raw
                parseHexColor(raw)?.let(onColorChange)
            },
            label = { Text("Hex") },
            singleLine = true,
            isError = parsed == null,
            supportingText = if (parsed == null) {
                { Text("Use #RRGGBB") }
            } else {
                null
            },
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Parses `#RRGGBB`, `RRGGBB`, `#RGB` or `RGB`, or returns `null`.
 *
 * Hand-parsed rather than handed to `Color.parseColor`, which throws on bad input
 * and would need a try/catch on every keystroke.
 */
private fun parseHexColor(raw: String): Color? {
    val hex = raw.trim().removePrefix("#")
    if (hex.any { it.digitToIntOrNull(16) == null }) return null
    val expanded = when (hex.length) {
        3 -> hex.map { "$it$it" }.joinToString("")
        6 -> hex
        else -> return null
    }
    val value = expanded.toLongOrNull(16) ?: return null
    return Color(0xFF000000.toInt() or value.toInt())
}

/**
 * Reports the position of every touch and drag, consuming the events.
 *
 * Two things matter here, and getting either wrong breaks dragging while leaving
 * tapping perfectly functional:
 *
 * 1. Written with [awaitEachGesture] and an explicit `consume()` rather than
 *    `detectDragGestures`, so an enclosing scrollable never gets the chance to
 *    claim the gesture. `detectDragGestures` only takes over once the drag passes
 *    touch slop, and a scrolling parent wins during that window.
 * 2. Keyed on `Unit`. Keying it on the current hue looked reasonable but was
 *    fatal: an HSV round-trip nudges the hue by a fraction of a degree, the key
 *    changes, and `pointerInput` restarts, cancelling the drag that caused it. A
 *    single tap survived that, so the bug only showed up when dragging.
 *
 * Callers must therefore read their own live state through `rememberUpdatedState`
 * rather than relying on the lambda being recreated.
 */
private fun Modifier.trackTouch(onFraction: (Float, Float) -> Unit): Modifier = pointerInput(Unit) {
    // Normalised here, where the pointer scope knows the size, so callers only
    // deal in 0..1 fractions.
    fun report(position: Offset) {
        onFraction(
            (position.x / size.width).coerceIn(0f, 1f),
            (position.y / size.height).coerceIn(0f, 1f),
        )
    }

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        report(down.position)
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            change.consume()
            report(change.position)
        }
    }
}

/** The two-dimensional saturation (x) and value (y) area. */
@Composable
private fun SaturationValuePanel(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (Float, Float) -> Unit,
) {
    val pureHue = remember(hue) {
        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
    }
    // Read through an updated state, because the gesture handler below is keyed
    // on Unit and so captures whatever lambda existed when it started.
    val latestOnChange by rememberUpdatedState(onChange)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(14.dp))
            .trackTouch { xFraction, yFraction ->
                latestOnChange(xFraction, 1f - yFraction)
            },
    ) {
        // White to the pure hue horizontally, then transparent to black
        // vertically, which together give the standard saturation/value square.
        drawRect(Brush.horizontalGradient(listOf(Color.White, pureHue)))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))

        val cx = saturation * size.width
        val cy = (1f - value) * size.height
        drawCircle(Color.White, 10.dp.toPx(), Offset(cx, cy), style = Stroke(2.dp.toPx()))
        drawCircle(
            Color.Black.copy(alpha = 0.35f),
            12.dp.toPx(),
            Offset(cx, cy),
            style = Stroke(1.dp.toPx()),
        )
    }
}

/** The hue selector strip. */
@Composable
private fun HueStrip(hue: Float, onChange: (Float) -> Unit) {
    val hueColors = remember {
        (0..360 step 30).map {
            Color(android.graphics.Color.HSVToColor(floatArrayOf(it.toFloat(), 1f, 1f)))
        }
    }
    val latestOnChange by rememberUpdatedState(onChange)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .trackTouch { xFraction, _ -> latestOnChange(xFraction * 360f) },
    ) {
        drawRect(Brush.horizontalGradient(hueColors))
        val cx = (hue / 360f) * size.width
        drawCircle(
            Color.White,
            10.dp.toPx(),
            Offset(cx, size.height / 2f),
            style = Stroke(2.dp.toPx()),
        )
    }
}

/** `#RRGGBB`, which is what a developer wants to copy out of a picker. */
fun Color.toHexString(): String {
    val argb = toArgb()
    return "#%02X%02X%02X".format(
        (argb shr 16) and 0xFF,
        (argb shr 8) and 0xFF,
        argb and 0xFF,
    )
}

/** Rounded percentage, for compact slider labels. */
fun Float.asPercent(): String = "${(this * 100).roundToInt()}%"

/** Spacing used by the swatch rows. */
val PickerRowSpacing: Arrangement.Horizontal = Arrangement.spacedBy(10.dp)
