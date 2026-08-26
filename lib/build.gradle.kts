plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    `maven-publish`
}

// Bumped by hand, one minor step per change, the same rule the apps follow. The publish
// workflow reads this to tag the release, so it is the single source of truth for the version.
val libraryVersion = "1.4.1"

android {
    namespace = "com.gios.light.common"
    compileSdk = 35

    defaultConfig {
        // Has to be the floor of every consumer, not the ceiling: LightPass, LightTip and
        // most of the others are minSdk 29, so anything higher here would refuse to link.
        minSdk = 29
        consumerProguardFiles("consumer-rules.pro")
        // Exposed as LightCommon.VERSION. Generated rather than written down a second time:
        // a hand-copied constant still compiles when it goes stale, and the value's only job
        // is to be true on a diagnostic screen.
        buildConfigField("String", "LIGHT_COMMON_VERSION", "\"$libraryVersion\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }

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

// Publication wiring sits inside a top-level afterEvaluate: the "release" software component
// is created by the Android plugin during its own afterEvaluate, so resolving it any earlier
// finds nothing and the publish task fails with "SoftwareComponent with name 'release' not found".
afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                groupId = "com.gios"
                artifactId = "light-common"
                version = libraryVersion
                from(components["release"])
                pom {
                    name.set("light-common")
                    description.set(
                        "Shared LightOS app plumbing: shake-to-report, hardware keys, " +
                            "the wheel, and the type/colour basics.",
                    )
                    url.set("https://github.com/gi-os/light-common")
                }
            }
        }
        repositories {
            maven {
                name = "GitHubPackages"
                // gi-os/BrightCommon, not gi-os/light-common. The repo was renamed and GitHub
                // Packages does not follow a rename on the way *in*: reads redirect fine, so
                // every consumer kept resolving and nothing looked wrong, but the PUT 404s. That
                // is what happened to 1.2.3's first attempt. The artifact coordinates stay
                // `com.gios:light-common` on purpose -- renaming those would break every
                // consumer's dependency line to fix a URL only this file uses.
                url = uri("https://maven.pkg.github.com/gi-os/BrightCommon")
                credentials {
                    username = System.getenv("GITHUB_ACTOR")
                        ?: providers.gradleProperty("gpr.user").orNull
                    password = System.getenv("GITHUB_TOKEN")
                        ?: providers.gradleProperty("gpr.key").orNull
                }
            }
        }
    }
}
