plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.navigation.safeargs)
}

android {
    namespace = "com.example.racerstats"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.racerstats"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        manifestPlaceholders["MAPS_API_KEY"] = providers.gradleProperty("MAPS_API_KEY")
            .orElse(providers.environmentVariable("MAPS_API_KEY")).getOrElse("")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-Xjvm-default=all",
            "-Xno-param-assertions"
        )
    }
    
    buildFeatures {
        viewBinding = true
    }

    kapt {
        correctErrorTypes = true
        useBuildCache = true
        javacOptions {
            option("-source", "17")
            option("-target", "17")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    
    // Navigation
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    
    // ViewPager2 和 Fragment 支持
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.fragment.ktx)
    
    // Material Design 组件
    implementation(libs.material)
    implementation(libs.androidx.cardview)
    
    // ViewModel 和 LiveData
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    
    // Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    
    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)
    
    // Lifecycle
    implementation(libs.androidx.lifecycle.common)
    implementation(libs.androidx.lifecycle.process)
    
    // 位置服务
    implementation(libs.google.location)
    implementation(libs.google.maps)
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    
    // 测试依赖
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation("androidx.test:core-ktx:1.5.0")
}
