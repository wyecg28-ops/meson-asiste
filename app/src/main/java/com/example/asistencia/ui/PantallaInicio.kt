/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 */
package com.example.asistencia.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.asistencia.R
import com.example.asistencia.datos.Destacados
import com.example.asistencia.datos.Jornada
import com.example.asistencia.datos.Novedad
import com.example.asistencia.datos.aMinutos
import com.example.asistencia.datos.Perfil
import com.example.asistencia.datos.Periodo
import com.example.asistencia.datos.terminaDiaSiguiente
import com.example.asistencia.datos.TipoRegistro
import com.example.asistencia.datos.agruparPorDia
import com.example.asistencia.datos.calcularEstadisticas
import com.example.asistencia.datos.formatoDuracion
import com.example.asistencia.datos.formatoDuracionCorta
import com.example.asistencia.datos.formatoHora
import com.example.asistencia.datos.inicioDelDia
import com.example.asistencia.datos.minutoDelDia
import com.example.asistencia.datos.TipoEvento
import com.example.asistencia.datos.claveDia
import com.example.asistencia.datos.textoDiasLaborales
import com.example.asistencia.ui.theme.Granate
import com.example.asistencia.ui.theme.Rojo
import com.example.asistencia.ui.theme.colorAviso
import com.example.asistencia.ui.theme.colorExito
import kotlin.math.max

