import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

// Release signing is loaded from keystore.properties (gitignored) so the
// signing secrets never enter version control. Falls back to debug signing
// when the file is absent (e.g. fresh clone / CI).
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

// Local overrides (gitignored): GOOGLE_SERVER_CLIENT_ID, EVENTFINDER_API_URL, …
// Exposed as project properties so project.findProperty(...) below resolves them.
val localPropertiesFile = rootProject.file("local.properties")
val localProperties = Properties().apply {
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}
localProperties.forEach { (key, value) -> extra[key.toString()] = value }

android {
    namespace = "com.eventfinder.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.eventfinder.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Base URL of the ASP.NET Core backend. Release builds point at the
        // Render deployment; the debug build type overrides this with the
        // emulator loopback address so `dotnet run` on the host works.
        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"${project.findProperty("EVENTFINDER_API_URL") ?: "https://eventfinder-api-o1t1.onrender.com/"}\""
        )
        // OAuth web client ID for Credential Manager Google sign-in (public value).
        buildConfigField(
            "String",
            "GOOGLE_SERVER_CLIENT_ID",
            "\"${project.findProperty("GOOGLE_SERVER_CLIENT_ID") ?: ""}\""
        )
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Local API on the host machine, reached from the emulator via 10.0.2.2.
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"${project.findProperty("EVENTFINDER_API_DEBUG_URL") ?: "https://eventfinder-api-o1t1.onrender.com/"}\""
            )
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
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

    // ---- Credential Manager: Google sign-in via a verified ID token ----
    implementation("androidx.credentials:credentials:1.2.2")
    implementation("androidx.credentials:credentials-play-services-auth:1.2.2")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.0.1")
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // FragmentActivity host required by the BiometricPrompt SDK.
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    // ---- Networking: Retrofit + OkHttp for the free REST APIs ----
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // Task<T>.await() for Firebase Auth / Firestore suspend bridges
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // ---- osmdroid: the free OpenStreetMap SDK (100% royalty-free mapping) ----
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // ---- Image loading ----
    implementation("io.coil-kt:coil-compose:2.6.0")

    // ---- Firebase (free Spark plan): Firestore NoSQL, FCM push ----
    // The API key inside google-services.json is a public app identifier issued
    // by Google, not a secret. Both services below are free for this scale.
    // Blob storage is NOT Firebase: event photos live in Azure Blob Storage
    // and are uploaded through the REST API, so Firebase Storage - which would
    // require the paid Blaze plan - is not a dependency any more.
    implementation(platform("com.google.firebase:firebase-bom:32.7.4"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")

    // ---- Unit testing (run on JVM / GitHub Actions) ----
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("net.sf.kxml:kxml2:2.3.0")

    // ---- Instrumented testing ----
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    // ui-test-manifest provides the ComponentActivity used by createComposeRule()
    // on the instrumented test APK.
    androidTestImplementation("androidx.compose.ui:ui-test-manifest")

    // ---- Compose tooling ----
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}