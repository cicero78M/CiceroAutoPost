package com.cicero.socialtools.ui

import android.graphics.Typeface
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cicero.socialtools.R
import com.cicero.socialtools.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogDataActivity : AppCompatActivity() {
    private val db by lazy { AppDatabase.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_data)
        val container = findViewById<LinearLayout>(R.id.log_data_container)
        lifecycleScope.launch {
            val logs = withContext(Dispatchers.IO) { db.logDao().allLogs() }
            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            for (entry in logs) {
                val tv = TextView(this@LogDataActivity).apply {
                    typeface = Typeface.MONOSPACE
                    text = "${formatter.format(Date(entry.timestamp))} [${entry.user}] ${entry.message}"
                }
                container.addView(tv)
            }
        }
    }
}
