plugins {
    // AGP 9 provides Kotlin compilation itself; applying org.jetbrains.kotlin.android
    // on top of it is now an error. See https://kotl.in/gradle/agp-built-in-kotlin
    alias(libs.plugins.android.library)
    `maven-publish`
}

// JitPack invokes the build with `-Pversion=<git tag>`; fall back to the value in
// gradle.properties for local builds and `publishToMavenLocal` smoke tests.
val libraryGroup = "com.github.rayzone107"
val libraryArtifact = "segmentedprogressbar"
val libraryVersion = (findProperty("version") as? String)
    ?.takeUnless { it.isBlank() || it == "unspecified" }
    ?: (findProperty("VERSION_NAME") as String)

group = libraryGroup
version = libraryVersion

android {
    namespace = "com.rachitgoyal.segmented"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        // `targetSdk` is intentionally absent: AGP removed it for library modules,
        // since the consuming application's targetSdk is what actually applies.
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        // This library ships no BuildConfig and no data binding; keep the AAR lean.
        // Resource processing stays on: the styleable attrs and the
        // accessibility string live in res/.
        buildConfig = false
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = false
        }
    }

    lint {
        abortOnError = true
        checkDependencies = true
        // A published library should not ship with unresolved lint debt, and its
        // surface is small enough that holding this line is cheap.
        warningsAsErrors = true
        // Flags newer versions of declared dependencies; that is a job for
        // dependency updates, not for the build.
        disable += "GradleDependency"
    }
}

kotlin {
    jvmToolchain(17)
    // Force every public declaration to carry an explicit visibility and return
    // type, so the published API surface can never grow by accident.
    explicitApi()
}

dependencies {
    implementation(libs.androidx.annotation)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.truth)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = libraryGroup
            artifactId = libraryArtifact
            version = libraryVersion

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("SegmentedProgressBar")
                description.set(
                    "An Android progress bar split into independently togglable segments.",
                )
                url.set("https://github.com/rayzone107/SegmentedProgressBar")
                inceptionYear.set("2016")

                licenses {
                    license {
                        name.set("The Apache Software License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("rayzone107")
                        name.set("Rachit Goyal")
                        url.set("https://github.com/rayzone107")
                    }
                }
                scm {
                    url.set("https://github.com/rayzone107/SegmentedProgressBar")
                    connection.set("scm:git:https://github.com/rayzone107/SegmentedProgressBar.git")
                    developerConnection.set("scm:git:ssh://git@github.com/rayzone107/SegmentedProgressBar.git")
                }
            }
        }
    }
}
