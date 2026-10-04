package com.example.autob1

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private val SHIZUKU_CODE = 100
    private lateinit var etInterval: EditText
    private var selectedColor = "blue"

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_CODE && grantResult == PackageManager.PERMISSION_GRANTED) {
            checkPermissionsAndAdd()
        } else {
            Toast.makeText(this, "Shizuku permission denied!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(permissionListener)

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
            setText("1000")
            inputType = InputType.TYPE_CLASS_NUMBER
            textSize = 18f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 30 }
        }

        val tvColorLabel = TextView(this).apply {
            text = "Select Color:"
            textSize = 16f
            setPadding(0, 0, 0, 16)
        }

        val colors = ClickerService.THEMES.keys.toList()
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, colors)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    selectedColor = colors[position]
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 40 }
        }

        val btnAdd = Button(this).apply {
            text = "Add Clicker"
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 20 }
            setOnClickListener { checkPermissionsAndAdd() }
        }

        val btnStopAll = Button(this).apply {
            text = "Stop All Clickers"
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                val intent = Intent(this@MainActivity, ClickerService::class.java).apply {
                    action = ClickerService.ACTION_REMOVE_ALL
                }
                startService(intent)
                Toast.makeText(this@MainActivity, "All clickers stopped", Toast.LENGTH_SHORT).show()
            }
        }

        layout.addView(tvLabel)
        layout.addView(etInterval)
        layout.addView(tvColorLabel)
        layout.addView(spinner)
        layout.addView(btnAdd)
        layout.addView(btnStopAll)

        setContentView(layout)
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }

    private fun checkPermissionsAndAdd() {
        if (!Shizuku.pingBinder()) {
            Toast.makeText(this, "Shizuku is not running!", Toast.LENGTH_LONG).show()
            return
        }

        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(SHIZUKU_CODE)
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            Toast.makeText(this, "Please enable overlay permission and try again", Toast.LENGTH_LONG).show()
            return
        }

        val intervalMs = etInterval.text.toString().toLongOrNull()?.coerceAtLeast(50L) ?: 1000L

        val serviceIntent = Intent(this, ClickerService::class.java).apply {
            action = ClickerService.ACTION_ADD
            putExtra(ClickerService.EXTRA_INTERVAL_MS, intervalMs)
            putExtra(ClickerService.EXTRA_COLOR_NAME, selectedColor)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}