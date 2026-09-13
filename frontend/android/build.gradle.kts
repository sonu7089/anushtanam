import java.util.Properties
import java.security.MessageDigest

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

// Keep this resource present even before Google is enabled in Firebase. Referencing it
// directly also prevents the release resource shrinker from removing the OAuth client ID.
val googleConfig = groovy.json.JsonSlurper().parse(file("google-services.json")) as Map<*, *>
val googleClient = (googleConfig["client"] as? List<*>)?.filterIsInstance<Map<*, *>>()?.firstOrNull {
    val info = it["client_info"] as? Map<*, *>
    val androidInfo = info?.get("android_client_info") as? Map<*, *>
    androidInfo?.get("package_name") == "shastralife.anushtanam"
}
val googleWebClientId = (googleClient?.get("oauth_client") as? List<*>)
    ?.filterIsInstance<Map<*, *>>()?.firstOrNull { it["client_type"] == 3 }?.get("client_id") as? String ?: ""

android {
    namespace = "com.mantrasiddhi"
    compileSdk = 36

    defaultConfig {
        applicationId = "shastralife.anushtanam"
        resValue("string", "google_sign_in_web_client_id", googleWebClientId)
        minSdk = 33
        // targetSdk 36 (Android 16): required by Play policy from Aug 31, 2026.
        targetSdk = 36
        // Version 2.0 follows the previous release's versionCode 11.
        versionCode = 12
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "PERF_PROBES", "false")
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

    buildTypes.create("benchmark") {
        initWith(buildTypes.getByName("release"))
        signingConfig = signingConfigs.getByName("debug")
        matchingFallbacks += listOf("release")
        isDebuggable = false
        buildConfigField("boolean", "PERF_PROBES", "true")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
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

dependencies {
    implementation(libs.google.code.scanner)
    implementation(libs.zxing.core)
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:resources"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)


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
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Firebase (Auth + Firestore + Cloud Messaging), versions via the BoM
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.coil.compose)

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

// Fast, encoder-free CI guard. Re-export intentionally with scripts/generate-artwork.py.
val verifyArtwork by tasks.registering {
    val design = file("design/artwork")
    inputs.dir(design)
    inputs.files(fileTree("src/main/res") { include("**/art_*.webp", "drawable-nodpi/*.png") })
    inputs.file("src/main/java/com/mantrasiddhi/presentation/components/ArtworkCatalog.kt")
    doLast {
        fun sha(file: File) = MessageDigest.getInstance("SHA-256")
            .digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        val parser = groovy.json.JsonSlurper()
        val manifest = parser.parse(File(design, "manifest.json")) as Map<*, *>
        (manifest["assets"] as List<*>).forEach {
            val asset = it as Map<*, *>
            check(sha(File(design, "masters/${asset["file"]}")) == asset["sha256"]) {
                "Artwork master changed; update manifest and regenerate derivatives."
            }
        }
        val generated = parser.parse(File(design, "generated.json")) as Map<*, *>
        check(sha(File(design, "manifest.json")) == generated["manifestSha256"] &&
            sha(rootProject.file("scripts/generate-artwork.py")) == generated["generatorSha256"]) {
            "Artwork manifest/exporter changed; regenerate the committed outputs."
        }
        val files = generated["files"] as Map<*, *>
        files.forEach { (path, hash) ->
            val output = rootProject.file(path.toString())
            check(output.isFile && sha(output) == hash) { "Regenerate artwork: $path" }
        }
        val resources = fileTree("src/main/res") { include("**/art_*.webp") }.files
        check(resources.map { it.relativeTo(rootDir).invariantSeparatorsPath }.toSet() ==
            files.keys.map { it.toString() }.filter { it.endsWith(".webp") }.toSet()) {
            "Unexpected or missing generated artwork resources."
        }
        check(fileTree("src/main/res/drawable-nodpi") { include("*.png") }.isEmpty) {
            "Full-size artwork masters must remain outside Android resources."
        }
    }
}
tasks.named("preBuild") { dependsOn(verifyArtwork) }
