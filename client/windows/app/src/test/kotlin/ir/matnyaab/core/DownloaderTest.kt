package ir.matnyaab.core

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloaderTest {
    @Test
    fun failedDownloadPreservesExistingFile() {
        withServer(MockResponse().setResponseCode(500)) { url ->
            val dir = Files.createTempDirectory("matnyaab-downloader").toFile()
            try {
                val target = File(dir, "content.bin").apply { writeText("known-good") }
                val code = runDownload(url, target)

                assertEquals(1, code)
                assertEquals("known-good", target.readText())
                assertFalse(dir.listFiles().orEmpty().any { it.name.endsWith(".part") })
            } finally {
                dir.deleteRecursively()
            }
        }
    }

    @Test
    fun successfulDownloadReplacesExistingFile() {
        withServer(MockResponse().setResponseCode(200).setBody("fresh-content")) { url ->
            val dir = Files.createTempDirectory("matnyaab-downloader").toFile()
            try {
                val target = File(dir, "content.bin").apply { writeText("old-content") }
                val code = runDownload(url, target)

                assertEquals(0, code)
                assertEquals("fresh-content", target.readText())
                assertFalse(dir.listFiles().orEmpty().any { it.name.endsWith(".part") })
            } finally {
                dir.deleteRecursively()
            }
        }
    }

    private fun runDownload(url: String, target: File): Int {
        val latch = CountDownLatch(1)
        var result = -1
        Downloader(url, target, onProgress = {}, onFinish = {
            result = it
            latch.countDown()
        }).start()
        assertTrue(latch.await(5, TimeUnit.SECONDS), "download callback timed out")
        return result
    }

    private fun withServer(response: MockResponse, block: (String) -> Unit) {
        MockWebServer().use { server ->
            server.enqueue(response)
            server.start()
            block(server.url("/file").toString())
        }
    }
}
