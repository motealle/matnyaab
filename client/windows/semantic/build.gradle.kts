import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// «بستهٔ جست‌وجوی معنایی»: جدا از برنامهٔ پایه ساخته و جداگانه دانلود می‌شود.
plugins { kotlin("jvm") version "1.9.24" }

repositories { mavenCentral() }

val ortVersion = "1.17.3"
val ort by configurations.creating

dependencies {
    compileOnly("com.google.code.gson:gson:2.11.0")
    compileOnly("com.microsoft.onnxruntime:onnxruntime:$ortVersion")
    ort("com.microsoft.onnxruntime:onnxruntime:$ortVersion")
    testImplementation("com.google.code.gson:gson:2.11.0")
    testImplementation("com.microsoft.onnxruntime:onnxruntime:$ortVersion")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.withType<KotlinCompile> { kotlinOptions.jvmTarget = "17" }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(17)) }

// رابط SemanticEngine فقط برای کامپایل (نسخهٔ واقعی را برنامهٔ پایه فراهم می‌کند)
sourceSets.main { kotlin.srcDir("src/stub/kotlin") }
tasks.jar { archiveFileName.set("matnyaab-semantic.jar"); exclude("ir/matnyaab/core/**") }

/** کپی jar رسمی ONNX Runtime برای بسته‌بندی */
tasks.register<Copy>("copyOrt") {
    from(ort); into(layout.buildDirectory.dir("ort"))
}

tasks.test {
    useJUnitPlatform()
    systemProperty("matnyaab.semantic.dir", System.getProperty("matnyaab.semantic.dir") ?: "")
    testLogging { showStandardStreams = true; exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL; events("failed", "passed") }
}
