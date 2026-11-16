package com.nexo.agenda

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        App(cargarDatos = ::cargarAgenda, guardarDatos = ::guardarAgenda,
            alAviso = ::sonarAviso, prepararSonido = ::prepararAudio)
    }
}
private external fun cargarAgenda(): String?
private external fun guardarAgenda(datos: String)
private external fun prepararAudio()
private external fun sonarAviso()
