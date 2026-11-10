import java.util.Properties

plugins { id("com.android.application") }

/** The open route repository (`routes/`) is packaged as assets: only the catalog and GPX files. */
abstract class RouteAssets : DefaultTask() {
    @get:InputDirectory abstract val source: DirectoryProperty
    @get:OutputDirectory abstract val output: DirectoryProperty
    @TaskAction fun copy() {
        val from = source.get().asFile; val to = File(output.get().asFile, "routes")
        output.get().asFile.deleteRecursively()
        from.walkTopDown().filter { it.isFile && (it.name == "index.json" || it.extension == "gpx") }.forEach { file ->
            file.copyTo(File(to, file.relativeTo(from).path), overwrite = true)
        }
    }
}
val routeAssets = tasks.register<RouteAssets>("routeAssets") { source.set(rootProject.layout.projectDirectory.dir("routes")) }
val signingValues = Properties().apply {
    val config = rootProject.file(".signing/signing.properties")
    if (config.isFile) config.inputStream().use { load(it) }
}
val personalStore = System.getenv("RUNLINE_STORE_FILE") ?: signingValues.getProperty("storeFile")
val personalPassword = System.getenv("RUNLINE_SIGNING_PASSWORD") ?: signingValues.getProperty("storePassword")
val hasPersonalSigning = personalStore != null && personalPassword != null && rootProject.file(personalStore).isFile

android {
    namespace = "com.runline.app"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.runline.app"; minSdk = 26; targetSdk = 34; versionCode = 26; versionName = "0.21.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
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
androidComponents { onVariants { variant -> variant.sources.assets?.addGeneratedSourceDirectory(routeAssets, RouteAssets::output) } }
dependencies {
    implementation("org.maplibre.gl:android-sdk:11.13.5")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
}
