package io.github.osamaliaqat.nodedependencyupdater.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.concurrency.AppExecutorUtil
import io.github.osamaliaqat.nodedependencyupdater.model.OutdatedDependency
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

class NpmService(private val rootDir: File) {
    private val log = thisLogger()
    private val mapper = ObjectMapper()
    private val reader = PackageJsonReader(rootDir)
    private val registry = NpmRegistryClient(rootDir)

    enum class PackageManager(val cliName: String) {
        NPM("npm"), YARN("yarn"), PNPM("pnpm"), BUN("bun")
    }

    class NpmInvocationException(message: String) : RuntimeException(message)

    /** Lockfile-based detection. Priority: bun > pnpm > yarn > npm. */
    fun detectPackageManager(): PackageManager = when {
        File(rootDir, "bun.lock").exists() || File(rootDir, "bun.lockb").exists() -> PackageManager.BUN
        File(rootDir, "pnpm-lock.yaml").exists() -> PackageManager.PNPM
        File(rootDir, "yarn.lock").exists() -> PackageManager.YARN
        else -> PackageManager.NPM
    }

    fun fetchOutdated(minReleaseAgeDays: Int = 0): List<OutdatedDependency> {
        val raw = fetchOutdatedRaw()
        return enrichAndFilter(raw, minReleaseAgeDays)
    }

    /** Single-dep registry enrichment for streaming loads. Returns null if filtered out by min-age. */
    fun enrichOne(dep: OutdatedDependency, minDays: Int): OutdatedDependency? {
        val cutoff = if (minDays > 0) Instant.now().minus(Duration.ofDays(minDays.toLong())) else null
        return processDep(dep, cutoff)
    }

    fun fetchOutdatedRaw(): List<OutdatedDependency> {
        val output = run(listOf("npm", "outdated", "--json"), timeoutMs = 120_000)
        val stdout = output.stdout.trim()

        // npm outdated exits 1 when there ARE outdated deps; that's normal.
        // Real failure: empty stdout AND non-zero exit.
        if (stdout.isEmpty()) {
            if (output.exitCode != 0) {
                throw NpmInvocationException(buildErrorMessage("npm outdated --json", output))
            }
            return emptyList() // genuinely up to date
        }

        val tree = try {
            mapper.readTree(stdout)
        } catch (e: Exception) {
            throw NpmInvocationException(
                "Could not parse npm output as JSON: ${e.message}\n\n" +
                    "First 500 chars of stdout:\n${stdout.take(500)}\n\n" +
                    "stderr:\n${output.stderr.take(500)}"
            )
        }
        return parse(tree)
    }

    private fun buildErrorMessage(cmd: String, output: ProcessOutput): String = buildString {
        append("`$cmd` failed in $rootDir (exit ${output.exitCode}).\n\n")
        if (output.stderr.isNotBlank()) {
            append("stderr:\n").append(output.stderr.takeLast(2000)).append("\n")
        }
        if (output.stdout.isNotBlank()) {
            append("stdout:\n").append(output.stdout.takeLast(2000)).append("\n")
        }
        append(
            "\nThis plugin uses `npm outdated --json` for its standardized output, even when " +
                "your project uses yarn / pnpm / bun. Make sure `npm` is installed and on the IDE's " +
                "PATH (`which npm` from a terminal should print a path). If you use nvm/asdf, " +
                "launch the IDE from a terminal so its environment is inherited."
        )
    }

    private fun parse(root: JsonNode): List<OutdatedDependency> {
        if (!root.isObject) return emptyList()
        val rootName = reader.rootName()
        val results = mutableListOf<OutdatedDependency>()
        val seen = mutableSetOf<String>()

        root.properties().forEach { (name, info) ->
            val entries = if (info.isArray) info.toList() else listOf(info)
            for (entry in entries) {
                val dependent = entry["dependent"]?.asText() ?: rootName
                if (!seen.add("$name@$dependent")) continue
                val workspace = if (dependent == rootName) null else dependent
                results += OutdatedDependency(
                    name = name,
                    current = entry["current"]?.asText() ?: "N/A",
                    wanted = entry["wanted"]?.asText() ?: "N/A",
                    latest = entry["latest"]?.asText() ?: "N/A",
                    workspace = workspace,
                    isDev = reader.isInSection(name, "devDependencies"),
                    isPeer = reader.isInSection(name, "peerDependencies"),
                    versionPrefix = reader.versionPrefix(name),
                )
            }
        }
        return results.sortedBy { it.name }
    }

