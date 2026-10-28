# Compose & AndroidX sudah menyertakan proguard rules bawaan (consumer rules),
# jadi biasanya tidak perlu tambahan apa pun. Rules di bawah ini jaga-jaga saja.

# Komponen yang dideklarasikan di AndroidManifest.xml (Activity, Service, Receiver)
# otomatis dipertahankan oleh AGP, tidak perlu keep rule manual.

# Jaga metadata Kotlin supaya reflection (kalau ada) tetap jalan.
-keepattributes *Annotation*
-keep class kotlin.Metadata { *; }

# Batch 250: layanan Shizuku (proses shell) dibuat lewat refleksi oleh Shizuku dari nama kelas + konstruktor tanpa argumen; tak ada
# pemanggil langsung di kode, jadi tanpa aturan ini R8 boleh membuang konstruktor/mengganti nama dan tombol Reverb mati HANYA di rilis.
-keep class com.audioenhancer.booster.ShizukuGrantService { *; }
