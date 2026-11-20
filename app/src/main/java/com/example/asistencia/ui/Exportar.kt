/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 */
package com.example.asistencia.ui

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.asistencia.Config
import com.example.asistencia.R
import com.example.asistencia.datos.Solicitud
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Una celda: el texto que se ve y, si es un número, su valor (en Excel queda como número para poder sumarlo). */
data class Celda(val texto: String, val numero: Double? = null)

/** Tabla de un reporte, lista para guardarla como PDF o Excel. */
data class TablaReporte(
    val titulo: String,
    val subtitulo: String,
    val encabezados: List<String>,
    val filas: List<List<Celda>>,
    /** Fila de totales (opcional), en negrita al final. */
    val totales: List<Celda>? = null,
    /** Líneas de firma al final del PDF (p. ej. "Firma del colaborador"), para archivarlo impreso. */
    val firmas: List<String> = emptyList(),
)

object Exportar {
    /** Logo del colegio (PNG) para los archivos que se descargan. Se carga con [cargarLogo] antes de guardar. */
    private var logoPng: ByteArray? = null
    private var logoBitmap: android.graphics.Bitmap? = null

    fun cargarLogo(context: android.content.Context) {
        if (logoPng != null) return
        logoPng = runCatching { context.resources.openRawResource(R.drawable.logo_colegio).use { it.readBytes() } }.getOrNull()
        // Para los PDF basta una versión pequeña (el archivo pesa menos); el Excel usa el PNG original.
        logoBitmap = logoPng?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
            ?.let { android.graphics.Bitmap.createScaledBitmap(it, 420, 420 * it.height / it.width, true) }
    }

