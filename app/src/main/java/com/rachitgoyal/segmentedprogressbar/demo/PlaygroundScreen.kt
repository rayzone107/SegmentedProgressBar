package com.rachitgoyal.segmentedprogressbar.demo

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rachitgoyal.segmented.CornerMode
import com.rachitgoyal.segmented.EntryAnimation
import com.rachitgoyal.segmented.RecurringAnimation
import com.rachitgoyal.segmented.SegmentAnimation
import com.rachitgoyal.segmented.ShadowTarget
import com.rachitgoyal.segmented.compose.SegmentShadow
import com.rachitgoyal.segmented.compose.SegmentedProgressBar
import kotlin.math.roundToInt

/**
 * Every option the library exposes, wired to one live bar.
 *
 * The preview is pinned above the controls and only the controls scroll, so the
 * bar you are configuring never leaves the screen.
 */
@Composable
fun PlaygroundScreen(modifier: Modifier = Modifier) {
    // The Compose content carries its own theme: a bare MaterialTheme defaults to
    // the light scheme regardless of the system setting, which looked wrong in
    // dark mode.
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
    ) {
        PlaygroundContent(state = rememberPlaygroundState(), modifier = modifier)
    }
}

@Composable
private fun PlaygroundContent(state: PlaygroundState, modifier: Modifier) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(Modifier.fillMaxSize()) {
            PinnedPreview(state)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SegmentsCard(state)
                ColorsCard(state)
                ShapeCard(state)
                SizeCard(state)
                ShadowCard(state)
                AnimationCard(state)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// region state

/** Everything the playground can change, in one place. */
private class PlaygroundState {
    var divisions by mutableIntStateOf(10)
    var lit by mutableStateOf(setOf(1, 2, 5, 6, 9))

    var onColor by mutableStateOf(Color(0xFF2F6FED))
    var offColor by mutableStateOf(Color(0xFFE4E7EB))

    var barHeight by mutableStateOf(28.dp)
    var cappedWidth by mutableStateOf(false)

    var gap by mutableStateOf(3.dp)
    var paintedDivider by mutableStateOf(false)

    var cornerRadius by mutableStateOf(8.dp)
    var cornerMode by mutableStateOf(CornerMode.EACH_RUN)

    var activeHeight by mutableFloatStateOf(1f)
    var inactiveHeight by mutableFloatStateOf(1f)

    var shadowOn by mutableStateOf(false)
    var shadowRadius by mutableStateOf(6.dp)
    var shadowDy by mutableStateOf(3.dp)
    var shadowTarget by mutableStateOf(ShadowTarget.ALL)

    var tapToToggle by mutableStateOf(true)
    var perSegmentA11y by mutableStateOf(false)

    var partialOn by mutableStateOf(false)
    var partialIndex by mutableIntStateOf(3)
    var partialFraction by mutableFloatStateOf(0.4f)

    var segmentAnimation by mutableStateOf(SegmentAnimation.FADE)
    var entryAnimation by mutableStateOf(EntryAnimation.STAGGER)
    var recurring by mutableStateOf(RecurringAnimation.NONE)
    var durationMs by mutableIntStateOf(240)
    var recurringMs by mutableIntStateOf(1600)

    /** Whether anything on screen is driven by [durationMs]. */
    val usesDuration: Boolean
        get() = segmentAnimation != SegmentAnimation.NONE ||
            entryAnimation != EntryAnimation.NONE

    /** Bumped to remount the bar, which replays the entry animation. */
    var replayKey by mutableIntStateOf(0)

    val shadow: SegmentShadow?
        get() = if (shadowOn) {
            SegmentShadow(radius = shadowRadius, dy = shadowDy, target = shadowTarget)
        } else {
            null
        }

    /**
     * The map handed to the bar, rounded to two decimals so the readout under
     * the preview shows the same value the bar draws.
     */
    val segmentProgress: Map<Int, Float>
        get() = if (partialOn) {
            mapOf(partialIndex to (partialFraction * 100).roundToInt() / 100f)
        } else {
            emptyMap()
        }

    fun toggle(index: Int) {
        lit = if (index in lit) lit - index else lit + index
    }

    fun clampToDivisions() {
        lit = lit.filter { it < divisions }.toSet()
        partialIndex = partialIndex.coerceIn(0, divisions - 1)
    }
}

@Composable
private fun rememberPlaygroundState() = remember { PlaygroundState() }

private val OnPresets = listOf(
    Color(0xFF2F6FED),
    Color(0xFF12A150),
    Color(0xFFF31260),
    Color(0xFF9353D3),
    Color(0xFFF5A524),
    Color(0xFF06B7DB),
)

private val OffPresets = listOf(
    Color(0xFFE4E7EB),
    Color(0xFFF1F3F5),
    Color(0xFFD0D7DE),
    Color(0xFF2A3138),
    Color(0xFFFFE1EC),
    Color(0xFFE3F0FF),
)

// endregion

// region pinned preview

/**
 * The bar under configuration, fixed above the scrolling controls.
 *
 * One bar, not two. The Compose and View implementations render identically, so
 * showing both was duplication rather than information.
 */
@Composable
private fun PinnedPreview(state: PlaygroundState) {
    // shadowElevation, not just tonalElevation: the point is for the scrolling
    // controls to visibly pass underneath the panel.
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                contentAlignment = Alignment.Center,
            ) {
                key(state.replayKey) {
                    SegmentedProgressBar(
                        divisions = state.divisions,
                        enabledSegments = state.lit,
                        modifier = Modifier
                            .then(
                                if (state.cappedWidth) {
                                    Modifier.width(240.dp)
                                } else {
                                    Modifier.fillMaxWidth()
                                },
                            )
                            .height(state.barHeight),
                        onColor = state.onColor,
                        offColor = state.offColor,
                        gap = state.gap,
                        gapColor = if (state.paintedDivider) Color.White else Color.Transparent,
                        cornerRadius = state.cornerRadius,
                        cornerMode = state.cornerMode,
                        activeHeightFraction = state.activeHeight,
                        inactiveHeightFraction = state.inactiveHeight,
                        shadow = state.shadow,
                        segmentAnimation = state.segmentAnimation,
                        entryAnimation = state.entryAnimation,
                        recurringAnimation = state.recurring,
                        animationDurationMillis = state.durationMs,
                        recurringDurationMillis = state.recurringMs,
                        segmentProgress = state.segmentProgress,
                        perSegmentAccessibility = state.perSegmentA11y,
                        // Compose holds the lit set outside the bar, so an
                        // interactive bar is one with a click handler and a
                        // read-only one is one without.
                        onSegmentClick = if (state.tapToToggle) state::toggle else null,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = "enabledDivisions = ${state.lit.sorted()}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.partialOn) {
                Text(
                    text = "segmentProgress = ${state.segmentProgress}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { state.lit = (0 until state.divisions).toSet() }) {
                    Text("All")
                }
                FilledTonalButton(onClick = { state.lit = emptySet() }) { Text("None") }
                FilledTonalButton(
                    onClick = {
                        state.lit = (0 until state.divisions).filter { (it * 7) % 3 != 0 }.toSet()
                    },
                ) { Text("Shuffle") }
                OutlinedButton(onClick = { state.replayKey++ }) { Text("Replay") }
            }
        }
    }
}

