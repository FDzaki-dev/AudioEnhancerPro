package com.audioenhancer.booster

/**
 * Batch 318: aturan murni pembacaan `dumpsys media.audio_flinger` untuk menemukan modul efek YATIM milik Boomly (tes
 * `OrphanEffectsTest`, memakai baris ASLI dari log device `aep_debug_20261113_073815`, Infinix X6855, Android 16).
 *
 * Bukti (log itu): setelah proses lama mati (update/dibunuh), rantai efek yang terikat sesi pemutar (mis. sesi 2457) TETAP hidup
 * di AudioFlinger: tiap modulnya `0 Clients`, `Enabled y`, state 003 (ACTIVE), nomor ID persis milik proses lama. Efek session 0
 * ikut hilang bersama prosesnya, jadi hanya rantai sesi pemutar yang jadi yatim. Yatim itu memproses audio pemutar dengan nilai
 * lama SELAGI efek global proses baru ikut memprosesnya (dua rantai bertumpuk) sampai proses baru sempat memindahkan efeknya ke
 * sesi itu — itulah "menggelegar sesaat" setelah update/fresh start.
 */
internal object OrphanEffects {
    val COMMAND: List<String> = listOf("dumpsys", "media.audio_flinger")

    const val MAX_ATTEMPTS = 3
    const val RETRY_DELAY_MS = 400L
    const val MAX_TARGETS = 12

    enum class Kind(val typeUuid: String, val label: String) {
        BASS("0634f220-ddd4-11db-a0fc-0002a5d5c51b", "BassBoost"),
        VIRTUALIZER("37cc2c00-dddd-11db-8577-0002a5d5c51b", "Virtualizer"),
        EQUALIZER("0bed4300-ddd6-11db-8f34-0002a5d5c51b", "Equalizer"),
        LOUDNESS("fe3199be-aed0-413f-87bb-11260eb63cf1", "LoudnessEnhancer"),
        DYNAMICS("7261676f-6d75-7369-6364-28e2fd3ac39e", "DynamicsProcessing")
    }

    /** Satu modul efek di rantai sesi [session] (> 0) tanpa klien dan masih menyala. */
    data class Orphan(val session: Int, val effectId: Int, val kind: Kind)

    private val CHAIN = Regex("""^\s*\d+ effects for session (-?\d+)\s*$""")
    private val EFFECT = Regex("""^\s*Effect ID (\d+):\s*$""")
    private val STATE_ROW = Regex("""^\s*(\d{5})\s+(\d{3})\s+([yn])\s+([yn])\s+([yn])\s+([yn])\s*$""")
    private val TYPE = Regex("""^\s*- TYPE: ([0-9a-fA-F-]{36})\s*$""")
    private val CLIENTS = Regex("""^\s*(\d+) Clients:\s*$""")
    private const val STATE_HEADER = "Session State Registered Internal Enabled Suspended:"

    fun kindOf(typeUuid: String): Kind? = Kind.values().firstOrNull { it.typeUuid.equals(typeUuid, ignoreCase = true) }

    /** Modul 5 jenis efek Boomly di sesi > 0 dengan `0 Clients` dan `Enabled y`. Sesi 0 (global) dan jenis lain diabaikan. */
    fun parse(lines: Sequence<String>): List<Orphan> {
        val out = ArrayList<Orphan>()
        var session = 0
        var effectId = -1
        var type: String? = null
        var enabled = false
        var expectRow = false
        for (line in lines) {
            if (expectRow) {
                expectRow = false
                val row = STATE_ROW.find(line)
                if (row != null) {
                    enabled = row.groupValues[5] == "y"
                    continue
                }
            }
            val chain = CHAIN.find(line)
            if (chain != null) {
                session = chain.groupValues[1].toIntOrNull() ?: 0
                effectId = -1
                type = null
                enabled = false
                continue
            }
            val effect = EFFECT.find(line)
            if (effect != null) {
                effectId = effect.groupValues[1].toIntOrNull() ?: -1
                type = null
                enabled = false
                continue
            }
            if (line.contains(STATE_HEADER)) {
                expectRow = true
                continue
            }
            if (effectId >= 0 && type == null) {
                val t = TYPE.find(line)
                if (t != null) {
                    type = t.groupValues[1]
                    continue
                }
            }
            val clients = CLIENTS.find(line) ?: continue
            val kind = type?.let { kindOf(it) }
            if (effectId >= 0 && session > 0 && enabled && kind != null && clients.groupValues[1] == "0") {
                out.add(Orphan(session, effectId, kind))
            }
            effectId = -1
            type = null
        }
        return out
    }

    fun describe(orphan: Orphan): String = "sesi=${orphan.session} ${orphan.kind.label} id=${orphan.effectId}"
}
