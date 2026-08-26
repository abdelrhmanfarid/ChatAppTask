import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}

fun configurationValue(name: String): String =
    localProperties.getProperty(name)
        ?.takeIf(String::isNotBlank)
        ?: providers.environmentVariable(name).orNull.orEmpty()

fun String.toBuildConfigString(): String =
    "\"" +
        replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r") +
        "\""

val supabaseUrl = configurationValue("SUPABASE_URL")
val supabaseAnonKey = configurationValue("SUPABASE_ANON_KEY")
val supabasePublishableKey = configurationValue("SUPABASE_PUBLISHABLE_KEY")

android {
    namespace = "com.example.chatapptask.core.network"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 26

        buildConfigField("String", "SUPABASE_URL", supabaseUrl.toBuildConfigString())
        buildConfigField("String", "SUPABASE_ANON_KEY", supabaseAnonKey.toBuildConfigString())
        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            supabasePublishableKey.toBuildConfigString(),
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:domain"))

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.functions)
    implementation(libs.ktor.client.okhttp)
    debugImplementation(libs.ktor.client.logging)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    ksp(libs.kotlin.metadata.jvm)
}
