# Instagram Comment Accessibility Guide

Dokumen ini menjelaskan cara menggunakan `InstagramCommentService` untuk menulis komentar secara otomatis ke postingan Instagram. Layanan ini memanfaatkan `AccessibilityService` sehingga proses penulisan komentar dilakukan di aplikasi Instagram langsung.

## Langkah-langkah

1. **Aktifkan layanan aksesibilitas**
   - Buka halaman *AI Comment Check* di aplikasi.
   - Tekan tombol untuk membuka pengaturan aksesibilitas, kemudian aktifkan layanan **SocialTools**.

2. **Buka postingan Instagram**
   - Jalankan aplikasi Instagram dan buka postingan yang ingin dikomentari. Pastikan tampilan komentar sudah terbuka sepenuhnya.

3. **Kirim perintah komentar**
   - Dari aplikasi atau komponen lain, kirim `Broadcast` dengan action `ACTION_INPUT_COMMENT` dan ekstra `EXTRA_COMMENT` berisi teks komentar.
   - Contoh kode Kotlin:

```kotlin
val intent = Intent(LandingActivity.ACTION_INPUT_COMMENT).apply {
    putExtra(LandingActivity.EXTRA_COMMENT, "Komentar otomatis")
}
sendBroadcast(intent)
```

4. **Service menuliskan komentar**
   - `InstagramCommentService` menerima broadcast tersebut lalu mencari kolom komentar, menuliskan teks, dan menekan tombol **Post** secara otomatis.

Jika kolom komentar tidak ditemukan, service akan menampilkan log sehingga memudahkan troubleshooting. Pastikan juga aplikasi Instagram dalam keadaan terbaru agar struktur antarmuka tidak berubah.

