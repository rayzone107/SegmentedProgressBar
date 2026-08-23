plugins {
    // AGP 9 provides Kotlin compilation itself; applying org.jetbrains.kotlin.android
    // on top of it is now an error. See https://kotl.in/gradle/agp-built-in-kotlin
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// The demo app carries the library's own version, read from the same
// gradle.properties entry the published AAR is cut from, so the APK attached to
// a GitHub release cannot claim a version that release does not contain. The
// release workflow additionally checks this against the tag it is building.
val libraryVersion = providers.gradleProperty("VERSION_NAME").get()

/**
 * Derives a monotonic `versionCode` from a semantic version: 2.1.0 becomes
 * 20100. Derived rather than hand-bumped, because a `versionCode` that has to be
 * remembered is one that eventually ships stale.
 */
fun versionCodeOf(version: String): Int {
    val parts = version.substringBefore('-').split('.').map {
        it.toIntOrNull() ?: error("VERSION_NAME '$version' is not a semantic version")
    }
    require(parts.size == 3) { "VERSION_NAME '$version' must have three parts" }
    val (major, minor, patch) = parts
    require(minor < 100 && patch < 100) {
        "VERSION_NAME '$version' overflows this scheme; minor and patch must stay below 100"
    }
    return major * 10_000 + minor * 100 + patch
}

android {
    namespace = "com.rachitgoyal.segmentedprogressbar.demo"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.rachitgoyal.segmentedprogressbar.demo"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = versionCodeOf(libraryVersion)
        versionName = libraryVersion
    }

    signingConfigs {
        // The demo APK attached to each GitHub release is signed with a key held
        // in repository secrets, so that one release installs over the previous
        // one instead of failing with INSTALL_FAILED_UPDATE_INCOMPATIBLE. The
        // key never lives in this repository; the release workflow writes it to
        // a temporary file and passes these four properties in through
        // ORG_GRADLE_PROJECT_ environment variables. See docs/PUBLISHING.md.
        //
        // They are absent on a developer machine and in pull request builds,
        // where `assembleRelease` only needs to prove that shrinking works and
        // the debug key is fine. That fallback must never reach a release, so
        // the workflow refuses to run without the secrets and verifies the
        // signing certificate of the APK it produced.
        val keystore = providers.gradleProperty("demoKeystoreFile").orNull
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = providers.gradleProperty("demoKeystorePassword").orNull
                keyAlias = providers.gradleProperty("demoKeyAlias").orNull
                keyPassword = providers.gradleProperty("demoKeyPassword").orNull
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        // The Gallery tab is XML; the Playground tab is Compose. Having both in
        // one app is the point, it demonstrates that the two artifacts are
        // interchangeable.
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    lint {
        abortOnError = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":segmented"))
    implementation(project(":segmented-compose"))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.material)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // These tests are the library's consumer-side coverage: they prove the AAR
    // inflates from a real layout and behaves when driven by an app.
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.truth)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
}

// Regenerating the README's images is opt-in:
//
//     ./gradlew :app:testDebugUnitTest --tests '*DocsScreenshotTest*' -Pdocs
//
// Without -Pdocs the generator still runs as an ordinary test, asserting that
// every documented configuration renders, but writes nothing. Keeping the write
// behind a flag means a normal test run never touches tracked files.
tasks.withType<Test>().configureEach {
    if (project.hasProperty("docs")) {
        systemProperty(
            "spb.docs.dir",
            rootProject.layout.projectDirectory.dir("docs/images").asFile.absolutePath,
        )
    }
}
