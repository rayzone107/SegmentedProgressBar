# Migrating from 0.0.1 to 2.0.0

Short version: **your Java and XML almost certainly still compile.** The 0.0.1
method signatures are all still present, and the XML attribute names are
unchanged. What follows is the complete list of things that could nonetheless
need your attention, roughly in descending order of likelihood.

There is a Java test in the repo
([`JavaApiCompatibilityTest.java`](../segmented/src/test/java/com/rachitgoyal/segmented/JavaApiCompatibilityTest.java))
that writes out the entire 0.0.1 API surface longhand, so any accidental break
in this promise fails the build.

---

## 1. minSdk moved from 16 to 26

This is the one change that can stop your build outright. If your app still
supports below API 26, you cannot use 2.0.0.

```
Manifest merger failed : uses-sdk:minSdkVersion 21 cannot be smaller than
version 26 declared in library
[com.github.rayzone107.SegmentedProgressBar:segmentedprogressbar:2.0.0]
```

There is no workaround other than raising your own `minSdk`, or staying on 0.0.1.

## 2. AndroidX is now required

0.0.1 depended on `com.android.support:appcompat-v7`. 2.0.0 depends only on
`androidx.annotation`. If your project is still on the support library you will
need to migrate (**Refactor → Migrate to AndroidX** in Android Studio), or set
`android.useAndroidX=true` and `android.enableJetifier=true` if you are
mid-migration.

## 3. `setBackgroundColor` is deprecated (but still works)

This was the library's worst wart: `setBackgroundColor(int)` overrode
`View.setBackgroundColor` while doing something completely different, painting
the progress track rather than the view background.

It still behaves exactly as it did in 0.0.1, so existing calls are safe. But the
replacement is now available and is what the 0.0.1 README always claimed existed:

```diff
- bar.setBackgroundColor(Color.GRAY);
+ bar.setProgressBarBackgroundColor(Color.GRAY);
```

If you actually wanted a view background, use `setBackground(...)`.

## 4. Invalid configuration now throws

In 0.0.1 these were logged with `Log.w` and ignored:

| Call | 0.0.1 | 2.0.0 |
|---|---|---|
| `setDivisions(0)` or below | logged, ignored | `IllegalArgumentException` |
| `setDividerWidth(-1f)` | logged, ignored | `IllegalArgumentException` |
| `setCornerRadius(-1f)` | silently accepted | `IllegalArgumentException` |
| `app:divisions="0"` in XML | silently accepted | throws at inflation |

If you were passing a computed value that could legitimately be out of range,
clamp it at the call site:

```kotlin
bar.divisions = computed.coerceAtLeast(1)
```

**Progress data is still forgiving.** Out-of-range indices in `enabledDivisions`
are ignored rather than throwing, because that list usually comes from live app
state. Negative indices, which used to crash inside `onDraw`, are now dropped.

## 5. `reset()` behaves differently, because it was broken

0.0.1's `reset()` cleared the internal divider positions (wiping the dividers)
and left `enabledDivisions` untouched, so it removed the wrong thing. In 2.0.0 it
clears the progress and preserves everything else.

If you were relying on the old behaviour to hide dividers, use
`isDividerEnabled = false` instead.

## 6. Bars will look slightly different

Several rendering bugs are fixed, so output is not pixel-identical:

- Dividers now appear on an empty bar. In 0.0.1 they only appeared once at least
  one segment was lit.
- Segments no longer extend underneath the divider to their right. Dividers are
  centred on cell boundaries and segments are inset by half a divider, so each
  segment is now `dividerWidth / 2` narrower on each interior edge than before.
- Corner rounding no longer has patch-rect artefacts, and an oversized
  `cornerRadius` clamps to a pill instead of misrendering.
- The bar now respects the view's padding. **If you had padding set on the view,
  the bar will get smaller.** Remove the padding to keep the old size.

Default colours, `dividerWidth` (`1px`) and `cornerRadius` (`2px`) are all
unchanged, so a bar that did not set them explicitly keeps its palette.

## 7. `enabledDivisions` is now sorted, de-duplicated and copied

```kotlin
bar.enabledDivisions = listOf(5, 1, 5, -2)
bar.enabledDivisions   // [1, 5], sorted, de-duplicated, negatives dropped
```

Two consequences:

- The getter returns a **copy**, so mutating it does nothing. Assign a new list.
- The setter **copies** the list you pass, so mutating your list afterwards no
  longer changes the view. 0.0.1 stored your list by reference, which meant later
  mutations silently changed the view's state without repainting it, if you were
  (perhaps unknowingly) relying on that, you now need an explicit assignment:

