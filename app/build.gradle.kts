plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("io.github.takahirom.roborazzi")
}

// Version driven by RELEASE_VERSION env var in CI (set from git tag); falls back to defaults locally.
val releaseVersion: String? = System.getenv("RELEASE_VERSION")
val computedVersionCode = releaseVersion
    ?.split(".")?.mapNotNull { it.toIntOrNull() }
    ?.takeIf { it.size == 3 }
    ?.let { (ma, mi, pa) -> ma * 10000 + mi * 100 + pa }
    ?: 1

android {
    namespace = "us.bergnet.oversight"
    compileSdk = 37

    defaultConfig {
        applicationId = "us.bergnet.oversight"
        minSdk = 26
        targetSdk = 34
        versionCode = computedVersionCode
        versionName = releaseVersion ?: "1.0.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("SIGNING_KEYSTORE_PATH") ?: "keystore-missing.jks")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: ""
            keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: ""
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (System.getenv("SIGNING_KEYSTORE_PATH") != null)
                signingConfigs.getByName("release")
            else
                signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE.md",
                "META-INF/io.netty.versions.properties"
            )
        }
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")

    // Ktor Server
    implementation("io.ktor:ktor-server-netty:3.6.0") {
        // Ktor 3.6+ pulls in HTTP/3 with desktop-only QUIC native jars (linux/osx/windows)
        exclude(group = "io.netty", module = "netty-codec-native-quic")
    }
    implementation("io.ktor:ktor-server-content-negotiation:3.6.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.6.0")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")

    // Icons (MDI via mikepenz Iconics)
    implementation("com.mikepenz:iconics-core:5.6.0")
    implementation("com.mikepenz:community-material-typeface:7.0.96.2-kotlin@aar")

    // Images (Coil)
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")

    // Video (Media3/ExoPlayer)
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.11.1")
    implementation("androidx.media3:media3-exoplayer-rtsp:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.3.0")

    // Core
    implementation("androidx.core:core-ktx:1.19.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // UI snapshot rendering on the JVM (Robolectric + Roborazzi): ./gradlew recordRoborazziDebug
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.77.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.77.0")
}
