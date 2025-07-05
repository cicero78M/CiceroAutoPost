# Check AI Comment

Dokumen ini diganti untuk menampilkan contoh aplikasi Android sederhana yang
memanfaatkan `AccessibilityService` guna mengisi kolom komentar pada aplikasi
Instagram secara otomatis. Komentar diinput melalui `EditText` di `MainActivity`
kemudian dikirim ke service agar dituliskan ke postingan yang sedang dibuka dan
menekan tombol "Post".

## Kode Kotlin
```kotlin
// MainActivity.kt
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val commentInput = findViewById<EditText>(R.id.input_comment)
        findViewById<Button>(R.id.button_send_comment).setOnClickListener {
            val intent = Intent(ACTION_INPUT_COMMENT).apply {
                putExtra(EXTRA_COMMENT, commentInput.text.toString())
            }
            sendBroadcast(intent)
        }
    }
}
```

```xml
<!-- res/layout/activity_main.xml -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:orientation="vertical"
    android:padding="16dp"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <EditText
        android:id="@+id/input_comment"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="Tulis komentar"/>

    <Button
        android:id="@+id/button_send_comment"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Kirim"/>
</LinearLayout>
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

Dengan konfigurasi di atas, ketika tombol pada `MainActivity` diklik, komentar
yang diinput akan diisi pada kolom komentar postingan Instagram yang sedang
terbuka lalu tombol "Post" ditekan secara otomatis.
