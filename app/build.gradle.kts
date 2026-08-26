import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.impl.VariantOutputImpl
import java.text.SimpleDateFormat
import java.time.ZonedDateTime
import java.util.Date

plugins {
    alias(libs.plugins.android.application)
}

fun getVersionName(): String? {
    return try {
        val df = SimpleDateFormat("yyyy.MM.dd")
        val date = Date.from(ZonedDateTime.now().toInstant())
        df.format(date)
    } catch (_: Exception) {
        null
    }
}

android {
    namespace = "ru.mark99.gk_heats"
    compileSdk = 37

    defaultConfig {
        applicationId = "ru.mark99.gk_heats"
        minSdk = 28
        //noinspection ExpiredTargetSdkVersion
        targetSdk = 28
        versionCode = 1
        versionName = "${getVersionName()}"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = false
        buildConfig = true
        aidl = false
    }
}

fun setAPKFileName(output: VariantOutputImpl, variant: ApplicationVariant) {
    val versionName = output.versionName.get()
    val buildType = variant.buildType?.substring(0, 1)?.uppercase()
    output.outputFileName = "GKHeats-${versionName}-${buildType}.apk"
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            if (output is VariantOutputImpl) {
                setAPKFileName(output, variant)
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.activity)
    implementation(libs.material)
    implementation(libs.okhttp)
}