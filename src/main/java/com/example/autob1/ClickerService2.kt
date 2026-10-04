package com.example.autob1

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import rikka.shizuku.Shizuku

class ClickerService2 : Service() {

    private lateinit var windowManager: WindowManager

    // Visual layer (draws everything: square + circle hole + borders)
    // FLAG_NOT_TOUCHABLE — purely decorative, never blocks clicks
    private lateinit var bgView: BgSquareView2
    private lateinit var bgParams: WindowManager.LayoutParams

    // 4 transparent drag strips around the ring
    private lateinit var topStrip: View
    private lateinit var topStripParams: WindowManager.LayoutParams
    private lateinit var botStrip: View
    private lateinit var botStripParams: WindowManager.LayoutParams
    private lateinit var leftStrip: View
    private lateinit var leftStripParams: WindowManager.LayoutParams
    private lateinit var rightStrip: View
    private lateinit var rightStripParams: WindowManager.LayoutParams

    // Top semi-circle: start/stop button (touchable)
    private lateinit var topHalf: TopSemiView2
    private lateinit var topHalfParams: WindowManager.LayoutParams

    private var squareSizePx   = 0
    private var circleRadiusPx = 0
    private var circleDiamPx   = 0
    private var circleOffsetPx = 0

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var clickJob: Job? = null
    private var isRunning      = false
    private var intervalMs: Long = 1000L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupOverlays()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intervalMs = intent?.getLongExtra(EXTRA_INTERVAL_MS, 1000L) ?: 1000L
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val channelId = "ClickerService2Channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Auto Clicker 2 Service", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Auto Clicker 2 Active")
            .setContentText("Floating clicker 2 (red) is active on screen.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
        startForeground(2, notification)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupOverlays() {
        val dp = resources.displayMetrics.density
        squareSizePx   = (160 * dp).toInt()
        circleRadiusPx = (46  * dp).toInt()
        circleDiamPx   = circleRadiusPx * 2
        circleOffsetPx = (squareSizePx - circleDiamPx) / 2

        // Offset from the first clicker so both are visible
        val sx = 300; val sy = 550

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

        val TOUCH    = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        val NO_TOUCH = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                       WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

        // ── VISUAL BACKGROUND ─────────────────────────────────────────────
        bgView   = BgSquareView2(this, circleRadiusPx.toFloat())
        bgParams = wlp(squareSizePx, squareSizePx, overlayType, NO_TOUCH, sx, sy)

        // ── TOP STRIP ─────────────────────────────────────────────────────
        topStrip       = View(this)
        topStripParams = wlp(squareSizePx, circleOffsetPx, overlayType, TOUCH, sx, sy)

        // ── BOTTOM STRIP ──────────────────────────────────────────────────
        botStrip       = View(this)
        botStripParams = wlp(
            squareSizePx, circleOffsetPx, overlayType, TOUCH,
            sx, sy + circleOffsetPx + circleDiamPx
        )

        // ── LEFT STRIP ───────────────────────────────────────────────────
        leftStrip       = View(this)
        leftStripParams = wlp(
            circleOffsetPx, circleDiamPx, overlayType, TOUCH,
            sx, sy + circleOffsetPx
        )

        // ── RIGHT STRIP ──────────────────────────────────────────────────
        rightStrip       = View(this)
        rightStripParams = wlp(
            circleOffsetPx, circleDiamPx, overlayType, TOUCH,
            sx + circleOffsetPx + circleDiamPx, sy + circleOffsetPx
        )

        // ── TOP SEMI-CIRCLE: start/stop button ───────────────────────────
        topHalf      = TopSemiView2(this) { if (isRunning) stopClickLoop() else startClickLoop() }
        topHalfParams = wlp(
            circleDiamPx, circleRadiusPx, overlayType, TOUCH,
            sx + circleOffsetPx, sy + circleOffsetPx
        )

        // ── DRAG LISTENER ────────────────────────────────────────────────
        val drag = makeDragListener()
        topStrip.setOnTouchListener(drag)
        botStrip.setOnTouchListener(drag)
        leftStrip.setOnTouchListener(drag)
        rightStrip.setOnTouchListener(drag)

        // Add in Z order
        windowManager.addView(bgView,      bgParams)
        windowManager.addView(topStrip,    topStripParams)
        windowManager.addView(botStrip,    botStripParams)
        windowManager.addView(leftStrip,   leftStripParams)
        windowManager.addView(rightStrip,  rightStripParams)
        windowManager.addView(topHalf,     topHalfParams)
    }

    /** Creates a WindowManager.LayoutParams with common settings */
    private fun wlp(w: Int, h: Int, type: Int, flags: Int, x: Int, y: Int) =
        WindowManager.LayoutParams(w, h, type, flags, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x; this.y = y
        }

    @SuppressLint("ClickableViewAccessibility")
    private fun makeDragListener(): View.OnTouchListener {
        var initX = 0;     var initY = 0
        var initTX = 0f;   var initTY = 0f

        return View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = bgParams.x;  initY = bgParams.y
                    initTX = event.rawX; initTY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    moveAll(
                        initX + (event.rawX - initTX).toInt(),
                        initY + (event.rawY - initTY).toInt()
                    )
                    true
                }
                else -> false
            }
        }
    }

    /** Moves all windows together as one unit */
    private fun moveAll(nx: Int, ny: Int) {
        bgParams.x        = nx;                     bgParams.y        = ny
        topStripParams.x  = nx;                     topStripParams.y  = ny
        botStripParams.x  = nx;                     botStripParams.y  = ny + circleOffsetPx + circleDiamPx
        leftStripParams.x = nx;                     leftStripParams.y = ny + circleOffsetPx
        rightStripParams.x = nx + circleOffsetPx + circleDiamPx
        rightStripParams.y = ny + circleOffsetPx
        topHalfParams.x   = nx + circleOffsetPx;   topHalfParams.y   = ny + circleOffsetPx

        windowManager.updateViewLayout(bgView,     bgParams)
        windowManager.updateViewLayout(topStrip,   topStripParams)
        windowManager.updateViewLayout(botStrip,   botStripParams)
        windowManager.updateViewLayout(leftStrip,  leftStripParams)
        windowManager.updateViewLayout(rightStrip, rightStripParams)
        windowManager.updateViewLayout(topHalf,    topHalfParams)
    }

    private fun startClickLoop() {
        isRunning = true
        topHalf.setLabel("stop")

        clickJob = serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(intervalMs)

                var targetX = 0
                var targetY = 0
                withContext(Dispatchers.Main) {
                    val location = IntArray(2)
                    bgView.getLocationOnScreen(location)
                    targetX = location[0] + (squareSizePx / 2)
                    targetY = location[1] + (squareSizePx / 2) + (circleRadiusPx / 2)
                }

                val process = Shizuku.newProcess(
                    arrayOf("sh", "-c", "input tap $targetX $targetY"), null, null
                )
                process.waitFor()
            }
        }
    }

    private fun stopClickLoop() {
        isRunning = false
        topHalf.setLabel("start")
        clickJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        try {
            if (::bgView.isInitialized     && bgView.isAttachedToWindow)     windowManager.removeView(bgView)
            if (::topStrip.isInitialized   && topStrip.isAttachedToWindow)   windowManager.removeView(topStrip)
            if (::botStrip.isInitialized   && botStrip.isAttachedToWindow)   windowManager.removeView(botStrip)
            if (::leftStrip.isInitialized  && leftStrip.isAttachedToWindow)  windowManager.removeView(leftStrip)
            if (::rightStrip.isInitialized && rightStrip.isAttachedToWindow) windowManager.removeView(rightStrip)
            if (::topHalf.isInitialized    && topHalf.isAttachedToWindow)    windowManager.removeView(topHalf)
        } catch (e: Exception) { e.printStackTrace() }
    }

    companion object {
        const val EXTRA_INTERVAL_MS = "EXTRA_INTERVAL_MS"
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CUSTOM VIEWS — RED THEME
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Draws the visual design with a RED color theme.
     * Dark square with a circular hole, red borders and accents.
     */
    inner class BgSquareView2(context: Context, private val radius: Float) : View(context) {
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CC1A0D0D") // dark red-tinted background
        }
        private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        private val squareBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#55FF3333") // red border
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        private val circleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#88FF3333") // red rim
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#66FF3333") // red divider
            strokeWidth = 2f
        }

        init { setLayerType(LAYER_TYPE_SOFTWARE, null) }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            val inset   = 3f
            val corner  = 12f
            val rect    = RectF(inset, inset, w - inset, h - inset)
            val cx = w / 2f; val cy = h / 2f

            // 1. Dark red square background
            canvas.drawRoundRect(rect, corner, corner, bgPaint)
            // 2. Punch the circle hole
            canvas.drawCircle(cx, cy, radius, clearPaint)

            // 2.5 Bottom semi-circle (dark red) and X
            val bottomSemiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EE1A0D0D") }
            val bottomPath = Path()
            bottomPath.addArc(RectF(cx - radius, cy - radius, cx + radius, cy + radius), 0f, 180f)
            bottomPath.close()
            canvas.drawPath(bottomPath, bottomSemiPaint)

            val xPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#AAFFFFFF")
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
                textSize = radius * 1.04f
            }
            canvas.drawText("X", cx, cy + radius * 0.75f, xPaint)

            // 3. Square border
            canvas.drawRoundRect(rect, corner, corner, squareBorderPaint)
            // 4. Circle rim border
            canvas.drawCircle(cx, cy, radius, circleBorderPaint)
            // 5. Horizontal divider
            canvas.drawLine(cx - radius, cy, cx + radius, cy, dividerPaint)
        }
    }

    /**
     * Top semi-circle: the start/stop button (RED theme).
     */
    inner class TopSemiView2(context: Context, private val onClick: () -> Unit) : View(context) {
        private var label = "start"
        private val bgPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EE1A0D0D") }
        private val txtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
        }
        private val path = Path()

        init {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
            setOnClickListener { onClick() }
        }

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            txtPaint.textSize = h * 0.42f
        }

        fun setLabel(text: String) { label = text; invalidate() }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            path.reset()
            path.addArc(RectF(0f, 0f, w, h * 2f), 180f, 180f)
            path.close()
            canvas.drawPath(path, bgPaint)
            canvas.drawText(label, w / 2f, h * 0.72f, txtPaint)
        }
    }

}
