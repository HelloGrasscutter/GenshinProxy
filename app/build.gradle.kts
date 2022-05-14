plugins {
    id("com.android.application")
    id("kotlin-android")
    id("top.niunaijun.blackobfuscator")
//    id("icu.nullptr.stringfuck") version "0.2.1"
}

android {
    compileSdk = 32

    defaultConfig {
        applicationId = "xfk233.genshinproxy"
        minSdk = 28
        targetSdk = 32
        versionCode = 5
        versionName = "1.5"
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
}

BlackObfuscator {
    isEnabled = true
    depth = 3
    setObfClass("xfk233.genshinproxy")
}

//stringFuck {
//    setKey("xfk233")
//    isPrintDebugInfo = false
//    isWorkOnDebug = true
//    isWhiteList = false
//    obfuscationList = setOf("xfk233.genshinproxy")
//}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
    compileOnly("de.robv.android.xposed:api:82:sources")
    implementation("com.github.kyuubiran:EzXHelper:0.9.2")
    implementation("icu.nullptr.stringfuck:library:0.2.1")
}