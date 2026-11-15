plugins {
    alias(libs.plugins.android.test)
}

// Device tests that drive the installed app from outside (UiAutomator + Macrobenchmark):
// one generates the app's baseline profile, the others measure start-up and navigation frames.
// See README.md in this folder.
android {
    namespace = "in.c1ph3rj.scanly.baselineprofile"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 29
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // Pairs with :app's "benchmark" build type.
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    targetProjectPath = ":app"
    // Run in this APK's own process so the tests can stop, compile and restart the app.
    experimentalProperties["android.experimental.self-instrumenting"] = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

androidComponents {
    beforeVariants { variant ->
        variant.enable = variant.buildType == "benchmark"
    }
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

// Copies the profile the generator wrote on the device into :app, where the Android Gradle
// plugin compiles it into every build. Run after connectedBenchmarkAndroidTest.
tasks.register<Copy>("copyBaselineProfile") {
    group = "baseline profile"
    description = "Copies the generated baseline profile into app/src/main/baselineProfiles."
    from(layout.buildDirectory.dir("outputs/connected_android_test_additional_output/benchmark/connected")) {
        include("**/BaselineProfileGenerator_generate-baseline-prof.txt")
    }
    eachFile { path = "baseline-prof.txt" }
    includeEmptyDirs = false
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    into(rootProject.file("app/src/main/baselineProfiles"))
    doLast {
        check(rootProject.file("app/src/main/baselineProfiles/baseline-prof.txt").exists()) {
            "No generated profile found. Run :baselineprofile:connectedBenchmarkAndroidTest on a device first."
        }
    }
}
