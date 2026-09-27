package ir.matnyaab.core

import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.Instant
import java.time.ZoneId

/**
 * معادل LicenseChecker پایتون:
 * - تولید شناسه سیستم از مشخصات سخت‌افزار (MD5 ۳۲ کاراکتری بزرگ)
 * - اعتبارسنجی سریال: 32 کاراکتر اول = شناسه سیستم، 10 کاراکتر بعد = timestamp شروع، بقیه = timestamp پایان
 *
 * در ویندوز از همان کوئری‌های WMI (CPU + BIOS + BaseBoard) و در لینوکس از شناسه‌های پایدار ماشین استفاده می‌شود.
 * توجه: برای مطابقت کامل با شناسه‌های قبلی ویندوزی، خروجی WMI با همان فیلدها خوانده می‌شود؛
 * در سیستم‌عامل جدید یا سخت‌افزار جدید، شناسه جدید تولید می‌شود (مطابق قرارداد اشتراک، با تغییر سخت‌افزار اشتراک قبلی معتبر نیست).
 */
class LicenseChecker(private val settings: AppSettingsManager, init: Boolean = false) {

    var licenseStart: LocalDateTime = LocalDateTime.MIN; private set
    var licenseEnd: LocalDateTime = LocalDateTime.MIN; private set
    var duration: Long = 0; private set
    var serial: String = settings.get("SERIAL")
    val systemId: String = generateSystemId()

    init { if (init) validateSerial(false) }

    fun isSubscribed(): Boolean = licenseEnd.isAfter(LocalDateTime.now())

    /** @return Triple(شروع، پایان، طول دوره بر حسب روز) */
    fun validateSerial(showMessage: Boolean, onError: (String) -> Unit = {}): Triple<LocalDateTime, LocalDateTime, Long> {
        val none = Triple(LocalDateTime.MIN, LocalDateTime.MIN, 0L)
        try {
            if (serial.isBlank()) return none
            val decoded = AesCipher().decrypt(serial)
            val text = decoded.toString(Charsets.UTF_8)
            val sysid = text.substring(0, 32)
            if (sysid != systemId) {
                licenseStart = LocalDateTime.MIN; licenseEnd = LocalDateTime.MIN; duration = 0
                return none
            }
            val buyTs = text.substring(32, 42).toLong()
            val endTs = text.substring(42).toLong()
            licenseStart = LocalDateTime.ofInstant(Instant.ofEpochSecond(buyTs), ZoneId.systemDefault())
            licenseEnd = LocalDateTime.ofInstant(Instant.ofEpochSecond(endTs), ZoneId.systemDefault())
            duration = java.time.Duration.between(licenseStart, licenseEnd).toDays()
            if (LocalDateTime.now().isAfter(licenseEnd) && showMessage) onError("اشتراک به اتمام رسیده است!")
            settings.setValue("SERIAL", serial)
            return Triple(licenseStart, licenseEnd, duration)
        } catch (ex: Exception) {
            licenseStart = LocalDateTime.MIN; licenseEnd = LocalDateTime.MIN; duration = 0
            if (showMessage) onError("خطا در سیستم اشتراک!")
            return none
        }
    }

    /** تولید شناسه سیستمی کاربر */
    fun generateSystemId(): String {
        val raw = try {
            if (System.getProperty("os.name").lowercase().contains("win")) windowsHardwareString()
            else linuxHardwareString()
        } catch (e: Exception) { fallbackHardwareString() }
        val md5 = MessageDigest.getInstance("MD5").digest(raw.toByteArray(Charsets.UTF_8))
        return md5.joinToString("") { "%02x".format(it) }.uppercase()
    }

    private fun runCmd(vararg cmd: String): String = try {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        p.inputStream.bufferedReader().readText().also { p.waitFor() }
    } catch (e: Exception) { "" }

    /** معادل کوئری‌های WMI نسخه اصلی: Win32_Processor + Win32_BIOS + Win32_BaseBoard */
    private fun windowsHardwareString(): String {
        val cpu = runCmd("powershell", "-NoProfile", "-Command",
            "Get-CimInstance Win32_Processor | Select-Object ProcessorId,Name,UniqueId,Manufacturer,MaxClockSpeed | Format-List | Out-String")
        val bios = runCmd("powershell", "-NoProfile", "-Command",
            "Get-CimInstance Win32_BIOS | Select-Object Manufacturer,SMBIOSBIOSVersion,IdentificationCode,SerialNumber,ReleaseDate,Version | Format-List | Out-String")
        val base = runCmd("powershell", "-NoProfile", "-Command",
            "Get-CimInstance Win32_BaseBoard | Select-Object Model,Manufacturer,Name,SerialNumber | Format-List | Out-String")
        val s = (cpu + bios + base).trim()
        return s.ifBlank { fallbackHardwareString() }
    }

    private fun linuxHardwareString(): String {
        val machineId = try { java.nio.file.Files.readString(java.nio.file.Paths.get("/etc/machine-id")).trim() } catch (e: Exception) { "" }
        val cpu = runCmd("sh", "-c", "lscpu 2>/dev/null | head -25")
        val board = runCmd("sh", "-c", "cat /sys/class/dmi/id/board_vendor /sys/class/dmi/id/board_name /sys/class/dmi/id/bios_version 2>/dev/null")
        val s = (machineId + cpu + board).trim()
        return s.ifBlank { fallbackHardwareString() }
    }

    private fun fallbackHardwareString(): String =
        System.getProperty("user.name") + System.getProperty("os.name") + Runtime.getRuntime().availableProcessors()
}
