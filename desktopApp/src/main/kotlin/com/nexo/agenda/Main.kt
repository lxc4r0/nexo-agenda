package com.nexo.agenda

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Toolkit
import java.io.File

fun main() = application {
    val archivo = File(System.getProperty("user.home"), ".nexo-agenda/datos.json")
    Window(onCloseRequest = ::exitApplication, title = "Nexo Agenda", state = rememberWindowState(width = 1100.dp, height = 800.dp)) {
        App(
            cargarDatos = { if (archivo.exists()) archivo.readText() else null },
            guardarDatos = { archivo.parentFile.mkdirs(); archivo.writeText(it) },
            alAviso = { Toolkit.getDefaultToolkit().beep() }
        )
    }
}
