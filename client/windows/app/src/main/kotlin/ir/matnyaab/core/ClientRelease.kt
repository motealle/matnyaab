package ir.matnyaab.core

import com.google.gson.JsonParser
import java.io.File
import java.net.URI
import java.security.MessageDigest

/** Public release metadata, never the legacy /update/ installer. */
data class ClientRelease(val version: String, val url: String, val sha256: String, val sizeBytes: Long) {
    fun verify(file: File): Boolean {
        if (!file.isFile || file.length() != sizeBytes) return false
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buf = ByteArray(128 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        val actual = md.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return actual.equals(sha256, ignoreCase = true)
    }
}

object ReleaseFeed {
    fun newerThan(remote: String, current: String): Boolean {
        fun parts(s: String): List<Int>? {
            if (!s.matches(Regex("""[0-9]+(\.[0-9]+){1,3}"""))) return null
            return s.split('.').map { it.toIntOrNull() ?: return null }
        }
        val a = parts(remote) ?: return false
        val b = parts(current) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val difference = a.getOrElse(i) { 0 }.compareTo(b.getOrElse(i) { 0 })
            if (difference != 0) return difference > 0
        }
        return false
    }

    fun parse(json: String, baseUrl: String = Constants.BASE_URL): ClientRelease? = runCatching {
        val kotlin = JsonParser.parseString(json).asJsonObject.getAsJsonObject("kotlin") ?: return null
        val version = kotlin.get("version")?.asString?.trim() ?: return null
        if (!version.matches(Regex("""[0-9]+(\.[0-9]+){1,3}"""))) return null
        val rawUrl = kotlin.get("url")?.asString ?: return null
        val source = URI(baseUrl)
        val uri = source.resolve(rawUrl)
        // Reject legacy, arbitrary external hosts and HTTP downloads.
        if (uri.scheme != "https" || uri.host?.removePrefix("www.") != source.host?.removePrefix("www.")) return null
        if (!uri.path.matches(Regex("""/downloadFiles/Matnyaab-Setup-[0-9.]+\.exe"""))) return null
        if (uri.rawQuery != null || uri.fragment != null || uri.userInfo != null) return null
        val sha = kotlin.get("sha256")?.asString ?: return null
        if (!sha.matches(Regex("""[a-fA-F0-9]{64}"""))) return null
        val size = kotlin.get("size_bytes")?.asLong ?: return null
        if (size !in 20_000_000L..250_000_000L) return null
        ClientRelease(version, uri.toString(), sha, size)
    }.getOrNull()
}
