# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and this project adheres
to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

Nothing in the library itself has changed; the entries below are about how it
ships.

### Added

- **The demo app is attached to each GitHub release** as
  `SegmentedProgressBar-demo-<version>.apk`, signed with a stable key so one
  release installs over the last. Trying the Playground no longer means cloning
  and building the repository. It is not on Google Play, and
  [docs/PUBLISHING.md](docs/PUBLISHING.md) says why.

### Changed

- The demo app's `versionName` and `versionCode` are derived from `VERSION_NAME`
  rather than hand-maintained, so a published APK cannot claim a version that
  was never released.

## [2.1.0] - 2026-08-22

Feature release. Everything is additive: code compiled against 2.0.0 keeps
working, source and binary alike, and `apiCheck` now enforces that in CI.

**Now on Maven Central**, which needs no custom repository:

```kotlin
implementation("io.github.rayzone107:segmentedprogressbar:2.1.0")
implementation("io.github.rayzone107:segmentedprogressbar-compose:2.1.0")
```

The JitPack coordinates keep working for 2.0.0 and 2.1.0, and the artifacts are
identical either way; only the group differs. Maven Central is the one to use
from here.

### Added

- **Partial fills.** `setDivisionProgress(index, fraction)` and
  `getDivisionProgress(index)` on the View, `segmentProgress: Map<Int, Float>`
  in Compose. Every division has a fill fraction: `1` is exactly
  `enableDivision`, `0` exactly `disableDivision`, and anything between draws
  the leading part of the cell, RTL mirrored, on any number of divisions at
  once. This is the stories and chapters pattern. A partial division is not
  enabled and not counted by `completedSegmentCount`. Every corner mode shapes
  the fill correctly: under `EACH_RUN` it continues the run beside it, joining
  squarely while its moving edge carries the run's rounded end; the other
  modes clip it to the cell's shape. Partial changes apply without transition,
  and reaching `1` hands over to `segmentAnimation` with a GROW continuing
  from the fill rather than restarting. Partial fills participate in the drop
  shadow's silhouette and survive instance state.
- **Per-division colours.** `setDivisionColor(index, color)`,
  `clearDivisionColor(index)`, `clearDivisionColors()`, `getDivisionColor` and
  `hasDivisionColor` on the View, `segmentColors: Map<Int, Color>` in Compose.
  One rule: a colour set for a division wins over `progressBarColor` for that
  division only; everything else keeps the single-colour path, and changing the
  global colour never clears overrides. Covers the full fill, the partial fill
  and the base a shimmer or pulse tints. Built for heatmaps and streak
  calendars.
- **Opt-in per-division accessibility.** `isPerDivisionAccessibilityEnabled`
  (`spb_perDivisionAccessibility` in XML) on the View,
  `perSegmentAccessibility` in Compose. Off by default, preserving the 2.0.0
  single-summary-node behaviour. On, every division is its own focusable,
  checkable node that a screen reader steps through and, on an interactive bar,
  toggles in place; activation shares the tap code path (toggle first, then
  notify the listener). The View additionally gains keyboard navigation: arrows
  move between divisions, Enter activates. Adds the library's one dependency
  beyond annotations, `androidx.customview`, for `ExploreByTouchHelper`.
- **API surface lock.** The public API of both modules is a checked-in dump
  (`api/<module>.api`); `apiCheck` fails CI whenever the compiled surface stops
  matching it, and `apiDump` regenerates it for intentional changes. The 2.0.0
  Compose signature is retained as a hidden bridge, so binaries compiled
  against 2.0.0 keep linking even though the composable gained parameters.
- **(Demo app)** The Playground gained a partial-fill tap mode, where each tap
  adds a quarter fill to any segment and the readout shows the exact
  `segmentProgress` map the bar receives, and the toolbar gained a light/dark
  toggle that remembers its choice, so every feature can be checked in both
  themes without touching system settings.

### Changed

- **A shadow no longer seals unpainted gaps.** A gap belongs to the shadow's
  silhouette only where the paint connects the cells around it: under a
  painted divider, which makes the bar one slab, and inside an `EACH_RUN` run,
  including the joint where a run flows into its partial. A transparent gap
  between separate pieces is now an opening the neighbours' blur spills into,
  so each piece casts like its own object. Previously the shadow bridged every
  gap, which made an unpainted gap read as a painted divider line the moment
  a shadow surrounded it.
