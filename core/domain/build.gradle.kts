plugins { alias(libs.plugins.kotlin.jvm) }

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(libs.coroutines.core)
    implementation("javax.inject:javax.inject:1")
    // Reads the bundled mantra catalog (src/main/resources/mantras). DOM API only.
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly(libs.junit5.launcher)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.json)
}
tasks.withType<Test> { useJUnitPlatform() }
