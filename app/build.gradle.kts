import java.util.Properties

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
val signingValues = Properties().apply {
    val config = rootProject.file(".signing/signing.properties")
    if (config.isFile) config.inputStream().use { load(it) }
}
val personalStore = System.getenv("RUNLINE_STORE_FILE") ?: signingValues.getProperty("storeFile")
val personalPassword = System.getenv("RUNLINE_SIGNING_PASSWORD") ?: signingValues.getProperty("storePassword")
val hasPersonalSigning = personalStore != null && personalPassword != null && rootProject.file(personalStore).isFile

android {
    namespace = "com.runline.app"
    compileSdk = 34
    defaultConfig { applicationId = "com.runline.app"; minSdk = 26; targetSdk = 34; versionCode = 3; versionName = "0.3.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    signingConfigs {
        if (hasPersonalSigning) create("personal") {
            storeFile = rootProject.file(personalStore!!)
            storePassword = personalPassword
            keyAlias = "runline"
            keyPassword = System.getenv("RUNLINE_SIGNING_PASSWORD") ?: signingValues.getProperty("keyPassword")
        }
    }
    buildTypes {
        debug { if (hasPersonalSigning) signingConfig = signingConfigs.getByName("personal") }
        release {
            isMinifyEnabled = false
            if (hasPersonalSigning) signingConfig = signingConfigs.getByName("personal")
        }
    }
}
dependencies {
    implementation("org.maplibre.gl:android-sdk:11.13.5")
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
