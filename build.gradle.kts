// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
  // AGP's built-in Kotlin defaults to its bundled KGP; pin it to the same version as the Compose compiler plugin.
  dependencies { classpath(libs.kotlin.gradle.plugin) }
}
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.roborazzi) apply false
}
