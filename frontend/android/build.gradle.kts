import java.security.MessageDigest
import java.io.File
import com.android.build.api.artifact.SingleArtifact
import javax.xml.parsers.DocumentBuilderFactory

abstract class VerifyReleaseManifest : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputManifest: RegularFileProperty

    @get:OutputFile
    abstract val outputManifest: RegularFileProperty

    @TaskAction
    fun verify() {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val manifest = inputManifest.get().asFile
        val document = factory.newDocumentBuilder().parse(manifest)
        val androidNs = "http://schemas.android.com/apk/res/android"
        val application = document.getElementsByTagName("application").item(0) as org.w3c.dom.Element
        for (attribute in listOf("debuggable", "testOnly", "usesCleartextTraffic")) {
            check(application.getAttributeNS(androidNs, attribute) in listOf("", "false")) {
                "Release manifest must not enable android:$attribute"
            }
        }
        // A network config can override usesCleartextTraffic; require review before adding one.
        check(!application.hasAttributeNS(androidNs, "networkSecurityConfig")) {
            "Review the release network security config and extend this guard before enabling it"
        }
        val elements = document.getElementsByTagName("*")
        for (index in 0 until elements.length) {
            val element = elements.item(index) as org.w3c.dom.Element
            for (attribute in listOf("name", "targetActivity")) {
                val name = element.getAttributeNS(androidNs, attribute)
                check(!name.startsWith("com.mantrasiddhi.review.") &&
                    !name.startsWith(".review.") && !name.startsWith("androidx.compose.ui.tooling.")) {
                    "Debug-only component in release manifest: $name"
                }
            }
        }
        manifest.copyTo(outputManifest.get().asFile.apply { parentFile.mkdirs() }, overwrite = true)
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
}

// Inject credentials from an OS vault/secret manager into this process only.
// Never read the legacy plaintext keystore.properties file.
val signingEnvironment = listOf(
    "ANDROID_KEYSTORE_PATH", "ANDROID_KEYSTORE_PASSWORD",
    "ANDROID_KEY_ALIAS", "ANDROID_KEY_PASSWORD"
).associateWith { providers.environmentVariable(it).orNull }
val hasReleaseSigning = signingEnvironment.values.any { it != null }
val requireReleaseSigning = providers.environmentVariable("ANDROID_REQUIRE_RELEASE_SIGNING")
    .map { it.toBoolean() }.getOrElse(false)
if (hasReleaseSigning) {
    require(signingEnvironment.values.all { !it.isNullOrBlank() }) {
        "Release signing requires all four ANDROID_KEYSTORE_PATH, ANDROID_KEYSTORE_PASSWORD, " +
            "ANDROID_KEY_ALIAS and ANDROID_KEY_PASSWORD environment variables."
    }
    val keyFile = File(signingEnvironment.getValue("ANDROID_KEYSTORE_PATH")!!)
    require(keyFile.isAbsolute && keyFile.isFile) {
        "ANDROID_KEYSTORE_PATH must be an absolute path to an existing keystore."
    }
    require(!keyFile.canonicalFile.toPath().startsWith(rootDir.canonicalFile.toPath())) {
        "Store the release keystore outside the project tree."
    }
} else {
    require(!requireReleaseSigning) { "Publishable build requested but release signing is missing." }
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
        // Version 2.1 follows the previous build's versionCode 13.
        versionCode = 14
        versionName = "2.1"

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
        if (hasReleaseSigning) {
            create("release") {
                storeFile = File(signingEnvironment.getValue("ANDROID_KEYSTORE_PATH")!!)
                storePassword = signingEnvironment.getValue("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = signingEnvironment.getValue("ANDROID_KEY_ALIAS")
                keyPassword = signingEnvironment.getValue("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // CI validates release code with a disposable debug key. Publishing must set
            // ANDROID_REQUIRE_RELEASE_SIGNING=true to fail closed without credentials.
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                logger.warn(
                    "Release signing environment not configured — signing release with the DEBUG key. " +
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

// Transforming the artifact makes verification mandatory for APK and bundle consumers.
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        val verify = tasks.register<VerifyReleaseManifest>("verifyReleaseManifestSecurity")
        variant.artifacts.use(verify)
            .wiredWithFiles(VerifyReleaseManifest::inputManifest, VerifyReleaseManifest::outputManifest)
            .toTransform(SingleArtifact.MERGED_MANIFEST)
        tasks.named("check") { dependsOn(verify) }
    }
}

// Package the same documents used for publication; policy access works offline.
val bundlePrivacyDocuments by tasks.registering(Copy::class) {
    from(rootProject.file("playstore-assets")) {
        include("privacy-policy.html", "delete-account.html")
        into("privacy")
    }
    into(layout.buildDirectory.dir("generated/privacyAssets"))
}
// A task-backed source directory also wires lint consumers, not only asset merging.
android.sourceSets.getByName("main").assets.srcDir(bundlePrivacyDocuments)

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
