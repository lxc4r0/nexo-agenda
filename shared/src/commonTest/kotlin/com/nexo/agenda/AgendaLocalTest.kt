package com.nexo.agenda

import com.nexo.shared.*
import kotlin.test.*

class AgendaLocalTest {
    @Test fun invitacionesYMensajesSeGuardanEnElMismoDispositivo() {
        var guardado: String? = null
        val agenda = AgendaLocal({ guardado }, { guardado = it })
        agenda.entrar("ana", "Nexo2026!")
        val sala = agenda.crearSala("Equipo", listOf("lucas"))
        agenda.enviar(sala, "Hola, Lucas")
        agenda.salir()
        agenda.entrar("lucas", "Nexo2026!")
        assertFailsWith<IllegalArgumentException> { agenda.mensajes(sala) }
        agenda.aceptar(sala)
        assertEquals("Hola, Lucas", agenda.mensajes(sala).single().text)
        agenda.enviar(sala, "Invitación aceptada")
        val reabierta = AgendaLocal({ guardado }, { guardado = it })
        reabierta.entrar("ana", "Nexo2026!")
        assertEquals(2, reabierta.mensajes(sala).size)
    }
    @Test fun passwordYPreferenciasSeConservanAlReabrir() {
        var guardado: String? = null
        val agenda = AgendaLocal({ guardado }, { guardado = it })
        agenda.entrar("ana", "Nexo2026!")
        agenda.guardarPreferencias(Preferences(dark = true, color = "Azul"))
        assertFailsWith<IllegalArgumentException> { agenda.cambiarPassword("equivocada", "Otra2026!") }
        agenda.cambiarPassword("Nexo2026!", "Otra2026!")
        assertNull(agenda.usuario)
        val reabierta = AgendaLocal({ guardado }, { guardado = it })
        assertFailsWith<IllegalArgumentException> { reabierta.entrar("ana", "Nexo2026!") }
        reabierta.entrar("ana", "Otra2026!")
        assertTrue(reabierta.usuario!!.preferences.dark)
        assertEquals("Azul", reabierta.usuario!!.preferences.color)
    }
    @Test fun cadaEmpleadoTieneSusCitasYSePuedenModificar() {
        var guardado: String? = null
        val agenda = AgendaLocal({ guardado }, { guardado = it })
        agenda.entrar("ana", "Nexo2026!")
        val cita = agenda.citas.first()
        agenda.guardarCita(cita.copy(title = "Reunión modificada"))
        assertEquals("Reunión modificada", agenda.citas.first().title)
        agenda.salir()
        agenda.entrar("lucas", "Nexo2026!")
        assertTrue(agenda.citas.isEmpty())
        assertFailsWith<IllegalArgumentException> { agenda.guardarCita(cita) }
    }
}
