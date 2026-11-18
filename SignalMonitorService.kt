package com.signalnama.widget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat

/**
 * سرویس پس‌زمینه سبک برای بروزرسانی خودکار ویجت هر 60 ثانیه
 * یا بلافاصله هنگام تغییر قدرت آنتن سیم‌کارت.
 */
class SignalMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var telephonyManager: TelephonyManager
    private var telephonyCallback: Any? = null

    private val periodicUpdater = object : Runnable {
        override fun run() {
            SignalWidgetProvider.updateAllWidgets(this@SignalMonitorService)
            handler.postDelayed(this, 60000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        startForeground(1001, buildNotification())
        registerSignalListener()
        handler.post(periodicUpdater)
    }

    private fun registerSignalListener() {
        if (!SignalReader.hasRequiredPermissions(this)) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                    SignalWidgetProvider.updateAllWidgets(this@SignalMonitorService)
                }
            }
            telephonyManager.registerTelephonyCallback(mainExecutor, callback)
            telephonyCallback = callback
        } else {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
                    SignalWidgetProvider.updateAllWidgets(this@SignalMonitorService)
                }
            }
            @Suppress("DEPRECATION")
            telephonyManager.listen(listener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
            telephonyCallback = listener
        }
    }

    private fun buildNotification(): Notification {
        val channelId = "signal_monitor_channel"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "پایش زنده سیگنال سیم‌کارت",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("سیگنال‌نما")
            .setContentText("در حال پایش زنده قدرت سیگنال برای ویجت صفحه اصلی")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(periodicUpdater)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (telephonyCallback as? TelephonyCallback)?.let {
                telephonyManager.unregisterTelephonyCallback(it)
            }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
