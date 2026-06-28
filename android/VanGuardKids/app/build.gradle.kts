plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "br.unicamp.iot.vanguardkids"
    compileSdk = 37

    buildFeatures {
        aidl = true
        buildConfig = true
    }

    defaultConfig {
        applicationId = "br.unicamp.iot.vanguardkids"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "MQTT_USERNAME",
            "\"vanguard_kids\""
        )

        buildConfigField(
            "String",
            "MQTT_PASSWORD",
            "\"vanguard_kids_pw\""
        )
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.identity.jvm)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    /* import dependency mqtt */
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
}