package com.yashraj.glidr

import com.yashraj.glidr.update.Versions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionsTest {
    @Test
    fun newerPatchMinorMajor() {
        assertTrue(Versions.compare("1.0.1", "1.0.0") > 0)
        assertTrue(Versions.compare("v1.10.0", "1.9.9") > 0)
        assertTrue(Versions.compare("2.0", "1.99.99") > 0)
    }

    @Test
    fun equalAndOlder() {
        assertEquals(0, Versions.compare("v1.0.0", "1.0.0"))
        assertEquals(0, Versions.compare("1.0.0", "1.0.0-beta"))
        assertTrue(Versions.compare("0.9", "1.0.0") < 0)
    }

    @Test
    fun picksTheApk() {
        val assets = listOf("GlidrCompanion.exe" to "https://github.com/e", "Glidr-1.1.0.apk" to "https://github.com/a")
        assertEquals("https://github.com/a", Versions.pickAsset(assets, ".apk"))
        assertNull(Versions.pickAsset(listOf("x.exe" to "https://github.com/e"), ".apk"))
    }

    @Test
    fun onlyGithubLinksAreTrusted() {
        assertTrue(Versions.isTrustedLink("https://github.com/yashraj-shitole/Glidr/releases/download/v1.1.0/Glidr-1.1.0.apk"))
        assertFalse(Versions.isTrustedLink("http://github.com/x"))
        assertFalse(Versions.isTrustedLink("https://github.com.evil.example/x"))
        assertFalse(Versions.isTrustedLink("intent://x"))
    }
}
