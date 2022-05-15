plugins {
    id("com.android.application")
    id("kotlin-android")
    id("top.niunaijun.blackobfuscator")
}

android {
    compileSdk = 32

    defaultConfig {
        applicationId = "xfk233.genshinproxy"
        minSdk = 28
        targetSdk = 32
        versionCode = 7
        versionName = "1.7"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            setProguardFiles(listOf(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro", "proguard-log.pro"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.majorVersion
    }
    packagingOptions {
        resources {
            excludes += "/META-INF/**"
            excludes += "/kotlin/**"
            excludes += "/*.txt"
            excludes += "/*.bin"
        }
        dex {
            useLegacyPackaging = true
        }
    }
    applicationVariants.all {
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "GenshinProxy-$name.apk"
        }
    }
}

BlackObfuscator {
    isEnabled = true
    depth = 3
    setObfClass("xfk233.genshinproxy")
}

fun getKey(project: Project): String {
    val keyFile = File(project.rootProject.projectDir, "genshin.jks")
    if (keyFile.exists() && keyFile.canRead()) {
        return keyFile.readText()
    }
    println("Key not found!")
    return "xfk2333"
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
    compileOnly("de.robv.android.xposed:api:82:sources")
    implementation("com.github.kyuubiran:EzXHelper:0.9.2")
}
