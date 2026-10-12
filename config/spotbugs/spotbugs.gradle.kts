// Batch 201 — SpotBugs REPORT-ONLY untuk Boomly (adaptasi `config/spotbugs/spotbugs.gradle.kts` template SONIX).
//
// Di-apply dari `app/build.gradle.kts` HANYA bila `-Pboomly.spotbugs=true` (dipakai workflow `android-artifact.yml`).
// Build/rilis biasa (`build.yml`), lint, detekt, dan tes TIDAK memuat file ini sama sekali: kalau skrip ini salah, yang
// rusak hanya langkah SpotBugs di laporan (jadi `NOT VERIFIED`), bukan build APK.
//
// Kenapa bukan plugin `com.github.spotbugs`: plugin itu tidak membuat task untuk varian Android (hanya source set
// Java), dan menambah plugin ke classpath build = menyentuh konfigurasi yang stabil. Di sini SpotBugs dijalankan
// langsung sebagai `JavaExec` (CLI `edu.umd.cs.findbugs.FindBugs2`) terhadap kelas Kotlin hasil `compileDebugKotlin`.
//
// Keluaran: `app/build/reports/spotbugs/spotbugs.sarif` (dibaca `jq` di workflow). Filter noise Kotlin:
// `config/spotbugs/exclude.xml`. NON-BLOCKING: exit code SpotBugs diabaikan.
// Aux classpath = `android.jar` (+ dependensi debug bila bisa di-resolve, best effort). Bila tak ada, SpotBugs hanya
// mencetak peringatan "missing class" dan analisis tetap jalan dengan presisi lebih rendah.
//
// Jalankan dgn `--no-configuration-cache` (task ini membaca konfigurasi proyek saat eksekusi, tak kompatibel dengan
// configuration cache yang menyala di gradle.properties).
//
// NOT VERIFIED: belum pernah dijalankan (sandbox tanpa akses Maven/Gradle).

import org.gradle.api.attributes.Attribute
import org.gradle.process.CommandLineArgumentProvider

val spotbugsVersion = "4.8.6"

val spotbugsRunner = configurations.create("spotbugsRunner") {
    description = "Classpath CLI SpotBugs (hanya di-resolve oleh task spotbugsDebug)"
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    add("spotbugsRunner", "com.github.spotbugs:spotbugs:$spotbugsVersion")
}

val spotbugsClassesDir = layout.buildDirectory.dir("tmp/kotlin-classes/debug")
val spotbugsSarif = layout.buildDirectory.file("reports/spotbugs/spotbugs.sarif")
val spotbugsExclude = rootProject.layout.projectDirectory.file("config/spotbugs/exclude.xml")
val spotbugsSources = layout.projectDirectory.dir("src/main/java")

tasks.register<JavaExec>("spotbugsDebug") {
    group = "verification"
    description = "SpotBugs report-only (SARIF) untuk varian debug; aktif hanya dengan -Pboomly.spotbugs=true"
    dependsOn("compileDebugKotlin")
    classpath = spotbugsRunner
    mainClass.set("edu.umd.cs.findbugs.FindBugs2")
    maxHeapSize = "1g"
    isIgnoreExitValue = true
    outputs.upToDateWhen { false }

    doFirst {
        spotbugsSarif.get().asFile.parentFile.mkdirs()
    }

    argumentProviders.add(
        CommandLineArgumentProvider {
            val aux = LinkedHashSet<File>()
            val sdkRoot = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
            if (sdkRoot != null) {
                val androidJar = File(sdkRoot, "platforms/android-34/android.jar")
                if (androidJar.isFile) aux.add(androidJar)
            }
            runCatching {
                val compileClasspath = project.configurations.getByName("debugCompileClasspath")
                for (type in listOf("android-classes-jar", "jar")) {
                    val view = compileClasspath.incoming.artifactView {
                        lenient(true)
                        attributes { attribute(Attribute.of("artifactType", String::class.java), type) }
                    }
                    aux.addAll(view.files.files)
                }
            }
            val args = mutableListOf(
                "-sarif",
                "-output", spotbugsSarif.get().asFile.absolutePath,
                "-exclude", spotbugsExclude.asFile.absolutePath,
                "-effort:default",
                "-medium",
                "-nested:false",
                "-sourcepath", spotbugsSources.asFile.absolutePath
            )
            if (aux.isNotEmpty()) {
                args.add("-auxclasspath")
                args.add(aux.joinToString(File.pathSeparator))
            }
            args.add(spotbugsClassesDir.get().asFile.absolutePath)
            args
        }
    )
}
