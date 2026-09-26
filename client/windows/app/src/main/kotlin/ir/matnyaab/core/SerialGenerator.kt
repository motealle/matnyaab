package ir.matnyaab.core

/** معادل GenerateSerial سمت سرور — برای تست دوسویه سریال در واحد تست‌ها */
object SerialGenerator {
    fun generate(userSystemId: String, buyTs: Long, endTs: Long): String =
        AesCipher().encrypt((userSystemId + buyTs.toString() + endTs.toString()).toByteArray(Charsets.UTF_8))
}
