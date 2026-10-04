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

class ClickerService : Service() {

    private lateinit var windowManager: WindowManager
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private val instances = mutableMapOf<Int, ClickerInstance>()

    private var squareSizePx   = 0
    private var circleRadiusPx = 0
    private var circleDiamPx   = 0
    private var circleOffsetPx = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val dp = resources.displayMetrics.density
        squareSizePx   = (160 * dp).toInt()
        circleRadiusPx = (46  * dp).toInt()
        circleDiamPx   = circleRadiusPx * 2
        circleOffsetPx = (squareSizePx - circleDiamPx) / 2
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ADD -> {
                val colorName = intent.getStringExtra(EXTRA_COLOR_NAME) ?: "blue"
                val interval  = intent.getLongExtra(EXTRA_INTERVAL_MS, 1000L)
                val id = nextId++
                val theme = THEMES[colorName] ?: THEMES["blue"]!!
                val instance = ClickerInstance(id, theme, interval)

                // Offset each new widget so they don't stack exactly on top of each other
                val offset = instances.size
                val sx = 200 + (offset % 4) * 70
                val sy = 350 + (offset % 3) * 100

                instance.setup(sx, sy)
                instances[id] = instance
                activeInstances[id] = colorName
                updateNotification()
            }
            ACTION_REMOVE -> {
                val id = intent.getIntExtra(EXTRA_INSTANCE_ID, -1)
                instances[id]?.removeViews()
                instances.remove(id)
                activeInstances.remove(id)
                updateNotification()
                if (instances.isEmpty()) stopSelf()
            }
            ACTION_REMOVE_ALL -> {
                for ((_, inst) in instances) inst.removeViews()
                instances.clear()
                activeInstances.clear()
                stopSelf()
            }
        }
        return START_STICKY
    }

    // ── Notification ─────────────────────────────────────────────────────

    private fun startForegroundNotification() {
        val channelId = "ClickerServiceChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Auto Clicker Service", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Auto Clicker Active")
            .setContentText("Managing floating clickers.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
        startForeground(1, notification)
    }

    private fun updateNotification() {
        val channelId = "ClickerServiceChannel"
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Auto Clicker Active")
            .setContentText("${instances.size} clicker(s) on screen.")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
        getSystemService(NotificationManager::class.java)?.notify(1, notification)
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun wlp(w: Int, h: Int, type: Int, flags: Int, x: Int, y: Int) =
        WindowManager.LayoutParams(w, h, type, flags, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x; this.y = y
        }

    override fun onDestroy() {
        super.onDestroy()
        for ((_, inst) in instances) inst.removeViews()
        instances.clear()
        activeInstances.clear()
        serviceScope.cancel()
    }

    // ── Companion (constants, themes, shared state) ──────────────────────

    companion object {
        const val ACTION_ADD        = "ACTION_ADD"
        const val ACTION_REMOVE     = "ACTION_REMOVE"
        const val ACTION_REMOVE_ALL = "ACTION_REMOVE_ALL"
        const val EXTRA_INTERVAL_MS = "EXTRA_INTERVAL_MS"
        const val EXTRA_COLOR_NAME  = "EXTRA_COLOR_NAME"
        const val EXTRA_INSTANCE_ID = "EXTRA_INSTANCE_ID"

        /** Tracks active widget IDs → color names so the Activity can display them. */
        val activeInstances = mutableMapOf<Int, String>()
        private var nextId = 1

        val THEMES = linkedMapOf(
            "blue"   to ColorTheme("blue",
                Color.parseColor("#CC0D0D1A"), Color.parseColor("#553399FF"),
                Color.parseColor("#883399FF"), Color.parseColor("#663399FF"),
                Color.parseColor("#EE0D0D1A"), Color.parseColor("#3399FF")),
            "red"    to ColorTheme("red",
                Color.parseColor("#CC1A0D0D"), Color.parseColor("#55FF3333"),
                Color.parseColor("#88FF3333"), Color.parseColor("#66FF3333"),
                Color.parseColor("#EE1A0D0D"), Color.parseColor("#FF3333")),
            "green"  to ColorTheme("green",
                Color.parseColor("#CC0D1A0D"), Color.parseColor("#5533CC66"),
                Color.parseColor("#8833CC66"), Color.parseColor("#6633CC66"),
                Color.parseColor("#EE0D1A0D"), Color.parseColor("#33CC66")),
            "yellow" to ColorTheme("yellow",
                Color.parseColor("#CC1A190D"), Color.parseColor("#55FFD633"),
                Color.parseColor("#88FFD633"), Color.parseColor("#66FFD633"),
                Color.parseColor("#EE1A190D"), Color.parseColor("#FFD633")),
            "orange" to ColorTheme("orange",
                Color.parseColor("#CC1A120D"), Color.parseColor("#55FF8C33"),
                Color.parseColor("#88FF8C33"), Color.parseColor("#66FF8C33"),
                Color.parseColor("#EE1A120D"), Color.parseColor("#FF8C33")),
            "purple" to ColorTheme("purple",
                Color.parseColor("#CC120D1A"), Color.parseColor("#55AA44FF"),
                Color.parseColor("#88AA44FF"), Color.parseColor("#66AA44FF"),
                Color.parseColor("#EE120D1A"), Color.parseColor("#AA44FF")),
            "white"  to ColorTheme("white",
                Color.parseColor("#CC1A1A1A"), Color.parseColor("#55DDDDDD"),
                Color.parseColor("#88DDDDDD"), Color.parseColor("#66DDDDDD"),
                Color.parseColor("#EE1A1A1A"), Color.parseColor("#DDDDDD")),
            "black"  to ColorTheme("black",
                Color.parseColor("#CC050505"), Color.parseColor("#55555555"),
                Color.parseColor("#88555555"), Color.parseColor("#66555555"),
                Color.parseColor("#EE080808"), Color.parseColor("#555555"))
        )
    }

    data class ColorTheme(
        val name: String,
        val bgColor: Int,       // dark tinted square background
        val borderColor: Int,   // square border
        val rimColor: Int,      // circle rim
        val dividerColor: Int,  // horizontal divider
        val semiBgColor: Int,   // semi-circle fill
        val dotColor: Int       // pure accent for the UI list
    )

    // ─────────────────────────────────────────────────────────────────────
    // CLICKER INSTANCE — one independent overlay widget
    // ─────────────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    inner class ClickerInstance(
        val id: Int,
        val theme: ColorTheme,
        val intervalMs: Long
    ) {
        private lateinit var bgView: View
        private lateinit var bgParams: WindowManager.LayoutParams
        private lateinit var topStrip: View
        private lateinit var topStripParams: WindowManager.LayoutParams
        private lateinit var botStrip: View
        private lateinit var botStripParams: WindowManager.LayoutParams
        private lateinit var leftStrip: View
        private lateinit var leftStripParams: WindowManager.LayoutParams
        private lateinit var rightStrip: View
        private lateinit var rightStripParams: WindowManager.LayoutParams
        private lateinit var topHalf: ThemedTopSemiView
        private lateinit var topHalfParams: WindowManager.LayoutParams

        private var clickJob: Job? = null
        private var isRunning = false

        fun setup(sx: Int, sy: Int) {
            val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE

            val TOUCH    = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            val NO_TOUCH = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                           WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

            bgView   = ThemedBgSquareView(this@ClickerService, circleRadiusPx.toFloat(), theme)
            bgParams = wlp(squareSizePx, squareSizePx, overlayType, NO_TOUCH, sx, sy)

            topStrip       = View(this@ClickerService)
            topStripParams = wlp(squareSizePx, circleOffsetPx, overlayType, TOUCH, sx, sy)

            botStrip       = View(this@ClickerService)
            botStripParams = wlp(squareSizePx, circleOffsetPx, overlayType, TOUCH,
                sx, sy + circleOffsetPx + circleDiamPx)

            leftStrip       = View(this@ClickerService)
            leftStripParams = wlp(circleOffsetPx, circleDiamPx, overlayType, TOUCH,
                sx, sy + circleOffsetPx)

            rightStrip       = View(this@ClickerService)
            rightStripParams = wlp(circleOffsetPx, circleDiamPx, overlayType, TOUCH,
                sx + circleOffsetPx + circleDiamPx, sy + circleOffsetPx)

            topHalf = ThemedTopSemiView(
                this@ClickerService, 
                theme, 
                onClick = {
                    if (isRunning) stopClickLoop() else startClickLoop()
                },
                onLongClick = {
                    val removeIntent = Intent(this@ClickerService, ClickerService::class.java).apply {
                        action = ACTION_REMOVE
                        putExtra(EXTRA_INSTANCE_ID, id)
                    }
                    startService(removeIntent)
                }
            )
            topHalfParams = wlp(circleDiamPx, circleRadiusPx, overlayType, TOUCH,
                sx + circleOffsetPx, sy + circleOffsetPx)

            val drag = makeDragListener()
            topStrip.setOnTouchListener(drag)
            botStrip.setOnTouchListener(drag)
            leftStrip.setOnTouchListener(drag)
            rightStrip.setOnTouchListener(drag)

            windowManager.addView(bgView,     bgParams)
            windowManager.addView(topStrip,   topStripParams)
            windowManager.addView(botStrip,   botStripParams)
            windowManager.addView(leftStrip,  leftStripParams)
            windowManager.addView(rightStrip, rightStripParams)
            windowManager.addView(topHalf,    topHalfParams)
        }

        private fun makeDragListener(): View.OnTouchListener {
            var initX = 0;   var initY = 0
            var initTX = 0f; var initTY = 0f

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

        private fun moveAll(nx: Int, ny: Int) {
            bgParams.x        = nx;                                bgParams.y        = ny
            topStripParams.x  = nx;                                topStripParams.y  = ny
            botStripParams.x  = nx;                                botStripParams.y  = ny + circleOffsetPx + circleDiamPx
            leftStripParams.x = nx;                                leftStripParams.y = ny + circleOffsetPx
            rightStripParams.x = nx + circleOffsetPx + circleDiamPx
            rightStripParams.y = ny + circleOffsetPx
            topHalfParams.x   = nx + circleOffsetPx;              topHalfParams.y   = ny + circleOffsetPx

            windowManager.updateViewLayout(bgView,     bgParams)
            windowManager.updateViewLayout(topStrip,   topStripParams)
            windowManager.updateViewLayout(botStrip,   botStripParams)
            windowManager.updateViewLayout(leftStrip,  leftStripParams)
            windowManager.updateViewLayout(rightStrip, rightStripParams)
            windowManager.updateViewLayout(topHalf,    topHalfParams)
        }

        fun startClickLoop() {
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
                        targetY = location[1] + (squareSizePx / 2)
                    }
                    val process = Shizuku.newProcess(
                        arrayOf("sh", "-c", "input tap $targetX $targetY"), null, null
                    )
                    process.waitFor()
                }
            }
        }

        fun stopClickLoop() {
            isRunning = false
            topHalf.setLabel("start")
            clickJob?.cancel()
        }

        fun removeViews() {
            stopClickLoop()
            try {
                if (bgView.isAttachedToWindow)     windowManager.removeView(bgView)
                if (topStrip.isAttachedToWindow)   windowManager.removeView(topStrip)
                if (botStrip.isAttachedToWindow)   windowManager.removeView(botStrip)
                if (leftStrip.isAttachedToWindow)  windowManager.removeView(leftStrip)
                if (rightStrip.isAttachedToWindow) windowManager.removeView(rightStrip)
                if (topHalf.isAttachedToWindow)    windowManager.removeView(topHalf)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // THEMED CUSTOM VIEWS
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Draws the dark square background with a circle hole punched out,
     * plus borders and a horizontal divider. Always FLAG_NOT_TOUCHABLE.
     */
    inner class ThemedBgSquareView(
        context: Context,
        private val radius: Float,
        private val theme: ColorTheme
    ) : View(context) {
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.bgColor }
        private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        private val squareBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.borderColor; style = Paint.Style.STROKE; strokeWidth = 3f
        }
        private val circleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.rimColor; style = Paint.Style.STROKE; strokeWidth = 2.5f
        }
        private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = theme.dividerColor; strokeWidth = 2f
        }

        init { setLayerType(LAYER_TYPE_SOFTWARE, null) }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            val inset = 3f; val corner = 12f
            val rect = RectF(inset, inset, w - inset, h - inset)
            val cx = w / 2f; val cy = h / 2f

            canvas.drawRoundRect(rect, corner, corner, bgPaint)
            canvas.drawCircle(cx, cy, radius, clearPaint)
            canvas.drawRoundRect(rect, corner, corner, squareBorderPaint)
            canvas.drawCircle(cx, cy, radius, circleBorderPaint)
            canvas.drawLine(cx - radius, cy, cx + radius, cy, dividerPaint)
        }
    }

    /** Top semi-circle — start / stop button. */
    inner class ThemedTopSemiView(
        context: Context,
        private val theme: ColorTheme,
        private val onClick: () -> Unit,
        private val onLongClick: () -> Unit
    ) : View(context) {
        private var label = "start"
        private val bgPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.semiBgColor }
        private val txtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
        }
        private val path = Path()

        init {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
            setOnClickListener { onClick() }
            setOnLongClickListener { 
                onLongClick()
                true
            }
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