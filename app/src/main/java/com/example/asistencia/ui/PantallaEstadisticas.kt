/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 */
package com.example.asistencia.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.asistencia.datos.Perfil
import com.example.asistencia.datos.Barra
import com.example.asistencia.datos.Estadisticas
import com.example.asistencia.datos.Marca
import com.example.asistencia.datos.Periodo
import com.example.asistencia.datos.calcularEstadisticas
import com.example.asistencia.datos.formatoDuracion
import com.example.asistencia.datos.formatoDuracionCorta
import com.example.asistencia.datos.formatoHora
import com.example.asistencia.ui.theme.colorAviso
import com.example.asistencia.ui.theme.colorExito
import kotlin.math.max
import kotlin.math.min

@Composable
fun PantallaEstadisticas(marcas: List<Marca>, perfil: Perfil) {
    var periodo by rememberSaveable { mutableStateOf(Periodo.SEMANA) }
    val ahora = rememberAhora(60_000)
    val e = remember(marcas, perfil, periodo, ahora) { calcularEstadisticas(marcas, perfil, periodo, ahora) }

    Pantalla {
        Titulo("Estadísticas", "Calculadas con los registros de este teléfono.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Periodo.entries.forEach { p ->
                FilterChip(selected = periodo == p, onClick = { periodo = p }, label = { Text(p.etiqueta) })
            }
        }

        if (e.diasAsistidos == 0) {
            EstadoVacio(
                "Aún no hay registros en este periodo",
                "Cuando marques tu entrada, aquí verás tu puntualidad, tus horas y tu racha.",
            )
        } else {
            TarjetaPuntualidad(e, perfil)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Mosaico("Días asistidos", "${e.diasAsistidos}", Modifier.weight(1f))
                Mosaico("Horas en total", formatoDuracionCorta(e.minutosTrabajados), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Mosaico(
                    "Horas extra", if (e.minutosExtra > 0) formatoDuracionCorta(e.minutosExtra) else "0", Modifier.weight(1f),
                    detalle = "Después de la meta",
                )
                Mosaico(
                    "Meta cumplida", "${e.metaCumplida}", Modifier.weight(1f),
                    detalle = "Días completos",
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Mosaico(
                    "Días incompletos", "${e.metaIncompleta}", Modifier.weight(1f),
                    detalle = if (e.minutosFaltantes > 0) "Faltaron ${formatoDuracionCorta(e.minutosFaltantes)}" else "¡Ninguno!",
                    alerta = e.metaIncompleta > 0,
                )
                Mosaico(
                    "Llegadas tarde", "${e.tardes}", Modifier.weight(1f),
                    detalle = if (e.tardes > 0) "${formatoDuracion(e.minutosTardeTotal.toLong())} en total" else "¡Ninguna!",
                    alerta = e.tardes > 0,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Mosaico("Racha puntual", "🔥 ${e.rachaActual}", Modifier.weight(1f), detalle = "Mejor: ${e.mejorRacha}")
                Mosaico("Actividades especiales", "${e.actividades}", Modifier.weight(1f), detalle = "Fuera del colegio")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Mosaico(
                    "Entrada promedio", e.promedioEntrada?.let(::formatoHora) ?: "—", Modifier.weight(1f),
                    detalle = "Tu hora: ${horaAmigable(perfil.horario.entrada)}",
                )
                Mosaico(
                    "Jornada promedio", e.promedioJornada?.let(::formatoDuracionCorta) ?: "—", Modifier.weight(1f),
                    detalle = "Días completos",
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Mosaico(
                    "Días sin marcar", "${e.diasSinMarcar}", Modifier.weight(1f),
                    detalle = "Días laborales", alerta = e.diasSinMarcar > 0,
                )
                Mosaico(
                    "Salidas sin marcar", "${e.salidasSinMarcar}", Modifier.weight(1f),
                    detalle = "Entrada sin salida", alerta = e.salidasSinMarcar > 0,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Mosaico("Días libres", "${e.diasLibres}", Modifier.weight(1f), detalle = "Vacaciones y permisos")
            }
            GraficoHoras(e.barras, periodo, perfil.metaMinutos)
        }
    }
}

@Composable
private fun TarjetaPuntualidad(e: Estadisticas, perfil: Perfil) {
    val porcentaje = e.puntualidad ?: 0
    val color = if (porcentaje >= 80) colorExito else colorAviso
    Tarjeta {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Puntualidad", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("$porcentaje %", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "${e.puntuales} de ${e.puntuales + e.tardes} llegadas a tiempo",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            LinearProgressIndicator(
                progress = { porcentaje / 100f },
                color = color,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
                strokeCap = StrokeCap.Round,
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
            Text(
                when {
                    porcentaje == 100 -> "¡Excelente! Llegaste puntual todos los días. 🎉"
                    porcentaje >= 80 -> "¡Muy bien! Sigue así."
                    porcentaje >= 50 -> "Vas por buen camino: intenta llegar unos minutos antes."
                    else -> "Hay que mejorar: tu hora de entrada es ${horaAmigable(perfil.horario.entrada)}."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun Mosaico(
    etiqueta: String,
    valor: String,
    modifier: Modifier,
    detalle: String? = null,
    alerta: Boolean = false,
) {
    Tarjeta(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                valor,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (alerta) colorAviso else Color.Unspecified,
                maxLines = 1,
            )
            detalle?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Barras de horas trabajadas por día laboral. Al tocar una barra se muestra su detalle arriba. */
@Composable
private fun GraficoHoras(barras: List<Barra>, periodo: Periodo, meta: Int) {
    var seleccion by remember(barras) {
        mutableIntStateOf(barras.indexOfFirst { it.esHoy }.takeIf { it >= 0 } ?: barras.indexOfLast { it.minutos != null })
    }
    val medidor = rememberTextMeasurer()
    val colorBarra = MaterialTheme.colorScheme.primary
    val colorGuia = MaterialTheme.colorScheme.outlineVariant
    val colorMeta = MaterialTheme.colorScheme.outline
    val estiloEje = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val estiloSeleccion = estiloEje.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)

    Tarjeta {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Horas por día", style = MaterialTheme.typography.titleMedium)
            Text(
                "La línea marcada es la meta de ${meta / 60} horas.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                barras.getOrNull(seleccion)?.let(::describir) ?: "Toca una barra para ver el detalle.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 2,
            )
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .semantics { contentDescription = barras.joinToString(". ") { describir(it) } }
                    .pointerInput(barras) {
                        detectTapGestures { pos ->
                            val eje = 32.dp.toPx()
                            val i = ((pos.x - eje) / ((size.width - eje) / barras.size)).toInt()
                            if (i in barras.indices) seleccion = i
                        }
                    },
            ) {
                val eje = 32.dp.toPx()
                val alto = size.height - 22.dp.toPx()
                val maximo = max(meta + 60L, barras.maxOf { it.minutos ?: 0L }).toFloat()

                // Guías horizontales recesivas; la de la meta, un poco más marcada.
                listOf(0, meta / 2, meta).forEach { m ->
                    val y = alto - alto * m / maximo
                    val esMeta = m == meta
                    drawLine(
                        if (esMeta) colorMeta else colorGuia, Offset(eje, y), Offset(size.width, y),
                        strokeWidth = (if (esMeta) 1.5f else 1f).dp.toPx(),
                    )
                    val texto = medidor.measure("${m / 60} h", if (esMeta) estiloSeleccion else estiloEje)
                    drawText(texto, topLeft = Offset(0f, y - texto.size.height / 2f))
                }

                val ranura = (size.width - eje) / barras.size
                val ancho = min(ranura * 0.6f, 24.dp.toPx())
                val radio = min(4.dp.toPx(), ancho / 2)
                barras.forEachIndexed { i, b ->
                    val centro = eje + ranura * i + ranura / 2
                    val minutos = b.minutos ?: 0L
                    if (minutos > 0) {
                        val h = alto * minutos / maximo
                        val forma = Path().apply {
                            addRoundRect(
                                RoundRect(
                                    left = centro - ancho / 2, top = alto - h, right = centro + ancho / 2, bottom = alto,
                                    topLeftCornerRadius = CornerRadius(radio), topRightCornerRadius = CornerRadius(radio),
                                )
                            )
                        }
                        drawPath(forma, colorBarra, alpha = if (i == seleccion) 1f else 0.45f)
                    }
                    // En el mes hay ~22 barras: solo se rotulan algunas para que no se encimen.
                    val rotular = barras.size <= 7 || i == seleccion ||
                        (i % 5 == 0 && kotlin.math.abs(i - seleccion) > 2)
                    if (rotular) {
                        val texto = medidor.measure(
                            etiquetaDia(b.dia, periodo),
                            if (i == seleccion) estiloSeleccion else estiloEje,
                        )
                        drawText(texto, topLeft = Offset(centro - texto.size.width / 2f, alto + 6.dp.toPx()))
                    }
                }
            }
        }
    }
}

private fun etiquetaDia(dia: Long, periodo: Periodo) =
    if (periodo == Periodo.SEMANA) formatear("EEE", dia).trimEnd('.') else formatear("d", dia)

private fun describir(b: Barra): String {
    val fecha = formatear("EEEE d", b.dia)
    val j = b.jornada
    val entrada = j?.minutoEntrada
    val salida = j?.minutoSalida
    return when {
        entrada == null && b.libre != null -> "$fecha · ${b.libre.tipo.etiqueta}"
        b.futuro -> "$fecha · Aún no llega"
        entrada == null -> "$fecha · Sin registro"
        else -> buildString {
            append(fecha)
            append(" · ")
            append(formatoDuracion(b.minutos ?: 0))
            if (b.enCurso) append(" (en curso)")
            append("\nEntrada ${formatoHora(entrada)}")
            append(
                when (j?.puntual) {
                    true -> " · puntual"
                    false -> " · tarde"
                    null -> " · actividad especial"
                }
            )
            when {
                salida != null -> append(" · Salida ${formatoHora(salida)}")
                !b.enCurso -> append(" · sin salida")
            }
        }
    }
}
