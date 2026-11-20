plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "de.kopffrei.app"

    defaultConfig {
        applicationId = "de.kopffrei.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Hinweis: Ein Release braucht eine eigene Signatur (siehe README). Ohne Signatur lässt sich
            // die Release-APK nicht installieren; die CI baut deshalb die Debug-APK.
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:reminders"))
    implementation(project(":core:security"))

    implementation(project(":feature:dashboard"))
    implementation(project(":feature:notes"))
    implementation(project(":feature:journal"))
    implementation(project(":feature:calendar"))
    implementation(project(":feature:tasks"))
    implementation(project(":feature:focus"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:widgets"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
}
