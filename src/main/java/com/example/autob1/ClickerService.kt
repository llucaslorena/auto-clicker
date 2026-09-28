package com.example.autob1

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import rikka.shizuku.Shizuku

class ClickerService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingOverlay: LinearLayout
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var btnStartStop: Button

    // Scope to run the click loop on background thread without freezing the UI
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var clickJob: Job? = null
    private var isRunning = false
    private var intervalMs: Long = 1000L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification() // Required to prevent Android from killing the background service
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupFloatingOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intervalMs = intent?.getLongExtra(EXTRA_INTERVAL_MS, 1000L) ?: 1000L
        
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val channelId = "ClickerServiceChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Auto Clicker Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Auto Clicker Active")
            .setContentText("Floating clicker is active on screen.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()

        startForeground(1, notification)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingOverlay() {
        floatingOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#99000000"))
            setPadding(10, 10, 10, 10)
        }

        // DRAG HANDLE - only dragging this bar moves the overlay
        val dragHandle = TextView(this).apply {
            text = "move"
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#000000"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 20
            }
            setPadding(0, 10, 0, 10)
        }

        // Crosshair target marker
        val targetMarker = TextView(this).apply {
            text = "X"
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            textSize = 16f
            setPadding(0, 0, 0, 20)
        }

        btnStartStop = Button(this).apply {
            text = "loop"
            setOnClickListener {
                if (isRunning) stopClickLoop() else startClickLoop()
            }
        }

        floatingOverlay.addView(dragHandle)
        floatingOverlay.addView(targetMarker)
        floatingOverlay.addView(btnStartStop)

        val panelWidthPx = (90 * resources.displayMetrics.density).toInt()
        layoutParams = WindowManager.LayoutParams(
            panelWidthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 200
            y = 400
        }

        // Touch listener for dragging the handle
        dragHandle.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingOverlay, layoutParams)
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(floatingOverlay, layoutParams)
    }

    private fun startClickLoop() {
        isRunning = true
        btnStartStop.text = "stop"
        
        // Subtract 50ms to compensate for shell process and IPC execution overhead
        val adjustedDelayMs = (intervalMs - 50L).coerceAtLeast(0L)

        clickJob = serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(adjustedDelayMs)
                val targetX = layoutParams.x + (floatingOverlay.width / 2)
                val targetY = layoutParams.y + floatingOverlay.height - 45
                try {
                    // 1. Temporarily make overlay untouchable so the tap passes to the underlying app
                    withContext(Dispatchers.Main) {
                        layoutParams.flags = layoutParams.flags or
                                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        windowManager.updateViewLayout(floatingOverlay, layoutParams)
                    }

                    // 2. Dispatch tap command via Shizuku
                    val command = "input tap $targetX $targetY"
                    val process = Shizuku.newProcess(
                        arrayOf("sh", "-c", command), null, null
                    )
                    process.waitFor()

                    // 3. Restore overlay touchability
                    withContext(Dispatchers.Main) {
                        layoutParams.flags = layoutParams.flags and
                                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                        windowManager.updateViewLayout(floatingOverlay, layoutParams)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Ensure the overlay does not get stuck in untouchable state
                    withContext(Dispatchers.Main) {
                        layoutParams.flags = layoutParams.flags and
                                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                        windowManager.updateViewLayout(floatingOverlay, layoutParams)
                    }
                }
            }
        }
    }

    private fun stopClickLoop() {
        isRunning = false
        btnStartStop.text = "loop"
        clickJob?.cancel() // Cancel the loop immediately
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        if (::floatingOverlay.isInitialized) {
            windowManager.removeView(floatingOverlay)
        }
    }

    companion object {
        const val EXTRA_INTERVAL_MS = "EXTRA_INTERVAL_MS"
    }
}