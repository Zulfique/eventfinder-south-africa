import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// Read the FREE Ticketmaster Discovery API key from (in order of precedence):
//  1. the TICKETMASTER_API_KEY environment variable (used by GitHub Actions),
//  2. local.properties (developer machine, git-ignored),
//  3. a Gradle -P property.
// There is deliberately NO default: when the key is absent the app runs in
// offline / demo mode using the local Room copy of the national event directory.
val localPropertiesFile = rootProject.file("local.properties")
val localProperties = Properties().apply {
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}
val ticketmasterApiKey: String = listOfNotNull(
    System.getenv("TICKETMASTER_API_KEY"),
    localProperties.getProperty("TICKETMASTER_API_KEY"),
    project.findProperty("TICKETMASTER_API_KEY") as? String
).firstOrNull { it.isNotBlank() }?.trim().orEmpty()

android {
    namespace = "com.eventfinder.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.eventfinder.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Exposes the free API key to the app code as a BuildConfig constant.
        // Kept empty when no key has been configured by the developer.
        buildConfigField("String", "TICKETMASTER_API_KEY", "\"$ticketmasterApiKey\"")
    }

    buildTypes {
        release {
            // Prototype: shrinking disabled for simpler debugging on physical devices.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
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
    // Compose compiler 1.5.8 is paired with Kotlin 1.9.22 (see official compatibility map).
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        // Android framework stubs (e.g. android.util.Log) are no-op in local unit tests
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // ---- Jetpack Compose (BOM keeps UI library versions in sync) ----
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // ---- AndroidX core & lifecycle ----
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // ---- Persistence: DataStore (preferences) + Room (offline cache) ----
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // ---- Security SDK: AndroidX Biometric (fingerprint / face unlock) ----
    implementation("androidx.biometric:biometric:1.1.0")

    // FragmentActivity host required by the BiometricPrompt SDK.
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    // ---- Networking: Retrofit + OkHttp for the free REST APIs ----
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // ---- osmdroid: the free OpenStreetMap SDK (100% royalty-free mapping) ----
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // ---- Image loading ----
    implementation("io.coil-kt:coil-compose:2.6.0")

    // ---- Unit testing (run on JVM / GitHub Actions) ----
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    // ---- Instrumented testing ----
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // ---- Compose tooling ----
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}