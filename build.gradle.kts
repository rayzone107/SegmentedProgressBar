// Top-level build file. Plugins are declared here with `apply false` so that the
// version catalog stays the single source of truth for versions; modules opt in.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
}
