// Top-level build file. Plugins are declared here with `apply false` so that the
// version catalog stays the single source of truth for versions; modules opt in.

import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
}

// region API surface lock
//
// The public API of both published modules is a checked-in dump (each module's
// api/<module>.api) and `apiCheck` fails the build whenever the compiled surface
// stops matching it. That turns "2.x will not break you" from an intention into
// something the build enforces: an accidental signature change cannot land
// without `apiDump` being run and the diff showing up in review.
//
// Hand-rolled rather than kotlinx-binary-compatibility-validator, which would
// otherwise be the obvious tool, because BCV hooks the Kotlin Gradle plugin and
// this build compiles Kotlin through AGP 9's built-in support, where KGP is
// never applied and BCV silently registers nothing. This does the same job on
// the same contract by running javap over the classes in the release AAR.
// Revisit when BCV understands built-in Kotlin.
//
// The dump deliberately includes Kotlin's compiler-generated members
// ($default overloads, @JvmOverloads bridges, Companion fields): they are part
// of the binary contract callers link against, which is exactly what this
// guards. Compose's ComposableSingletons/WhenMappings noise is filtered out,
// since those are implementation details whose names shift between compiler
// versions.

/** Classes that are compiler bookkeeping, not API anyone can link against. */
val syntheticClassMarkers = listOf("ComposableSingletons", "LiveLiterals", "\$WhenMappings")

/** Renders the public and protected surface of an AAR's classes as stable text. */
fun apiSurfaceOf(aarFile: File, javapPath: String, workDir: File): String {
    workDir.mkdirs()
    val classesJar = File(workDir, "classes.jar")
    ZipFile(aarFile).use { aar ->
        val entry = aar.getEntry("classes.jar")
            ?: error("no classes.jar inside ${aarFile.name}")
        aar.getInputStream(entry).use { input ->
            classesJar.outputStream().use { input.copyTo(it) }
        }
    }

    val classNames = ZipFile(classesJar).use { jar ->
        jar.entries().asSequence()
            .map { it.name }
            .filter { it.endsWith(".class") }
            .map { it.removeSuffix(".class").replace('/', '.') }
            .filterNot { name -> syntheticClassMarkers.any { it in name } }
            // Anonymous and local classes ($1, $lambda-2, ...) are never API.
            .filterNot { it.substringAfterLast('$').firstOrNull()?.isDigit() == true }
            .sorted()
            .toList()
    }

    val output = StringBuilder()
    // Batched into one javap invocation per module; per-class would fork the
    // JVM a hundred times.
    val process = ProcessBuilder(
        listOf(javapPath, "-protected", "-classpath", classesJar.absolutePath) + classNames,
    ).redirectErrorStream(true).start()
    val raw = process.inputStream.bufferedReader().readText()
    check(process.waitFor() == 0) { "javap failed:\n$raw" }

    // javap emits one block per class. Members are re-sorted inside each block
    // because the classfile's member order is a compiler implementation detail
    // that must not read as an API change.
    var header: String? = null
    val members = sortedSetOf<String>()
    fun flush() {
        val h = header ?: return
        // Package-private classes get a headerless body from -protected;
        // anything without an explicit public/protected is not API.
        if (h.startsWith("public") || h.startsWith("protected")) {
            output.append(h).append('\n')
            members.forEach { output.append("  ").append(it).append('\n') }
            output.append("}\n")
        }
        header = null
        members.clear()
    }
    raw.lineSequence().forEach { line ->
        val trimmed = line.trim()
        when {
            trimmed.startsWith("Compiled from") -> Unit
            trimmed.endsWith("{") -> {
                flush()
                header = trimmed
            }
            trimmed == "}" -> flush()
            // access$ methods are synthetic bridges the compiler mints for
            // private members reached from lambdas; their names shift between
            // compiler versions and nothing can sensibly link against them.
            "access$" in trimmed -> Unit
            trimmed.isNotEmpty() && header != null -> members.add(trimmed)
        }
    }
    flush()
    return output.toString()
}

subprojects {
    if (name != "segmented" && name != "segmented-compose") return@subprojects

    plugins.withId("com.android.library") {
        val moduleName = name
        val javap = org.gradle.internal.jvm.Jvm.current().javaHome
            .resolve("bin/javap").absolutePath
        val aar = layout.buildDirectory.file("outputs/aar/$moduleName-release.aar")
        val goldenFile = layout.projectDirectory.file("api/$moduleName.api")
        val scratch = layout.buildDirectory.dir("api-surface")

        val dump = tasks.register("apiDump") {
            group = "verification"
            description = "Regenerates api/$moduleName.api from the release AAR"
            dependsOn("assembleRelease")
            notCompatibleWithConfigurationCache("reads the AAR produced in this build")
            doLast {
                val surface = apiSurfaceOf(aar.get().asFile, javap, scratch.get().asFile)
                goldenFile.asFile.apply { parentFile.mkdirs() }.writeText(surface)
                logger.lifecycle("Wrote ${goldenFile.asFile.relativeTo(rootDir)}")
            }
        }

        val check = tasks.register("apiCheck") {
            group = "verification"
            description = "Fails if the release AAR's API no longer matches api/$moduleName.api"
            dependsOn("assembleRelease")
            notCompatibleWithConfigurationCache("reads the AAR produced in this build")
            doLast {
                check(goldenFile.asFile.exists()) {
                    "${goldenFile.asFile} is missing; run ./gradlew $moduleName:apiDump once"
                }
                val actual = apiSurfaceOf(aar.get().asFile, javap, scratch.get().asFile)
                val expected = goldenFile.asFile.readText()
                if (actual != expected) {
                    val diff = expected.lines().toSet().let { exp ->
                        val act = actual.lines().toSet()
                        buildString {
                            (exp - act).forEach { appendLine("  removed: $it") }
                            (act - exp).forEach { appendLine("  added:   $it") }
                        }
                    }
                    throw GradleException(
                        "The public API of :$moduleName changed:\n$diff\n" +
                            "If this change is intentional, run ./gradlew " +
                            "$moduleName:apiDump and commit the updated dump.",
                    )
                }
            }
        }

        tasks.named("check") { dependsOn(check) }
        // Keep the two tasks from racing over the same scratch directory.
        check.configure { mustRunAfter(dump) }
    }
}

// endregion
