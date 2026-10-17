plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.nara.world"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.nara.world"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

// Keep the repository's existing index.html as the single source of truth.
// The APK packages it as a local asset, so the app does not depend on Chrome or hosting.
tasks.register<org.gradle.api.tasks.Copy>("syncWebApp") {
    from(rootProject.file("index.html"))
    into(layout.projectDirectory.dir("src/main/assets"))
    rename { "index.html" }
}
tasks.named("preBuild").configure {
    dependsOn("syncWebApp")
}