- **A shadow no longer forces a software layer.** The View renders the shadow
  into a cached bitmap rebuilt only when the silhouette changes and blits it
  each frame, so the view stays fully hardware accelerated; previously every
  frame of a shimmer re-rasterised the whole view in software. Same memory as
  the layer it replaces, freed when the radius drops to zero.
- **Compose shimmer no longer recomposes per frame.** The recurring phase is
  now read only inside the draw pass, and the shadow's paths are built in
  `drawWithCache` and survive across frames, so a shimmer frame is a redraw
  and nothing more. Every pixel-level rendering test passes unchanged in both
  renderers.

## [2.0.0] - 2026-08-21

The first release in eight years. The library is brought up to a current
toolchain, the rendering bugs are fixed, and it gains a test suite and real
documentation. The Java API from 0.0.1 still compiles, see
[docs/MIGRATION.md](docs/MIGRATION.md).

### Security

- **Removed publishing credentials from `gradle.properties`.** The file
  committed a Nexus password and a GPG signing passphrase in plain text.
  They are gone from the working tree, but they remain in the git history of a
  public repository and **must be treated as compromised and rotated.**

### Fixed

- **Dividers no longer disappear on an empty bar.** The divider-drawing loop was
  nested inside the loop over enabled segments, so a bar with nothing lit drew no
  dividers at all.
- **Dividers are drawn once each instead of once per lit segment.** The same
  nesting bug caused redundant overdraw that scaled with progress.
- **Segments no longer bleed under the divider to their right.** The old geometry
  added the full divider width to a segment's left edge without subtracting it
  from the right. Dividers are now centred on cell boundaries and segments are
  inset by half a divider on interior edges, so the two tile the bar exactly.
- **`reset()` now resets.** It used to clear the internal divider positions,
  wiping the dividers, while leaving the progress untouched.
- **Every setter now repaints.** Only `setDivisions` and `setEnabledDivisions`
  called `invalidate()`, so programmatic colour, divider-width, divider-enabled
  and corner-radius changes did not appear until something else redrew the view.
- **`setDivisions()` before layout no longer places every divider at zero.**
  Divider positions were computed eagerly from a width that was still `0`.
- **Negative indices in `enabledDivisions` no longer crash.** They caused an
  `IndexOutOfBoundsException` inside `onDraw`; they are now dropped on assignment.
- **The caller's list is copied on assignment.** `setEnabledDivisions` stored the
  caller's `List` by reference, so later mutations changed the view's state
  behind its back and without a repaint.
- **Removed the `ViewTreeObserver.OnGlobalLayoutListener` sizing hack.** It only
  detached itself once the view reached a non-zero width, so a view that never
  did leaked the listener, and it cached geometry that later resizes never
  recomputed. Geometry is now derived from the view's current size at draw time,
  so there is no cached state to go stale.
- **Corner rounding is now correct.** The old code drew a round rect and then
  painted square patches over parts of it, which produced visible artefacts.
  Only the bar's outer corners are rounded now, via per-corner paths, and the
  radius is clamped to half the smaller dimension.
- **An oversized `dividerWidth` can no longer collapse segments to a negative
  width.** It is clamped to one segment's width.
- **Removed a dead `percentCompleted` field** that was assigned but never read.
- **Fade transitions now report their opacity through the paint colour** rather
  than `Paint.setAlpha`. The two are equivalent for a solid paint on a device,
  but only the former is visible through `Paint.getColor`, which is what made the
  behaviour testable, and the tests immediately caught that a cleared segment
  was being dropped from the draw loop before it could animate out.
- **The drop shadow follows the bar's outline instead of its individual layers.**
  Hanging a `Paint` shadow layer on both fill paints and letting it fall where it
  may produced four separate visible artefacts, all of which are now covered by
  pixel-level tests in both renderers:
  - A lit segment came out twice as dark as an unlit one, because the track cell
    and the segment drawn over it each cast their own shadow onto the same spot.
  - Every gap grew a dark tick above and below the bar, where the blurs of its two
    neighbours met and added up.
  - Each cell was outlined in shadow, because the shadow shape sat at full alpha
    directly beneath an anti-aliased fill edge; anything translucent, including a
    segment mid-fade, looked dirty for the same reason.
  - A shadow landed on top of whichever neighbouring cell had already been
    painted, drawing a dark line down one side of every cell.

  The shadow is now built as a single shape, blurred once, drawn before any fill
  and clipped to the outside of the bar's outline, gaps included. One consequence
  worth knowing: since it can no longer be drawn inside the bar, `ON_SEGMENTS`
  shows along the outside of a lit run rather than as a shadow cast onto the track.
