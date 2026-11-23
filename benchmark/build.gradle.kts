plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "com.example.album.benchmark"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Macrobenchmark drives the real app process instead of running in it.
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.benchmark.macro.junit4)
}