@Composable
fun PantallaInicio(
    estado: EstadoUi,
    onMarcar: (TipoRegistro, String, Boolean) -> Unit,
    onPermisoDenegado: (String) -> Unit,
    onReintentar: () -> Unit,
    onVerEstadisticas: () -> Unit,
    onMesRanking: (String) -> Unit,
    onLeerNovedad: (String) -> Unit = {},
    onLeerNovedades: () -> Unit = {},
) {
    val context = LocalContext.current
    val perfil = estado.perfil
    val ahora = rememberAhora(60_000)
    var observacion by remember { mutableStateOf("") }
    var conObservacion by remember { mutableStateOf(false) }
    var actividad by remember { mutableStateOf(false) }
    LaunchedEffect(estado.marcasGuardadas) {
        observacion = ""
        conObservacion = false
        actividad = false
    }

    // Por si la persona quitó el permiso de ubicación desde los ajustes después del primer inicio.
    var tipoPendiente by remember { mutableStateOf<TipoRegistro?>(null) }
    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val tipo = tipoPendiente
        tipoPendiente = null
        if (ubicacionConcedida(context) && tipo != null) onMarcar(tipo, observacion, actividad)
        else onPermisoDenegado("Se necesita la ubicación precisa para registrar dónde marcas.")
    }
    val marcar = { tipo: TipoRegistro ->
        if (ubicacionConcedida(context)) onMarcar(tipo, observacion, actividad)
        else {
            tipoPendiente = tipo
            pedirPermiso.launch(PERMISOS_UBICACION)
        }
    }

    // El turno abierto (también el de ayer si termina de madrugada) o, si no hay, el de hoy.
    val hoy = remember(estado.marcas, perfil, ahora) {
        val turnos = agruparPorDia(estado.marcas, perfil)
        turnos.firstOrNull { it.enCurso(ahora) } ?: turnos.firstOrNull { it.dia == inicioDelDia(ahora) }
    }
    val turnoDeAyer = hoy != null && hoy.dia != inicioDelDia(ahora)
    val semana = remember(estado.marcas, perfil, ahora) { calcularEstadisticas(estado.marcas, perfil, Periodo.SEMANA, ahora) }
    val siguiente = when {
        hoy?.entrada == null -> TipoRegistro.ENTRADA
        hoy?.salida == null -> TipoRegistro.SALIDA
        else -> null
    }
    val trabajado = hoy?.minutosTrabajados(ahora) ?: 0L

    Pantalla {
        // Saludo
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    saludo(minutoDelDia(ahora)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    estado.nombre.orEmpty().substringBefore(' '),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Avatar(estado.nombre, 46.dp)
        }

        Reloj(perfil, ahora)

        // Novedades: recordatorios de este momento y avisos guardados hasta tocar "Entendido".
        TarjetaNovedades(estado.novedades, recordatoriosAhora(perfil, hoy, ahora), onLeerNovedad, onLeerNovedades)

        // Vacaciones y asuetos que asignó la dirección: lo que está tomando y lo próximo (en los siguientes 30 días).
        val claveHoy = claveDia(ahora)
        val proximas = perfil.diasLibres.orEmpty()
        val vacacionesHoy = proximas.firstOrNull { it.esVacaciones && claveHoy in it.desde..it.hasta }
        val proxima = proximas.firstOrNull { it.desde > claveHoy && it.desde <= claveDia(ahora + 30 * 86_400_000L) }

        // Aviso del calendario: vacaciones, feriado, permiso o entrada especial.
        (perfil.diaLibre(ahora) ?: perfil.entradaEspecial(ahora) ?: perfil.permisosPorHoras(ahora).firstOrNull())?.let { e ->
            Tarjeta {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        if (e.porHoras) "Permiso por horas" else e.tipo.etiqueta,
                        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary,
                    )
                    Text(
                        when {
                            e.porHoras -> "Tienes permiso de ${e.horaInicio} a ${e.horaFin}; esas horas cuentan para tu meta. ${e.detalle}".trim()
                            e.tipo == TipoEvento.VACACIONES && vacacionesHoy != null ->
                                "Estás de vacaciones hasta el ${fechaLegible(vacacionesHoy.hasta).lowercase()}. " +
                                    "Hoy no se espera que marques. ¡Que descanses!"
                            e.tipo.diaLibre -> "Hoy no se espera que marques. ${e.detalle}".trim()
                            else -> ("Hoy la entrada es a las ${e.horaEntrada}" +
                                (if (e.horaSalida.isNotBlank()) " y la salida a las ${e.horaSalida}" else "") +
                                ". ${e.detalle}").trim()
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        proxima?.let { t ->
            Tarjeta {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        if (t.esVacaciones) "🌴 Próximas vacaciones" else "🌴 Próximo asueto",
                        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary,
                    )
                    Text(
                        (if (t.desde == t.hasta) fechaLegible(t.desde) else "${fechaLegible(t.desde)} al ${fechaLegible(t.hasta).lowercase()}") +
                            " · ${textoDiasLaborales(t.dias)}. Los demás están en Permisos.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        // Resumen de hoy
        Tarjeta {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (turnoDeAyer) "Turno de ayer (nocturno)" else "Hoy",
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                    )
                    if (hoy?.actividad == true) Etiqueta("Actividad especial", MaterialTheme.colorScheme.secondary)
                    else hoy?.puntual?.let { EtiquetaPuntualidad(it) }
                }
                Row {
                    Dato("Entrada", hoy?.minutoEntrada?.let(::formatoHora) ?: "--:--", Modifier.weight(1f))
                    Dato("Salida", hoy?.minutoSalida?.let(::formatoHora) ?: "--:--", Modifier.weight(1f))
                    Dato("Horas", if (hoy?.entrada != null) formatoDuracionCorta(trabajado) else "—", Modifier.weight(1f))
                }
                if (hoy?.entrada != null) {
                    MetaDelDia(trabajado + (hoy?.minutosPermiso ?: 0), hoy?.meta ?: perfil.metaDelDia(ahora), terminada = hoy?.salida != null)
                }
                val extra = hoy?.minutosExtra(ahora) ?: 0
                if (extra > 0) {
                    Text("⏱ Horas extra: ${formatoDuracion(extra)}", style = MaterialTheme.typography.bodySmall, color = colorExito)
                }
                val tarde = hoy?.minutosTarde ?: 0
                if (tarde > 0) {
                    Text(
                        "Llegaste ${formatoDuracion(tarde.toLong())} después de tu hora (${horaAmigable(hoy?.horaEntradaEsperada.orEmpty())}).",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorAviso,
                    )
                }
            }
        }

        // Acción principal: un solo botón que cambia según lo que falta marcar.
        if (siguiente != null) {
            BotonMarcar(siguiente, actividad, estado.ocupado) { marcar(siguiente) }

            // Actividad especial: se puede marcar fuera del colegio, con descripción obligatoria.
            Tarjeta {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Estoy en una actividad especial", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Excursión, evento o capacitación fuera del colegio. La dirección revisará las horas.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(checked = actividad, onCheckedChange = { actividad = it }, enabled = !estado.ocupado)
                    }
                }
            }

            if (conObservacion || actividad) {
                OutlinedTextField(
                    value = observacion,
                    onValueChange = { observacion = it.take(300) },
                    label = { Text(if (actividad) "Describe la actividad (obligatorio)" else "Observación") },
                    placeholder = {
                        Text(if (actividad) "Ej.: excursión al museo con 3.º básico" else "Ej.: llegué tarde por tráfico…")
                    },
                    isError = actividad && observacion.isBlank(),
                    enabled = !estado.ocupado,
                    minLines = 2,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                TextButton(onClick = { conObservacion = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("+ Agregar observación")
                }
            }
            Text(
                (estado.metodo?.let { "Confirmarás con ${it.etiqueta.lowercase()}" } ?: "Se usará tu ubicación") +
                    if (actividad) ". Se guardará tu ubicación actual." else " dentro del colegio, con el GPS activado.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            val cumplio = hoy?.cumplioMeta(ahora) == true
            Tarjeta {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(if (cumplio || hoy?.actividad == true) R.drawable.ic_check else R.drawable.ic_alerta),
                        null,
                        tint = if (cumplio || hoy?.actividad == true) colorExito else colorAviso,
                        modifier = Modifier.size(36.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Jornada terminada", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Registraste ${formatoDuracion(trabajado)} hoy. ¡Hasta mañana!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (estado.pendientes > 0) {
            Tarjeta {
                Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_nube), null, tint = colorAviso)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "${estado.pendientes} registro(s) sin enviar a la hoja",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onReintentar, enabled = !estado.ocupado) { Text("Enviar") }
                }
            }
        }

        // Mini resumen de la semana
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Esta semana", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onVerEstadisticas) { Text("Ver más") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniDato("🔥 ${semana.rachaActual}", "Racha puntual", Modifier.weight(1f))
            MiniDato(semana.puntualidad?.let { "$it %" } ?: "—", "Puntualidad", Modifier.weight(1f))
            MiniDato(if (semana.minutosExtra > 0) formatoDuracionCorta(semana.minutosExtra) else "0", "Horas extra", Modifier.weight(1f))
        }

        // Ranking de horas del personal (todos lo ven).
        TarjetaRanking(estado.ranking, estado.cargandoRanking, onMesRanking)

        perfil.destacados?.takeIf { it.hayDatos }?.let { DestacadosSemana(it) }
    }
}

