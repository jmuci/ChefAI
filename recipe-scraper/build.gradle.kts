import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

// Pure Kotlin recipe extraction library. Deliberately free of Android, Hilt, Room and Ktor
// dependencies: it takes an HTML string and returns a structured recipe, doing no network I/O.
// Only a jvm() target is declared today; the code all lives in commonMain so additional KMP
// targets (iOS, JS) can be added without moving files.
kotlin {
    // Targets JVM 11 bytecode to match :app, without requiring a JDK 11 toolchain be installed.
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.ksoup)
            // Used for its untyped JsonElement API only — schema.org shapes are too
            // polymorphic to model with @Serializable classes, so no plugin is needed.
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
