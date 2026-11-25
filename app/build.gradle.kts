plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.onis.kanji"
    compileSdk = 37

    // Release signing is opt-in. CI/local builds without all values use the
    // debug key instead of relying on credentials committed to the repository.
    val releaseKeystorePath = providers.environmentVariable("RELEASE_KEYSTORE_PATH")
        .orElse(providers.gradleProperty("RELEASE_KEYSTORE_PATH"))
        .orNull
    val releaseStorePassword = providers.environmentVariable("RELEASE_STORE_PASSWORD")
        .orElse(providers.gradleProperty("RELEASE_STORE_PASSWORD"))
        .orNull
    val releaseKeyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS")
        .orElse(providers.gradleProperty("RELEASE_KEY_ALIAS"))
        .orNull
    val releaseKeyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD")
        .orElse(providers.gradleProperty("RELEASE_KEY_PASSWORD"))
        .orNull
    val releaseKeystoreFile = releaseKeystorePath?.let(::file)
    val hasReleaseSigning = releaseKeystoreFile?.isFile == true &&
        !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

    defaultConfig {
        applicationId = "com.onis.kanji"
        minSdk = 24
        targetSdk = 37
        versionCode = 9
        versionName = "1.0.8"

        // Release only the current 64-bit ARM phone ABI. Older 32-bit ARM
        // devices and x86/x86_64 emulators are excluded from phone releases.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.create("release") {
                    storeFile = releaseKeystoreFile
                    storePassword = releaseStorePassword
                    keyAlias = releaseKeyAlias
                    keyPassword = releaseKeyPassword
                }
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/*.md"
            excludes += "META-INF/*.txt"
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("io.coil-kt:coil-compose:2.4.0")
    implementation("io.coil-kt:coil-svg:2.4.0")
    implementation("org.pytorch:pytorch_android_lite:1.13.0")
    implementation("org.pytorch:pytorch_android_torchvision_lite:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("com.atilika.kuromoji:kuromoji-ipadic:0.9.0")
    testImplementation(libs.junit)
    testImplementation("org.json:json:20231013")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