- **(Demo app) The first section is no longer hidden behind the app bar.** Since
  targetSdk 35 Android enforces edge-to-edge, and a theme `ActionBar` lays the
  content out from the top of the window. The demo now uses a `Toolbar` in its
  own layout with explicit window-inset padding.

### Added

- `progressBarBackgroundColor`: the method the 0.0.1 README documented as
  `setProgressBarBackgroundColor` but which never existed.
- `enableDivision(index)`, `disableDivision(index)`, `toggleDivision(index)`,
  `isDivisionEnabled(index)`: operate on a single segment without rebuilding the
  whole list.
- `setOnDivisionClickListener`: makes segments individually tappable, reporting
  which one the user hit. Implemented via `onTouchEvent` plus a `performClick`
  override so it stays reachable by accessibility services, rather than pushing
  an `OnTouchListener` onto callers.
- `isTapToToggleEnabled` and `spb_tapToToggle`: makes the bar interactive with no
  code at all, which is all an XML-only consumer needs. Off by default, and it
  toggles before notifying any listener so the listener sees the state the user is
  looking at. Touch as a whole is still gated by the platform's `isClickable`, so
  `android:clickable="false"` switches everything here off.
- `divisionAt(x)` and `NO_DIVISION`: the raw mapping from a touch position to a
  segment index, accounting for padding and layout direction so callers never
  have to duplicate (and mirror wrong) the geometry.
- `completedSegmentCount`: how many segments are lit and in range.
- **Padding support.** The bar draws inside the view's padding.
- **Right-to-left support.** Under `layoutDirection=rtl`, segment `0` is drawn at
  the right-hand end. Requires `android:supportsRtl="true"` in the consuming app,
  which is how Android gates RTL for all views.
- **Sensible `wrap_content` measurement.** The view had no `onMeasure`; it now
  reports an intrinsic `144dp × 8dp`.
- **Instance state saving.** Progress and division count survive configuration
  changes when the view has an `android:id`.
- **Accessibility.** The bar reports itself as a `ProgressBar` and supplies a
  localised, correctly pluralised content description such as
  "6 of 10 segments complete" when the caller has not set one.
- **A test suite of 314 tests** covering geometry, attribute parsing, validation,
  drawing output, corner modes, height bands, gaps, size constraints, drop
  shadow, toggle/entry/recurring animation, touch handling, RTL, padding, instance
  state, accessibility, Java interoperability, the Compose bindings and the demo
  app. Most assert on recorded draw calls; the drop-shadow tests assert on real
  pixels, in both renderers, because how shadows accumulate is invisible in a list
  of draw calls.
- **Height bands.** `activeHeightRatio` and `inactiveHeightRatio` give the lit
  segments and the unlit track independent heights, both centred on the same
  axis, for the common "slim rail with chunky completed segments" look. Dividers
  span the taller of the two so neighbours stay separated.
- **Corner modes.** `cornerMode` selects which edges `cornerRadius` applies to. A
  cell covered by a segment takes that segment's rounding, so an `EACH_RUN` run
  does not show the square corner of the rail beneath its rounded end. The modes
  are:
  `BAR_ENDS` (the previous and default behaviour), `EACH_SEGMENT` (every cell
  becomes its own pill, track included) or `EACH_RUN` (each contiguous run of lit
  segments becomes one pill, so the gaps define the shapes).
- **Drop shadow.** `shadowRadius`, `shadowDx`, `shadowDy`, `shadowColor` and
  `shadowTarget`, the last choosing whether the on segments, the off segments or
  all of them cast it. The shadow is drawn *outside* the bar and **never changes
  the bar's size or position**, so the View needs padding for the blur to land in;
  Compose does not clip to bounds, so it overflows there. Enabling one
  switches the View to a software layer, because Android ignores `Paint` shadow
  layers for shapes on a hardware-accelerated canvas; it is off by default for
  that reason. Separately, the view now supplies a correctly rounded
  `ViewOutlineProvider`, so `android:elevation` casts a bar-shaped shadow instead
  of a rectangular one.
- **Segment animation.** `segmentAnimation` (`NONE`, `FADE`, `GROW`) with
  `animationDurationMs`. Off by default so upgrading changes nothing. Animates in
  both directions, mirrors `GROW` under RTL, never animates the initial state,
  and resumes interrupted transitions from where they had got to rather than
  snapping.
- **Entry animation.** `entryAnimation` (`NONE`, `FADE`, `GROW`, `STAGGER`) with
  `entryStaggerDelayMs`, for how the bar's initial state arrives. A separate
  opt-in from `segmentAnimation`, so a bar can animate itself in without
  animating every later change, and it runs once rather than on every re-measure.
