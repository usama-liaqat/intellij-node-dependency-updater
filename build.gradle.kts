plugins {
    id("java")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intellijPlatform)
    alias(libs.plugins.changelog)
    alias(libs.plugins.qodana)
}

group = "io.github.osamaliaqat.nodedependencyupdater"
// CI sets PLUGIN_VERSION from the git tag (e.g. tag "v0.1.0-beta.2" → "0.1.0-beta.2").
// Local builds without the env var fall back to "0.1.0" — no suffix means the channel-from-suffix
// logic below resolves cleanly to "default" instead of inventing a fake "dev" channel.
// `ifEmpty` covers both unset AND empty-string cases so a misfired CI step can't ship empty.
version = providers.environmentVariable("PLUGIN_VERSION").orElse("").get()
    .removePrefix("v")
    .ifEmpty { "0.1.0" }

// Set the JVM language level used to build the project.
kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        webstorm(providers.gradleProperty("platformVersion"))
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        // Add plugin dependencies for compilation here:
        bundledPlugin("JavaScript")
    }
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
}

intellijPlatform {
    pluginConfiguration {
        // Listing identity is derived from PLUGIN_VERSION (= the git tag):
        //   tag "v0.1.0"        (no suffix)  → STABLE listing  ("Node Dependency Updater")
        //   tag "v0.1.0-beta.2" (any suffix) → BETA   listing  ("Node Dependency Updater Beta")
        // The same tag suffix also drives the Marketplace channel below, so all pre-release
        // flavours (beta/rc/eap/canary) share one beta listing with sub-channels.
        // Strings are inlined inside the lambdas so the providers are configuration-cache safe.
        id = providers.environmentVariable("PLUGIN_VERSION").orElse("").map { raw ->
            val v = raw.removePrefix("v").ifEmpty { "0.1.0" }
            "io.github.osamaliaqat.nodedependencyupdater" + if (v.contains('-')) ".beta" else ""
        }
        name = providers.environmentVariable("PLUGIN_VERSION").orElse("").map { raw ->
            val v = raw.removePrefix("v").ifEmpty { "0.1.0" }
            "Node Dependency Updater" + if (v.contains('-')) " Beta" else ""
        }

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }

        // Single source of truth: render the matching CHANGELOG.md section into <change-notes>.
        changeNotes = providers.provider {
            with(changelog) {
                renderItem(
                    (getOrNull(project.version.toString()) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    org.jetbrains.changelog.Changelog.OutputType.HTML,
                )
            }
        }
    }

    // Marketplace publishing — reads PUBLISH_TOKEN from the environment (used by the release workflow).
    publishing {
        token.set(providers.environmentVariable("PUBLISH_TOKEN"))
        // Channel is derived from the git tag's pre-release suffix (PLUGIN_VERSION reflects the tag):
        //   tag "v0.1.0-beta"     → ["beta"]    (or "v0.1.0-beta.2", same channel)
        //   tag "v0.1.0-rc"       → ["rc"]
        //   tag "v0.1.0-eap.3"    → ["eap"]
        //   tag "v0.1.0-anything" → ["anything"] (any suffix works — pick whatever channel name you like)
        //   tag "v0.1.0"           → ["default"] (visible to everyone on the listing's main channel)
        // Local builds without PLUGIN_VERSION set fall back to "default" too.
        channels.set(providers.environmentVariable("PLUGIN_VERSION").orElse("").map { raw ->
            val v = raw.removePrefix("v")
            val suffix = v.substringAfter('-', "").substringBefore('.')
            listOf(suffix.ifEmpty { "default" })
        })
    }
}

changelog {
    version.set(project.version.toString())
    groups.set(emptyList())
    repositoryUrl.set("https://github.com/osamaliaqat/intellij-node-dependency-updater")
}

qodana {
    cachePath.set(file(".qodana").canonicalPath)
}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }

    // Diagnostic helper: `./gradlew showRelease` prints what publishPlugin would actually use.
    // Useful when validating the "tag → version + listing + channel" wiring before pushing a tag.
    register("showRelease") {
        group = "verification"
        description = "Print the version, listing, and channel that publishPlugin would use right now."
        doLast {
            val envRaw = System.getenv("PLUGIN_VERSION")
            val rawEnv = if (envRaw.isNullOrEmpty()) "(unset/empty, falls back to 0.1.0)" else envRaw
            val version = (envRaw ?: "").removePrefix("v").ifEmpty { "0.1.0" }
            val isPreRelease = version.contains('-')
            val suffix = version.substringAfter('-', "").substringBefore('.')
            val channel = suffix.ifEmpty { "default" }
            val pluginId = "io.github.osamaliaqat.nodedependencyupdater" + if (isPreRelease) ".beta" else ""
            val pluginName = "Node Dependency Updater" + if (isPreRelease) " Beta" else ""
            val listing = if (isPreRelease) "BETA listing (separate Marketplace entry)" else "STABLE listing"
            println("PLUGIN_VERSION env : $rawEnv")
            println("→ version          : $version")
            println("→ listing          : $listing")
            println("→ plugin id        : $pluginId")
            println("→ plugin name      : $pluginName")
            println("→ channel          : $channel")
        }
    }
}
