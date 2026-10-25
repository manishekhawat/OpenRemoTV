plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("app.cash.paparazzi") version "1.3.5" apply false
}

/** version.properties, shared by both apps. */
val versions = java.util.Properties().apply { rootProject.file("version.properties").inputStream().use { load(it) } }
extra["versionName"] = versions.getProperty("versionName")
extra["versionCode"] = versions.getProperty("versionCode").toInt()
