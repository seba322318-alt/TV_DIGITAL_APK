plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.tvdigital.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tvdigital.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.2.0"
        val configuredApiUrl = providers.gradleProperty("TV_DIGITAL_API_URL").orElse("http://10.0.2.2:3000/").get()
        val normalizedApiUrl = if (configuredApiUrl.endsWith("/")) configuredApiUrl else "$configuredApiUrl/"
        buildConfigField("String", "API_BASE_URL", "\"$normalizedApiUrl\"")
    }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.media3:media3-exoplayer:1.11.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.0")
    implementation("androidx.media3:media3-exoplayer-dash:1.11.0")
    implementation("androidx.media3:media3-ui:1.11.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
}
