plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)

    id("com.gladed.androidgitversion")
    id("com.google.devtools.ksp")
}


android {
    compileSdk = 35
    flavorDimensions += "main"
    namespace = "nl.mpcjanssen.simpletask"

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE.txt",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/NOTICE.txt",
                "LICENSE.txt"
            )
        }
    }

    defaultConfig {
        versionCode = 2
        versionName = "1.0.1"
        buildConfigField("String", "GIT_VERSION", "\"" + androidGitVersion.name() + "\"")

        // minSdk = 23
        minSdk = 29
        // targetSdk = 35
        targetSdk = 34

        applicationId = "willemw12.simpletask"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    productFlavors {
        create("cloudless") {
            dimension = "main"
            applicationId = "willemw12.simpletask"
            manifestPlaceholders["providerFlavour"] = "cloudless"
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("D:/GitHub_Repos/KeyStore for Releases/Simpletask_Release.jks")
            storePassword = "fBefihcx4CtcF8oL!hkVz@w"
            keyAlias = "SimpletaskKey"
            keyPassword = "fBefihcx4CtcF8oL!hkVz@w"
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug (Simpletask fork)"
            // manifestPlaceholders["providerBuildType"] = "debug"
        }
        release {
            applicationIdSuffix = ".release"
            versionNameSuffix = " (Simpletask fork)"
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
            // manifestPlaceholders["providerBuildType"] = "release"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // lint {
    //     disable += setOf("InvalidPackage", "MissingTranslation", "ResourceType")
    // }
}

dependencies {
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    implementation("com.google.api-client:google-api-client-android:1.35.0")
    implementation("com.google.api-client:google-api-client-gson:1.35.0")
    implementation("com.google.http-client:google-http-client-gson:1.43.3")
    implementation(files("libs/google-api-services-drive-v3-rev197-1.25.0.jar"))
    // Optional: Use the Play Services Drive API for easier Drive file operations on Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.recyclerview)
    implementation(libs.commonmark)
    implementation(libs.hirondelle.date4j)
    implementation(libs.kotlin.stdlib)
    implementation(libs.luaj.jse)
    implementation(libs.material)

    annotationProcessor(libs.androidx.room.compiler)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.runtime)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    // androidTestImplementation(libs.androidx.espresso.core)
}

allprojects {
    afterEvaluate {
        tasks.withType<JavaCompile> {
            options.compilerArgs.addAll(listOf("-Xlint:unchecked", "-Xlint:deprecation"))
        }
    }
}
