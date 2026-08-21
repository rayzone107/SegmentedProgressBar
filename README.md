# Segmented Progress Bar

[![CI](https://github.com/rayzone107/SegmentedProgressBar/actions/workflows/ci.yml/badge.svg)](https://github.com/rayzone107/SegmentedProgressBar/actions/workflows/ci.yml)
[![JitPack](https://jitpack.io/v/rayzone107/SegmentedProgressBar.svg)](https://jitpack.io/#rayzone107/SegmentedProgressBar)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

An Android progress bar split into a fixed number of equal segments, where **each
segment turns on and off independently**.

Progress here is not a single scalar. Any subset of segments can be on at once, in
any order, which makes the view useful for step indicators, checklist completion,
streak calendars, onboarding flows and anything else that answers "which of these
are done?".

Available as a **View** and as a **Jetpack Compose** Composable, from separate
artifacts that share the same layout maths and the same option types.

![Segmented Progress Bar](/Image.png)

---

## Contents

- [Install](#install)
- [Quick start](#quick-start)
- [Jetpack Compose](#jetpack-compose)
- [XML attributes](#xml-attributes)
- [API reference](#api-reference)
- [Styling](#styling)
- [How it renders](#how-it-renders)
- [Right-to-left support](#right-to-left-support)
- [Accessibility](#accessibility)
- [Requirements](#requirements)
- [Upgrading from 0.0.1](#upgrading-from-001)
- [Building from source](#building-from-source)
- [Contributing](#contributing)
- [License](#license)

---

## Install

Add JitPack to your repositories. In a modern project that means
`settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Then take whichever artifacts you need:

```kotlin
dependencies {
    // The View. No Compose dependency.
    implementation("com.github.rayzone107:segmentedprogressbar:2.0.0")

    // Optional: the Jetpack Compose bindings.
    implementation("com.github.rayzone107:segmentedprogressbar-compose:2.0.0")
}
```

They are separate artifacts on purpose, so a View-only project never inherits the
Compose runtime. Both share the same option enums and the same geometry, so they
render identically.

> [!NOTE]
> JitPack builds each tag on first request, so the very first resolve after a
> release can take a minute. The exact coordinate for a tag is printed on that
> tag's [JitPack build page](https://jitpack.io/#rayzone107/SegmentedProgressBar).

---

## Quick start

### From XML

```xml
<com.rachitgoyal.segmented.SegmentedProgressBar
    android:id="@+id/progress"
    android:layout_width="match_parent"
    android:layout_height="16dp"
    app:cornerRadius="8dp"
    app:dividerColor="@android:color/white"
    app:dividerWidth="2dp"
    app:divisions="10"
    app:progressBarBackgroundColor="#E4E7EB"
    app:progressBarColor="#2F6FED" />
```

### From code

```kotlin
val bar = findViewById<SegmentedProgressBar>(R.id.progress)

bar.divisions = 10

// One assignment sets exactly which segments are on.
bar.enabledDivisions = listOf(1, 2, 5, 6, 9)

// Or work one segment at a time; order does not matter.
bar.enableDivision(4)
bar.disableDivision(9)
bar.toggleDivision(0)
bar.isDivisionEnabled(5)      // true

// Back to empty. Colours, divisions and gaps are preserved.
bar.reset()
```

<details>
<summary>Java</summary>

```java
SegmentedProgressBar bar = findViewById(R.id.progress);

bar.setDivisions(10);
bar.setEnabledDivisions(Arrays.asList(1, 2, 5, 6, 9));
bar.toggleDivision(4);
bar.setProgressBarColor(Color.BLUE);
bar.setDividerWidth(2 * getResources().getDisplayMetrics().density);
bar.reset();
```
</details>

### Letting users tap segments

One attribute makes the bar interactive, with no code at all:

```xml
app:spb_tapToToggle="true"
```

or, from code:

```kotlin
bar.isTapToToggleEnabled = true
```

If a tap has to do something else as well, register a listener. It reports which
segment was hit, and runs *after* the bar has toggled it, so it always sees the
state the user is looking at:

```kotlin
bar.setOnDivisionClickListener { view, index ->
    label.text = "${view.completedSegmentCount} of ${view.divisions}"
}
```

A listener on its own reports taps without changing anything, which is what you
want if you hold the lit set yourself:

```kotlin
bar.setOnDivisionClickListener { view, index -> viewModel.onSegmentTapped(index) }
```

Leave `spb_tapToToggle` off in that case, or the bar and your own code will both
toggle and cancel each other out.

To switch touch off entirely, use the platform's own gate, `isClickable = false`
or `android:clickable="false"`; nothing here is dispatched while a view is not
clickable. In Compose there is no separate flag: a bar with an `onSegmentClick` is
interactive and one without it is not.

The view does the coordinate work itself, padding and layout direction included,
and routes taps through `View.performClick`, so it stays accessible. If you need
the raw mapping, `divisionAt(x)` is public too.

> [!NOTE]
> An accessibility service or keyboard activates a view without pointer
> coordinates, so there is no segment to report, and neither the toggle nor the
> listener fires in that case. Pair the bar with a non-positional control; the
> demo app uses a "Clear all" button.

---

## Jetpack Compose

`segmentedprogressbar-compose` is a real Composable. It draws with Compose's own
`Canvas` rather than wrapping the View in an `AndroidView`, so it composes with
modifiers, previews, themes and animation the way you would expect.

```kotlin
var on by remember { mutableStateOf(setOf(1, 2, 5, 6, 9)) }

SegmentedProgressBar(
    divisions = 10,
    enabledSegments = on,
    modifier = Modifier.fillMaxWidth().height(24.dp),
    onColor = Color(0xFF2F6FED),
    offColor = Color(0xFFE4E7EB),
    gap = 3.dp,
    cornerRadius = 8.dp,
    cornerMode = CornerMode.EACH_RUN,
    onSegmentClick = { index -> on = if (index in on) on - index else on + index },
)
```

Everything the View supports is a parameter here, using the same option enums
(`CornerMode`, `SegmentAnimation`, `EntryAnimation`, `RecurringAnimation`,
`ShadowTarget`) imported from the base artifact. Differences worth knowing:

| | View | Compose |
|---|---|---|
| Segment set | `List<Int>`, sorted and de-duplicated for you | `Set<Int>`, already unordered |
| Space between segments | `dividerWidth` and `dividerColor`, defaulting to a 1px white line | `gap` and `gapColor`, defaulting to a real 2dp gap |
| Sizing | `layout_height`, `spb_maxWidth` | `Modifier` |
| Drop shadow | `Paint` shadow layer, which forces a software layer and confines the blur to the view | stacked outset passes, hardware accelerated, free to overflow the composable |
| Interaction | `spb_tapToToggle`, or a listener, or both | an `onSegmentClick` handler, since the lit set lives outside the bar |

The demo app's **Playground** tab drives every option from live controls, which is
the quickest way to see what each one does.

---

## XML attributes

The original seven:

| Attribute | Format | Default | Description |
|---|---|---|---|
| `divisions` | integer | `1` | Number of equal segments. Must be at least `1`. |
| `progressBarColor` | color | `#5097E2` | Colour of segments that are on. |
| `progressBarBackgroundColor` | color | `#C1C1C1` | Colour of segments that are off. |
| `dividerColor` | color | `#FFFFFF` | Colour painted in the gap. Transparent leaves a real gap. |
| `dividerWidth` | dimension | `1px` | Space between segments. Must not be negative. |
| `isDividerEnabled` | boolean | `true` | Whether that space is applied at all. |
| `cornerRadius` | dimension | `2px` | Corner radius, applied per `spb_cornerMode`. |

Everything added in 2.0.0 is prefixed `spb_`:

| Attribute | Format | Default | Description |
|---|---|---|---|
| `spb_cornerMode` | enum | `barEnds` | `barEnds`, `eachSegment` or `eachRun`. |
| `spb_activeHeightRatio` | float | `1.0` | Height of on segments as a fraction of the bar. |
| `spb_inactiveHeightRatio` | float | `1.0` | Height of off segments as a fraction of the bar. |
| `spb_shadowRadius` | dimension | `0` | Drop-shadow blur. `0` disables it. |
| `spb_shadowDx` / `spb_shadowDy` | dimension | `0` | Drop-shadow offset. |
| `spb_shadowColor` | color | `#40000000` | Drop-shadow colour. |
| `spb_shadowTarget` | enum | `all` | `onSegments`, `offSegments` or `all`. |
| `spb_segmentAnimation` | enum | `none` | Transition when a segment is toggled: `none`, `fade`, `grow`. |
| `spb_animationDuration` | integer | `200` | Duration of that transition, in milliseconds. |
| `spb_entryAnimation` | enum | `none` | How the initial state arrives: `none`, `fade`, `grow`, `stagger`. |
| `spb_entryStaggerDelay` | integer | `60` | Per-segment delay for a staggered entry. |
| `spb_recurringAnimation` | enum | `none` | Continuous animation while visible: `none`, `shimmer`, `pulse`. |
| `spb_recurringDuration` | integer | `1600` | Period of one recurring cycle. |
| `spb_shimmerColor` | color | `#73FFFFFF` | Colour blended in at the peak of a shimmer. |
| `spb_maxWidth` / `spb_maxHeight` | dimension | none | Upper bound on the measured size. |
| `spb_tapToToggle` | boolean | `false` | Whether tapping a segment toggles it. |

> [!NOTE]
> The original names are unprefixed for backwards compatibility with 0.0.1
> layouts. Names added since are prefixed because `shadowColor`, `shadowRadius`
> and `cornerMode` are common enough that an unprefixed attribute of the same name
> but a different format in another library would break the resource merge for
> consumers, which is worse than an inconsistent-looking attribute set.

---

## API reference

### Properties

| Property | Type | Notes |
|---|---|---|
| `divisions` | `Int` | Throws `IllegalArgumentException` below `1`. |
| `enabledDivisions` | `List<Int>` | Sorted, de-duplicated copy. Assigning copies the list and drops negatives. |
| `progressBarColor` | `Int` | `@ColorInt`. Segments that are on. |
| `progressBarBackgroundColor` | `Int` | `@ColorInt`. Segments that are off, **not** the view background. |
| `dividerColor` | `Int` | `@ColorInt`. Transparent leaves a real gap. |
| `dividerWidth` | `Float` | Pixels. Throws on negative or non-finite values. |
| `isDividerEnabled` | `Boolean` | |
| `cornerRadius` | `Float` | Pixels. Throws on negative or non-finite values. |
| `cornerMode` | `CornerMode` | `BAR_ENDS`, `EACH_SEGMENT` or `EACH_RUN`. |
| `activeHeightRatio` | `Float` | `0..1`. Height of on segments; throws outside that range. |
| `inactiveHeightRatio` | `Float` | `0..1`. Height of off segments; throws outside that range. |
| `shadowRadius` | `Float` | Pixels. `0` disables the shadow. Throws on negative or non-finite. |
| `shadowDx` / `shadowDy` | `Float` | Pixels. Shadow offset. |
| `shadowColor` | `Int` | `@ColorInt`. |
| `shadowTarget` | `ShadowTarget` | `ON_SEGMENTS`, `OFF_SEGMENTS` or `ALL`. |
| `segmentAnimation` | `SegmentAnimation` | `NONE`, `FADE` or `GROW`. Applies to toggles. |
| `animationDurationMs` | `Long` | Milliseconds. `0` disables animation. Throws on negative. |
| `entryAnimation` | `EntryAnimation` | `NONE`, `FADE`, `GROW` or `STAGGER`. Runs once, on first layout. |
| `entryStaggerDelayMs` | `Long` | Per-segment delay for `STAGGER`. Throws on negative. |
| `recurringAnimation` | `RecurringAnimation` | `NONE`, `SHIMMER` or `PULSE`. Pauses while detached or hidden. |
| `recurringDurationMs` | `Long` | Period of one cycle. Throws if not positive. |
| `shimmerColor` | `Int` | `@ColorInt`. |
| `maxWidth` / `maxHeight` | `Int` | Pixels, or `NO_MAX_SIZE`. A minimum still wins over a smaller maximum. |
| `isTapToToggleEnabled` | `Boolean` | Whether a tap toggles the segment it hit. Sets `isClickable` and `isFocusable`. |
| `completedSegmentCount` | `Int` | Read-only: how many segments are on *and* in range. |

### Functions

| Function | Description |
|---|---|
| `enableDivision(index)` | Turns one segment on, leaving the others alone. Negative indices ignored. |
| `disableDivision(index)` | Turns one segment off, leaving the others alone. |
| `toggleDivision(index)` | Flips one segment and returns its new state. |
| `isDivisionEnabled(index)` | Whether that segment is on. |
| `divisionAt(x)` | The segment index at horizontal position `x` (view coordinates, for example `MotionEvent.getX`), or `NO_DIVISION` outside the bar. Handles padding and RTL. |
| `setOnDivisionClickListener(l)` | Reports which segment was tapped, after `isTapToToggleEnabled` has had its say. Sets `isClickable` and `isFocusable`; pass `null` to clear. |
| `reset()` | Turns every segment off. Preserves `divisions`, colours, gaps and corners. |

### Validation policy

The two kinds of bad input are treated differently, on purpose:

- **Invalid configuration**, such as `divisions < 1` or a negative `dividerWidth`,
  `cornerRadius` or `shadowRadius`, throws `IllegalArgumentException`. These can
  only be programming errors, and a bar that silently renders wrong is harder to
  debug than a stack trace. This applies to XML too, so a bad layout fails at
  inflation.
- **Invalid progress data**, meaning indices in `enabledDivisions` outside the
  current division count, is tolerated and simply not drawn, because that list
  usually comes from live application state.

Out-of-range indices are also *retained* rather than discarded, so the order in
which you set `divisions` and `enabledDivisions` does not matter:

```kotlin
bar.enabledDivisions = listOf(0, 3, 7)
bar.completedSegmentCount               // 1, only index 0 is in range
bar.divisions = 10
bar.completedSegmentCount               // 3, the rest are revealed
```

### Instance state

Which segments are on, and the division count, are saved and restored
automatically across configuration changes, provided the view has an
`android:id`.

---

## Styling

### Colours

`progressBarColor` and `progressBarBackgroundColor` are the on and off colours.
Any colour works; there is nothing special about the defaults.

### Gaps

The space between segments is one number, `dividerWidth`, and what it looks like
depends on `dividerColor`:

```kotlin
bar.dividerWidth = 4 * density

bar.dividerColor = Color.TRANSPARENT   // a real gap: the page shows through
bar.dividerColor = Color.WHITE         // a painted divider line
bar.dividerWidth = 0f                  // segments run flush into each other
```

The bar is drawn cell by cell rather than as one continuous strip, which is what
makes a transparent divider a genuine gap rather than a window onto the track. An
over-wide value is clamped to one segment, so segments can never collapse.

### Rounded edges

`cornerRadius` sets the radius; set it to half the height for a pill.
`cornerMode` decides which edges it applies to:

| Mode | Effect |
|---|---|
| `BAR_ENDS` (default) | Only the outer ends of the whole bar. Interior edges stay square. |
| `EACH_SEGMENT` | All four corners of every segment, on or off, so each reads as its own pill. |
| `EACH_RUN` | The outer ends of each *contiguous run* of on segments. Edges touching another on segment stay square, so a run reads as one pill. |

`EACH_RUN` plus a transparent gap is the combination worth trying: a sparse
selection renders as a series of pills whose shapes are defined by the gaps.

The radius is always clamped to half the smaller dimension, so an over-large value
gives a pill rather than an artefact.

### Height

Both heights default to the full bar, so this costs nothing until you ask for it.
Set them independently to make on segments read as raised, or as an inset fill
inside a channel. Both bands stay centred on the same axis:

```kotlin
bar.inactiveHeightRatio = 0.7f    // off segments slightly inset
bar.activeHeightRatio = 1f        // on segments at full height
```

Either can be the smaller of the two. Gaps always span the taller band, so
neighbouring segments stay visually separated.

> [!TIP]
> Keep the difference modest. A very small ratio, say `0.3`, turns the on segments
> into disconnected blocks floating over a thin rail, which reads as a rendering
> bug rather than a design. Pair it with `CornerMode.EACH_RUN` to keep runs
> looking connected.

### Size

Standard layout params work as on any view, plus a maximum, which Android does not
otherwise give a plain `View`:

```kotlin
bar.maxWidth = (420 * density).toInt()   // do not stretch across a tablet
bar.maxHeight = SegmentedProgressBar.NO_MAX_SIZE
```

A minimum wins over a smaller maximum, matching how the framework treats minimums
as the harder constraint. `wrap_content` falls back to an intrinsic 144dp x 8dp.

### Drop shadow

```kotlin
bar.shadowRadius = 5 * density
bar.shadowDy = 3 * density
bar.shadowColor = 0x73000000
bar.shadowTarget = ShadowTarget.ALL     // or ON_SEGMENTS, or OFF_SEGMENTS
```

**A shadow never changes the bar.** It is drawn outside the bar, and enabling one
or changing its blur or offset cannot move or resize anything. The trade-off is
that it needs somewhere to go: **give the view padding**, or the blur has nowhere
to land. `android:clipChildren="false"` on the parent does not help, because the
software layer the shadow needs is itself the size of the view. Compose has no
such layer and does not clip to a composable's bounds, so there the shadow simply
overflows.

**The bar casts one shadow, shaped like its outline.** Concretely:

- Each segment contributes at most one shadow, so a lit segment is never darker
  than the unlit one beside it.
- Nothing is drawn inside the bar, so a shadow cannot outline a segment or show
  through a translucent one.
- Nothing is drawn *between* segments either. A narrow gap would otherwise fill in
  with blur from both sides and become the very divider line that leaving it
  transparent asked to be rid of.

`shadowTarget` then chooses which segments contribute: `ALL` gives the whole bar
one shadow, `ON_SEGMENTS` shadows the lit runs only, and `OFF_SEGMENTS` the rest.
Because no shadow is ever drawn inside the bar, `ON_SEGMENTS` shows up along the
outside of each lit run rather than as a shadow cast onto the track.

One caveat for the View: **it forces a software layer.** Android ignores `Paint`
shadow layers for shapes on a hardware-accelerated canvas, so enabling one
switches the view to `LAYER_TYPE_SOFTWARE`. That is an off-screen bitmap the size
of the view, cheap for a bar, but the reason this is off by default.

For a shadow under the bar *as a whole* with no padding needed, prefer
`android:elevation`. The view supplies a correctly rounded outline, so the
elevation shadow follows the bar's shape rather than its bounding box.

### Animation

Three independent axes, all off by default so upgrading from 0.0.1 changes
nothing.

**When a segment is toggled**, once the bar is already on screen:

```kotlin
bar.segmentAnimation = SegmentAnimation.FADE  // or GROW
bar.animationDurationMs = 320
```

`FADE` cross-fades. `GROW` extends from the leading edge, mirrored under RTL so it
always grows in the reading direction. Both animate in *both* directions: turning
a segment off animates it out.

`animationDurationMs` covers this transition and the entry animation below; the
recurring loop has its own `recurringDurationMs`. A duration of `0` means the same
thing as `NONE`: the change happens on the next frame, with nothing in between. A
new duration applies to the next transition, so changing it while the bar is idle
looks like nothing happened.

**When the bar first appears**:

```kotlin
bar.entryAnimation = EntryAnimation.STAGGER  // or FADE, GROW
bar.entryStaggerDelayMs = 60
```

`STAGGER` reveals segments one after another, which reads as the bar filling
itself in. Entry is a separate opt-in from `segmentAnimation`, so a bar can
animate itself in without animating every later change. Exactly one of the two is
ever in effect at a time.

**Always, while on screen**:

```kotlin
bar.recurringAnimation = RecurringAnimation.SHIMMER  // or PULSE
bar.recurringDurationMs = 1600
bar.shimmerColor = 0x73FFFFFF
```

`SHIMMER` sweeps a highlight across the on segments; `PULSE` breathes them in and
out together. The loop stops on its own while the view is detached or hidden and
resumes when it comes back, so it costs nothing off-screen.

Three deliberate behaviours:

- **The initial state never animates unless you ask.** With `entryAnimation` at
  `NONE`, a screen does not visibly assemble itself when it first appears.
- **The entry animation runs once**, not again on every re-measure or scroll.
- **Interrupted transitions resume from where they got to.** Toggling a segment
  twice in quick succession reverses smoothly instead of snapping.

---

## How it renders

A bar of width `W` with `n` divisions is split into `n` equal cells. Each of the
`n - 1` interior boundaries carries a gap of `dividerWidth`, **centred** on the
boundary, and segments are inset by half a gap on every side that touches an
interior boundary. Segments and gaps therefore tile the bar exactly, with no
overlap:

```
divisions = 3, dividerWidth = d

0            W/3          2W/3           W
|-------------|------------|-------------|
[  segment 0 ]d[ segment 1 ]d[ segment 2 ]
              ^             ^
              gaps, centred on the boundary
```

A few consequences worth knowing:

- The bar is drawn inside the view's **padding**, so padding works as expected.
- `dividerWidth` is clamped to one segment's width, so an over-large value can
  never collapse segments to a negative size.
- With `layout_height="wrap_content"` the view measures to an intrinsic
  144dp x 8dp rather than collapsing to nothing.
- `onDraw` allocates nothing.

---

## Right-to-left support

The view honours `layoutDirection`: under RTL, segment `0` is drawn at the
**right-hand** end of the bar and taps map to the segment actually under the
finger.

> [!IMPORTANT]
> Android gates RTL resolution for *every* view on the application declaring
> `android:supportsRtl="true"` in its manifest. Without it the platform never
> resolves a right-to-left layout direction and this view will draw
> left-to-right no matter what you set.

---

## Accessibility

The bar reports itself to accessibility services as a `ProgressBar` and, when you
have not set a `contentDescription` of your own, supplies a generated one such as
"6 of 10 segments complete", localisable and correctly pluralised. Setting your
own `contentDescription` always wins.

---

## Requirements

| | |
|---|---|
| **minSdk** | 26 (Android 8.0) |
| **compileSdk** | 37 (Android 17) |
| **Java/Kotlin target** | 17 |
| **Language** | Kotlin, fully usable from Java |
| **View dependencies** | `androidx.annotation` and `kotlin-stdlib`, nothing else |
| **Compose dependencies** | the above plus Compose foundation and UI |

The View artifact ships no colour resources that could collide with yours,
declares nothing in its manifest, and bundles consumer ProGuard rules so it works
under R8 with no configuration on your side.

---

## Upgrading from 0.0.1

Most code needs no changes. See [docs/MIGRATION.md](docs/MIGRATION.md) for the
full list; the short version:

- **`setBackgroundColor(int)` still works** but is deprecated, because it shadows
  `View.setBackgroundColor` with a different meaning. Use
  `setProgressBarBackgroundColor(int)` instead, the method the old README
  documented but which never existed.
- **Invalid configuration now throws** instead of being logged and ignored.
- **`reset()` now actually resets.** In 0.0.1 it cleared the gaps and left the
  selection alone.
- **Several rendering bugs are fixed**, so bars look slightly different. In
  particular gaps now appear on an empty bar, and segments no longer bleed under
  the gap to their right.
- **minSdk moved from 16 to 26** and the library is now AndroidX-only.

---

## Building from source

```bash
git clone https://github.com/rayzone107/SegmentedProgressBar.git
cd SegmentedProgressBar

./gradlew test                  # 228 unit tests across three modules
./gradlew lint                  # must report zero findings
./gradlew :app:installDebug     # the demo app
```

The demo app has two tabs. **Playground** pins a live bar above a scrolling set of
controls for every option, including a colour picker, so you can dial in a look
and read the resulting values off the screen. **Gallery** is a set of worked
examples: a tappable bar, a declarative selection, a weekly habit tracker, gaps
without divider lines, RTL, and each styling option on its own.

Publishing a release:

```bash
git tag 2.0.0 && git push origin 2.0.0
```

JitPack builds the tag on first request using [`jitpack.yml`](jitpack.yml), and
publishes both artifacts from the one tag.

---

## Contributing

Issues and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md).

---

## License

```
Copyright 2016-2026 Rachit Goyal

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
