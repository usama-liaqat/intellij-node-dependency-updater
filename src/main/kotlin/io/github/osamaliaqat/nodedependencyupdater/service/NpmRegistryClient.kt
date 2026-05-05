package io.github.osamaliaqat.nodedependencyupdater.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.diagnostic.thisLogger
import java.io.File
import java.time.Instant

/** Fetches per-version publish times for an npm package via `npm view <pkg> time --json`. */
class NpmRegistryClient(private val workDir: File) {
    private val log = thisLogger()
    private val mapper = ObjectMapper()

    /** Returns version -> publish Instant, excluding the synthetic "created"/"modified" keys. Empty on failure. */
    fun versionTimes(packageName: String): Map<String, Instant> {
        val cmd = GeneralCommandLine("npm", "view", packageName, "time", "--json")
            .withWorkDirectory(workDir)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            .withCharset(Charsets.UTF_8)
        val output = try {
            CapturingProcessHandler(cmd.createProcess(), Charsets.UTF_8, cmd.commandLineString)
                .runProcess(30_000)
        } catch (e: Exception) {
            log.warn("npm view $packageName failed to start: ${e.message}")
            return emptyMap()
        }
        if (output.exitCode != 0 || output.stdout.isBlank()) {
            log.warn("npm view $packageName exit ${output.exitCode}: ${output.stderr.take(200)}")
            return emptyMap()
        }
        val tree = try {
            mapper.readTree(output.stdout.trim())
        } catch (_: Exception) {
            return emptyMap()
        }
        if (!tree.isObject) return emptyMap()
        val out = mutableMapOf<String, Instant>()
        tree.properties().forEach { (k, v) ->
            if (k == "created" || k == "modified") return@forEach
            try {
                out[k] = Instant.parse(v.asText())
            } catch (_: Exception) {
                // skip unparseable timestamps
            }
        }
        return out
    }
}
