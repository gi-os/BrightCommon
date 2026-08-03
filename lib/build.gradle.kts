plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    `maven-publish`
}

// Bumped by hand, one minor step per change, the same rule the apps follow. The publish
// workflow reads this to tag the release, so it is the single source of truth for the version.
val libraryVersion = "1.0.0"

android {
    namespace = "com.gios.light.common"
    compileSdk = 35

    defaultConfig {
        // Has to be the floor of every consumer, not the ceiling: LightPass, LightTip and
        // most of the others are minSdk 29, so anything higher here would refuse to link.
        minSdk = 29
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    // `api`, not `implementation`: consumers write Compose against these types, and the
    // versions they resolve must be the ones they already use — the BOM above is a floor,
    // not a pin, so an app on a newer BOM keeps it.
    api("androidx.compose.ui:ui")
    api("androidx.compose.foundation:foundation")
    api("androidx.compose.material3:material3")
    api("androidx.compose.runtime:runtime")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.gios"
            artifactId = "light-common"
            version = libraryVersion
            afterEvaluate { from(components["release"]) }
            pom {
                name.set("light-common")
                description.set("Shared LightOS app plumbing: shake-to-report, hardware keys, the wheel, and the type/colour basics.")
                url.set("https://github.com/gi-os/light-common")
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/gi-os/light-common")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                    ?: providers.gradleProperty("gpr.user").orNull
                password = System.getenv("GITHUB_TOKEN")
                    ?: providers.gradleProperty("gpr.key").orNull
            }
        }
    }
}
