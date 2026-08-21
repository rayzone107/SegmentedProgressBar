plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    `maven-publish`
}

// Published separately from :segmented so that View-only consumers never inherit
// the Compose runtime. See segmented/build.gradle.kts for the version logic.
val libraryGroup = "com.github.rayzone107"
val libraryArtifact = "segmentedprogressbar-compose"
val libraryVersion = (findProperty("version") as? String)
    ?.takeUnless { it.isBlank() || it == "unspecified" }
    ?: (findProperty("VERSION_NAME") as String)

group = libraryGroup
version = libraryVersion

android {
    namespace = "com.rachitgoyal.segmented.compose"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
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
        buildConfig = false
        compose = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    lint {
        abortOnError = true
        checkDependencies = true
        warningsAsErrors = true
        disable += "GradleDependency"
    }
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

dependencies {
    // The View library, for the shared geometry and the shared option enums.
    // Depending on it rather than duplicating them is what keeps the two
    // renderers pixel-identical and covered by one set of geometry tests.
    api(project(":segmented"))

    implementation(platform(libs.androidx.compose.bom))
    // The public API takes a Modifier, a Dp and a Color, so ui and ui-graphics are
    // api dependencies: a consumer needs them on its *compile* classpath to call
    // this at all. Only foundation stays internal, since Box, defaultMinSize and
    // the tap detector never appear in a signature.
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    compileOnly(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.truth)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    // Supplies the empty ComponentActivity that createComposeRule hosts content in.
    testImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
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
                name.set("SegmentedProgressBar for Compose")
                description.set(
                    "Jetpack Compose bindings for SegmentedProgressBar, an Android progress " +
                        "bar split into independently togglable segments.",
                )
                url.set("https://github.com/rayzone107/SegmentedProgressBar")
                inceptionYear.set("2026")

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
