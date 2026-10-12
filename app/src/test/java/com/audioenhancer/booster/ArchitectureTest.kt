package com.audioenhancer.booster

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.core.importer.Location
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.GeneralCodingRules
import org.junit.Test
import java.io.File

/**
 * Batch 201 — ArchUnit REPORT-ONLY (adaptasi `ArchitectureTest` dari template SONIX).
 *
 * Selalu mengevaluasi aturan dan menulis `app/build/reports/archunit/archunit-report.txt` (kunci `ARCHUNIT_STATUS` /
 * `RULES` / `VIOLATIONS` + baris `[PASS]` / `[FAIL]`), dirakit workflow `android-artifact.yml` ke
 * `laporan-untuk-claude.txt`. Tes TIDAK pernah gagal karena pelanggaran (non-blocking). Status laporan TIDAK PERNAH
 * "OK" kalau alat gagal jalan atau kelas acuan tak terimpor (alat gagal != lulus). Tanpa env/flag: JUnit polos (bukan
 * Robolectric), jadi jalan sendiri di `testDebugUnitTest` biasa maupun dgn `--tests`.
 *
 * Seluruh kode ada di 1 package datar (`com.audioenhancer.booster`), jadi "lapisan" didefinisikan lewat NAMA kelas
 * (kelas file Kotlin berakhiran `Kt`, lambda/`Companion` berakhiran `$...`). Aturan ditulis agar LULUS pada kode saat
 * ini (v198): pelanggaran baru = regresi arsitektur.
 */
class ArchitectureTest {

    @Test
    fun `archunit rules are evaluated and written to the report`() {
        val dir = File(System.getProperty("user.dir"), "build/reports/archunit")
        dir.mkdirs()
        val report = File(dir, "archunit-report.txt")
        try {
            report.writeText(evaluateAll())
        } catch (t: Throwable) {
            // Alat gagal jalan: status ERROR (BUKAN "lulus"), pesan mentah tanpa stack frame.
            report.writeText(
                "ARCHUNIT_STATUS=ERROR\nRULES=0\nVIOLATIONS=0\n" +
                    generateSequence(t) { it.cause }.take(5).joinToString("\n") { "ERROR: $it" } + "\n"
            )
        }
    }

    private fun evaluateAll(): String {
        val classes: JavaClasses = ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .withImportOption(WithoutUnitTestClasses)
            .importPackages(ROOT_PACKAGE)

        val lines = mutableListOf<String>()
        var rules = 0
        var violations = 0

        // Kelas acuan harus ada: kalau ada yang di-rename/dihapus, aturan berbasis nama diam-diam jadi hampa.
        val present = classes.map { it.name }.toSet()
        val missing = REFERENCE_CLASSES.filter { ROOT_PACKAGE + "." + it !in present }
        rules++
        if (missing.isEmpty()) {
            lines += "[PASS] kelas acuan lapisan semua terimpor (${REFERENCE_CLASSES.size})"
        } else {
            violations += missing.size
            lines += "[FAIL] kelas acuan lapisan hilang dari hasil kompilasi (${missing.size}): perbarui daftar di ArchitectureTest"
            missing.forEach { lines += "  - $it" }
        }

        for ((label, rule) in architectureRules()) {
            rules++
            val details = rule.evaluate(classes).failureReport.details
                .map { it.replace('\n', ' ').trim().take(DETAIL_MAX_CHARS) }
                .distinct()
            if (details.isEmpty()) {
                lines += "[PASS] $label"
            } else {
                violations += details.size
                lines += "[FAIL] $label (${details.size})"
                details.take(DETAIL_MAX_LINES).forEach { lines += "  - $it" }
                if (details.size > DETAIL_MAX_LINES) lines += "  ... +${details.size - DETAIL_MAX_LINES} lainnya"
            }
        }

        val status = if (present.isEmpty()) "ERROR" else "OK"
        return (
            listOf("ARCHUNIT_STATUS=$status", "CLASSES=${present.size}", "RULES=$rules", "VIOLATIONS=$violations") +
                lines
            ).joinToString("\n") + "\n"
    }

