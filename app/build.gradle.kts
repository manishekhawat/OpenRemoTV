plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    // Screenshots of the screens on a computer, no phone needed: ./gradlew :app:recordPaparazziDebug
    id("app.cash.paparazzi")
}

android {
    namespace = "com.openremotv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.openremotv"
        minSdk = 26
        targetSdk = 35
        versionCode = rootProject.extra["versionCode"] as Int
        versionName = rootProject.extra["versionName"] as String
    }

    signingConfigs {
        // OPENREMOTV_KEYSTORE and its passwords (see CONTRIBUTING.md); builds without them use the debug key.
        create("release") {
            System.getenv("OPENREMOTV_KEYSTORE")?.let { store ->
                storeFile = file(store)
                storePassword = System.getenv("OPENREMOTV_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("OPENREMOTV_KEY_ALIAS")
                keyPassword = System.getenv("OPENREMOTV_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(
                if (System.getenv("OPENREMOTV_KEYSTORE") != null) "release" else "debug"
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
    packaging {
        resources.excludes += setOf("META-INF/*.version", "META-INF/*.kotlin_module", "kotlin/**")
    }
    // The TV helper and the TV home screen are built from source into these folders, never committed.
    sourceSets["main"].assets.srcDirs(
        layout.buildDirectory.dir("generated/helper"),
        layout.buildDirectory.dir("generated/home"),
    )
}

/** keyserver/src/KeyServer.java → assets/keyserver.dex: javac against android.jar, then d8. */
val buildHelper by tasks.registering {
    val source = rootProject.file("keyserver/src/KeyServer.java")
    val out = layout.buildDirectory.dir("generated/helper")
    inputs.file(source)
    outputs.dir(out)
    doLast {
        val sdk = android.sdkDirectory
        val platform = File(sdk, "platforms/${android.compileSdkVersion}/android.jar")
        val d8 = File(sdk, "build-tools/${android.buildToolsVersion}/d8")
        val classes = temporaryDir.apply { deleteRecursively(); mkdirs() }
        val dir = out.get().asFile.apply { mkdirs() }
        providers.exec {
            commandLine("javac", "-source", "8", "-target", "8", "-nowarn", "-cp", platform, "-d", classes, source)
        }.result.get()
        providers.exec {
            commandLine(listOf(d8.path, "--min-api", "26", "--output", dir.path) +
                classes.listFiles { f -> f.name.endsWith(".class") }!!.map { it.path })
        }.result.get()
        File(dir, "classes.dex").renameTo(File(dir, "keyserver.dex"))
    }
}

/** The TV home screen's release APK → assets/openremotv-home.apk, which the app installs on the TV. */
val bundleHome by tasks.registering(Copy::class) {
    dependsOn(":homescreen:assembleRelease")
    from(project(":homescreen").layout.buildDirectory.file("outputs/apk/release/homescreen-release.apk"))
    into(layout.buildDirectory.dir("generated/home"))
    rename { "openremotv-home.apk" }
}

tasks.named("preBuild") { dependsOn(buildHelper, bundleHome) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // ADB over Wi-Fi to the TV (pairing by the TV's "Allow USB debugging?" prompt, shell, file push).
    implementation("dev.mobile:dadb:2.0.0")

    testImplementation("junit:junit:4.13.2")
}
