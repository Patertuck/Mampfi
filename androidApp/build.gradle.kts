import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val localEndpointProperties = Properties().apply {
    val configFile = rootProject.file("mampfi.local.properties")
    if (configFile.isFile) configFile.inputStream().use(::load)
}

fun endpointProperty(name: String, fallback: String): String = providers.gradleProperty(name)
    .orElse(localEndpointProperties.getProperty(name) ?: fallback)
    .get()

val lanBaseUrl = endpointProperty("mampfi.lanBaseUrl", "http://10.0.2.2:8080/").ensureTrailingSlash()
val tailscaleBaseUrl = endpointProperty("mampfi.tailscaleBaseUrl", "").let { if (it.isBlank()) "" else it.ensureTrailingSlash() }

fun String.ensureTrailingSlash() = if (endsWith('/')) this else "$this/"

android {
    namespace = "ch.mampfi.app"
    compileSdk = 35

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "ch.mampfi.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "MAMPFI_LAN_BASE_URL", "\"$lanBaseUrl\"")
        buildConfigField("String", "MAMPFI_TAILSCALE_BASE_URL", "\"$tailscaleBaseUrl\"")
    }
    buildFeatures { compose = true; buildConfig = true }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
    implementation("com.kizitonwose.calendar:compose:2.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