    /**
     * Enriches each dep with current/latest release dates from the npm registry.
     * If [minDays] > 0, also replaces `latest` with the highest stable version older than the cutoff,
     * and drops deps where no qualifying version exists.
     */
    private fun enrichAndFilter(deps: List<OutdatedDependency>, minDays: Int): List<OutdatedDependency> {
        if (deps.isEmpty()) return deps
        val cutoff = if (minDays > 0) Instant.now().minus(Duration.ofDays(minDays.toLong())) else null
        val executor = AppExecutorUtil.getAppExecutorService()
        val futures = deps.map { dep ->
            executor.submit<OutdatedDependency?> { processDep(dep, cutoff) }
        }
        return futures.mapNotNull { future ->
            try {
                future.get(60, TimeUnit.SECONDS)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun processDep(dep: OutdatedDependency, cutoff: Instant?): OutdatedDependency? {
        val times = registry.versionTimes(dep.name)
        val currentDate = times[dep.current]

        // All stable versions > current, optionally filtered by min-age cutoff, sorted descending by semver.
        val candidates = times.entries
            .asSequence()
            .filter { (v, t) ->
                Semver.isStable(v) &&
                    Semver.isGreater(v, dep.current) &&
                    (cutoff == null || t.isBefore(cutoff))
            }
            .mapNotNull { e -> Semver.parse(e.key)?.let { p -> Triple(e.key, e.value, p) } }
            .sortedByDescending { it.third }
            .map { io.github.osamaliaqat.nodedependencyupdater.model.VersionInfo(it.first, it.second) }
            .toList()

        if (cutoff != null && candidates.isEmpty()) {
            // Min-age filter excluded everything: drop this dep (nothing safe to update to).
            return null
        }

        // Fall back to the npm-outdated `latest` if the registry didn't give us anything usable.
        val available = candidates.ifEmpty {
            listOf(io.github.osamaliaqat.nodedependencyupdater.model.VersionInfo(dep.latest, times[dep.latest]))
        }
        val effectiveLatest = available.first().version
        return dep.copy(
            latest = effectiveLatest,
            currentReleaseDate = currentDate,
            latestReleaseDate = available.first().releaseDate,
            availableVersions = available,
            selectedVersion = effectiveLatest,
            enriched = true,
        )
    }

    /** Runs an arbitrary shell command in the project root. Returns null if [command] is blank. */
    fun runPostCommand(command: String): ProcessOutput? {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return null
        val shell = if (SystemInfo.isWindows) listOf("cmd", "/c", trimmed) else postCommandShell(trimmed)
        return run(shell, timeoutMs = 30 * 60_000)
    }

    /** Pick `bash -lc` if available (login shell sources nvm/asdf), otherwise fall back to `sh -c`. */
    private fun postCommandShell(command: String): List<String> {
        val bashCandidates = listOf("/usr/local/bin/bash", "/opt/homebrew/bin/bash", "/usr/bin/bash", "/bin/bash")
        val bash = bashCandidates.firstOrNull { File(it).canExecute() }
        return if (bash != null) listOf(bash, "-lc", command) else listOf("sh", "-c", command)
    }

    /** Returns the first failing ProcessOutput, or the last successful one, or null if nothing to do. */
    fun update(deps: List<OutdatedDependency>): ProcessOutput? {
        if (deps.isEmpty()) return null
        val pm = detectPackageManager()
        val groups = listOf(
            "prod" to deps.filter { !it.isDev && !it.isPeer },
            "dev" to deps.filter { it.isDev && !it.isPeer },
            "peer" to deps.filter { !it.isDev && it.isPeer },
            "devPeer" to deps.filter { it.isDev && it.isPeer },
        )
        var last: ProcessOutput? = null
        for ((type, list) in groups) {
            if (list.isEmpty()) continue
            val packages = list.map {
                val target = it.selectedVersion.ifEmpty { it.latest }
                "${it.name}@${it.versionPrefix}$target"
            }
            val output = run(installCommand(pm, type, packages), timeoutMs = 10 * 60_000)
            last = output
            if (output.exitCode != 0) return output
        }
        return last
    }

    private fun installCommand(pm: PackageManager, type: String, packages: List<String>): List<String> {
        val base: List<String> = when (pm) {
            PackageManager.NPM -> when (type) {
                "dev" -> listOf("npm", "install", "--save-dev")
                "peer" -> listOf("npm", "install", "--save-peer")
                "devPeer" -> listOf("npm", "install", "--save-dev", "--save-peer")
                else -> listOf("npm", "install")
            }
            PackageManager.YARN -> when (type) {
                "dev" -> listOf("yarn", "add", "-D")
                "peer" -> listOf("yarn", "add", "-P")
                "devPeer" -> listOf("yarn", "add", "-D", "-P")
                else -> listOf("yarn", "add")
            }
            PackageManager.PNPM -> when (type) {
                "dev" -> listOf("pnpm", "add", "-D")
                "peer" -> listOf("pnpm", "add", "--save-peer")
                "devPeer" -> listOf("pnpm", "add", "-D", "--save-peer")
                else -> listOf("pnpm", "add")
            }
            PackageManager.BUN -> when (type) {
                "dev" -> listOf("bun", "add", "-d")
                "peer" -> listOf("bun", "add", "--peer")
                "devPeer" -> listOf("bun", "add", "-d", "--peer")
                else -> listOf("bun", "add")
            }
        }
        return base + packages
    }

    private fun run(command: List<String>, timeoutMs: Int): ProcessOutput {
        val cmd = GeneralCommandLine(command)
            .withWorkDirectory(rootDir)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            .withCharset(Charsets.UTF_8)
        log.info("Running ${cmd.commandLineString} in $rootDir")
        val process = try {
            cmd.createProcess()
        } catch (e: Exception) {
            throw NpmInvocationException(
                "Could not start `${command.first()}`: ${e.message}\n\n" +
                    "Working directory: $rootDir\n" +
                    "Make sure `${command.first()}` is installed and on the IDE's PATH " +
                    "(restart the IDE from a terminal if you use nvm/asdf/Homebrew)."
            )
        }
        val output = CapturingProcessHandler(process, Charsets.UTF_8, cmd.commandLineString)
            .runProcess(timeoutMs)
        log.info("Exit ${output.exitCode}; stdout=${output.stdout.length} bytes; stderr=${output.stderr.length} bytes")
        return output
    }
}