// endregion

// region control cards

@Composable
private fun SegmentsCard(state: PlaygroundState) {
    PlaygroundCard(
        title = "Segments",
        subtitle = if (state.tapToToggle) {
            "Tap the bar above to turn segments on and off"
        } else {
            "Touch is off, so the bar above ignores taps"
        },
    ) {
        SliderRow(
            label = "Total segments",
            value = state.divisions.toFloat(),
            range = 1f..24f,
            steps = 22,
            display = "${state.divisions}",
        ) {
            state.divisions = it.roundToInt()
            state.clampToDivisions()
        }

        ToggleRow(label = "Tapping a segment toggles it", checked = state.tapToToggle) {
            state.tapToToggle = it
        }

        ToggleRow(
            label = "Each segment is its own accessibility node",
            checked = state.perSegmentA11y,
        ) { state.perSegmentA11y = it }

        ToggleRow(
            label = "One segment is partially filled",
            checked = state.partialOn,
        ) { state.partialOn = it }

        if (state.partialOn) {
            if (state.divisions > 1) {
                SliderRow(
                    label = "Which segment",
                    value = state.partialIndex.toFloat(),
                    range = 0f..(state.divisions - 1).toFloat(),
                    steps = (state.divisions - 2).coerceAtLeast(0),
                    display = "${state.partialIndex}",
                ) { state.partialIndex = it.roundToInt() }
            }

            SliderRow(
                label = "Fill",
                value = state.partialFraction,
                range = 0f..1f,
                steps = 0,
                display = state.partialFraction.asPercent(),
            ) { state.partialFraction = it }

            // The library's rule, demonstrated rather than hidden: turning the
            // same segment fully on makes the partial entry inert.
            if (state.partialIndex in state.lit) {
                Text(
                    text = "Segment ${state.partialIndex} is on, so the partial " +
                        "is ignored: a full segment supersedes its entry.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ColorsCard(state: PlaygroundState) {
    // Two labelled rows rather than one row behind an On/Off selector: a bare
    // "On"/"Off" pair reads as a feature toggle, not as "which colour am I
    // editing", which is the question the control actually answers.
    PlaygroundCard(title = "Colours", subtitle = "Any colour, not just the presets") {
        SwatchPicker(
            label = "Segments that are ON",
            presets = OnPresets,
            selected = state.onColor,
            dialogTitle = "Colour for segments that are on",
            onChange = { state.onColor = it },
        )

        Spacer(Modifier.height(18.dp))

        SwatchPicker(
            label = "Segments that are OFF",
            presets = OffPresets,
            selected = state.offColor,
            dialogTitle = "Colour for segments that are off",
            onChange = { state.offColor = it },
        )
    }
}

/**
 * A row of preset swatches with a custom-colour swatch at the end.
 *
 * The custom swatch shows the colour in use when it is not one of the presets,
 * so the row always reflects the current value.
 */
@Composable
private fun SwatchPicker(
    label: String,
    presets: List<Color>,
    selected: Color,
    dialogTitle: String,
    onChange: (Color) -> Unit,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    val isCustom = selected !in presets

    Text(label, style = labelStyle())
    Spacer(Modifier.height(8.dp))

    Row(horizontalArrangement = PickerRowSpacing) {
        presets.forEach { color ->
            Swatch(
                color = color,
                selected = color == selected && !isCustom,
                onClick = { onChange(color) },
            )
        }
        CustomSwatch(
            color = if (isCustom) selected else null,
            selected = isCustom,
            onClick = { pickerOpen = true },
        )
    }

    if (pickerOpen) {
        ColorPickerDialog(
            initialColor = selected,
            title = dialogTitle,
            onDismiss = { pickerOpen = false },
            onConfirm = {
                onChange(it)
                pickerOpen = false
            },
        )
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick),
    )
}

/** The last swatch in a row: opens the colour picker. */
@Composable
private fun CustomSwatch(color: Color?, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                color ?: Color.Transparent,
            )
            .then(
                if (color == null) {
                    // A hue sweep, so it is obvious this one opens a picker.
                    Modifier.background(
                        Brush.sweepGradient(
                            listOf(
                                Color(0xFFF31260),
                                Color(0xFFF5A524),
                                Color(0xFF12A150),
                                Color(0xFF06B7DB),
                                Color(0xFF2F6FED),
                                Color(0xFF9353D3),
                                Color(0xFFF31260),
                            ),
                        ),
                    )
                } else {
                    Modifier
                },
            )
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "+",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
    }
}

@Composable
private fun ShapeCard(state: PlaygroundState) {
    PlaygroundCard(title = "Shape") {
        SliderRow(
            label = "Gap",
            value = state.gap.value,
            range = 0f..16f,
            steps = 15,
            display = "${state.gap.value.roundToInt()}dp",
        ) { state.gap = it.roundToInt().dp }

        ToggleRow(
            label = "Paint the gap as a divider line",
            checked = state.paintedDivider,
        ) { state.paintedDivider = it }

        SliderRow(
            label = "Corner radius",
            value = state.cornerRadius.value,
            range = 0f..24f,
            steps = 23,
            display = "${state.cornerRadius.value.roundToInt()}dp",
        ) { state.cornerRadius = it.roundToInt().dp }

        ChoiceRow(
            label = "Corner mode",
            options = CornerMode.entries,
            selected = state.cornerMode,
            name = { it.label() },
        ) { state.cornerMode = it }
    }
}

@Composable
private fun SizeCard(state: PlaygroundState) {
    PlaygroundCard(title = "Size") {
        SliderRow(
            label = "Bar height",
            value = state.barHeight.value,
            range = 6f..64f,
            steps = 57,
            display = "${state.barHeight.value.roundToInt()}dp",
        ) { state.barHeight = it.roundToInt().dp }

        ToggleRow(label = "Cap width at 240dp", checked = state.cappedWidth) {
            state.cappedWidth = it
        }

        SliderRow(
            label = "On-segment height",
            value = state.activeHeight,
            range = 0.2f..1f,
            steps = 0,
            display = state.activeHeight.asPercent(),
        ) { state.activeHeight = it }

        SliderRow(
            label = "Off-segment height",
            value = state.inactiveHeight,
            range = 0.2f..1f,
            steps = 0,
            display = state.inactiveHeight.asPercent(),
        ) { state.inactiveHeight = it }
    }
}

@Composable
private fun ShadowCard(state: PlaygroundState) {
    PlaygroundCard(
        title = "Drop shadow",
        subtitle = "Drawn outside the bar, so it never changes the bar's size",
    ) {
        ToggleRow(label = "Enabled", checked = state.shadowOn) { state.shadowOn = it }
        if (state.shadowOn) {
            ChoiceRow(
                label = "Cast by",
                options = ShadowTarget.entries,
                selected = state.shadowTarget,
                name = { it.label() },
            ) { state.shadowTarget = it }

            SliderRow(
                label = "Blur",
                value = state.shadowRadius.value,
                range = 0f..16f,
                steps = 15,
                display = "${state.shadowRadius.value.roundToInt()}dp",
            ) { state.shadowRadius = it.roundToInt().dp }

            SliderRow(
                label = "Y offset",
                value = state.shadowDy.value,
                range = 0f..12f,
                steps = 11,
                display = "${state.shadowDy.value.roundToInt()}dp",
            ) { state.shadowDy = it.roundToInt().dp }
        }
    }
}

@Composable
private fun AnimationCard(state: PlaygroundState) {
    PlaygroundCard(
        title = "Animation",
        subtitle = "Tap a segment to see the first; press Replay to see the second",
    ) {
        ChoiceRow(
            label = "When a segment is tapped",
            options = SegmentAnimation.entries,
            selected = state.segmentAnimation,
            name = { it.name.lowercase() },
        ) { state.segmentAnimation = it }

        ChoiceRow(
            label = "When the bar first appears",
            options = EntryAnimation.entries,
            selected = state.entryAnimation,
            name = { it.name.lowercase() },
        ) { state.entryAnimation = it }

        ChoiceRow(
            label = "Always, while on screen",
            options = RecurringAnimation.entries,
            selected = state.recurring,
            name = { it.name.lowercase() },
        ) { state.recurring = it }

        // Both sliders appear only while something is using them, which is what
        // makes it obvious which duration belongs to which animation. Shown
        // unconditionally, the transition slider looked broken whenever both
        // transitions were set to none.
        if (state.usesDuration) {
            SliderRow(
                label = "Tap and first-appear duration",
                value = state.durationMs.toFloat(),
                range = 0f..1000f,
                steps = 19,
                display = if (state.durationMs == 0) "instant" else "${state.durationMs}ms",
            ) { state.durationMs = it.roundToInt() }
        }

        if (state.recurring != RecurringAnimation.NONE) {
            SliderRow(
                label = "One cycle of the recurring animation",
                value = state.recurringMs.toFloat(),
                range = 400f..3000f,
                steps = 12,
                display = "${state.recurringMs}ms",
            ) { state.recurringMs = it.roundToInt() }
        }
    }
}

private fun ShadowTarget.label() = when (this) {
    ShadowTarget.ON_SEGMENTS -> "on segments"
    ShadowTarget.OFF_SEGMENTS -> "off segments"
    ShadowTarget.ALL -> "all segments"
}

private fun CornerMode.label() = when (this) {
    CornerMode.BAR_ENDS -> "bar ends"
    CornerMode.EACH_SEGMENT -> "each segment"
    CornerMode.EACH_RUN -> "each run"
}

// endregion

// region building blocks

@Composable
private fun labelStyle() = MaterialTheme.typography.labelLarge.copy(
    fontWeight = FontWeight.Medium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
private fun PlaygroundCard(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    display: String,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(bottom = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = labelStyle(), modifier = Modifier.weight(1f))
            Text(
                text = display,
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
            )
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The whole row is the control, not just the switch: a larger
            // target, and accessibility services read the label and the state
            // as one thing.
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = labelStyle(), modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun <T> ChoiceRow(
    label: String,
    options: List<T>,
    selected: T,
    name: (T) -> String,
    onChange: (T) -> Unit,
) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label, style = labelStyle())
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                Chip(label = name(option), selected = option == selected) { onChange(option) }
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(
        if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        label = "chip-bg",
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

// endregion
