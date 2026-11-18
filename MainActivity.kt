package com.signalnama.widget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnPinWidget: Button
    private lateinit var btnStartService: Button

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshDiagnostics()
        SignalWidgetProvider.updateAllWidgets(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tv_diagnostics)
        btnPinWidget = findViewById(R.id.btn_pin_widget)
        btnStartService = findViewById(R.id.btn_toggle_service)

        findViewById<Button>(R.id.btn_request_permissions).setOnClickListener {
            requestNeededPermissions()
        }

        btnPinWidget.setOnClickListener {
            pinWidgetToHomeScreen()
        }

        btnStartService.setOnClickListener {
            val serviceIntent = Intent(this, SignalMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Toast.makeText(this, "پایش زنده ویجت فعال شد", Toast.LENGTH_SHORT).show()
        }

        if (!SignalReader.hasRequiredPermissions(this)) {
            requestNeededPermissions()
        }
        refreshDiagnostics()
    }

    override fun onResume() {
        super.onResume()
        refreshDiagnostics()
    }

    private fun requestNeededPermissions() {
        val perms = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    private fun refreshDiagnostics() {
        val sims = SignalReader.readActiveSims(this)
        val report = sims.joinToString("\n\n") { s ->
            "سیم‌کارت ${s.slotIndex + 1} (${s.operatorName})\n" +
                "شبکه: ${s.networkTech} | قدرت: ${s.rsrpDbm} dBm\n" +
                "کیفیت: ${s.qualityLabel} (ASU: ${s.asuLevel})"
        }
        tvStatus.text = report
    }

    private fun pinWidgetToHomeScreen() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val provider = ComponentName(this, SignalWidgetProvider::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            appWidgetManager.isRequestPinAppWidgetSupported
        ) {
            appWidgetManager.requestPinAppWidget(provider, null, null)
        } else {
            Toast.makeText(
                this,
                "لانچر گوشی شما از افزودن خودکار پشتیبانی نمی‌کند؛ ویجت را دستی به صفحه اصلی اضافه کنید.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
