import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.24"
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "ir.matnyaab"
version = "1.2"

repositories { mavenCentral() }

val luceneVersion = "9.10.0"
val tikaVersion = "2.9.2"

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-javafx:1.8.1")

    // جایگزین Whoosh: آپاچی لوسین
    implementation("org.apache.lucene:lucene-core:$luceneVersion")
    implementation("org.apache.lucene:lucene-analysis-common:$luceneVersion")
    implementation("org.apache.lucene:lucene-queryparser:$luceneVersion")
    implementation("org.apache.lucene:lucene-highlighter:$luceneVersion")
    implementation("org.apache.lucene:lucene-queries:$luceneVersion")

    // جایگزین سرور Tika (بدون نیاز به سرور REST جدا؛ به صورت کتابخانه داخلی)
    implementation("org.apache.tika:tika-core:$tikaVersion")
    implementation("org.apache.tika:tika-parsers-standard-package:$tikaVersion")

    // JSON (fileInfo.json / file_list.txt / ارتباط با سرور)
    implementation("com.google.code.gson:gson:2.11.0")

    // HTTP (اخبار، بسته‌های محتوایی، گزارش آمار، بررسی آپدیت)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // تاریخ جلالی
    implementation("com.github.mfathi91:persian-date-time:4.2.1")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

javafx {
    version = "21.0.3"
    modules = listOf("javafx.controls", "javafx.web", "javafx.swing")
}

application {
    mainClass.set("ir.matnyaab.MainKt")
}

tasks.withType<KotlinCompile> { kotlinOptions.jvmTarget = "17" }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(17)) }
tasks.test { useJUnitPlatform() }
