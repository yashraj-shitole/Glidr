package com.yashraj.glidr

import android.content.pm.PackageInstaller
import com.yashraj.glidr.update.DamagedDownload
import com.yashraj.glidr.update.InstallError
import com.yashraj.glidr.update.Release
import com.yashraj.glidr.update.UpdatePrompt
import com.yashraj.glidr.update.Versions
import com.yashraj.glidr.update.copyVerified
import com.yashraj.glidr.update.installError
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException

class UpdateTest {
    private val data = "abc".toByteArray()
    private val sha = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    private fun copy(length: Long, sha256: String?): Pair<ByteArray, List<Int>> {
        val out = ByteArrayOutputStream()
        val seen = mutableListOf<Int>()
        copyVerified(data.inputStream(), out, length, sha256, onProgress = { seen += it })
        return out.toByteArray() to seen
    }

    @Test
    fun goodDownloadIsCopiedWhole() {
        val (copied, progress) = copy(3, sha)
        assertArrayEquals(data, copied)
        assertEquals(listOf(100), progress)
        assertArrayEquals(data, copy(-1, null).first)
    }

    @Test
    fun wrongChecksumOrShortFileIsRefused() {
        assertThrows(DamagedDownload::class.java) { copy(3, "0".repeat(64)) }
        assertThrows(IOException::class.java) { copy(4, sha) }
    }

    @Test
    fun readsGithubsDigest() {
        assertEquals(sha, Versions.parseDigest("sha256:$sha"))
        assertEquals(sha, Versions.parseDigest("sha256:${sha.uppercase()}"))
        assertNull(Versions.parseDigest(""))
        assertNull(Versions.parseDigest(sha))
        assertNull(Versions.parseDigest("sha256:1234"))
    }

    @Test
    fun onlyAnApkInstallsInside() {
        assertTrue(Release("3.0.0", "https://github.com/a/Glidr-3.0.0.apk", "https://github.com/p").hasApk)
        assertFalse(Release("3.0.0", "https://github.com/p", "https://github.com/p").hasApk)
    }

    @Test
    fun onlyTheUsersOwnNoIsSilent() {
        val aborted = PackageInstaller.STATUS_FAILURE_ABORTED
        assertNull(installError(aborted, "INSTALL_FAILED_ABORTED: User rejected permissions"))
        assertEquals(InstallError.Refused, installError(aborted, "INSTALL_FAILED_VERIFICATION_FAILURE: Install not allowed"))
        assertEquals(InstallError.Storage, installError(PackageInstaller.STATUS_FAILURE_STORAGE, null))
        assertEquals(InstallError.Refused, installError(PackageInstaller.STATUS_FAILURE_CONFLICT, null))
    }

    @Test
    fun laterHidesTheSameVersionForADay() {
        val now = 1_000_000_000L
        val until = now + UpdatePrompt.SNOOZE_MS
        assertTrue(UpdatePrompt.shouldAsk("3.0.0", null, 0, now))
        assertFalse(UpdatePrompt.shouldAsk("3.0.0", "3.0.0", until, now))
        assertTrue(UpdatePrompt.shouldAsk("3.0.0", "3.0.0", until, until))
        assertTrue(UpdatePrompt.shouldAsk("3.0.1", "3.0.0", until, now))
        // The clock was set back a week: do not wait a week.
        assertTrue(UpdatePrompt.shouldAsk("3.0.0", "3.0.0", until, now - 7 * UpdatePrompt.SNOOZE_MS))
    }
}
