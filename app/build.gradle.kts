plugins {
    // AGP 9 provides Kotlin compilation itself; applying org.jetbrains.kotlin.android
    // on top of it is now an error. See https://kotl.in/gradle/agp-built-in-kotlin
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.rachitgoyal.segmentedprogressbar.demo"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.rachitgoyal.segmentedprogressbar.demo"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 3
        versionName = "2.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // The demo app is not distributed; sign release builds with the debug
            // key so `assembleRelease` verifies shrinking end to end in CI.
            signingConfig = signingConfigs.getByName("debug")
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
