plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.reporove"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.reporove"
        minSdk = 29
        targetSdk = 36
        versionCode = 5
        versionName = "0.4.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val clientId = providers.gradleProperty("githubOAuthClientId").orElse("").get()
        require(clientId.matches(Regex("[A-Za-z0-9_]*"))) { "Invalid OAuth client ID" }
        buildConfigField("String", "GITHUB_OAUTH_CLIENT_ID", "\"$clientId\"")
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    lint { abortOnError = true }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.navigation)
    implementation(libs.androidx.datastore)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation("org.jsoup:jsoup:1.21.2")
    implementation("org.commonmark:commonmark:0.24.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.24.0")
    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.24.0")
    debugImplementation(libs.compose.tooling)
    debugImplementation(libs.compose.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockwebserver)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.compose.ui.test)
    androidTestImplementation(libs.mockwebserver)
}
