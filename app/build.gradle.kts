plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "om.mgtrener.mgym"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = if (providers.gradleProperty("mgymUiTestInstall").orNull == "true")
            "om.mgtrener.mgym.uitest" else "om.mgtrener.mgym"
        minSdk = 30
        targetSdk = 37
        versionCode = 18
        versionName = "0.6.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val uploadVars = listOf("MGYM_UPLOAD_STORE", "MGYM_UPLOAD_STORE_PASSWORD", "MGYM_UPLOAD_ALIAS", "MGYM_UPLOAD_KEY_PASSWORD")
    val uploadReady = uploadVars.all { !System.getenv(it).isNullOrBlank() }
    if (uploadReady) signingConfigs.create("upload") {
        storeFile = file(System.getenv("MGYM_UPLOAD_STORE"))
        storePassword = System.getenv("MGYM_UPLOAD_STORE_PASSWORD")
        keyAlias = System.getenv("MGYM_UPLOAD_ALIAS")
        keyPassword = System.getenv("MGYM_UPLOAD_KEY_PASSWORD")
    }
    buildTypes {
        debug { buildConfigField("boolean", "STORE_DISTRIBUTION", "false") }
        release {
            buildConfigField("boolean", "STORE_DISTRIBUTION", "true")
            if (uploadReady) signingConfig = signingConfigs.getByName("upload")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // The in-app language picker must work offline, including Material's translated controls.
    bundle { language { enableSplit = false } }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
