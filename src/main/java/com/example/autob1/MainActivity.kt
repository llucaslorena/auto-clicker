package com.example.autob1

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private val SHIZUKU_CODE = 100
    private lateinit var etInterval: EditText

    // Listens for the user's response in the Shizuku permission dialog
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_CODE && grantResult == PackageManager.PERMISSION_GRANTED) {
            checkPermissionsAndStart()
        } else {
            Toast.makeText(this, "Shizuku permission denied!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Register Shizuku listener
        Shizuku.addRequestPermissionResultListener(permissionListener)

        // Vertical layout for input field and start button
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(60, 60, 60, 60)
        }

        val tvLabel = TextView(this).apply {
            text = "Click Interval (ms):"
            textSize = 16f
            setPadding(0, 0, 0, 16)
        }

        etInterval = EditText(this).apply {
            hint = "e.g. 1000 (1 second)"
            setText("1000") // Default to 1000ms
            inputType = InputType.TYPE_CLASS_NUMBER
            textSize = 18f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 30
            }
        }

        val btnStart = Button(this).apply {
            text = "Start Auto Clicker"
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                checkPermissionsAndStart()
            }
        }

        layout.addView(tvLabel)
        layout.addView(etInterval)
        layout.addView(btnStart)

        setContentView(layout)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister listener to prevent memory leaks
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }

    private fun checkPermissionsAndStart() {
        // 1. Verify if Shizuku service is running
        if (!Shizuku.pingBinder()) {
            Toast.makeText(this, "Shizuku is not running!", Toast.LENGTH_LONG).show()
            return
        }

        // 2. Check for Shizuku permission
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(SHIZUKU_CODE)
            return
        }

        // 3. Check for Screen Overlay ("Display over other apps") permission
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            Toast.makeText(this, "Please enable overlay permission and try again", Toast.LENGTH_LONG).show()
            return
        }

        // 4. Retrieve input interval (minimum 50ms guard against system lockup)
        val intervalMs = etInterval.text.toString().toLongOrNull()?.coerceAtLeast(50L) ?: 1000L

        // 5. Start the floating service with the interval extra
        val serviceIntent = Intent(this, ClickerService::class.java).apply {
            putExtra(ClickerService.EXTRA_INTERVAL_MS, intervalMs)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        finish() // Minimize activity and display only the floating overlay
    }
}