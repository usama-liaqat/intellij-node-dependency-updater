package io.github.osamaliaqat.nodedependencyupdater.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File

class PackageJsonReader(private val rootDir: File) {
    private val mapper = ObjectMapper()
    private val rootJson: JsonNode? = readJson(File(rootDir, "package.json"))

    fun rootName(): String = rootJson?.get("name")?.asText().orEmpty().ifEmpty { "root" }

    fun isInSection(packageName: String, section: String): Boolean =
        rootJson?.get(section)?.has(packageName) == true

    fun versionPrefix(packageName: String): String {
        val raw = listOf("dependencies", "devDependencies", "peerDependencies")
            .mapNotNull { rootJson?.get(it)?.get(packageName)?.asText() }
            .firstOrNull()
            .orEmpty()
        return when {
            raw.startsWith("^") -> "^"
            raw.startsWith("~") -> "~"
            else -> ""
        }
    }

    private fun readJson(file: File): JsonNode? = try {
        if (file.exists()) mapper.readTree(file) else null
    } catch (_: Exception) {
        null
    }
}
