# Check AI Comment

Dokumen ini menampilkan contoh pemanfaatan `AccessibilityService` untuk
mengisi kolom komentar Instagram secara otomatis. Teks komentar dikirim lewat
`Broadcast` tanpa kolom input sehingga service akan menuliskannya ke postingan
yang sedang dibuka kemudian menekan tombol "Post".

## Kode Kotlin
```kotlin
// MainActivity.kt
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Kirim komentar otomatis tanpa UI input
        val intent = Intent(ACTION_INPUT_COMMENT).apply {
            putExtra(EXTRA_COMMENT, "Komentar otomatis")
        }
        sendBroadcast(intent)
    }
}
```

```xml
Tidak ada lagi kolom input komentar pada layout.
```

```kotlin
// InstagramCommentService.kt
class InstagramCommentService : AccessibilityService() {
    private var currentComment: String? = null
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_INPUT_COMMENT) {
                currentComment = intent.getStringExtra(EXTRA_COMMENT)
                fillComment()
            }
        }
    }

    override fun onServiceConnected() {
        registerReceiver(receiver, IntentFilter(ACTION_INPUT_COMMENT))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    private fun fillComment() {
        val text = currentComment ?: return
        val root = rootInActiveWindow ?: return
        val input = root.findAccessibilityNodeInfosByViewId(
            "com.instagram.android:id/layout_comment_thread_edittext"
        ).firstOrNull() ?: return
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        input.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        root.findAccessibilityNodeInfosByText("Post")
            .firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }
}
```

## Konfigurasi Service dan Permission
Tambahkan entri berikut di `AndroidManifest.xml`:
```xml
<service
    android:name=".InstagramCommentService"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:exported="false">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService"/>
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/insta_service_config"/>
</service>
```

`res/xml/insta_service_config.xml`:
```xml
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:packageNames="com.instagram.android"
    android:description="@string/service_description"/>
```

Dengan konfigurasi di atas, ketika broadcast berisi komentar dikirim,
`InstagramCommentService` akan mengisi kolom komentar pada postingan yang
sedang terbuka dan menekan tombol "Post" secara otomatis.

### Troubleshooting

Jika log menampilkan pesan **"Root window is null"**, pastikan tampilan
Instagram yang berisi kolom komentar sudah terbuka sepenuhnya. Accessibility
Service membutuhkan jendela aktif yang valid agar bisa menemukan input
komentar. Coba tunggu beberapa detik setelah membuka postingan atau tekan
tombol komentar secara manual sebelum mengirim broadcast.
