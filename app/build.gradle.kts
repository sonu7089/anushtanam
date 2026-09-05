import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
}

// Optional release-signing config. When `keystore.properties` (gitignored) is present,
// release builds are signed with the real upload key for Play; otherwise they fall back
// to the debug key so local release builds stay installable.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.mantrasiddhi"
    compileSdk = 36

    defaultConfig {
        applicationId = "shastralife.anushtanam"
        minSdk = 33
        // targetSdk 36 (Android 16): required by Play policy from Aug 31, 2026.
        targetSdk = 36
        // Incrementing to 11 as versionCode 10 was consumed by Google Play Console
        versionCode = 11
        // Minor bump, not a patch: this release adds the Purashcharana sankalpa with its
        // cloud mirror, the Sadhana Marg teachings, the japa-counted pulse, and the
        // Sanskrit-in-English pass across the whole app.
        versionName = "1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

    }

    androidResources {
        // Keep only English + Hindi locale resources; strips the ~80 locales that
        // libraries (Material, AppCompat) bundle but this app never uses.
        localeFilters += listOf("en", "hi")
    }

    signingConfigs {
        // Real Play upload key, used automatically when keystore.properties is present.
        // storeFile is resolved relative to the repo root (where keystore.properties lives).
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // With keystore.properties → real upload key (publishable). Without it → the
            // AGP-managed debug key so local release builds still install (CI-safe, the
            // keystore is auto-generated). A debug-signed build is NOT publishable to Play;
            // Play rejects the debug certificate, and we warn loudly here.
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                logger.warn(
                    "keystore.properties not found — signing release with the DEBUG key. " +
                        "This build is NOT publishable to Google Play."
                )
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Ship native debug symbols inside the bundle so Play can symbolicate
            // crash/ANR stack traces from any bundled .so libraries.
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // Expose the exported Room schema JSONs to instrumentation tests so
    // MigrationTestHelper can find them when validating migrations.
    sourceSets {
        getByName("androidTest") {
            assets.srcDir("$projectDir/schemas")
        }
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    bundle {
        // Per-language / density / ABI split delivery: each user downloads only the
        // resources their device needs, shrinking the install considerably.
        language { enableSplit = true }
        density { enableSplit = true }
        abi { enableSplit = true }
    }
}

ksp {
    // Export Room schemas so migrations are verifiable and tracked over time.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Dagger Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.navigation)

    // Lifecycle & Activity
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Firebase (Auth + Firestore + Cloud Messaging), versions via the BoM
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)

    // Local/Unit Tests
    testImplementation(libs.junit) // Fallback / standard junit4
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly(libs.junit5.launcher)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.json)

    // Instrumented / Integration Tests
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.room.testing)

    // Debug toolings
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
    }
}
