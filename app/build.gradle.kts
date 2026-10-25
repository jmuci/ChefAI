import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.room)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Base URL the release build talks to: the deployed backend by default, overridable via
 * `-PchefaiApiBaseUrl=https://…` or a `chefaiApiBaseUrl` entry in `~/.gradle/gradle.properties`
 * (e.g. to point a build at a staging host). Pass it blank to build with no remote at all.
 */
val releaseApiBaseUrl: String =
    (project.findProperty("chefaiApiBaseUrl") as String?) ?: "https://api.pocket-chef.app"

/**
 * Short hash of the commit a debug build was made from, shown next to its version name. Falls back
 * to "unknown" outside a git checkout (e.g. a source tarball) rather than failing the build.
 */
val gitCommitHash: String = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.get().trim().ifEmpty { "unknown" }

android {
    namespace = "com.tenmilelabs.chefai"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tenmilelabs.chefai"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "com.tenmilelabs.chefai.HiltTestRunner"

        // The deployed host, in every build type: release talks to it directly (API_BASE_URL
        // below), and a debug build can be switched onto it from Developer options.
        buildConfigField("String", "REMOTE_API_BASE_URL", "\"${releaseApiBaseUrl}\"")
        // False only when chefaiApiBaseUrl is passed blank, so Developer options can refuse it.
        buildConfigField("boolean", "REMOTE_API_CONFIGURED", "${releaseApiBaseUrl.isNotBlank()}")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Overridable from ~/.gradle/gradle.properties or -PchefaiApiBaseUrl=...
            // Must be https: the release build permits no cleartext.
            buildConfigField("String", "API_BASE_URL", "\"${releaseApiBaseUrl}\"")
            // Release builds show the plain version name; only debug builds carry the commit.
            buildConfigField("String", "GIT_COMMIT_HASH", "\"\"")
        }
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            // 10.0.2.2 is the host loopback as seen from the Android emulator.
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080\"")
            buildConfigField("String", "GIT_COMMIT_HASH", "\"${gitCommitHash}\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/INDEX.LIST"
        }
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.testLogging {
                events(TestLogEvent.FAILED, TestLogEvent.STANDARD_ERROR)
                exceptionFormat = TestExceptionFormat.FULL
                showExceptions = true
                showCauses = true
                showStackTraces = true
                showStandardStreams = true
            }
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

// MigrationTestHelper reads the exported schemas off the device, so they have to ship as test assets.
android.sourceSets.getByName("androidTest") {
    assets.srcDir("$projectDir/schemas")
}

hilt {
    enableAggregatingTask = true
}

dependencies {

    // Recipe URL scraping (pure Kotlin, no Android deps)
    implementation(project(":recipe-scraper"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.core)
    implementation(libs.material)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // Jetpack Compose ===
    val composeBom = platform("androidx.compose:compose-bom:${libs.versions.androidxComposeBom.get()}")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    // Material Design 3
    implementation(libs.androidx.material3)
    implementation(libs.androidx.ui.text.google.fonts)

    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    // Optional - Integration with activities
    implementation(libs.androidx.activity.compose)
    // Optional - Integration with ViewModels
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // Android Studio Preview support
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material.icons.extended)
    // Optional - Add window size utils
    implementation(libs.androidx.adaptive)
    // Compose Navigation
    implementation(libs.androidx.navigation.compose)

    //UUID v7 gen
    implementation(libs.block.uuidv7)


    debugImplementation(libs.androidx.ui.tooling)

    // Hilt DI
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.android.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Hilt + WorkManager integration
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // DataStore for encrypted storage (replaces deprecated EncryptedSharedPreferences)
    implementation(libs.androidx.datastore.preferences)

    // Room Database
    implementation(libs.androidx.room.runtime)
    // If this project uses any Kotlin source, use Kotlin Symbol Processing (KSP)
    ksp(libs.androidx.room.compiler)
    // If this project only uses Java source, use the Java annotationProcessor
    // No additional plugins are necessary
    annotationProcessor(libs.androidx.room.compiler)
    // optional - Kotlin Extensions and Coroutines support for Room
    implementation(libs.androidx.room.ktx)
    // optional - Guava support for Room, including Optional and ListenableFuture
    implementation(libs.androidx.room.guava)
    // optional - Test helpers
    testImplementation(libs.androidx.room.testing)

    // Coil Image Loader
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    testImplementation(libs.coil.test)

    // Networking Client
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json) // latest for 2.x Kotlin


    // Login for Ktor requests
    implementation(libs.logback.core)
    implementation(libs.slf4j.android)
    implementation(libs.ktor.client.logging)

    implementation(libs.timber)


    // Dependencies for local unit tests ====
    testImplementation(libs.junit)
    testImplementation(libs.androidx.archcore.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    
    // Testing
    testImplementation(libs.google.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.hilt.android.testing)
    testImplementation(libs.mockk)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.robolectric)
    kspTest(libs.hilt.android.compiler)


    // Dependencies for Android tests ====
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // MigrationTestHelper needs a real SQLite, so the Room test artifact is needed on device too.
    androidTestImplementation(libs.androidx.room.testing)
    
    // Compose UI testing
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
    
    // Hilt testing for androidTest
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.android.compiler)
}
