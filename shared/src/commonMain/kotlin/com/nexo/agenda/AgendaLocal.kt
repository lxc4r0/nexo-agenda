package com.nexo.agenda

import androidx.compose.runtime.*
import com.nexo.shared.*
import kotlinx.datetime.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Instant

@Serializable
data class DatosAgenda(
    val empleados: List<Employee>,
    val citas: List<Appointment> = emptyList(),
    val salas: List<Room> = emptyList(),
    val mensajes: List<Message> = emptyList(),
    val siguienteId: Int = 3,
)

// Todo se guarda en el dispositivo. Para probar el chat, se cambia de empleado.
class AgendaLocal(cargar: () -> String?, private val guardar: (String) -> Unit) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var datos by mutableStateOf(runCatching { cargar()?.let { json.decodeFromString<DatosAgenda>(it) } }.getOrNull() ?: datosDeEjemplo())
    private var usuarioId by mutableStateOf<String?>(null)
    val usuario: Employee? get() = datos.empleados.find { it.id == usuarioId }
    val empleados: List<Employee> get() = datos.empleados
    val citas: List<Appointment> get() = datos.citas.filter { it.ownerId == usuarioId }.sortedBy { it.start }
    val salas: List<Room> get() = datos.salas.filter { sala -> sala.members.any { it.userId == usuarioId } }

    private fun guardarCambios(nuevos: DatosAgenda) {
        guardar(json.encodeToString(nuevos))
        datos = nuevos
    }
    private fun usuarioActual() = requireNotNull(usuario) { "Inicia sesión." }
    fun entrar(nombre: String, password: String) {
        require(validPassword(password)) { "La contraseña necesita 8 caracteres como mínimo, un número y un símbolo." }
        val empleado = datos.empleados.find { it.username == nombre.trim().lowercase() && it.password == password }
        require(empleado != null) { "Usuario o contraseña incorrectos." }
        usuarioId = empleado.id
    }
    fun salir() { usuarioId = null }
    fun guardarPreferencias(preferencias: Preferences) {
        val empleado = usuarioActual()
        guardarCambios(datos.copy(empleados = datos.empleados.map { if (it.id == empleado.id) it.copy(preferences = preferencias) else it }))
    }
    fun cambiarPassword(actual: String, nueva: String) {
        val empleado = usuarioActual()
        require(empleado.password == actual) { "La contraseña actual no es correcta." }
        require(validPassword(nueva)) { "La nueva contraseña no cumple el formato." }
        guardarCambios(datos.copy(empleados = datos.empleados.map { if (it.id == empleado.id) it.copy(password = nueva) else it }))
        salir()
    }
    fun guardarCita(cita: Appointment) {
        val empleado = usuarioActual()
        require(cita.title.isNotBlank()) { "Escribe el título de la cita." }
        require(Instant.parse(cita.end) > Instant.parse(cita.start)) { "El final debe ser posterior al inicio." }
        val nueva = cita.id.isEmpty()
        require(nueva || datos.citas.any { it.id == cita.id && it.ownerId == empleado.id }) { "No se encuentra la cita." }
        val guardada = cita.copy(id = if (nueva) "cita-${datos.siguienteId}" else cita.id, ownerId = empleado.id)
        guardarCambios(datos.copy(citas = if (nueva) datos.citas + guardada else datos.citas.map { if (it.id == cita.id) guardada else it }, siguienteId = datos.siguienteId + if (nueva) 1 else 0))
    }
    fun crearSala(nombre: String, invitados: List<String>): String {
        val empleado = usuarioActual()
        require(nombre.isNotBlank()) { "Escribe el nombre de la sala." }
        val sala = Room("sala-${datos.siguienteId}", nombre.trim(), empleado.id,
            listOf(Member(empleado.id, true)) + invitados.distinct().filter { id -> id != empleado.id && datos.empleados.any { it.id == id } }.map { Member(it, false) })
        guardarCambios(datos.copy(salas = datos.salas + sala, siguienteId = datos.siguienteId + 1))
        return sala.id
    }
    private fun salaAceptada(id: String): Room {
        val empleado = usuarioActual()
        return requireNotNull(datos.salas.find { sala -> sala.id == id && sala.members.any { it.userId == empleado.id && it.accepted } }) { "Acepta primero la invitación." }
    }
    fun aceptar(id: String) {
        val empleado = usuarioActual()
        val sala = requireNotNull(datos.salas.find { sala -> sala.id == id && sala.members.any { it.userId == empleado.id } }) { "No tienes una invitación." }
        val aceptada = sala.copy(members = sala.members.map { if (it.userId == empleado.id) it.copy(accepted = true) else it })
        guardarCambios(datos.copy(salas = datos.salas.map { if (it.id == id) aceptada else it }))
    }
    fun invitar(salaId: String, empleadoId: String) {
        val sala = salaAceptada(salaId)
        require(datos.empleados.any { it.id == empleadoId }) { "Empleado no encontrado." }
        require(sala.members.none { it.userId == empleadoId }) { "Ese compañero ya está invitado." }
        val actualizada = sala.copy(members = sala.members + Member(empleadoId, false))
        guardarCambios(datos.copy(salas = datos.salas.map { if (it.id == salaId) actualizada else it }))
    }
    fun mensajes(salaId: String?): List<Message> {
        if (salaId == null) return emptyList()
        salaAceptada(salaId)
        return datos.mensajes.filter { it.roomId == salaId }
    }
    fun enviar(salaId: String, texto: String) {
        salaAceptada(salaId)
        require(texto.isNotBlank()) { "Escribe un mensaje." }
        val mensaje = Message("mensaje-${datos.siguienteId}", salaId, usuarioActual().id, texto.trim(), Clock.System.now().toString())
        guardarCambios(datos.copy(mensajes = datos.mensajes + mensaje, siguienteId = datos.siguienteId + 1))
    }
}

private fun datosDeEjemplo(): DatosAgenda {
    val empleados = listOf(Employee("ana", "Ana García", "ana"), Employee("lucas", "Lucas Martín", "lucas"), Employee("marta", "Marta López", "marta"))
    val dia = today().plus(1, DateTimeUnit.DAY)
    val zona = TimeZone.currentSystemDefault()
    fun cita(id: String, titulo: String, hora: Int, lugar: String) = Appointment(id, "ana", titulo,
        dia.atTime(hora, 0).toInstant(zona).toString(), dia.atTime(hora + 1, 0).toInstant(zona).toString(), lugar)
    return DatosAgenda(empleados, listOf(cita("cita-1", "Reunión de equipo", 9, "Sala principal"), cita("cita-2", "Revisión del proyecto", 12, "Sala 2")))
}