```diff
  val steps = mutableListOf(0, 1)
  bar.enabledDivisions = steps
  steps.add(2)
+ bar.enabledDivisions = steps   // now required
```

## 8. Order of `divisions` and `enabledDivisions` no longer matters

Out-of-range indices are retained rather than dropped, so this now works in
either order:

```kotlin
bar.enabledDivisions = listOf(0, 3, 7)
bar.divisions = 10        // indices 3 and 7 are now revealed
```

## 9. Dependency coordinate

The 2.0.0 coordinate is:

```kotlin
implementation("com.github.rayzone107.SegmentedProgressBar:segmentedprogressbar:2.0.0")
```

Note the **dot** before the repository name. That is how JitPack addresses a
repository publishing more than one artifact, which this one now does. The
artifact id itself is unchanged.

`com.github.rayzone107:segmentedprogressbar` also resolves, since JitPack reads
the artifact id as a repository name and this repository happens to be called
that, but there is no repository named `segmentedprogressbar-compose`, so the
Compose artifact needs the dotted group. Using it for both keeps them consistent.

Whatever you had before was probably not working anyway:

- The 0.0.1 README documented the coordinate as
  `com.github.rayzone107:durationview:0.0.1`, a copy-paste error naming a
  different library of mine entirely.
- The version was never `0.0.1` as far as JitPack is concerned. It serves by git
  tag, and the tags on this repository are `1.00`, `1.01` and `tag_v1`. The `1.01`
  build failed even at the time.
- None of them resolve today. JitPack rebuilds on request, and the 2018 build
  cannot run on current infrastructure.

## 10. Things that did *not* change

- The class name and package: `com.rachitgoyal.segmented.SegmentedProgressBar`.
- All seven original XML attribute names. Everything added since is prefixed
  `spb_`, so it cannot collide with another library's attributes.
- All four constructors.
- `setDivisions`, `setEnabledDivisions`, `setProgressBarColor`,
  `setDividerColor`, `setDividerWidth`, `setDividerEnabled`, `setCornerRadius`,
  `reset`.
- Default colours and dimensions.

---

## New in 2.0.0 that you may want

Mostly better ergonomics around the thing the library is for, operating on
individual segments:

```kotlin
bar.enableDivision(7)               // light one segment
bar.disableDivision(2)              // clear one segment
bar.toggleDivision(7)               // flip one segment, returns its new state
bar.isDivisionEnabled(7)            // query one segment
bar.divisionAt(event.x)             // which segment is under this touch?
bar.completedSegmentCount           // how many are lit and in range
bar.progressBarBackgroundColor      // readable, not just writable
```

`setOnDivisionClickListener` is the one worth knowing about: it makes "let the
user tap segments to toggle them" a one-liner, handles padding and RTL (which a
hand-rolled `x / width * divisions` gets silently wrong when the layout is
mirrored), and routes through `View.performClick` so the bar stays accessible.

There are also five new styling options, all off or neutral by default so none of
them change how an existing bar looks:

```kotlin
bar.inactiveHeightRatio = 0.7f                        // slimmer off segments
bar.cornerMode = CornerMode.EACH_RUN                  // one pill per run
bar.dividerColor = Color.TRANSPARENT                  // a real gap, not a line
bar.maxWidth = (420 * density).toInt()                // don't stretch on tablets
bar.shadowRadius = 5 * density                        // drop shadow
bar.segmentAnimation = SegmentAnimation.FADE          // animate toggles
bar.entryAnimation = EntryAnimation.STAGGER           // animate the first show
bar.recurringAnimation = RecurringAnimation.SHIMMER   // keep shimmering
```

All of them default to off or neutral, so none change how an existing bar looks.
See [Styling variants](../README.md#styling-variants) for the details; the only
remaining caveat is that a drop shadow forces a software layer.

## 12. Jetpack Compose

There is now a second artifact if you want a Composable rather than a View:

```kotlin
implementation("com.github.rayzone107.SegmentedProgressBar:segmentedprogressbar-compose:2.0.0")
```

It shares the option enums and the layout maths with the View, so the two render
identically, see [Jetpack Compose](../README.md#jetpack-compose). Taking it is
entirely optional; the View artifact has no Compose dependency.

Plus, with no code changes on your part: padding support, RTL support, working
`wrap_content`, automatic instance-state saving, and an accessibility content
description.
