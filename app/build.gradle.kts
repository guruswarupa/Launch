import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.google.ksp)
}

// Local dev: create keystore.properties (gitignored, never committed) with storeFile/
// storePassword/keyAlias/keyPassword. CI: the same four values come from KEYSTORE_PATH/
// KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD env vars instead (see .github/workflows/release.yml).
// Neither present (e.g. a contributor without the release key) just leaves the release build
// type unsigned rather than failing the build.
val keystoreProperties = Properties().apply {
    val propsFile = rootProject.file("keystore.properties")
    if (propsFile.exists()) {
        FileInputStream(propsFile).use { load(it) }
    }
}

fun signingProp(envVar: String, propertyKey: String): String? =
    System.getenv(envVar) ?: keystoreProperties.getProperty(propertyKey)

android {
    namespace = "com.guruswarupa.launch"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.guruswarupa.launch"
        minSdk = 26
        targetSdk = 36
        versionCode = 68
        versionName = "7.8"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val storeFilePath = signingProp("KEYSTORE_PATH", "storeFile")
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = signingProp("KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingProp("KEY_ALIAS", "keyAlias")
                keyPassword = signingProp("KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (signingConfigs.getByName("release").storeFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
    }

    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = false
        checkReleaseBuilds = false
    }
    
    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/*.kotlin_module",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "debug/**",
                "*.txt",
                "com/sun/jna/**",
                "org/checkerframework/**"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        viewBinding = true
    }

    androidResources {
        generateLocaleConfig = true
    }
}


gradle.projectsEvaluated {
    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.add("-Xlint:-processing")
    }
}

dependencies {
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.cardview)
    implementation(libs.material)
    implementation(libs.exp4j)
    implementation(libs.glide)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.play.billing)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation("com.google.mediapipe:tasks-genai:0.10.27")
    implementation("com.squareup.moshi:moshi:1.15.1")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.1")
    implementation("org.jsoup:jsoup:1.17.2")
}