    /** Dibuja el logo con la altura dada; devuelve su ancho (0 si no hay logo). */
    private fun dibujarLogo(canvas: android.graphics.Canvas, x: Float, y: Float, alto: Float, alDerecha: Boolean = false): Float {
        val b = logoBitmap ?: return 0f
        val ancho = alto * b.width / b.height
        val izquierda = if (alDerecha) x - ancho else x
        canvas.drawBitmap(b, null, android.graphics.RectF(izquierda, y, izquierda + ancho, y + alto), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return ancho
    }

    private val generado get() = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
    private val pie get() = "Mesón Asiste · Creado por ${Config.CREADOR} · Generado el $generado"

    // ---------------------------------------------------------------- PDF

    /** Dibuja la tabla en un PDF horizontal (A4), con encabezado repetido en cada página y pie con el crédito. */
    fun pdf(tabla: TablaReporte, salida: OutputStream) {
        val ancho = 842
        val alto = 595
        val margen = 32f
        val util = ancho - 2 * margen
        val titulo = Paint().apply { isAntiAlias = true; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; color = 0xFF6B1412.toInt() }
        val subtitulo = Paint().apply { isAntiAlias = true; textSize = 9.5f; color = 0xFF5E5E5D.toInt() }
        val cabecera = Paint().apply { isAntiAlias = true; textSize = 8.5f; typeface = Typeface.DEFAULT_BOLD; color = Color.WHITE }
        val texto = Paint().apply { isAntiAlias = true; textSize = 8.5f; color = 0xFF1C1C1C.toInt() }
        val negrita = Paint(texto).apply { typeface = Typeface.DEFAULT_BOLD }
        val fondoCabecera = Paint().apply { color = 0xFFA62022.toInt() }
        val fondoPar = Paint().apply { color = 0xFFF6F1F1.toInt() }
        val fondoTotal = Paint().apply { color = 0xFFEDE3E3.toInt() }
        val pieP = Paint().apply { isAntiAlias = true; textSize = 7.5f; color = 0xFF999998.toInt() }

        // Ancho de cada columna según su contenido, ajustado al ancho de la página.
        val todas = listOf(tabla.encabezados.map { Celda(it) }) + tabla.filas + listOfNotNull(tabla.totales)
        val n = tabla.encabezados.size
        val naturales = (0 until n).map { c ->
            (todas.maxOf { f -> texto.measureText(f.getOrNull(c)?.texto.orEmpty()) } + 10f).coerceIn(34f, 190f)
        }
        val escala = util / naturales.sum()
        val anchos = naturales.map { it * escala }
        val altoFila = 16f

        val doc = PdfDocument()
        var numero = 0
        var pagina: PdfDocument.Page? = null
        var y = 0f

        fun recortar(t: String, p: Paint, max: Float): String {
            if (p.measureText(t) <= max) return t
            var s = t
            while (s.isNotEmpty() && p.measureText("$s…") > max) s = s.dropLast(1)
            return "$s…"
        }

        fun fila(celdas: List<String>, p: Paint, fondo: Paint?) {
            val canvas = pagina!!.canvas
            if (fondo != null) canvas.drawRect(margen, y, margen + util, y + altoFila, fondo)
            var x = margen
            celdas.forEachIndexed { i, t ->
                canvas.drawText(recortar(t, p, anchos[i] - 6f), x + 3f, y + altoFila - 4.5f, p)
                x += anchos[i]
            }
            y += altoFila
        }

        fun nuevaPagina() {
            pagina?.let { cerrar ->
                cerrar.canvas.drawText("$pie · Página $numero", margen, alto - 16f, pieP)
                doc.finishPage(cerrar)
            }
            numero++
            pagina = doc.startPage(PdfDocument.PageInfo.Builder(ancho, alto, numero).create())
            val canvas = pagina!!.canvas
            val anchoLogo = dibujarLogo(canvas, ancho - margen, margen - 10f, 40f, alDerecha = true)
            val espacioTexto = util - anchoLogo - 12f
            y = margen + 14f
            canvas.drawText(recortar(tabla.titulo, titulo, espacioTexto), margen, y, titulo)
            y += 14f
            tabla.subtitulo.split("\n").forEach { linea ->
                canvas.drawText(recortar(linea, subtitulo, espacioTexto), margen, y, subtitulo)
                y += 12f
            }
            y += 6f
            fila(tabla.encabezados, cabecera, fondoCabecera)
        }

        nuevaPagina()
        tabla.filas.forEachIndexed { i, f ->
            if (y + altoFila > alto - 36f) nuevaPagina()
            fila(f.map { it.texto }, texto, if (i % 2 == 1) fondoPar else null)
        }
        tabla.totales?.let {
            if (y + altoFila > alto - 36f) nuevaPagina()
            fila(it.map { c -> c.texto }, negrita, fondoTotal)
        }
        if (tabla.filas.isEmpty()) {
            pagina!!.canvas.drawText("Sin datos para este periodo.", margen, y + 14f, texto)
            y += 20f
        }
        if (tabla.firmas.isNotEmpty()) {
            if (y + 90f > alto - 36f) nuevaPagina()
            dibujarFirmas(pagina!!.canvas, tabla.firmas, margen, util, y + 58f)
        }
        pagina!!.canvas.drawText("$pie · Página $numero", margen, alto - 16f, pieP)
        doc.finishPage(pagina)
        doc.writeTo(salida)
        doc.close()
    }

    /** Líneas de firma repartidas a lo ancho, con el texto debajo de cada línea. */
    private fun dibujarFirmas(canvas: android.graphics.Canvas, firmas: List<String>, x0: Float, ancho: Float, y: Float) {
        val linea = Paint().apply { color = 0xFF1C1C1C.toInt(); strokeWidth = 0.8f }
        val etiqueta = Paint().apply { isAntiAlias = true; textSize = 9f; color = 0xFF1C1C1C.toInt(); textAlign = Paint.Align.CENTER }
        val nota = Paint(etiqueta).apply { textSize = 8f; color = 0xFF5E5E5D.toInt() }
        val espacio = ancho / firmas.size
        firmas.forEachIndexed { i, texto ->
            val partes = texto.split("\n")
            val centro = x0 + espacio * i + espacio / 2
            val mitad = (espacio * 0.38f).coerceAtMost(130f)
            canvas.drawLine(centro - mitad, y, centro + mitad, y, linea)
            canvas.drawText(partes.first(), centro, y + 12f, etiqueta)
            partes.drop(1).forEachIndexed { k, p -> canvas.drawText(p, centro, y + 23f + k * 10f, nota) }
        }
    }

    // ---------------------------------------------------------------- Solicitud de permiso (formato para firmar)

    /** "2026-11-19" → "19/11/2026". */
    private fun fecha(f: String) = f.split("-").takeIf { it.size == 3 }?.reversed()?.joinToString("/") ?: f

    /**
     * Formato de una solicitud de permiso o vacaciones (A4 vertical), con los datos, la resolución y los espacios
     * para la firma del solicitante y del Director o Supervisor. Sirve para imprimirla y archivarla.
     */
    fun solicitudPdf(s: Solicitud, salida: OutputStream) {
        val ancho = 595
        val alto = 842
        val m = 48f
        val util = ancho - 2 * m
        val rojo = 0xFFA62022.toInt()
        val gris = 0xFF5E5E5D.toInt()
        val doc = PdfDocument()
        val pagina = doc.startPage(PdfDocument.PageInfo.Builder(ancho, alto, 1).create())
        val c = pagina.canvas
        fun p(tam: Float, negrita: Boolean = false, color: Int = 0xFF1C1C1C.toInt()) =
            Paint().apply { isAntiAlias = true; textSize = tam; this.color = color; if (negrita) typeface = Typeface.DEFAULT_BOLD }
        val borde = Paint().apply { style = Paint.Style.STROKE; color = 0xFFBDBDBD.toInt(); strokeWidth = 0.8f }

        // Encabezado
        c.drawRect(0f, 0f, ancho.toFloat(), 8f, Paint().apply { color = rojo })
        var y = m + 4f
        if (dibujarLogo(c, m, 22f, 56f) == 0f) c.drawText("COLEGIO MESÓN", m, y, p(18f, true, 0xFF6B1412.toInt()))
        val derecha = { t: String, yy: Float, pp: Paint -> c.drawText(t, ancho - m - pp.measureText(t), yy, pp) }
        derecha("No. " + s.id.take(8).uppercase(), y, p(10f, true))
        derecha("Control de asistencia · Mesón Asiste", y + 16f, p(9.5f, color = gris))
        if (s.creada.isNotBlank()) derecha("Solicitada el " + fecha(s.creada), y + 30f, p(9.5f, color = gris))
        y = 112f
        val titulo = if (s.tipo == "VACACIONES") "SOLICITUD DE VACACIONES" else "SOLICITUD DE PERMISO"
        c.drawText(titulo, ancho / 2f, y, p(15f, true).apply { textAlign = Paint.Align.CENTER })
        y += 26f

        fun seccion(nombre: String) {
            c.drawRect(m, y, m + util, y + 18f, Paint().apply { color = 0xFFF6F1F1.toInt() })
            c.drawText(nombre.uppercase(), m + 8f, y + 12.5f, p(9f, true, 0xFF6B1412.toInt()))
            y += 30f
        }
        fun campo(etiqueta: String, valor: String, x: Float = m, anchoCampo: Float = util) {
            c.drawText(etiqueta, x, y, p(8.5f, color = gris))
            c.drawText(valor.ifBlank { "—" }, x, y + 14f, p(11f))
            c.drawLine(x, y + 19f, x + anchoCampo - 12f, y + 19f, borde)
        }
        fun parrafo(texto: String, alturaMin: Float) {
            val pp = p(10.5f)
            val lineas = mutableListOf<String>()
            texto.ifBlank { "—" }.split("\n").forEach { renglon ->
                var actual = ""
                renglon.split(" ").forEach { palabra ->
                    val prueba = if (actual.isEmpty()) palabra else "$actual $palabra"
                    if (pp.measureText(prueba) > util - 16f && actual.isNotEmpty()) {
                        lineas += actual
                        actual = palabra
                    } else actual = prueba
                }
                lineas += actual
            }
            val alturaCaja = maxOf(alturaMin, lineas.size * 14f + 14f)
            c.drawRect(m, y, m + util, y + alturaCaja, borde)
            lineas.forEachIndexed { i, l -> c.drawText(l, m + 8f, y + 16f + i * 14f, pp) }
            y += alturaCaja + 16f
        }

        seccion("Datos del solicitante")
        campo("Nombre completo", s.nombre, m, util * 0.6f)
        campo("Puesto o rol", s.rol, m + util * 0.6f, util * 0.4f)
        y += 34f
        campo("Correo institucional", s.correo)
        y += 40f

        seccion("Detalle de la solicitud")
        val dias = runCatching {
            val f = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            ((f.parse(s.hasta)!!.time - f.parse(s.desde)!!.time) / 86_400_000L + 1).toInt()
        }.getOrDefault(1)
        val unDia = s.desde == s.hasta
        campo("Tipo", s.tipoTexto, m, util / 3)
        campo(if (unDia) "Fecha" else "Desde", fecha(s.desde), m + util / 3, util / 3)
        when {
            s.porHoras -> campo("Horario", "${s.horaInicio} a ${s.horaFin}", m + 2 * util / 3, util / 3)
            unDia -> campo("Duración", "1 día", m + 2 * util / 3, util / 3)
            else -> campo("Hasta", "${fecha(s.hasta)} ($dias días)", m + 2 * util / 3, util / 3)
        }
        y += 40f
        c.drawText("Motivo", m, y, p(8.5f, color = gris))
        y += 6f
        parrafo(s.motivo, 60f)

        seccion("Resolución")
        var x = m
        val marca = Paint().apply { color = rojo; strokeWidth = 1.8f; isAntiAlias = true }
        listOf("APROBADA" to "Aprobada", "RECHAZADA" to "Rechazada", "PENDIENTE" to "Pendiente").forEach { (clave, texto) ->
            c.drawRect(x, y - 10f, x + 11f, y + 1f, borde)
            if (s.estado == clave) {
                c.drawLine(x + 2f, y - 4f, x + 5f, y - 1f, marca)
                c.drawLine(x + 5f, y - 1f, x + 10f, y - 9f, marca)
            }
            c.drawText(texto, x + 17f, y, p(10.5f))
            x += 110f
        }
        y += 24f
        campo("Revisada por", s.revisadaPor, m, util * 0.6f)
        campo("Fecha de revisión", fecha(s.revisadaEl), m + util * 0.6f, util * 0.4f)
        y += 40f
        c.drawText("Observaciones", m, y, p(8.5f, color = gris))
        y += 6f
        parrafo(s.respuesta, 46f)

        // Firmas (al pie de la página)
        val yFirmas = maxOf(y + 56f, alto - 150f)
        dibujarFirmas(
            c,
            listOf(
                "Firma del solicitante\n${s.nombre}",
                "Firma del Director o Supervisor" + if (s.revisadaPor.isNotBlank()) "\n${s.revisadaPor}" else "",
            ),
            m, util, yFirmas,
        )
        c.drawText(pie, m, alto - 24f, p(7.5f, color = 0xFF999998.toInt()))
        doc.finishPage(pagina)
        doc.writeTo(salida)
        doc.close()
    }

    // ---------------------------------------------------------------- Excel (.xlsx)

    /** Escribe un archivo .xlsx sencillo (una hoja), que abre Excel, Google Sheets o cualquier hoja de cálculo. */
    fun xlsx(tabla: TablaReporte, salida: OutputStream) {
        val filas = mutableListOf<Pair<List<Celda>, Boolean>>() // celdas, negrita
        val logo = logoPng
        // Las primeras 4 filas quedan libres para el logo.
        if (logo != null) repeat(4) { filas += emptyList<Celda>() to false }
        filas += listOf(Celda(tabla.titulo)) to true
        tabla.subtitulo.split("\n").forEach { filas += listOf(Celda(it)) to false }
        filas += emptyList<Celda>() to false
        filas += tabla.encabezados.map { Celda(it) } to true
        tabla.filas.forEach { filas += it to false }
        tabla.totales?.let { filas += it to true }
        filas += emptyList<Celda>() to false
        filas += listOf(Celda(pie)) to false

        val n = tabla.encabezados.size
        val anchos = (0 until n).map { c ->
            ((listOf(tabla.encabezados) + tabla.filas.map { f -> f.map { it.texto } })
                .maxOf { it.getOrNull(c)?.length ?: 0 } + 2).coerceIn(8, 45)
        }
        val hoja = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><cols>""")
            anchos.forEachIndexed { i, w -> append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""") }
            append("</cols><sheetData>")
            filas.forEachIndexed { r, (celdas, bold) ->
                append("""<row r="${r + 1}">""")
                celdas.forEachIndexed { c, celda ->
                    val ref = columna(c) + (r + 1)
                    val estilo = if (bold) " s=\"1\"" else ""
                    if (celda.numero != null && !bold) {
                        append("""<c r="$ref"$estilo><v>${celda.numero}</v></c>""")
                    } else {
                        append("""<c r="$ref" t="inlineStr"$estilo><is><t xml:space="preserve">${xml(celda.texto)}</t></is></c>""")
                    }
                }
                append("</row>")
            }
            append("</sheetData>")
            if (logo != null) append("""<drawing r:id="rId1"/>""")
            append("</worksheet>")
        }
        ZipOutputStream(salida).use { zip ->
            fun archivo(nombre: String, contenido: ByteArray) {
                zip.putNextEntry(ZipEntry(nombre))
                zip.write(contenido)
                zip.closeEntry()
            }
            fun archivo(nombre: String, contenido: String) = archivo(nombre, contenido.toByteArray(Charsets.UTF_8))
            if (logo != null) {
                // Logo de 0.75 pulgadas de alto (EMU: 914400 por pulgada), arriba a la izquierda.
                val alto = 685_800L
                val ancho = logoBitmap?.let { alto * it.width / it.height } ?: (alto * 2)
                archivo("xl/media/logo.png", logo)
                archivo("xl/worksheets/_rels/sheet1.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing" Target="../drawings/drawing1.xml"/></Relationships>""")
                archivo("xl/drawings/_rels/drawing1.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/logo.png"/></Relationships>""")
                archivo("xl/drawings/drawing1.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><xdr:wsDr xmlns:xdr="http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><xdr:oneCellAnchor><xdr:from><xdr:col>0</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>0</xdr:row><xdr:rowOff>38100</xdr:rowOff></xdr:from><xdr:ext cx="$ancho" cy="$alto"/><xdr:pic><xdr:nvPicPr><xdr:cNvPr id="2" name="Logo"/><xdr:cNvPicPr><a:picLocks noChangeAspect="1"/></xdr:cNvPicPr></xdr:nvPicPr><xdr:blipFill><a:blip r:embed="rId1"/><a:stretch><a:fillRect/></a:stretch></xdr:blipFill><xdr:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$ancho" cy="$alto"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></xdr:spPr></xdr:pic><xdr:clientData/></xdr:oneCellAnchor></xdr:wsDr>""")
            }
            archivo("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="png" ContentType="image/png"/><Override PartName="/xl/drawings/drawing1.xml" ContentType="application/vnd.openxmlformats-officedocument.drawing+xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>""")
            archivo("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            archivo("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Reporte" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            archivo("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
            archivo("xl/styles.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs></styleSheet>""")
            archivo("xl/worksheets/sheet1.xml", hoja)
        }
    }

    /** 0 → "A", 25 → "Z", 26 → "AA". */
    private fun columna(i: Int): String {
        var n = i
        var s = ""
        do {
            s = ('A' + n % 26) + s
            n = n / 26 - 1
        } while (n >= 0)
        return s
    }

    private fun xml(t: String) = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        .filter { it == '\t' || it == '\n' || it >= ' ' }
}
