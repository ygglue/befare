import com.android.build.api.variant.ApplicationAndroidComponentsExtension

plugins {
    id("com.android.application")
    kotlin("plugin.compose")
}

android {
    namespace = "app.befare.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.befare.android"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        // The tiles and graph are read as files after install; keep them uncompressed in the APK.
        noCompress += listOf("pmtiles", "bin", "pbf")
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")

    implementation("org.maplibre.gl:android-sdk:13.7.1")
}

// ---- Offline data ------------------------------------------------------------------------------
// The same bundle the iOS app ships: tiles, style, fonts, sprites, fade mask, road graph, fares and
// the fare matrix photo. Built by scripts/build_mvp.sh into data/build/.

val offlineAssetsDir: File = layout.buildDirectory.dir("generated/offlineAssets").get().asFile.also { it.mkdirs() }

val prepareOfflineAssets = tasks.register<Sync>("prepareOfflineAssets") {
    val root = rootProject.layout.projectDirectory
    into(offlineAssetsDir.resolve("offline"))
    from(root.dir("data/build")) {
        include("graph.bin", "befare.pmtiles", "mask.geojson", "coverage-smooth.geojson", "style/**")
    }
    from(root.file("data/fares/fares.json"))
    from(root.file("docs/sources/pp-city-tricycle-ord1185-2021.jpg"))
}

extensions.getByType<ApplicationAndroidComponentsExtension>().onVariants { variant ->
    variant.sources.assets?.addStaticSourceDirectory(offlineAssetsDir.path)
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(prepareOfflineAssets)
}
