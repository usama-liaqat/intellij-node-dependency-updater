package io.github.osamaliaqat.nodedependencyupdater.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemverTest {
    @Test fun `parses standard versions`() {
        assertEquals(Semver.Parsed(1, 2, 3), Semver.parse("1.2.3"))
        assertEquals(Semver.Parsed(0, 0, 0), Semver.parse("0.0.0"))
        assertEquals(Semver.Parsed(10, 20, 30), Semver.parse("10.20.30"))
    }

    @Test fun `strips v prefix and prerelease and build`() {
        assertEquals(Semver.Parsed(1, 2, 3), Semver.parse("v1.2.3"))
        assertEquals(Semver.Parsed(1, 2, 3), Semver.parse("1.2.3-alpha.1"))
        assertEquals(Semver.Parsed(1, 2, 3), Semver.parse("1.2.3+build.5"))
    }

    @Test fun `rejects malformed versions`() {
        assertNull(Semver.parse("1.2"))
        assertNull(Semver.parse("not-a-version"))
        assertNull(Semver.parse(""))
    }

    @Test fun `isStable rejects prereleases`() {
        assertTrue(Semver.isStable("1.2.3"))
        assertFalse(Semver.isStable("1.2.3-rc.1"))
        assertFalse(Semver.isStable("1.2.3-alpha"))
    }

    @Test fun `isGreater compares by major then minor then patch`() {
        assertTrue(Semver.isGreater("2.0.0", "1.9.9"))
        assertTrue(Semver.isGreater("1.10.0", "1.9.9"))
        assertTrue(Semver.isGreater("1.0.10", "1.0.9"))
        assertFalse(Semver.isGreater("1.0.0", "1.0.0"))
        assertFalse(Semver.isGreater("1.0.0", "1.0.1"))
    }

    @Test fun `isGreater returns false on unparseable inputs`() {
        assertFalse(Semver.isGreater("garbage", "1.0.0"))
        assertFalse(Semver.isGreater("1.0.0", "garbage"))
    }
}
