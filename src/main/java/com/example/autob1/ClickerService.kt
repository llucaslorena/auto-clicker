package com.example.autob1 // MANTENHA O SEU PACOTE AQUI

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
    private lateinit var floatingView: LinearLayout
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var btnStartStop: Button

    // Escopo para rodar o loop na UI e disparar comandos em background
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var clickJob: Job? = null
    private var isRunning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        iniciarForegroundService() // OBRIGATÓRIO: Impede que o app crashe após 5s
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        configurarInterfaceFlutuante()
    }

    private fun iniciarForegroundService() {
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
            .setContentTitle("Auto Clicker Ativo")
            .setContentText("A mira flutuante está na tela.")
            .setSmallIcon(android.R.drawable.ic_menu_compass) // Troque pelo ícone do seu app
            .build()

        startForeground(1, notification)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun configurarInterfaceFlutuante() {
        floatingView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#99000000"))
            setPadding(10, 10, 10, 10)
        }

        // ALÇA DE ARRASTAR - só esta barra move o painel
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
            setPadding(0, 10, 0, 10) // espaçamento vertical para dar altura
        }

        val mira = TextView(this).apply {
            text = "X"
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            textSize = 16f
            setPadding(0, 0, 0, 20)
        }

        btnStartStop = Button(this).apply {
            text = "loop"
            setOnClickListener {
                if (isRunning) pararClique() else iniciarClique()
            }
        }

        floatingView.addView(dragHandle)
        floatingView.addView(mira)
        floatingView.addView(btnStartStop)

        val larguraPx = (90 * resources.displayMetrics.density).toInt()
        layoutParams = WindowManager.LayoutParams(
            larguraPx, // <- largura adaptável em dp
            WindowManager.LayoutParams.WRAP_CONTENT, // altura ainda se adapta ao conteúdo
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

        // LISTENER DE ARRASTAR só na alça, não no painel todo!
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
                        windowManager.updateViewLayout(floatingView, layoutParams)
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(floatingView, layoutParams)
    }

    private fun iniciarClique() {
        isRunning = true
        btnStartStop.text = "stop"
        val intervaloMs =   5000L
        clickJob = serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(intervaloMs) // Espera PRIMEIRO, depois clica
                val alvoX = layoutParams.x + (floatingView.width / 2)
                val alvoY = layoutParams.y + floatingView.height - 45
                try {
                    // 1. Torna a janela intocável para o tap passar para o jogo
                    withContext(Dispatchers.Main) {
                        layoutParams.flags = layoutParams.flags or
                                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        windowManager.updateViewLayout(floatingView, layoutParams)
                    }
                    // 2. Executa o tap
                    val comando = "input tap $alvoX $alvoY"
                    val process = Shizuku.newProcess(
                        arrayOf("sh", "-c", comando), null, null
                    )
                    process.waitFor()
                    // 3. Restaura a touchabilidade da janela
                    withContext(Dispatchers.Main) {
                        layoutParams.flags = layoutParams.flags and
                                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                        windowManager.updateViewLayout(floatingView, layoutParams)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Garante que a janela não fica presa em modo intocável
                    withContext(Dispatchers.Main) {
                        layoutParams.flags = layoutParams.flags and
                                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                        windowManager.updateViewLayout(floatingView, layoutParams)
                    }
                }
            }
        }
    }

    private fun pararClique() {
        isRunning = false
        btnStartStop.text = "loop"
        clickJob?.cancel() // Interrompe o Loop instantaneamente
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }
}