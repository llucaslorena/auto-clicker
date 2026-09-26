package com.example.autob1 // MANTENHA O SEU PACOTE AQUI

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private val SHIZUKU_CODE = 100

    // Observa se o usuário clicou em "Permitir" ou "Negar" no Pop-up do Shizuku
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_CODE && grantResult == PackageManager.PERMISSION_GRANTED) {
            verificarPermissoesEIniciar() // Tenta iniciar de novo agora que tem permissão
        } else {
            Toast.makeText(this, "Permissão do Shizuku negada!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Adiciona o observador ao abrir o app
        Shizuku.addRequestPermissionResultListener(permissionListener)

        val btnIniciar = Button(this).apply {
            text = "Iniciar AutoClicker"
            setOnClickListener {
                verificarPermissoesEIniciar()
            }
        }
        setContentView(btnIniciar)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Limpa o observador para evitar vazamento de memória
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }

    private fun verificarPermissoesEIniciar() {
        // 1. Verifica se o Shizuku está rodando no celular
        if (!Shizuku.pingBinder()) {
            Toast.makeText(this, "Shizuku não está rodando!", Toast.LENGTH_LONG).show()
            return
        }

        // 2. Verifica permissão
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(SHIZUKU_CODE)
            return // Para a execução, o listener ali de cima vai assumir quando o usuário responder
        }

        // 3. Verifica a permissão de sobreposição de tela (Janelas flutuantes)
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            Toast.makeText(this, "Ative a sobreposição e tente novamente", Toast.LENGTH_LONG).show()
            return
        }

        // 4. Inicia o serviço flutuante corretamente
        val serviceIntent = Intent(this, ClickerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        finish() // Minimiza a activity e deixa só a mira
    }
}