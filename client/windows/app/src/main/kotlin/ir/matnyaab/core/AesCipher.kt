package ir.matnyaab.core

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * پیاده‌سازی دقیق AESCipher پایتون (هم در کلاینت هم در سرور):
 * خروجی = Base32( key16 + iv16 + AES_CBC(raw + padding) )
 * پدینگ مطابق نسخه اصلی (مشابه PKCS7).
 * سریال‌های تولید سایت فعلی بدون تغییر قابل رمزگشایی هستند.
 */
class AesCipher {
    private val random = SecureRandom()

    fun encrypt(raw: ByteArray): String {
        val key = ByteArray(16).also { random.nextBytes(it) }
        val iv = ByteArray(16).also { random.nextBytes(it) }
        val padLen = 16 - raw.size % 16
        val padded = raw + ByteArray(padLen) { padLen.toByte() }
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        val enc = cipher.doFinal(padded)
        return Base32.encode(key + iv + enc)
    }

    fun decrypt(encoded: String): ByteArray {
        val all = Base32.decode(encoded.trim())
        require(all.size > 32) { "serial too short" }
        val key = all.copyOfRange(0, 16)
        val iv = all.copyOfRange(16, 32)
        val body = all.copyOfRange(32, all.size)
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        val dec = cipher.doFinal(body)
        val padLen = dec.last().toInt() and 0xFF
        return dec.copyOfRange(0, dec.size - padLen)
    }
}

/** Base32 (RFC 4648) — معادل base64.b32encode/b32decode پایتون */
object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun encode(data: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0L; var bits = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toLong() and 0xFF); bits += 8
            while (bits >= 5) { sb.append(ALPHABET[((buffer shr (bits - 5)) and 0x1F).toInt()]); bits -= 5 }
        }
        if (bits > 0) sb.append(ALPHABET[((buffer shl (5 - bits)) and 0x1F).toInt()])
        while (sb.length % 8 != 0) sb.append('=')
        return sb.toString()
    }

    fun decode(s: String): ByteArray {
        val clean = s.trimEnd('=')
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0L; var bits = 0
        for (c in clean) {
            val v = ALPHABET.indexOf(c.uppercaseChar())
            require(v >= 0) { "invalid base32 char: $c" }
            buffer = (buffer shl 5) or v.toLong(); bits += 5
            if (bits >= 8) { out.write(((buffer shr (bits - 8)) and 0xFF).toInt()); bits -= 8 }
        }
        return out.toByteArray()
    }
}
