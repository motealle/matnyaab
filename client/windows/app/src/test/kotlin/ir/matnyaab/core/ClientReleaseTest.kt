package ir.matnyaab.core

import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClientReleaseTest {
    private val digest = "a".repeat(64)
    private fun manifest(url: String, version: String = "3.1.1") =
        """{"kotlin":{"version":"$version","url":"$url","sha256":"$digest","size_bytes":83937502}}"""

    @Test fun comparesSemanticVersionsNumerically() {
        assertTrue(ReleaseFeed.newerThan("3.1.1", "3.1"))
        assertTrue(ReleaseFeed.newerThan("3.10", "3.9"))
        assertFalse(ReleaseFeed.newerThan("3.1", "3.1.1"))
        assertFalse(ReleaseFeed.newerThan("3.1.0", "3.1"))
        assertFalse(ReleaseFeed.newerThan("4.bad", "3.1"))
    }

    @Test fun acceptsModernInstallerAndRejectsClassicOrExternalDownloads() {
        val release = ReleaseFeed.parse(manifest("/downloadFiles/Matnyaab-Setup-3.1.1.exe"))
        assertNotNull(release)
        assertEquals("https://www.matnyaab.ir/downloadFiles/Matnyaab-Setup-3.1.1.exe", release.url)
        assertNull(ReleaseFeed.parse(manifest("/update/MATNYAAB_x64_setup.exe")))
        assertNull(ReleaseFeed.parse(manifest("https://other.example/downloadFiles/Matnyaab-Setup-3.1.1.exe")))
        assertNull(ReleaseFeed.parse(manifest("http://www.matnyaab.ir/downloadFiles/Matnyaab-Setup-3.1.1.exe")))
    }

    @Test fun validatesDownloadedBytesBeforeInstallerLaunch() {
        val file = File.createTempFile("matnyaab-release-checksum", ".exe")
        try {
            file.writeText("new-installer")
            val hash = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            val valid = ClientRelease("3.1.1", "https://www.matnyaab.ir/downloadFiles/Matnyaab-Setup-3.1.1.exe", hash, file.length())
            assertTrue(valid.verify(file))
            assertFalse(valid.copy(sha256 = digest).verify(file))
            assertFalse(valid.copy(sizeBytes = file.length() + 1).verify(file))
        } finally {
            file.delete()
        }
    }
}
