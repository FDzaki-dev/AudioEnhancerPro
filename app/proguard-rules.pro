# Compose & AndroidX sudah menyertakan proguard rules bawaan (consumer rules),
# jadi biasanya tidak perlu tambahan apa pun. Rules di bawah ini jaga-jaga saja.

# Komponen yang dideklarasikan di AndroidManifest.xml (Activity, Service, Receiver)
# otomatis dipertahankan oleh AGP, tidak perlu keep rule manual.

# Jaga metadata Kotlin supaya reflection (kalau ada) tetap jalan.
-keepattributes *Annotation*
-keep class kotlin.Metadata { *; }
