#!/usr/bin/env python3
"""Cek statis standar level preset built-in Boomly (Batch 263). Pakai: python3 docs/preset_loudness_check.py [BoosterScreen.kt]

Membaca tabel `presets` di BoosterScreen.kt lalu menghitung kenaikan level tiap preset terhadap Flat (nominal, dB) dengan model sandbox:
 - EQ: 5 band [60/230/910/3600/14000 Hz], gain diinterpolasi linear di sumbu log-frekuensi (aproksimasi, bukan filter Android asli);
 - BassBoost: shelf rendah (<150 Hz), +9 dB pada strength 1000 (ASUMSI vendor-dependent, NOT VERIFIED);
 - LoudnessEnhancer: gain nominal loudness/100 dB (efektif di device lebih kecil karena kompresi adaptif);
 - spektrum musik: energi per oktaf sama sampai 1 kHz, -1,5 dB/oktaf di atasnya; bobot K (ITU-R BS.1770) = K, bobot speaker HP = K x HP 350 Hz x LP 9 kHz.
Standar: H1 |band| <= 800; L1 rata-rata(K, speaker) = 3,0 +-0,5 dB; L2 K <= 5,5 dan speaker >= 0,5; L3 loudness <= 500, bass <= 700, EQ 60 Hz <= +500.
Flat WAJIB nol semua. Model BUKAN pengukuran DynamicsProcessing/efek asli: angka relatif, bukan jaminan di HP."""
import re
import sys

import numpy as np
from scipy import signal

SRC = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/java/com/audioenhancer/booster/BoosterScreen.kt"
FS = 48000
f = np.logspace(np.log10(20), np.log10(20000), 4000)
w = 2 * np.pi * f / FS


def mag2(b, a):
    return np.abs(signal.freqz(b, a, worN=w)[1]) ** 2


K = mag2([1.53512485958697, -2.69169618940638, 1.19839281085285], [1, -1.69065929318241, 0.73248077421585]) * \
    mag2([1, -2, 1], [1, -1.99004745483398, 0.99007225036621])
P = 1 / f * np.where(f < 1000, 1.0, (f / 1000) ** -0.5)
hb, ha = signal.butter(2, 350, "high", fs=FS, output="ba")
lb, la = signal.butter(2, 9000, "low", fs=FS, output="ba")
SPK = K * mag2(hb, ha) * mag2(lb, la)
CEN = np.array([60, 230, 910, 3600, 14000.0])


def delta_db(g_db, weight):
    return 10 * np.log10(np.trapezoid(P * weight * 10 ** (g_db / 10), f) / np.trapezoid(P * weight, f))


def parse(path):
    text = open(path, encoding="utf-8").read()
    start = text.index("val presets = listOf(")
    block = text[start:text.index("var bass by remember", start)]
    out = {}
    for m in re.finditer(r"stringResource\(R\.string\.preset_(\w+)\), bass = (\d+)f, virtualizer = (\d+)f, loudness = (\d+)f,\s*eqBands = listOf\(([^)]*)\)", block):
        out[m.group(1)] = (int(m.group(2)), int(m.group(3)), int(m.group(4)), [int(x) for x in m.group(5).split(",")])
    return out


def main():
    presets = parse(SRC)
    assert len(presets) == 9, f"harus 9 preset, ketemu {len(presets)}"
    bad = []
    print(f"{'preset':13s} bass virt  LE   EQ(mB)                       K_dB  Spk_dB mean")
    for name, (bass, virt, le, eq) in presets.items():
        g = np.interp(np.log(f), np.log(CEN), np.array(eq, float) / 100) + (bass / 1000) * 9.0 / np.sqrt(1 + (f / 150) ** 4)
        k = le / 100 + delta_db(g, K)
        s = le / 100 + delta_db(g, SPK)
        mean = (k + s) / 2
        print(f"{name:13s}{bass:5d}{virt:5d}{le:5d}   {str(eq):28s}{k:6.1f}{s:7.1f}{mean:6.1f}")
        if len(eq) != 5 or any(abs(x) > 800 for x in eq):
            bad.append(f"{name}: H1 (|band| <= 800 mB, 5 band)")
        if name == "flat":
            if any([bass, virt, le] + eq):
                bad.append("flat: harus nol semua")
            continue
        if abs(mean - 3.0) > 0.5:
            bad.append(f"{name}: L1 rata-rata {mean:.1f} dB di luar 3,0 +-0,5")
        if k > 5.5 or s < 0.5:
            bad.append(f"{name}: L2 K={k:.1f} (maks 5,5) / speaker={s:.1f} (min 0,5)")
        if le > 500 or bass > 700 or eq[0] > 500:
            bad.append(f"{name}: L3 loudness={le} (maks 500) bass={bass} (maks 700) EQ60Hz={eq[0]} (maks 500)")
    if bad:
        print("\nMELANGGAR STANDAR:")
        for b in bad:
            print(" -", b)
        sys.exit(1)
    print("\nSEMUA PRESET LOLOS H1 + L1-L3 (model sandbox, NOT VERIFIED di HP)")


if __name__ == "__main__":
    main()
