package com.example.backgroundservices

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.backgroundservices.MainActivity.Companion.ACTION_SERVICE_STOPPED_JUMP
import com.example.backgroundservices.MainActivity.Companion.ACTION_START_EXERCISE
import com.example.backgroundservices.MainActivity.Companion.EXTRA_JUMP_COUNT
import kotlin.math.abs
import kotlin.math.sqrt

class TrackerService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private val NOTIFICATION_CHANNEL_ID_SERVICE_RUNNING = "tracker_service_running"
    private val NOTIFICATION_CHANNEL_ID_JUMP_DETECTED = "jump_detected"
    private val NOTIFICATION_ID_SERVICE_RUNNING = 1
    private val NOTIFICATION_ID_JUMP_DETECTED = 2
    private var jumpCount = 0
    private var isExerciseRunning = false
    private val jumpThreshold = 12.0f
    private val maxJumpsTarget = 3

    override fun onBind(p0: Intent?): IBinder? = null

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {}

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        if (isExerciseRunning) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val gVector = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

            if (gVector > jumpThreshold) {
                jumpCount++
                Log.d("TrackerService", "Lompatan/Langkah Latihan ke-$jumpCount terdeteksi!")

                if (jumpCount >= maxJumpsTarget) {
                    finishExercise()
                }
            }
        } else {
            val y = event.values[1]
            val singleJumpThreshold = 15.0

            if (abs(y) > singleJumpThreshold) {
                Log.d("TrackerService", "LOMPATAN CODELAB TERDETEKSI! y: $y")
                sensorManager.unregisterListener(this)
                showJumpNotification()
                stopSelf()
            }
        }
    }

    private fun finishExercise() {
        isExerciseRunning = false
        sensorManager.unregisterListener(this)

        showJumpNotification()

        val intent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_SERVICE_STOPPED_JUMP
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_JUMP_COUNT, jumpCount)
        }
        startActivity(intent)

        stopSelf()
    }

    private fun createNotificationsChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID_SERVICE_RUNNING,
                "Service Running Channel",
                NotificationManager.IMPORTANCE_LOW
            )

            val jumpChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID_JUMP_DETECTED,
                "Jump Detected Channel",
                NotificationManager.IMPORTANCE_HIGH
            )

            manager?.createNotificationChannel(serviceChannel)
            manager?.createNotificationChannel(jumpChannel)
        }
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        createNotificationsChannel()
        Log.d("TrackerService", "Service onCreate")
    }

    private fun startJumpDetection() {
        accelerometer?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("TrackerService", "Service onStartCommand")

        if (intent?.action == ACTION_START_EXERCISE) {
            isExerciseRunning = true
            jumpCount = 0
        } else {
            isExerciseRunning = false
        }

        startForeground(
            NOTIFICATION_ID_SERVICE_RUNNING,
            buildServiceRunningNotification()
        )

        startJumpDetection()
        return START_STICKY
    }

    private fun buildServiceRunningNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID_SERVICE_RUNNING)
            .setContentTitle("Pelacak Aktivitas Latar")
            .setContentText("Aplikasi Tracker Services sedang berjalan..")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun showJumpNotification() {
        val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_SERVICE_STOPPED_JUMP
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingMainActivityIntent = PendingIntent.getActivity(
            this, 1, mainActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val jumpNotification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID_JUMP_DETECTED)
            .setContentTitle("Latihan Selesai!")
            .setContentText("Target lompatan/langkah tercapai. Service berhenti.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingMainActivityIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID_JUMP_DETECTED, jumpNotification)
        } catch (e: SecurityException) {
            Log.e("TrackerService", "Error showing notification: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        Log.d("TrackerService", "Service onDestroy")
    }
}