    private fun architectureRules(): List<Pair<String, ArchRule>> = listOf(
        "Service/worker/receiver/prefs/ViewModel tidak bergantung ke layar Compose" to
            noClasses().that().haveNameMatching(NON_UI)
                .should().dependOnClassesThat().haveNameMatching(UI_COMPOSE),
        "UI murni (Theme, komponen Skeu, EqCurveEditor, Onboarding) tidak bergantung ke service/worker/updater" to
            noClasses().that().haveNameMatching(UI_PURE)
                .should().dependOnClassesThat().haveNameMatching(ENGINE),
        "PrefsHelper (persistensi) berdiri sendiri: tanpa ViewModel/service/worker/UI" to
            noClasses().that().haveNameMatching(names("PrefsHelper"))
                .should().dependOnClassesThat().haveNameMatching(
                    names("BoosterViewModel", "MainActivity") + "|" + ENGINE + "|" + UI_COMPOSE
                ),
        "BoosterViewModel tidak memakai UI Compose (ui/foundation/material3) maupun Activity" to
            noClasses().that().haveNameMatching(names("BoosterViewModel"))
                .should().dependOnClassesThat().resideInAnyPackage(
                    "androidx.compose.ui..", "androidx.compose.foundation..", "androidx.compose.material3..",
                    "androidx.activity.."
                ),
        "Tanpa akses System.out/err atau printStackTrace" to
            GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS,
        "Tanpa java.util.logging" to
            GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING,
        "Tanpa File.readBytes()/InputStream.readBytes() (Feature Lock: risiko OOM, pakai stream Okio)" to
            noClasses().should().callMethod("kotlin.io.FilesKt", "readBytes", "java.io.File")
                .orShould().callMethod("kotlin.io.ByteStreamsKt", "readBytes", "java.io.InputStream"),
        "Tanpa GlobalScope (coroutine harus lifecycle-aware)" to
            noClasses().should().dependOnClassesThat().haveFullyQualifiedName("kotlinx.coroutines.GlobalScope"),
        "Tanpa java.util.Timer (kerja terjadwal = WorkManager)" to
            noClasses().should().dependOnClassesThat().haveFullyQualifiedName("java.util.Timer"),
        "Tanpa Thread.sleep" to
            noClasses().should().callMethod("java.lang.Thread", "sleep", "long"),
        "Tanpa System.exit" to
            noClasses().should().callMethod("java.lang.System", "exit", "int")
    )

    // Lokasi kelas tes unit AGP (`.../kotlin-classes/debugUnitTest`, `.../javac/debugUnitTest`) tak dikenali
    // `ImportOption.DoNotIncludeTests`, jadi disaring sendiri.
    private object WithoutUnitTestClasses : ImportOption {
        override fun includes(location: Location): Boolean = !location.asURI().toString().contains("UnitTest")
    }

    private companion object {
        const val ROOT_PACKAGE = "com.audioenhancer.booster"
        const val DETAIL_MAX_CHARS = 200
        const val DETAIL_MAX_LINES = 20

        // Ekor nama: kelas file Kotlin (`...Kt`) dan kelas bersarang/lambda (`$...`).
        const val TAIL = "(Kt)?([\$].*)?"
        const val PREFIX = "com\\.audioenhancer\\.booster\\."

        fun names(vararg simple: String): String = PREFIX + "(" + simple.joinToString("|") + ")" + TAIL

        // Berkas Compose (kelas file `...Kt` + `ComposableSingletons$...Kt`). `MainActivity` SENGAJA bukan bagian dari
        // grup ini: service/tile/widget/shortcut memang membuka Activity lewat Intent/PendingIntent.
        val UI_COMPOSE: String = PREFIX + "(ComposableSingletons[\$])?" +
            "(BoosterScreen|SettingsScreen|OnboardingScreen|EqCurveEditor|SkeuomorphicComponents|Theme)" + TAIL
        val UI_PURE: String = PREFIX + "(ComposableSingletons[\$])?" +
            "(OnboardingScreen|EqCurveEditor|SkeuomorphicComponents|Theme)" + TAIL
        val ENGINE: String = names(
            "AudioEnhancerService", "ServiceWatchdogWorker", "ScheduleWorker", "WatchdogAlarmReceiver",
            "BootReceiver", "QuickToggleTileService", "BoosterWidgetProvider", "UpdateManager", "CrashLogger"
        )
        val NON_UI: String = names(
            "AudioEnhancerService", "ServiceWatchdogWorker", "ScheduleWorker", "WatchdogAlarmReceiver",
            "BootReceiver", "QuickToggleTileService", "BoosterWidgetProvider", "UpdateManager", "CrashLogger",
            "OemAutostartHelper", "PrefsHelper", "BoosterViewModel", "ShortcutHelper", "AudioEnhancerApp"
        )

        val REFERENCE_CLASSES: List<String> = listOf(
            "AudioEnhancerService", "ServiceWatchdogWorker", "ScheduleWorker", "WatchdogAlarmReceiver",
            "BootReceiver", "QuickToggleTileService", "BoosterWidgetProvider", "UpdateManager", "CrashLogger",
            "OemAutostartHelper", "PrefsHelper", "BoosterViewModel", "ShortcutHelper", "AudioEnhancerApp",
            "MainActivity", "BoosterScreenKt", "SettingsScreenKt", "OnboardingScreenKt", "EqCurveEditorKt",
            "SkeuomorphicComponentsKt", "ThemeKt"
        )
    }
}
