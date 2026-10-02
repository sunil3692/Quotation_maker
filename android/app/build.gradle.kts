plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Each GitHub Actions run gets a higher version code, so a new APK installs over the old one.
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.quotationmaker.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.quotationmaker.app"
        minSdk = 24
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    signingConfigs {
        create("release") {
            // A fixed key, kept in the repo, so every build can update the installed app.
            // Before publishing on Play Store, move this key into GitHub Secrets (see README).
            storeFile = file(System.getenv("QM_KEYSTORE") ?: "release.jks")
            storePassword = System.getenv("QM_STORE_PASSWORD") ?: "qmaker2026"
            keyAlias = System.getenv("QM_KEY_ALIAS") ?: "quotationmaker"
            keyPassword = System.getenv("QM_KEY_PASSWORD") ?: "qmaker2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.webkit:webkit:1.11.0")
}
