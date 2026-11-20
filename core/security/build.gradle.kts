plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "de.kopffrei.core.security"
}

dependencies {
    api(libs.androidx.biometric)
    // BiometricPrompt braucht eine FragmentActivity (MainActivity erbt davon).
    api(libs.androidx.fragment)
    implementation(libs.androidx.core.ktx)
}
