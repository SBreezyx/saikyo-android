// Top-level build file where you can add configuration options common to all sub-projects/modules.


buildscript {
    dependencies {
        classpath(libs.secrets.gradle.plugin)
//        classpath(libs.ktor.client.core)
    }
}

plugins {
    alias(libs.plugins.com.android.application) apply false
    alias(libs.plugins.com.android.library) apply false
    alias(libs.plugins.org.jetbrains.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false

    alias(libs.plugins.secrets.gradle.plugin) apply false
    alias(libs.plugins.androidx.room) apply false
    alias(libs.plugins.org.jetbrains.kotlin.serialization) apply false
    alias(libs.plugins.com.google.devtools.ksp) apply false
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false

}

val defaultMinSdkVersion by extra(30)
