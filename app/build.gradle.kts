import com.android.build.gradle.internal.api.ApkVariantOutputImpl
import org.gradle.security.internal.gnupg.GnupgSignatoryFactory

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin)
    publishing
}

android {
    namespace = "dev.jkcarino.revanced.integrations"
    compileSdk = 33

    applicationVariants.all {
        outputs.all {
            this as ApkVariantOutputImpl

            outputFileName = "${rootProject.name}-$versionName.apk"
        }
    }

    defaultConfig {
        applicationId = "dev.jkcarino.revanced.integrations"
        minSdk = 23
        targetSdk = 33
        multiDexEnabled = false
        versionName = version as String
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }
}

dependencies {
    compileOnly(project(":stub"))
}

tasks {
    // Because the signing plugin doesn't support signing APKs, do it manually.
    register("sign") {
        group = "signing"

        dependsOn(build)

        doLast {
            val outputDirectory = layout.buildDirectory.dir("outputs/apk/release").get().asFile
            val integrationsApk = outputDirectory.resolve("${rootProject.name}-$version.apk")

            GnupgSignatoryFactory().createSignatory(project)
                .sign(
                    integrationsApk.inputStream(),
                    outputDirectory.resolve("${integrationsApk.name}.asc").outputStream(),
                )
        }
    }

    // Needed by gradle-semantic-release-plugin.
    // Tracking: https://github.com/KengoTODA/gradle-semantic-release-plugin/issues/435
    publish {
        dependsOn(build)
        dependsOn("sign")
    }
}