/** Mejor racha y más horas de la semana entre todo el personal. */
@Composable
fun DestacadosSemana(d: Destacados) {
    Tarjeta {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("🏆 Destacados de la semana", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
            if (d.racha > 0) FilaDestacado("🔥", "Mejor racha", d.rachaNombre, "${d.racha} ${if (d.racha == 1) "día" else "días"}")
            if (d.horasMinutos > 0) FilaDestacado("⏱", "Más horas", d.horasNombre, formatoDuracion(d.horasMinutos))
        }
    }
}

@Composable
private fun FilaDestacado(icono: String, titulo: String, nombre: String, valor: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icono, fontSize = 22.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(nombre, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1)
        }
        Text(valor, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

/** Barra de avance hacia la meta de horas del día. */
@Composable
private fun MetaDelDia(trabajado: Long, meta: Int, terminada: Boolean) {
    val faltan = max(0L, meta - trabajado)
    val textoMeta = formatoDuracion(meta.toLong())
    val color = if (faltan == 0L) colorExito else if (terminada) colorAviso else MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinearProgressIndicator(
            progress = { if (meta <= 0) 1f else (trabajado.toFloat() / meta).coerceIn(0f, 1f) },
            color = color,
            trackColor = MaterialTheme.colorScheme.outlineVariant,
            strokeCap = StrokeCap.Round,
            modifier = Modifier.fillMaxWidth().height(8.dp),
        )
        Text(
            when {
                faltan == 0L -> "Meta de $textoMeta cumplida ✓"
                terminada -> "Faltaron ${formatoDuracion(faltan)} para la meta de $textoMeta."
                else -> "Te faltan ${formatoDuracion(faltan)} para cumplir la meta de $textoMeta."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (faltan == 0L) colorExito else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun saludo(minuto: Int) = when {
    minuto < 12 * 60 -> "Buenos días,"
    minuto < 19 * 60 -> "Buenas tardes,"
    else -> "Buenas noches,"
}

@Composable
private fun Reloj(perfil: Perfil, hoy: Long) {
    val ahora = rememberAhora(1_000)
    Tarjeta {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 22.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatear("HH:mm", ahora), fontSize = 56.sp, fontWeight = FontWeight.Light)
                Text(
                    formatear(":ss", ahora),
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp, start = 2.dp),
                )
            }
            Text(
                formatear("EEEE d 'de' MMMM", ahora),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            val entrada = perfil.horaEntrada(hoy)
            val salida = perfil.horaSalida(hoy)
            Etiqueta(
                "${perfil.horario.nombre} · ${horaAmigable(entrada)} – ${horaAmigable(salida)}" +
                    if (terminaDiaSiguiente(entrada, salida)) " (día sig.)" else "",
                MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun BotonMarcar(tipo: TipoRegistro, actividad: Boolean, ocupado: Boolean, onClick: () -> Unit) {
    val color = if (tipo == TipoRegistro.ENTRADA) Rojo else Granate
    Button(
        onClick = onClick,
        enabled = !ocupado,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color.White,
            disabledContainerColor = color.copy(alpha = 0.6f),
            disabledContentColor = Color.White,
        ),
        modifier = Modifier.fillMaxWidth().height(68.dp),
    ) {
        if (ocupado) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text("Verificando…", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        } else {
            Icon(
                painterResource(if (tipo == TipoRegistro.ENTRADA) R.drawable.ic_entrada else R.drawable.ic_salida),
                contentDescription = null,
            )
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Marcar ${tipo.etiqueta.lowercase()}", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                if (actividad) Text("Actividad especial", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun MiniDato(valor: String, etiqueta: String, modifier: Modifier) {
    Tarjeta(modifier) {
        Column(Modifier.padding(vertical = 14.dp, horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(valor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                etiqueta,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                minLines = 2,
                maxLines = 2,
            )
        }
    }
}

// ---------- Novedades ----------

/** Recordatorio de este momento (no se guarda: aparece mientras sirve). */
private data class RecordatorioAhora(val icono: String, val titulo: String, val texto: String)

/**
 * Los mismos recordatorios que llegan como notificación, mientras la app está abierta: entrada en 15 minutos, entrada
 * sin marcar (15 minutos después de la tolerancia) y salida en 10 minutos.
 */
private fun recordatoriosAhora(perfil: Perfil, turno: Jornada?, ahora: Long): List<RecordatorioAhora> {
    if (!perfil.marca) return emptyList()
    val lista = mutableListOf<RecordatorioAhora>()
    val hoy = inicioDelDia(ahora)
    val minuto = minutoDelDia(ahora)
    val entrada = aMinutos(perfil.horaEntrada(hoy))
    var salida = aMinutos(perfil.horaSalida(hoy))
    if (salida <= entrada) salida += 24 * 60
    val trabaja = perfil.horario.trabaja(hoy) || perfil.entradaEspecial(hoy) != null
    val enCurso = turno?.enCurso(ahora) == true
    if (trabaja && perfil.diaLibre(hoy) == null && !enCurso && !(turno?.dia == hoy && turno.entrada != null)) {
        if (minuto in (entrada - 15) until entrada) {
            lista += RecordatorioAhora(
                "⏰",
                if (perfil.entradaEspecial(hoy) != null) "En ${entrada - minuto} minutos: entrada especial" else "Tu entrada es en ${entrada - minuto} minutos",
                "Entras a las ${horaAmigable(perfil.horaEntrada(hoy))} y sales a las ${horaAmigable(perfil.horaSalida(hoy))}. " +
                    "¡Llega puntual y mantén tu racha!",
            )
        } else if (minuto >= entrada + perfil.tolerancia + 15 && minuto < salida) {
            lista += RecordatorioAhora(
                "⚠️", "Aún no registras tu entrada",
                "Ya pasó tu hora de entrada (${horaAmigable(perfil.horaEntrada(hoy))}). Marca en cuanto llegues; si estás en " +
                    "una actividad fuera del colegio, usa \"Actividad especial\".",
            )
        }
    }
    if (turno != null && enCurso) {
        val salidaTurno = aMinutos(perfil.horaSalida(turno.dia))
        var fin = turno.dia + salidaTurno * 60_000L
        if (salidaTurno <= aMinutos(perfil.horaEntrada(turno.dia))) fin += 86_400_000L // salida del día siguiente
        val faltanMin = ((fin - ahora + 59_999) / 60_000).toInt()
        if (faltanMin in 1..10) {
            val alSalir = turno.minutosTrabajados(fin) ?: 0
            val faltan = turno.meta - turno.minutosPermiso - alSalir
            lista += RecordatorioAhora(
                "🏁", "Tu salida es en $faltanMin minutos",
                "A las ${horaAmigable(perfil.horaSalida(turno.dia))} llevarás ${formatoDuracion(alSalir)}. " +
                    if (faltan <= 0) "Cumplirás tu meta del día. No olvides marcar tu salida."
                    else "Te faltarán ${formatoDuracion(faltan)} para tu meta del día.",
            )
        }
    }
    return lista
}

/** Tarjeta "Novedades" de Inicio (no se muestra si no hay nada). */
@Composable
private fun TarjetaNovedades(
    novedades: List<Novedad>,
    recordatorios: List<RecordatorioAhora>,
    onLeer: (String) -> Unit,
    onLeerTodas: () -> Unit,
) {
    if (novedades.isEmpty() && recordatorios.isEmpty()) return
    Tarjeta {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "🔔 Novedades", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                )
                if (novedades.size > 1) TextButton(onClick = onLeerTodas) { Text("Marcar todas") }
            }
            val fila = @Composable { icono: String, titulo: String, texto: String, id: String? ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(icono, fontSize = 20.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(titulo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (id != null) TextButton(onClick = { onLeer(id) }) { Text("Entendido") }
                    }
                }
            }
            recordatorios.forEach { fila(it.icono, it.titulo, it.texto, null) }
            novedades.forEach { fila(it.icono, it.titulo, it.texto, it.id) }
        }
    }
}
