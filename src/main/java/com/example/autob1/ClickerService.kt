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
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import rikka.shizuku.Shizuku

class ClickerService : Service() {

    private lateinit var windowManager: WindowManager

    // --- The 3 segments that together form a single floating square ---
    // 1. Top Section: "move" handle (Touchable)
    private lateinit var topOverlay: TextView
    private lateinit var topLayoutParams: WindowManager.LayoutParams

    // 2. Middle Section: "X" crosshair (PERMANENTLY UNTOUCHABLE)
    private lateinit var middleOverlay: TextView
    private lateinit var middleLayoutParams: WindowManager.LayoutParams

    // 3. Bottom Section: "loop / stop" button (Touchable)
    private lateinit var bottomOverlay: Button
    private lateinit var bottomLayoutParams: WindowManager.LayoutParams

    private var panelWidthPx = 0
    private var topHeightPx = 0
    private var middleHeightPx = 0
    private var bottomHeightPx = 0

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var clickJob: Job? = null
    private var isRunning = false
    private var intervalMs: Long = 1000L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupSquareWithUntouchableCenter()
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
    private fun setupSquareWithUntouchableCenter() {
        val density = resources.displayMetrics.density
        panelWidthPx = (90 * density).toInt()
        topHeightPx = (32 * density).toInt()
        middleHeightPx = (45 * density).toInt()
        bottomHeightPx = (45 * density).toInt()

        val startX = 200
        val startY = 350

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

        // --- 1. TOP BAR: "move" (Touchable drag handle) ---
        topOverlay = TextView(this).apply {
            text = "move"
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#E6000000")) // Solid dark top
        }

        topLayoutParams = WindowManager.LayoutParams(
            panelWidthPx,
            topHeightPx,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = startX
            y = startY
        }

        // --- 2. MIDDLE HOLE: "X" (PERMANENTLY UNTOUCHABLE) ---
        middleOverlay = TextView(this).apply {
            text = "X"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#66000000")) // Semi-transparent middle
        }

        middleLayoutParams = WindowManager.LayoutParams(
            panelWidthPx,
            middleHeightPx,
            overlayType,
            // FLAG_NOT_TOUCHABLE permanently: clicks pass straight through!
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = startX
            y = startY + topHeightPx
        }

        // --- 3. BOTTOM BAR: "loop" / "stop" Button (Touchable) ---
        bottomOverlay = Button(this).apply {
            text = "loop"
            textSize = 12f
            setOnClickListener {
                if (isRunning) stopClickLoop() else startClickLoop()
            }
        }

        bottomLayoutParams = WindowManager.LayoutParams(
            panelWidthPx,
            bottomHeightPx,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = startX
            y = startY + topHeightPx + middleHeightPx
        }

        // --- DRAG HANDLE: Moves all 3 sections together as a single square ---
        topOverlay.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = topLayoutParams.x
                        initialY = topLayoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()

                        val newX = initialX + dx
                        val newY = initialY + dy

                        // Update all 3 segments together
                        topLayoutParams.x = newX
                        topLayoutParams.y = newY

                        middleLayoutParams.x = newX
                        middleLayoutParams.y = newY + topHeightPx

                        bottomLayoutParams.x = newX
                        bottomLayoutParams.y = newY + topHeightPx + middleHeightPx

                        windowManager.updateViewLayout(topOverlay, topLayoutParams)
                        windowManager.updateViewLayout(middleOverlay, middleLayoutParams)
                        windowManager.updateViewLayout(bottomOverlay, bottomLayoutParams)
                        return true
                    }
                }
                return false
            }
        })

        // Add all 3 seamlessly to the WindowManager
        windowManager.addView(topOverlay, topLayoutParams)
        windowManager.addView(middleOverlay, middleLayoutParams)
        windowManager.addView(bottomOverlay, bottomLayoutParams)
    }

    private fun startClickLoop() {
        isRunning = true
        bottomOverlay.text = "stop"

        clickJob = serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(intervalMs)

                // Calculate the exact center of the "X" in the middle hole
                val targetX = middleLayoutParams.x + (panelWidthPx / 2)
                val targetY = middleLayoutParams.y + (middleHeightPx / 2) + 105

                // Dispatch tap directly: middleOverlay is permanently untouchable!
                // NO flag toggling, NO Main thread switching, ZERO lag!
                val command = "input tap $targetX $targetY"
                val process = Shizuku.newProcess(
                    arrayOf("sh", "-c", command), null, null
                )
                process.waitFor()
            }
        }
    }

    private fun stopClickLoop() {
        isRunning = false
        bottomOverlay.text = "loop"
        clickJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        if (::topOverlay.isInitialized) windowManager.removeView(topOverlay)
        if (::middleOverlay.isInitialized) windowManager.removeView(middleOverlay)
        if (::bottomOverlay.isInitialized) windowManager.removeView(bottomOverlay)
    }

    companion object {
        const val EXTRA_INTERVAL_MS = "EXTRA_INTERVAL_MS"
    }
}