package com.nexo.agenda

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexo.shared.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.datetime.*
import kotlin.time.Clock
import kotlin.time.Instant

private val Green = Color(0xFF18392F)
private val Mint = Color(0xFFC0E5AA)
private val MonthNames = listOf("Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre")
private val WeekNames = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
private fun fullDate(date: LocalDate) = "${WeekNames[date.dayOfWeek.isoDayNumber - 1]}, ${date.day} de ${MonthNames[date.month.number - 1].lowercase()}"
private fun accent(name: String) = when (name) { "Azul" -> Color(0xFF315CA8); "Violeta" -> Color(0xFF7852A0); "Terracota" -> Color(0xFFA24E34); else -> Color(0xFF32664C) }

@Composable
fun App(cargarDatos: () -> String? = { null }, guardarDatos: (String) -> Unit = {}, alAviso: () -> Unit = {}, prepararSonido: () -> Unit = {}) {
    val agenda = remember { AgendaLocal(cargarDatos, guardarDatos) }
    val user = agenda.usuario
    val events = agenda.citas
    val employees = agenda.empleados
    val rooms = agenda.salas
    var page by remember { mutableStateOf("Agenda") }
    var roomId by remember(user?.id) { mutableStateOf<String?>(null) }
    val messages = agenda.mensajes(roomId)
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var selectedDate by remember { mutableStateOf(today()) }
    var editing by remember { mutableStateOf<Appointment?>(null) }
    var eventDialog by remember { mutableStateOf(false) }
    var roomDialog by remember { mutableStateOf(false) }
    var inviteDialog by remember { mutableStateOf(false) }
    var reminder by remember(user?.id) { mutableStateOf<Appointment?>(null) }
    val shownReminders = remember(user?.id) { mutableSetOf<String>() }
    fun accion(block: () -> Unit) {
        error = null; notice = null
        try { block() } catch (e: Exception) { error = e.message ?: "No se ha podido guardar el cambio." }
    }
    val prefs = user?.preferences ?: Preferences()
    LaunchedEffect(user?.id, prefs, events) {
        if (user == null || !prefs.alerts) { reminder = null; return@LaunchedEffect }
        while (isActive) {
            if (reminder == null) {
                val proxima = events.firstOrNull { reminderDue(it, Clock.System.now(), prefs.minutes) && reminderKey(it, prefs.minutes) !in shownReminders }
                if (proxima != null) {
                    shownReminders.add(reminderKey(proxima, prefs.minutes))
                    reminder = proxima
                    if (prefs.sound) alAviso()
                }
            }
            delay(1_000)
        }
    }
    val color = accent(prefs.color)
    val colors = if (prefs.dark) darkColorScheme(primary = lerp(color, Color.White, .5f), background = Color(0xFF14201B), surface = Color(0xFF1E2B25))
    else lightColorScheme(primary = color, primaryContainer = lerp(color, Color.White, .87f), secondaryContainer = lerp(color, Color.White, .87f), background = Color(0xFFF5F6F2), surface = Color.White, onSurface = Green)
    MaterialTheme(colorScheme = colors) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                val compact = maxWidth < 960.dp
                if (user == null) {
                    LoginScreen(error) { nombre, password ->
                        prepararSonido()
                        accion { agenda.entrar(nombre, password); page = "Agenda" }
                    }
                } else {
                    Row(Modifier.fillMaxSize()) {
                        if (!compact) Sidebar(user, page, rooms.count { r -> r.members.any { it.userId == user.id && !it.accepted } }, onPage = { page = it; error = null; notice = null }, logout = { agenda.salir() })
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            if (compact) Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Logo(28); Spacer(Modifier.width(8.dp)); Text("nexo", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f)); TextButton(onClick = { agenda.salir() }) { Text("Salir") }
                            }
                            Column(Modifier.weight(1f).padding(horizontal = if (compact) 16.dp else 30.dp)) {
                                error?.let { Banner(it, true) { error = null } }
                                notice?.let { Banner(it, false) { notice = null } }
                                when (page) {
                                    "Agenda" -> AgendaScreen(events, selectedDate, compact, onDate = { selectedDate = it }, onNew = { editing = null; eventDialog = true }, onEdit = { editing = it; eventDialog = true })
                                    "Chat" -> ChatScreen(user, employees, rooms, roomId, messages, compact,
                                        onSelect = { roomId = it }, onCreate = { roomDialog = true },
                                        onAccept = { id -> accion { agenda.aceptar(id); roomId = id } },
                                        onInvite = { inviteDialog = true }, onSend = { texto -> accion { agenda.enviar(roomId!!, texto) } })
                                    "Mi perfil" -> ProfileScreen(user,
                                        onPreferences = { preferencias -> accion { agenda.guardarPreferencias(preferencias); notice = "Preferencias guardadas." } },
                                        onPassword = { actual, nueva -> accion { agenda.cambiarPassword(actual, nueva); error = "Contraseña cambiada. Entra con la nueva contraseña." } })
                                }
                            }
                            if (compact) Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                listOf("Agenda", "Chat", "Mi perfil").forEach { tab -> TextButton(onClick = { page = tab; error = null; notice = null }) { Text(tab, fontWeight = if (page == tab) FontWeight.Bold else FontWeight.Normal) } }
                            }
                        }
                    }
                    if (eventDialog) EventDialog(editing, selectedDate, error,
                        onDismiss = { eventDialog = false; error = null }, onSave = { cita -> accion { agenda.guardarCita(cita); eventDialog = false } })
                    if (roomDialog) NewRoomDialog(employees.filter { it.id != user.id }, error,
                        onDismiss = { roomDialog = false; error = null }, onCreate = { sala -> accion { roomId = agenda.crearSala(sala.name, sala.invitees); roomDialog = false } })
                    if (inviteDialog) {
                        val sala = rooms.find { it.id == roomId }
                        InviteDialog(employees.filter { empleado -> sala?.members?.none { it.userId == empleado.id } == true }, error,
                            onDismiss = { inviteDialog = false; error = null }, onInvite = { id -> accion { agenda.invitar(roomId!!, id); inviteDialog = false } })
                    }
                    reminder?.let { cita -> AlertDialog(onDismissRequest = { reminder = null }, title = { Text("Tu próxima cita") },
                        text = { Text("${cita.title}\n${dateLabel(localStart(cita).date)} · ${timeLabel(localStart(cita).time)}\n${cita.place}") },
                        confirmButton = { Button(onClick = { reminder = null }) { Text("Entendido") } }) }
                }
            }
        }
    }
}

