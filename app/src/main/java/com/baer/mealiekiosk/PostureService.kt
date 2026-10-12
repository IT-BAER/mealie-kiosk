package com.baer.mealiekiosk

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.os.PowerManager
import android.util.Log

private const val CHANNEL = "posture"
private const val WAKE_MS = 3000L

/** Holds the `force-lock` policy that lets the service switch the display off. */
class LockAdmin : DeviceAdminReceiver()

/**
 * Keeps the accelerometer running while the display sleeps, so the tablet wakes when it is
 * flipped down out of the cabinet mount and sleeps when it is stowed. Without an accelerometer
 * or without an active device admin the display simply stays awake.
 */
class PostureService : Service(), SensorEventListener {
    private val detector = TiltDetector()
    private val admin by lazy { ComponentName(this, LockAdmin::class.java) }
    private lateinit var sensors: SensorManager
    private lateinit var power: PowerManager
    private lateinit var policy: DevicePolicyManager
    private var cpu: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        sensors = getSystemService(SensorManager::class.java)
        power = getSystemService(PowerManager::class.java)
        policy = getSystemService(DevicePolicyManager::class.java)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Halterung", NotificationManager.IMPORTANCE_MIN),
        )
        startForeground(
            1,
            Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentTitle("Mealie Kiosk")
                .build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        val accelerometer = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer == null) {
            Log.w(TAG, "no accelerometer, display stays awake")
            return
        }
        cpu = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MealieKiosk:posture")
            .apply { acquire() }
        sensors.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        sensors.unregisterListener(this)
        cpu?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(e: SensorEvent) {
        when (detector.sample(e.values[0], e.values[1], e.values[2], e.timestamp)) {
            Posture.STOWED -> sleep()
            Posture.DEPLOYED -> wake()
            null -> Unit
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun sleep() {
        if (!policy.isAdminActive(admin)) {
            Log.w(TAG, "stowed but device admin is not active, display stays awake")
            return
        }
        Log.i(TAG, "stowed, display off")
        policy.lockNow()
    }

    @Suppress("DEPRECATION")
    private fun wake() {
        Log.i(TAG, "deployed, display on")
        MainActivity.deployed = true
        power.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "MealieKiosk:wake",
        ).acquire(WAKE_MS)
        // The wake lock only lights the display. Without this the kiosk stays behind the lock
        // surface, nothing holds the screen on, and it sleeps again when the lock times out.
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
