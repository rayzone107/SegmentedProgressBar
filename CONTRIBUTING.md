# Contributing

Thanks for taking an interest. Issues and pull requests are both welcome.

## Getting set up

You need **JDK 17** and the Android SDK with **API 37** installed. Nothing else,
the Gradle wrapper handles the rest.

```bash
git clone https://github.com/rayzone107/SegmentedProgressBar.git
cd SegmentedProgressBar
./gradlew test
```

If Gradle cannot find your SDK, create a `local.properties` at the repo root:

```properties
sdk.dir=/Users/you/Library/Android/sdk
```

`local.properties` is gitignored and must never be committed.

## Repository layout

| Path | What it is |
|---|---|
| `segmented/` | The published View library. Ships as `segmentedprogressbar`. |
| `segmented-compose/` | The Jetpack Compose bindings. Ships as `segmentedprogressbar-compose`. Depends on `:segmented` for the shared geometry and option enums rather than duplicating them. |
| `app/` | Demo app, a Gallery tab in XML and a Playground tab in Compose, and the library's consumer-side tests. |
| `gradle/libs.versions.toml` | Every dependency version. Change versions here, not in module files. |

## Before opening a pull request

```bash
./gradlew test lint
```

Both must pass. Specifically:

- **`lint` must report zero findings.** The library module runs with
  `warningsAsErrors = true`, so a new warning fails the build. Fix it rather than
  suppressing it; if a suppression is genuinely right, say why in the PR.
- **New behaviour needs a test.** See below for where it belongs.

## Where tests go

The suite is split by what it can prove, and it is worth putting a new test in
the right place:

| File | Scope |
|---|---|
| `SegmentGeometryTest.kt` | Pure layout maths. No Android, no Robolectric, so it can be exhaustive and stays fast. Prefer this whenever the logic can be expressed as arithmetic. |
| `SegmentedProgressBarTest.kt` | The view's public contract: attribute parsing, validation, progress bookkeeping, measurement, instance state, accessibility. |
| `SegmentedProgressBarDrawingTest.kt` | What actually reaches the canvas, recorded through a `RecordingCanvas`. Every rendering bug fixed in 2.0.0 has a regression test here. |
| `JavaApiCompatibilityTest.java` | The Java-visible API surface. Written in Java on purpose, a Kotlin test would silently follow a rename. |
| `app/src/test/.../InflationTest.kt` | Inflation from a real XML layout, which the library module cannot do since it has no layouts. |
| `app/src/test/.../MainActivityTest.kt` | The demo app driven like a user, including a configuration change. |
| `segmented-compose/src/test/.../SegmentedProgressBarComposeTest.kt` | The Composable: composition, parameter validation, tap mapping and semantics. The layout maths is *not* retested here, it is the same shared code. |

Two conventions in the existing tests that are worth keeping:

- **Drawing tests hard-code their expected coordinates** rather than deriving
  them from `SegmentGeometry`, so a bug in the geometry cannot make a drawing
  test pass.
- **Tests that depend on framework state assert that state as a precondition.**
  The RTL helper is the example: layout direction silently resolves to LTR unless
  the application declares `supportsRtl`, which would leave the assertions
  quietly checking the wrong thing, so the helper `check()`s it explicitly.

## Style

- Kotlin official style (`kotlin.code.style=official`), four-space indents, a
  120-column soft limit.
- The library module runs in **explicit API mode**: every public declaration needs
  an explicit visibility modifier and return type. This is deliberate, it makes
  growing the published API surface a conscious act.
- Public members need KDoc. Say what it does *and* what it does when given
  something unreasonable.
- `onDraw` is allocation-free. Keep it that way: the geometry helpers return
  scalars, and the `Path`, `RectF` and radii array are reused across frames.

## Backwards compatibility

This library has users on 0.0.1 who found it years after the last commit. Treat
the public API as something people depend on:

- Don't rename or re-type anything in the 0.0.1 surface. Deprecate and delegate
  instead, `setBackgroundColor` is the worked example.
- Don't rename the XML attributes. They are unprefixed, which is not ideal, but
  renaming them would break every existing layout.
- Changing a default value changes how existing bars render. Don't, unless it is
  fixing an outright bug, and note it in `CHANGELOG.md` if so.

## Releasing

The full runbook, including the one-time account and signing-key setup, is
[docs/PUBLISHING.md](docs/PUBLISHING.md). In outline:

1. Update `VERSION_NAME` in `gradle.properties`.
2. Add a `CHANGELOG.md` entry.
3. Tag and push: `git tag 2.1.0 && git push origin 2.1.0`. Both artifacts are
   built from the one tag, and JitPack builds it on first request per
   `jitpack.yml`.
4. Publish to Maven Central, which is the channel consumers should use:
   `./gradlew publishToMavenCentral --no-configuration-cache`, then press
   Publish on the deployment in the Central Portal. Check the deployment's
   status rather than the build's exit code; the runbook explains why.

Publishing credentials and signing keys belong in `~/.gradle/gradle.properties`
or environment variables. Never in the repo, this project has been burned by
that before.