@Composable private fun Logo(size: Int = 40, tint: Color = Green) {
    Canvas(Modifier.size(size.dp)) {
        val gap = this.size.width * .14f; val side = (this.size.width - gap) / 2
        for (row in 0..1) for (col in 0..1) drawRoundRect(tint, Offset(col * (side + gap), row * (side + gap)), Size(side, side), CornerRadius(side * .25f))
    }
}

@Composable private fun LoginScreen(error: String?, onLogin: (String, String) -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 430.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Logo(); Spacer(Modifier.width(12.dp)); Text("nexo", fontSize = 40.sp, fontWeight = FontWeight.Bold) }
            Text("Agenda de empleados", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Card {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Iniciar sesión", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(username, { username = it }, label = { Text("Usuario") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(password, { password = it }, label = { Text("Contraseña") }, singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { TextButton(onClick = { visible = !visible }) { Text(if (visible) "Ocultar" else "Ver") } },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { onLogin(username, password) }))
                    Text("Mínimo 8 caracteres, un número y un símbolo.", style = MaterialTheme.typography.bodySmall)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(onClick = { onLogin(username, password) }, enabled = username.isNotBlank() && password.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Entrar") }
                }
            }
            Text("Usuarios: ana, lucas y marta. Contraseña: Nexo2026!", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun Sidebar(user: Employee, page: String, invitations: Int, onPage: (String) -> Unit, logout: () -> Unit) {
    Column(Modifier.width(216.dp).fillMaxHeight().background(Green).padding(22.dp)) {
        Row(Modifier.padding(top = 16.dp, bottom = 48.dp), verticalAlignment = Alignment.CenterVertically) { Logo(32, Mint); Spacer(Modifier.width(10.dp)); Text("nexo", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold) }
        Text("MI ESPACIO", color = Mint.copy(alpha = .65f), fontSize = 10.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(16.dp))
        listOf("Agenda", "Chat", "Mi perfil").forEach { item ->
            val active = item == page
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (active) Mint else Color.Transparent).clickable { onPage(item) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(item, color = if (active) Green else Color.White.copy(alpha = .8f), fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                if (item == "Chat" && invitations > 0) { Spacer(Modifier.weight(1f)); Text("$invitations", color = if (active) Green else Mint) }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.weight(1f))
        HorizontalDivider(color = Color.White.copy(alpha = .15f)); Spacer(Modifier.height(20.dp))
        Text(user.name, color = Color.White, fontWeight = FontWeight.SemiBold)
        Text("@${user.username}", color = Mint.copy(alpha = .7f), fontSize = 12.sp)
        TextButton(onClick = logout, contentPadding = PaddingValues(top = 14.dp)) { Text("Cerrar sesión", color = Mint) }
    }
}

@Composable private fun Banner(text: String, isError: Boolean, dismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).background(if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = dismiss) { Text("Cerrar") }
    }
}

@Composable private fun Heading(title: String, subtitle: String, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontSize = 28.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(4.dp)); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
        action?.invoke()
    }
}

