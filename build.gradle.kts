plugins {
    id("java")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intellijPlatform)
}

group = "io.github.osamaliaqat.nodedependencyupdater"
version = "1.0.0"

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
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
        }

        changeNotes = """
            <h3>1.0.0</h3>
            <ul>
              <li>Initial release.</li>
              <li>Tool window listing outdated dependencies with current → latest versions and release dates.</li>
              <li>Per-package version picker, ignore checkbox, and minimum release-age filter.</li>
              <li>Optional post-update shell command (e.g. <code>yarn install &amp;&amp; pod install</code>).</li>
              <li>Auto-detected package manager — npm, yarn, pnpm, bun.</li>
            </ul>
        """.trimIndent()
    }
}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }
}
