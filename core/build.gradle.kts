import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin: the SMS parser, merchant registry, categorizer, import filters and budget maths.
// No Android imports, so these tests run in milliseconds on any JVM.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.junit)
}
