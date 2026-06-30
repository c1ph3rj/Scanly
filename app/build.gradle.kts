import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}

val releaseStoreFile = providers.gradleProperty("SCANLY_RELEASE_STORE_FILE")
    .orElse(providers.environmentVariable("SCANLY_RELEASE_STORE_FILE"))
    .orElse(localProperties.getProperty("SCANLY_RELEASE_STORE_FILE") ?: "")
    .get()
    .trim()

val releaseStorePassword = providers.gradleProperty("SCANLY_RELEASE_STORE_PASSWORD")
    .orElse(providers.environmentVariable("SCANLY_RELEASE_STORE_PASSWORD"))
    .orElse(localProperties.getProperty("SCANLY_RELEASE_STORE_PASSWORD") ?: "")
    .get()
    .trim()

val releaseKeyAlias = providers.gradleProperty("SCANLY_RELEASE_KEY_ALIAS")
    .orElse(providers.environmentVariable("SCANLY_RELEASE_KEY_ALIAS"))
    .orElse(localProperties.getProperty("SCANLY_RELEASE_KEY_ALIAS") ?: "")
    .get()
    .trim()

val releaseKeyPassword = providers.gradleProperty("SCANLY_RELEASE_KEY_PASSWORD")
    .orElse(providers.environmentVariable("SCANLY_RELEASE_KEY_PASSWORD"))
    .orElse(localProperties.getProperty("SCANLY_RELEASE_KEY_PASSWORD") ?: "")
    .get()
    .trim()

val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all(String::isNotBlank)

android {
    namespace = "in.c1ph3rj.scanly"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "in.c1ph3rj.scanly"
        minSdk = 29
        targetSdk = 36
        versionCode = 9
        versionName = "1.0.9"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            installation {
                enableBaselineProfile = false
            }
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        create("verification") {
            initWith(getByName("release"))
            installation {
                enableBaselineProfile = false
            }
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    androidResources {
        noCompress += "tflite"
    }
}

androidComponents {
    onVariants { variant ->
        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        tasks.matching { it.name == "ksp${variantName}Kotlin" }.configureEach {
            doFirst {
                mkdir(layout.buildDirectory.dir("generated/ksp/${variant.name}"))
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.exifinterface)
    implementation(libs.opencv)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.google.ai.edge.litert)
    implementation(libs.google.dagger.hilt.android)
    implementation(libs.google.play.app.update)
    implementation(libs.google.play.app.update.ktx)
    implementation(libs.pdfbox.android)
    ksp(libs.androidx.room.compiler)
    ksp(libs.google.dagger.hilt.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