@Composable private fun AgendaScreen(events: List<Appointment>, date: LocalDate, compact: Boolean, onDate: (LocalDate) -> Unit, onNew: () -> Unit, onEdit: (Appointment) -> Unit) {
    var view by remember { mutableStateOf("Mes") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Heading("Mi agenda", "Organiza tu jornada", action = { Button(onClick = onNew) { Text(if (compact) "+ Cita" else "+ Nueva cita") } })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { onDate(when (view) { "Mes" -> date.minus(1, DateTimeUnit.MONTH); "Semana" -> date.minus(7, DateTimeUnit.DAY); else -> date.minus(1, DateTimeUnit.DAY) }) }, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("‹") }
            TextButton(onClick = { onDate(today()) }) { Text("Hoy") }
            OutlinedButton(onClick = { onDate(when (view) { "Mes" -> date.plus(1, DateTimeUnit.MONTH); "Semana" -> date.plus(7, DateTimeUnit.DAY); else -> date.plus(1, DateTimeUnit.DAY) }) }, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("›") }
            Spacer(Modifier.width(4.dp)); Text("${MonthNames[date.month.number - 1]} ${date.year}", fontSize = if (compact) 16.sp else 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Mes", "Semana", "Día").forEach { item -> FilterChip(selected = view == item, onClick = { view = item }, label = { Text(item) }) }
        }
        if (view == "Mes") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                CalendarMonth(events, date, compact, Modifier.weight(1f), onDate, onEdit)
                if (!compact) Column(Modifier.width(230.dp)) { DayAppointments(events, date, onEdit, onNew) }
            }
            if (compact) { Spacer(Modifier.height(22.dp)); DayAppointments(events, date, onEdit, onNew) }
        } else if (view == "Semana") {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
              val columnWidth = if (compact) 156.dp else ((maxWidth - 48.dp) / 7).coerceAtLeast(116.dp)
              Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                weekDates(date).forEach { day ->
                    Card(Modifier.width(columnWidth).heightIn(min = 380.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(WeekNames[day.dayOfWeek.isoDayNumber - 1].take(3).uppercase(), fontSize = 10.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${day.day}", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = if (day == today()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            HorizontalDivider()
                            val items = events.filter { appointmentOnDay(it, day) }
                            if (items.isEmpty()) Text("Sin citas", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            items.forEach { EventCard(it, { onEdit(it) }) }
                            TextButton(onClick = { onDate(day); onNew() }) { Text("+ Cita") }
                        }
                    }
                }
              }
            }
        } else {
            DayAppointments(events, date, onEdit, onNew)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable private fun CalendarMonth(events: List<Appointment>, selected: LocalDate, compact: Boolean, modifier: Modifier, onDate: (LocalDate) -> Unit, onEdit: (Appointment) -> Unit) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp)) { listOf("L", "M", "X", "J", "V", "S", "D").forEach { label -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
        monthGrid(selected).chunked(7).forEach { week ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    val active = day == selected
                    val items = events.filter { appointmentOnDay(it, day) }
                    Column(Modifier.weight(1f).height(if (compact) 61.dp else 88.dp).background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = .09f) else Color.Transparent).clickable { onDate(day) }.padding(if (compact) 5.dp else 8.dp)) {
                        Box(Modifier.size(25.dp).clip(RoundedCornerShape(8.dp)).background(if (day == today()) MaterialTheme.colorScheme.primary else Color.Transparent), contentAlignment = Alignment.Center) {
                            Text("${day.day}", color = if (day == today()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = if (day.month == selected.month) 1f else .35f), fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                        }
                        if (compact && items.isNotEmpty()) Text("•".repeat(minOf(items.size, 3)), color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                        if (!compact) items.take(2).forEach { event ->
                            Text(event.title, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth().padding(top = 3.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = .09f)).clickable { onEdit(event) }.padding(horizontal = 3.dp))
                        }
                        if (!compact && items.size > 2) Text("+${items.size - 2} más", fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

@Composable private fun DayAppointments(events: List<Appointment>, date: LocalDate, onEdit: (Appointment) -> Unit, onNew: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(fullDate(date), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        val items = events.filter { appointmentOnDay(it, date) }
        Text("${items.size} ${if (items.size == 1) "cita" else "citas"}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        if (items.isEmpty()) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) { Text("Un día por organizar", fontWeight = FontWeight.Medium); Spacer(Modifier.height(6.dp)); Text("Todavía no tienes citas para este día.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); TextButton(onClick = onNew) { Text("+ Añadir cita") } }
        }
        items.forEach { event -> EventCard(event, { onEdit(event) }) }
    }
}

@Composable private fun EventCard(event: Appointment, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = .08f))) {
        Row(Modifier.padding(12.dp)) {
            Box(Modifier.width(3.dp).height(56.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
            Column(Modifier.padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("${timeLabel(localStart(event).time)} – ${timeLabel(localEnd(event).time)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                Text(event.title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (event.place.isNotBlank()) Text(event.place, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable private fun EventDialog(event: Appointment?, date: LocalDate, requestError: String?, onDismiss: () -> Unit, onSave: (Appointment) -> Unit) {
    var title by remember { mutableStateOf(event?.title ?: "") }; var day by remember { mutableStateOf(event?.let { localStart(it).date.toString() } ?: date.toString()) }
    var start by remember { mutableStateOf(event?.let { timeLabel(localStart(it).time) } ?: "09:00") }; var end by remember { mutableStateOf(event?.let { timeLabel(localEnd(it).time) } ?: "10:00") }
    var place by remember { mutableStateOf(event?.place ?: "") }; var notes by remember { mutableStateOf(event?.notes ?: "") }; var validation by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (event == null) "Nueva cita" else "Modificar cita") }, text = {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(title, { title = it.take(100) }, label = { Text("Título") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(day, { day = it.take(10) }, label = { Text("Fecha · AAAA-MM-DD") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(start, { start = it.take(5) }, label = { Text("Inicio · HH:MM") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(end, { end = it.take(5) }, label = { Text("Fin · HH:MM") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            OutlinedTextField(place, { place = it.take(120) }, label = { Text("Lugar") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(notes, { notes = it.take(2000) }, label = { Text("Notas") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            validation?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            requestError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { Button(onClick = {
        try {
            require(title.isNotBlank()) { "Escribe el título de la cita." }
            val parsedDate = runCatching { LocalDate.parse(day) }.getOrElse { throw IllegalArgumentException("Usa una fecha válida con formato AAAA-MM-DD.") }
            val startTime = runCatching { LocalTime.parse(start) }.getOrElse { throw IllegalArgumentException("Usa horas válidas con formato HH:MM.") }
            val endTime = runCatching { LocalTime.parse(end) }.getOrElse { throw IllegalArgumentException("Usa horas válidas con formato HH:MM.") }
            require(endTime > startTime) { "La hora final debe ser posterior al inicio." }
            val zone = TimeZone.currentSystemDefault()
            onSave(Appointment(event?.id ?: "", event?.ownerId ?: "", title.trim(), parsedDate.atTime(startTime).toInstant(zone).toString(), parsedDate.atTime(endTime).toInstant(zone).toString(), place.trim(), notes.trim()))
        } catch (e: IllegalArgumentException) { validation = e.message }
    }) { Text("Guardar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable private fun ProfileScreen(user: Employee, onPreferences: (Preferences) -> Unit, onPassword: (String, String) -> Unit) {
    var prefs by remember(user.preferences) { mutableStateOf(user.preferences) }
    var old by remember { mutableStateOf("") }; var next by remember { mutableStateOf("") }; var confirm by remember { mutableStateOf("") }; var validation by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Heading("Mi perfil", user.name)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("A tu manera", style = MaterialTheme.typography.titleLarge)
                ToggleRow("Modo noche", "Cambia entre el tema claro y oscuro", prefs.dark) { prefs = prefs.copy(dark = it) }
                Text("Color de la agenda", fontWeight = FontWeight.Medium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Verde", "Azul", "Violeta", "Terracota").forEach { name -> FilterChip(selected = prefs.color == name, onClick = { prefs = prefs.copy(color = name) }, label = { Text(name) }, leadingIcon = { Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(accent(name))) }) } }
                HorizontalDivider()
                ToggleRow("Alertas de citas", "Ventana modal antes de cada cita", prefs.alerts) { prefs = prefs.copy(alerts = it) }
                ToggleRow("Sonido", "Acompaña el aviso con un sonido", prefs.sound) { prefs = prefs.copy(sound = it) }
                Text("Avisar con antelación", fontWeight = FontWeight.Medium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(5, 10, 15, 30).forEach { minutes -> FilterChip(selected = prefs.minutes == minutes, onClick = { prefs = prefs.copy(minutes = minutes) }, label = { Text("$minutes min") }) } }
                Text("Los avisos se muestran mientras la app está abierta.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Button(onClick = { onPreferences(prefs) }) { Text("Guardar preferencias") }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(22.dp).widthIn(max = 500.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Cambiar contraseña", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(old, { old = it }, label = { Text("Contraseña actual") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(next, { next = it }, label = { Text("Nueva contraseña") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(confirm, { confirm = it }, label = { Text("Repetir nueva contraseña") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Al menos 8 caracteres, un número y un símbolo. Al cambiarla tendrás que iniciar sesión de nuevo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                validation?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedButton(onClick = { validation = when { !validPassword(next) -> "La nueva contraseña no cumple el formato."; next != confirm -> "Las contraseñas no coinciden."; old.isEmpty() -> "Introduce tu contraseña actual."; else -> null }; if (validation == null) onPassword(old, next) }) { Text("Actualizar contraseña") }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable private fun ToggleRow(title: String, description: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium); Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }; Switch(checked, onChecked) }
}

@Composable private fun ChatScreen(user: Employee, employees: List<Employee>, rooms: List<Room>, selected: String?, messages: List<Message>, compact: Boolean, onSelect: (String?) -> Unit, onCreate: () -> Unit, onAccept: (String) -> Unit, onInvite: () -> Unit, onSend: (String) -> Unit) {
    val room = rooms.find { it.id == selected }
    Column(Modifier.fillMaxSize()) {
        Heading("Chat de equipo", "Un espacio para cada conversación", action = { Button(onClick = onCreate) { Text("+ Sala") } })
        Row(Modifier.weight(1f).padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            if (!compact || room == null) {
                Column(Modifier.then(if (compact) Modifier.fillMaxWidth() else Modifier.width(248.dp)).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("MIS SALAS", fontSize = 11.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (rooms.isEmpty()) Text("Aún no tienes salas. Crea una e invita a tus compañeros.", modifier = Modifier.padding(vertical = 22.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    rooms.forEach { item ->
                        val accepted = item.members.any { it.userId == user.id && it.accepted }
                        Card(colors = CardDefaults.cardColors(containerColor = if (item.id == selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().then(if (accepted) Modifier.clickable { onSelect(item.id) } else Modifier).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(item.name, fontWeight = FontWeight.Medium)
                                if (!accepted) { Text("Te han invitado a esta sala", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Button(onClick = { onAccept(item.id) }) { Text("Aceptar invitación") } }
                                else Text("${item.members.count { it.accepted }} participantes", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            if (room != null) {
                Card(Modifier.weight(1f).fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxSize().padding(if (compact) 12.dp else 20.dp)) {
                        if (compact) TextButton(onClick = { onSelect(null) }) { Text("‹ Salas") }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(room.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold); Text(room.members.joinToString(", ") { member -> (employees.find { it.id == member.userId }?.name ?: member.userId) + if (!member.accepted) " (invitado)" else "" }, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                            TextButton(onClick = onInvite) { Text("Invitar") }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        val scroll = rememberScrollState()
                        LaunchedEffect(messages.size) { scroll.animateScrollTo(scroll.maxValue) }
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (messages.isEmpty()) Text("Empieza la conversación.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            messages.forEach { message ->
                                val own = message.authorId == user.id
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (own) Arrangement.End else Arrangement.Start) {
                                    Column(Modifier.widthIn(max = if (compact) 240.dp else 380.dp).clip(RoundedCornerShape(12.dp)).background(if (own) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background).padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text(employees.find { it.id == message.authorId }?.name ?: message.authorId, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                        Text(message.text, fontSize = 14.sp)
                                        val sent = Instant.parse(message.sentAt).toLocalDateTime(TimeZone.currentSystemDefault())
                                        Text("${dateLabel(sent.date)} · ${timeLabel(sent.time)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        var draft by remember(room.id) { mutableStateOf("") }
                        var pending by remember(room.id) { mutableStateOf(false) }
                        LaunchedEffect(messages) { if (pending) { if (messages.lastOrNull()?.let { it.authorId == user.id && it.text == draft.trim() } == true) draft = ""; pending = false } }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(draft, { draft = it.take(2000) }, placeholder = { Text("Escribe un mensaje…") }, modifier = Modifier.weight(1f), maxLines = 4)
                            Button(enabled = draft.isNotBlank(), onClick = { pending = true; onSend(draft.trim()) }) { Text("Enviar") }
                        }
                        Text("Los mensajes y las salas no se pueden borrar.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            } else if (!compact) {
                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) { Logo(40); Text("Conecta con tu equipo", fontSize = 22.sp, fontWeight = FontWeight.SemiBold); Text("Selecciona una sala o crea una nueva.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable private fun NewRoomDialog(employees: List<Employee>, requestError: String?, onDismiss: () -> Unit, onCreate: (NewRoom) -> Unit) {
    var name by remember { mutableStateOf("") }; var selected by remember { mutableStateOf(emptySet<String>()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Nueva sala") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(name, { name = it.take(60) }, label = { Text("Nombre de la sala") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Invitar a compañeros", fontWeight = FontWeight.Medium)
            employees.forEach { employee -> Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(employee.id in selected, { selected = if (it) selected + employee.id else selected - employee.id }); Text(employee.name) } }
            Text("Cada compañero tendrá que aceptar su invitación.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            requestError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { Button(enabled = name.isNotBlank(), onClick = { onCreate(NewRoom(name.trim(), selected.toList())) }) { Text("Crear sala") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable private fun InviteDialog(employees: List<Employee>, requestError: String?, onDismiss: () -> Unit, onInvite: (String) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Invitar a un compañero") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (employees.isEmpty()) Text("Todos los compañeros ya están en la sala o tienen una invitación.")
            employees.forEach { employee -> OutlinedButton(onClick = { onInvite(employee.id) }, modifier = Modifier.fillMaxWidth()) { Text(employee.name) } }
            requestError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } })
}
