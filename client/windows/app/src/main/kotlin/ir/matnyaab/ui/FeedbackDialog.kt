package ir.matnyaab.ui

import ir.matnyaab.core.Constants
import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.NodeOrientation
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.stage.Stage
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** پیشنهاد قابلیت یا گزارش مشکل از درون برنامه (به صندوق پیام‌های سایت می‌رود) */
object FeedbackDialog {
    private val http by lazy { OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build() }

    fun show(owner: Stage) {
        val feature = RadioButton("💡 پیشنهاد قابلیت تازه"); val bug = RadioButton("🐞 گزارش مشکل")
        ToggleGroup().also { feature.toggleGroup = it; bug.toggleGroup = it }; feature.isSelected = true
        val msg = TextArea().apply { prefRowCount = 7; isWrapText = true; promptText = "چه چیزی کارتان را آسان‌تر می‌کند؟ هرچه دقیق‌تر بنویسید (مثلاً با یک نمونه)، بهتر می‌توانیم بسازیمش." }
        val contact = TextField().apply { promptText = "ایمیل یا موبایل (اختیاری؛ فقط برای پاسخ‌دادن به شما)" }
        val status = Label().apply { isWrapText = true }
        val ok = ButtonType("ارسال", ButtonBar.ButtonData.OK_DONE)
        val d = Dialog<ButtonType>().apply {
            initOwner(owner); title = "پیشنهاد و گزارش"; headerText = null
            dialogPane.content = VBox(10.0, HBox(14.0, feature, bug), msg, contact, status,
                Label("پیشنهادها را می‌خوانیم و در برنامهٔ نسخه‌های بعد بررسی می‌کنیم. متن پیشنهاد بدون نام و راه تماس در فهرست پیشنهادها نگه داشته می‌شود.").apply { isWrapText = true; styleClass.add("muted"); maxWidth = 460.0 })
                .apply { padding = Insets(12.0); prefWidth = 480.0 }
            dialogPane.nodeOrientation = NodeOrientation.RIGHT_TO_LEFT; dialogPane.style = Theme.FONT
            dialogPane.buttonTypes.addAll(ok, ButtonType("انصراف", ButtonBar.ButtonData.CANCEL_CLOSE))
        }
        val send = d.dialogPane.lookupButton(ok) as Button
        send.addEventFilter(javafx.event.ActionEvent.ACTION) { e ->
            e.consume()
            val text = msg.text.trim()
            if (text.length < 10) { status.text = "کمی بیشتر بنویسید (دست‌کم چند کلمه)."; return@addEventFilter }
            send.isDisable = true; status.text = "در حال ارسال…"
            Thread {
                val r = runCatching {
                    val body = FormBody.Builder().add("type", if (bug.isSelected) "bug" else "feature").add("message", text)
                        .add("contact", contact.text.trim()).add("app_version", Constants.APP_VERSION).build()
                    http.newCall(Request.Builder().url("${Constants.BASE_URL}/api/client/feedback").post(body).build()).execute().use { it.isSuccessful && (it.body?.string() ?: "").contains("\"ok\":true") }
                }
                Platform.runLater {
                    if (r.getOrDefault(false)) { d.result = ok; d.close(); CustomMessageBox.show(owner, "سپاسگزاریم", "پیام شما رسید. 🌱") }
                    else { send.isDisable = false; status.text = "ارسال نشد؛ اتصال اینترنت را بررسی کنید یا از بخش «تماس» سایت بفرستید." }
                }
            }.apply { isDaemon = true }.start()
        }
        d.showAndWait()
    }
}