- **Recurring animation.** `recurringAnimation` (`NONE`, `SHIMMER`, `PULSE`) with
  `recurringDurationMs` and `shimmerColor`. The loop stops while the view is
  detached or hidden and resumes when it comes back, so it costs nothing
  off-screen.
- **Size constraints.** `maxWidth` and `maxHeight`, which Android does not
  otherwise offer for a plain `View`. A minimum still wins over a smaller
  maximum, matching how the framework treats minimums.
- **Gaps.** The track is now drawn cell by cell rather than as one continuous
  bar, so a transparent `dividerColor` produces a genuine gap with the page
  showing through, instead of a window onto the track. An opaque colour still
  paints a divider line exactly as before.
- **A Jetpack Compose artifact**, `segmentedprogressbar-compose`. A real
  Composable drawing through Compose's own `Canvas` rather than an `AndroidView`
  wrapper, sharing this library's `SegmentGeometry` and option enums so the two
  renderers stay identical. Published separately so View-only consumers never
  inherit the Compose runtime.
- **Consumer ProGuard rules** bundled in the AAR, so R8 works with no
  configuration on the consumer's side.
- **GitHub Actions CI** running tests, lint, both assemble tasks and a publish
  smoke test.
- **`LICENSE`, `CHANGELOG.md`, `CONTRIBUTING.md`, `docs/MIGRATION.md`** and KDoc
  on every public member.
- **A demo app with two tabs:** a Gallery of worked examples, and a Playground
  that pins a live bar above a scrolling set of controls for every option, with an
  HSV colour picker (and hex entry) behind the last swatch in each colour row.

### Changed

- **Rewritten in Kotlin.** The published API is unchanged for Java callers; see
  the Java compatibility test for the proof. The class is explicitly `open`,
  because Kotlin classes are final by default and 0.0.1's Java class was
  subclassable.
- **Invalid configuration throws instead of being logged and ignored.**
  `divisions < 1` and a negative `dividerWidth` or `cornerRadius` now throw
  `IllegalArgumentException`. Invalid *progress data* is still tolerated.
- **Out-of-range `enabledDivisions` are retained rather than discarded,** so the
  order in which `divisions` and `enabledDivisions` are set no longer matters.
- `enabledDivisions` now returns a sorted, de-duplicated copy.
- **minSdk 16 → 26**, **compileSdk 27 → 37**, target Java 7 → 17.
- **Migrated from the Android Support Library to AndroidX.** The library's only
  AndroidX dependency is now `androidx.annotation`.
- **`CornerMode` and `SegmentAnimation` are top-level types** in
  `com.rachitgoyal.segmented` rather than nested in the View, so the Compose
  artifact can use the same enums. `SegmentGeometry` is public for the same
  reason.
- **Library namespace is now `com.rachitgoyal.segmented`** (it previously
  collided with the demo app's package). The `SegmentedProgressBar` class package
  is unchanged, so imports do not move.
- **Toolchain:** Gradle 4.4 → 9.7.1, AGP 3.1.2 → 9.3.1, Groovy build scripts →
  Kotlin DSL with a version catalog.
- **Publishing:** replaced the abandoned `android-maven-gradle-plugin` and the
  dead Bintray plugin with Gradle's `maven-publish`. The published POM now
  carries correct licence, SCM and developer metadata, and a sources jar ships
  alongside the AAR.
- **Repositories:** `jcenter()` (shut down in 2021) → `mavenCentral()`.

### Removed

- `segmented/maven-push.gradle` and the Sonatype/Bintray publishing path.
- The library's `app_name` string and its `white`, `grey_light` and
  `progress_bar` colour resources. A library has no business shipping either, and
  they could collide with a consumer's resources. Defaults are compile-time
  constants now, with identical values.
- `.idea/` project files from version control (they were already gitignored).

### Known issues

- Individual segments are not exposed as separate accessibility nodes, so
  `setOnDivisionClickListener` cannot fire for a service that activates the view
  without pointer coordinates. Pair the bar with a non-positional control.
- Robolectric is pinned to SDK 35 rather than 37: its API 36+ sandboxes require
  Java 21 and this project builds on the Java 17 toolchain.

## [0.0.1] - 2018-05-06

- Initial release.

[2.0.0]: https://github.com/rayzone107/SegmentedProgressBar/releases/tag/2.0.0
[0.0.1]: https://github.com/rayzone107/SegmentedProgressBar/releases/tag/0.0.1
