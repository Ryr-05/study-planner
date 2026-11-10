import groovy.json.JsonSlurper
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// CI passes these in so every build gets a higher version number,
// which lets a new APK install over the old one on your phone.
val ciVersionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
val ciVersionName = System.getenv("VERSION_NAME") ?: "0.1.0-local"
val keystorePath: String? = System.getenv("KEYSTORE_PATH")

/*
 * Firebase settings come from google-services.json, which CI writes from a GitHub secret.
 * It's read here directly (instead of the Google Services plugin) so builds without the file,
 * like pull requests, still work. They just ship without cloud sync.
 */
data class FirebaseValues(val appId: String, val apiKey: String, val projectId: String, val projectNumber: String, val bucket: String, val webClientId: String)

val firebaseValues: FirebaseValues? = file("google-services.json").takeIf { it.exists() }?.let { f ->
    @Suppress("UNCHECKED_CAST")
    val json = JsonSlurper().parse(f) as Map<String, Any?>
    val project = json["project_info"] as Map<String, Any?>
    @Suppress("UNCHECKED_CAST")
    val client = (json["client"] as List<Map<String, Any?>>).first {
        val info = it["client_info"] as Map<String, Any?>
        (info["android_client_info"] as Map<String, Any?>)["package_name"] == "io.github.ryr05.noctil"
    }
    @Suppress("UNCHECKED_CAST")
    val oauth = client["oauth_client"] as List<Map<String, Any?>>
    @Suppress("UNCHECKED_CAST")
    FirebaseValues(
        appId = (client["client_info"] as Map<String, Any?>)["mobilesdk_app_id"] as String,
        apiKey = ((client["api_key"] as List<Map<String, Any?>>).first())["current_key"] as String,
        projectId = project["project_id"] as String,
        projectNumber = project["project_number"] as String,
        bucket = (project["storage_bucket"] as String?) ?: "",
        webClientId = oauth.first { (it["client_type"] as Number).toInt() == 3 }["client_id"] as String,
    )
}

android {
    namespace = "io.github.ryr05.noctil"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.ryr05.noctil"
        minSdk = 26
        targetSdk = 36
        versionCode = ciVersionCode
        versionName = ciVersionName

        buildConfigField("boolean", "CLOUD_SYNC", (firebaseValues != null).toString())
        firebaseValues?.let {
            // The same resource names the Google Services plugin would create, so Firebase starts on its own.
            resValue("string", "google_app_id", it.appId)
            resValue("string", "google_api_key", it.apiKey)
            resValue("string", "google_crash_reporting_api_key", it.apiKey)
            resValue("string", "project_id", it.projectId)
            resValue("string", "gcm_defaultSenderId", it.projectNumber)
            resValue("string", "google_storage_bucket", it.bucket)
            resValue("string", "default_web_client_id", it.webClientId)
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storeType = "pkcs12"
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS") ?: "studyplanner"
                keyPassword = System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Print a one-line summary of the unit tests in the CI log.
tasks.withType<Test>().configureEach {
    testLogging { events("failed") }
    addTestListener(object : TestListener {
        override fun beforeSuite(suite: TestDescriptor) {}
        override fun beforeTest(testDescriptor: TestDescriptor) {}
        override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {}
        override fun afterSuite(suite: TestDescriptor, result: TestResult) {
            if (suite.parent == null) {
                println("Unit tests: ${result.testCount} run, ${result.successfulTestCount} passed, ${result.failedTestCount} failed")
            }
        }
    })
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.haze)

    implementation(libs.glance.appwidget)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.credentials)
    implementation(libs.credentials.play)
    implementation(libs.googleid)
    implementation(libs.coroutines.play)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
}
