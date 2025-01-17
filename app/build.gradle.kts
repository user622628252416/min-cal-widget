import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR
import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_OUT

plugins {
    id("com.android.application")
    id("kotlin-android")
    // https://github.com/detekt/detekt/releases
    id("io.gitlab.arturbosch.detekt").version("1.22.0")
}

android {

    namespace = "cat.mvmike.minimalcalendarwidget"

    // https://source.android.com/setup/start/build-numbers
    val minAndroidVersion = 26   // 8.0
    val androidVersion = 33      // 13.0

    // https://openjdk.java.net/projects/jdk/
    val javaVersion = JavaVersion.VERSION_17

    compileSdk = androidVersion
    // https://developer.android.com/studio/releases/build-tools
    buildToolsVersion = "30.0.3"

    defaultConfig {

        applicationId = namespace
        minSdk = minAndroidVersion
        targetSdk = androidVersion
        versionCode = 56
        versionName = "2.7.1"

        multiDexEnabled = true
    }

    compileOptions {
        compileOptions.encoding = Charsets.UTF_8.name()

        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    kotlinOptions {
        jvmTarget = javaVersion.toString()
    }

    sourceSets {
        sourceSets["main"].kotlin {
            srcDirs("src/main/kotlin")
        }
        sourceSets["main"].res {
            srcDirs(
                "src/main/res/layouts/common",
                "src/main/res/layouts/dark",
                "src/main/res/layouts/light",
                "src/main/res"
            )
        }

        sourceSets["test"].kotlin {
            srcDirs("src/test/kotlin")
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events(SKIPPED, FAILED, STANDARD_ERROR, STANDARD_OUT)
        }
    }

    detekt {
        config = files("${project.rootDir}/config/detekt/detekt.yml")
    }

    /*
     * To sign release builds, create the file gradle.properties in
     * ~/.gradle/ with this content:
     *
     * signingStoreFile=key.store
     * signingStorePassword=xxx
     * signingKeyAlias=alias
     * signingKeyPassword=xxx
     */
    val isKeyStoreDefined = project.hasProperty("signingStoreFile")
            && project.hasProperty("signingStorePassword")
            && project.hasProperty("signingKeyAlias")
            && project.hasProperty("signingKeyPassword")
    signingConfigs {
        create("release") {
            if (isKeyStoreDefined) {
                println("Found sign properties in gradle.properties! Signing build...")
                storeFile = file(properties["signingStoreFile"]!!)
                storePassword = properties["signingStorePassword"]!! as String
                keyAlias = properties["signingKeyAlias"]!! as String
                keyPassword = properties["signingKeyPassword"]!! as String
            }
        }
    }

    buildTypes {
        release {
            signingConfig = when {
                isKeyStoreDefined -> signingConfigs.getByName("release")
                else -> null
            }
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "${project.rootDir}/config/proguard/proguard-rules.pro")
        }
    }

    applicationVariants.all {
        if (this.buildType.name == "release") {
            outputs.all {
                val output = this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl
                output?.outputFileName = "min-cal-widget-v${defaultConfig.versionName}.apk"
            }
        }
    }
}

dependencies {

    // https://developer.android.com/jetpack/androidx/versions/
    implementation("androidx.appcompat:appcompat:1.6.0")
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.multidex:multidex:2.0.1")
    implementation("androidx.preference:preference-ktx:1.2.0")

    // https://github.com/junit-team/junit5/releases
    val junitJupiterVersion = "5.9.2"
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")

    // https://github.com/mockk/mockk/releases
    testImplementation("io.mockk:mockk:1.13.4")

    // https://github.com/assertj/assertj-core/tags
    testImplementation("org.assertj:assertj-core:3.24.2")

    // https://github.com/TNG/ArchUnit/releases
    testImplementation("com.tngtech.archunit:archunit-junit5:1.0.1")
}