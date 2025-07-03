package com.cicero.socialtools.core.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.util.Log
import com.cicero.socialtools.R
import com.cicero.socialtools.utils.ConsoleLoading
import kotlinx.coroutines.*

class PostService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Post Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Social Tools")
            .setContentText("Layanan berjalan di latar belakang")
            .setSmallIcon(R.drawable.ic_status_true)
            .build()
        startForeground(1, notification)

        scope.launch { runAutopost() }
        return START_STICKY
    }

    private suspend fun runAutopost() {
        ConsoleLoading.showLoading(tag = "PostService")
        val delays = listOf(1000L, 2000L, 3000L)
        for ((index, d) in delays.withIndex()) {
            Log.d("PostService", "Process ${index + 1} delay: ${d}ms")
            delay(d)
            Log.d("PostService", "Process ${index + 1} executed")
        }
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "post_service"
    }
}

