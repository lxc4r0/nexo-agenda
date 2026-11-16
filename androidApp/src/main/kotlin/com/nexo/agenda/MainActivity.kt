package com.nexo.agenda

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    private var sonido: ToneGenerator? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sonido = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) }.getOrNull()
        val preferencias = getSharedPreferences("nexo", MODE_PRIVATE)
        setContent {
            App(
                cargarDatos = { preferencias.getString("agenda", null) },
                guardarDatos = { preferencias.edit().putString("agenda", it).apply() },
                alAviso = { sonido?.startTone(ToneGenerator.TONE_PROP_BEEP, 500) }
            )
        }
    }
    override fun onDestroy() { sonido?.release(); super.onDestroy() }
}
