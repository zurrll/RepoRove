plugins { alias(libs.plugins.android.application) }

android {
    namespace = "app.reporove.smoke"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.reporove.smoke"
        minSdk = 29
        targetSdk = 36
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { abortOnError = true }
}
