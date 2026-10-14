package com.edag.seethroughapplication

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log

class AppMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var previousValue: String = "unknown"

    private val checker = object : Runnable {
        override fun run() {

            val currentValue =
                Settings.System.getString(
                    contentResolver,
                    "app_enable"
                ) ?: "true" // MH: Set to "true" for testing

            Log.d("APP_MONITOR", "Current value = $currentValue")

            if (currentValue != previousValue) {

                if (currentValue == "false") {

                    MainActivity.instance?.runOnUiThread {
                        MainActivity.instance?.showDisableDialog()
                    }

                } else if (currentValue == "true") {

                    MainActivity.instance?.restoreApp()
                }

                previousValue = currentValue
            }

            handler.postDelayed(this, 1000)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        Log.d("APP_MONITOR", "SERVICE STARTED")

        checker.run()

        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(checker)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}