package io.github.osamaliaqat.nodedependencyupdater.service

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PackageJsonReaderTest {
    private lateinit var dir: File

    @Before fun setUp() {
        dir = Files.createTempDirectory("ndu-test").toFile()
    }

    @After fun tearDown() {
        dir.deleteRecursively()
    }

    private fun write(json: String) {
        File(dir, "package.json").writeText(json)
    }

    @Test fun `rootName falls back to 'root' when missing`() {
        write("""{}""")
        assertEquals("root", PackageJsonReader(dir).rootName())
    }

    @Test fun `rootName returns the name field`() {
        write("""{"name":"my-pkg"}""")
        assertEquals("my-pkg", PackageJsonReader(dir).rootName())
    }

    @Test fun `isInSection detects dev and peer membership`() {
        write(
            """
            {
              "dependencies": { "react": "^18.0.0" },
              "devDependencies": { "typescript": "^5.0.0" },
              "peerDependencies": { "react-dom": "^18.0.0" }
            }
            """.trimIndent(),
        )
        val r = PackageJsonReader(dir)
        assertTrue(r.isInSection("react", "dependencies"))
        assertTrue(r.isInSection("typescript", "devDependencies"))
        assertTrue(r.isInSection("react-dom", "peerDependencies"))
        assertFalse(r.isInSection("react", "devDependencies"))
        assertFalse(r.isInSection("nonexistent", "dependencies"))
    }

    @Test fun `versionPrefix returns caret tilde or empty`() {
        write(
            """
            {
              "dependencies": {
                "caret-pkg": "^1.0.0",
                "tilde-pkg": "~2.0.0",
                "exact-pkg": "3.0.0"
              }
            }
            """.trimIndent(),
        )
        val r = PackageJsonReader(dir)
        assertEquals("^", r.versionPrefix("caret-pkg"))
        assertEquals("~", r.versionPrefix("tilde-pkg"))
        assertEquals("", r.versionPrefix("exact-pkg"))
        assertEquals("", r.versionPrefix("missing-pkg"))
    }

    @Test fun `handles missing package_json file gracefully`() {
        // No package.json written — reader should return defaults without throwing.
        val r = PackageJsonReader(dir)
        assertEquals("root", r.rootName())
        assertFalse(r.isInSection("anything", "dependencies"))
        assertEquals("", r.versionPrefix("anything"))
    }
}
