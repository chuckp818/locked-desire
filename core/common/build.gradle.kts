// Reines Kotlin/JVM-Modul: keine Android-Abhängigkeiten, daher schnell und lokal testbar.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    testImplementation(libs.junit)
}
