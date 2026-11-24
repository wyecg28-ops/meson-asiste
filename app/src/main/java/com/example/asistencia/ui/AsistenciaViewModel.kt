/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 */
package com.example.asistencia.ui

import android.app.Application
import android.text.format.DateUtils
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.asistencia.Config
import com.example.asistencia.auth.Biometria
import com.example.asistencia.auth.MetodoBiometrico
import com.example.asistencia.datos.CuentaGuardada
import com.example.asistencia.datos.EstadoMarca
import com.example.asistencia.datos.Marca
import com.example.asistencia.datos.HistorialHoja
import com.example.asistencia.datos.Marcas
import com.example.asistencia.datos.Perfil
import com.example.asistencia.datos.Periodo
import com.example.asistencia.datos.Ranking
import com.example.asistencia.datos.ServidorException
import com.example.asistencia.datos.SheetsApi
import com.example.asistencia.datos.Sesion
import com.example.asistencia.datos.TipoRegistro
import com.example.asistencia.datos.agruparPorDia
import com.example.asistencia.datos.calcularEstadisticas
import com.example.asistencia.datos.datosDelTelefono
import com.example.asistencia.datos.formatoDuracion
import com.example.asistencia.datos.formatoHora
import com.example.asistencia.datos.inicioDelDia
import com.example.asistencia.datos.minutoDelDia
import com.example.asistencia.datos.Novedad
import com.example.asistencia.datos.Novedades
import com.example.asistencia.datos.aMinutos
import com.example.asistencia.datos.claveDia
import com.example.asistencia.datos.diaIso
import com.example.asistencia.datos.terminaDiaSiguiente
import com.example.asistencia.datos.textoDiasLaborales
import com.example.asistencia.notificaciones.Notificaciones
import com.example.asistencia.notificaciones.Recordatorios
import com.example.asistencia.ubicacion.Ubicacion
import com.example.asistencia.ubicacion.UbicacionException
import com.example.asistencia.verificacion.CodigoQr
import com.example.asistencia.verificacion.MetodoVerificacion
import com.example.asistencia.verificacion.WifiCercano
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.UUID

data class Mensaje(val texto: String, val error: Boolean)

enum class TipoResultado(val positivo: Boolean) {
    PUNTUAL(true),
    TARDE(false),
    ACTIVIDAD(true),
    META_CUMPLIDA(true),
    META_INCOMPLETA(false),
}

/** Lo que se muestra en el diálogo después de marcar: felicitación o alerta. */
data class Resultado(val tipo: TipoResultado, val titulo: String, val detalle: String, val nota: String? = null) {
    val positivo get() = tipo.positivo
}

data class EstadoUi(
    val correo: String? = null,
    val nombre: String? = null,
    val permisosListos: Boolean = false,
    val metodo: MetodoBiometrico? = null,
    val perfil: Perfil = Perfil.POR_DEFECTO,
    val recordatorios: Boolean = true,
    /** Pedir huella/rostro al abrir la app. */
    val pedirHuella: Boolean = true,
    /** La app está bloqueada hasta confirmar la huella o el rostro. */
    val bloqueado: Boolean = false,
    /** Cuentas guardadas en este teléfono (pantalla de inicio de sesión). */
    val cuentas: List<CuentaGuardada> = emptyList(),
    /** Mensaje del servidor si el administrador desactivó o eliminó a esta persona. */
    val accesoCerrado: String? = null,
    /** Preguntando al servidor si este teléfono ya estaba registrado. */
    val buscandoRegistro: Boolean = false,
    val ocupado: Boolean = false,
    val mensaje: Mensaje? = null,
    val resultado: Resultado? = null,
    val marcas: List<Marca> = emptyList(),
    /** Aumenta con cada marca guardada; la pantalla lo usa para limpiar la observación. */
    val marcasGuardadas: Int = 0,
    /** Ranking de horas del mes elegido (pantalla de inicio). */
    val ranking: Ranking? = null,
    val cargandoRanking: Boolean = false,
    /** El super administrador entra en un dispositivo nuevo: correo y mensaje para pedir su clave. */
    val pedirClave: Pair<String, String>? = null,
    /** Avisos de la tarjeta "Novedades" que aún no se marcan como leídos. */
    val novedades: List<Novedad> = emptyList(),
) {
    val registrado get() = correo != null
    val listo get() = registrado && permisosListos

    /** Pide huella o rostro para entrar: si la persona lo activó y el teléfono tiene cómo confirmar. */
    val entraConHuella get() = pedirHuella && metodo != null
    val pendientes get() = marcas.count { it.estado == EstadoMarca.PENDIENTE }
}

class AsistenciaViewModel(app: Application) : AndroidViewModel(app) {

    private val sesion = Sesion(app)
    /** Historial de la cuenta abierta (cada cuenta tiene el suyo). */
    private val marcas get() = Marcas(getApplication(), sesion.correo)

    /** Lo que se muestra: la hoja de Google (con las correcciones de la administración) + lo pendiente del teléfono. */
    private fun visibles() = HistorialHoja.combinar(marcas.todas(), sesion.historialHoja, sesion.historialHojaHora, sesion.perfil)
    private val sincronizando = Mutex()

    private val _estado = MutableStateFlow(estadoGuardado())
    val estado = _estado.asStateFlow()

    init {
        Recordatorios.programarTodos(app)
        if (sesion.correo != null && sesion.metodo == null) {
            elegirMetodoAutomatico()
            _estado.update { it.copy(metodo = sesion.metodo) }
        }
        viewModelScope.launch {
            // Teléfono sin ninguna cuenta guardada (app nueva o reinstalada): se busca su registro en el servidor.
            if (sesion.correo == null && sesion.cuentas.isEmpty()) recuperarRegistro()
            sincronizar()
            // El ranking llega con el perfil; si no vino (servidor anterior o falló), se pide aparte.
            if (_estado.value.ranking == null) cargarRanking()
        }
    }

    /**
     * Si el teléfono no recuerda su registro (app reinstalada o datos borrados) pero el servidor sí,
     * se recupera solo: nombre, correo y, si se puede, el método. Así no se repiten los 3 pasos.
     */
    private suspend fun recuperarRegistro() {
        val app = getApplication<Application>()
        _estado.update { it.copy(buscandoRegistro = true) }
        try {
            val r = SheetsApi.enviar("recuperar", datosDelTelefono(app))
            if (!r.optBoolean("encontrado") || sesion.correo != null) return
            sesion.abrir(r.getString("correo").lowercase())
            sesion.nombre = r.getString("nombre")
            sesion.enviadoAlServidor = true
            MetodoBiometrico.entries
                .firstOrNull { it.etiqueta == r.optString("metodo") && Biometria.disponible(app, it) }
                ?.let { sesion.metodo = it }
            elegirMetodoAutomatico()
            if (ubicacionConcedida(app)) sesion.permisosListos = true
            _estado.value = estadoGuardado().copy(
                bloqueado = false,
                mensaje = Mensaje("¡Bienvenido(a) de nuevo, ${sesion.nombre}! Recuperamos tu registro.", error = false),
            )
            Recordatorios.programarTodos(app)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Sin conexión o servidor anterior: se hace el registro normal.
        } finally {
            _estado.update { it.copy(buscandoRegistro = false) }
        }
    }

    private fun estadoGuardado() = EstadoUi(
        correo = sesion.correo,
        nombre = sesion.nombre,
        permisosListos = sesion.permisosListos,
        metodo = sesion.metodo,
        perfil = sesion.perfil,
        recordatorios = sesion.recordatorios,
        pedirHuella = sesion.pedirHuella,
        cuentas = sesion.cuentas,
        accesoCerrado = sesion.accesoCerrado,
        marcas = visibles(),
        novedades = buzon.items(),
    ).let { it.copy(bloqueado = it.listo && it.entraConHuella) }

    // ---------- Novedades (tarjeta de Inicio) ----------

    /** Buzón de novedades de la cuenta abierta. */
    private val buzon get() = Novedades(getApplication(), sesion.correo)

    private fun agregarNovedad(n: Novedad) {
        buzon.agregar(n)
        _estado.update { it.copy(novedades = buzon.items()) }
    }

    /** "Entendido": quita un aviso de la tarjeta. */
    fun leerNovedad(id: String) {
        buzon.quitar(id)
        _estado.update { it.copy(novedades = buzon.items()) }
    }

    fun leerNovedades() {
        buzon.quitarTodas()
        _estado.update { it.copy(novedades = emptyList()) }
    }

    /**
     * Resumen de la semana en la tarjeta de Novedades: al terminar el último día laboral (media hora después de la
     * salida) o en el fin de semana, una vez por semana. Es el mismo resumen que llega como notificación.
     */
    private fun revisarResumenSemanal() {
        val perfil = sesion.perfil
        if (sesion.correo == null || !perfil.marca) return
        val ahora = System.currentTimeMillis()
        val hoy = inicioDelDia(ahora)
        val ultimo = perfil.horario.dias.maxOrNull() ?: 5
        val entrada = perfil.horaEntrada(hoy)
        val salida = perfil.horaSalida(hoy)
        val termino = diaIso(ahora) > ultimo || (diaIso(ahora) == ultimo && !terminaDiaSiguiente(entrada, salida) &&
            minutoDelDia(ahora) >= aMinutos(salida) + 30)
        val lunes = java.util.Calendar.getInstance().apply {
            timeInMillis = hoy
            while (get(java.util.Calendar.DAY_OF_WEEK) != java.util.Calendar.MONDAY) add(java.util.Calendar.DAY_OF_YEAR, -1)
        }.timeInMillis.let(::claveDia)
        val b = buzon
        if (!termino || b.semanaResumida == lunes) return
        val e = calcularEstadisticas(marcas.todas(), perfil, Periodo.SEMANA, ahora)
        if (e.diasAsistidos > 0) {
            agregarNovedad(
                Novedad(
                    "semana-$lunes", "📊",
                    when {
                        e.tardes == 0 && e.metaIncompleta == 0 -> "¡Felicidades! Semana perfecta"
                        (e.puntualidad ?: 100) >= 80 -> "¡Buena semana!"
                        else -> "Tu semana en resumen"
                    },
                    "${e.diasAsistidos} días · ${e.puntuales} puntuales · ${e.tardes} tarde · ${e.metaCumplida} con la meta " +
                        "cumplida · ${formatoDuracion(e.minutosTrabajados)} en total" +
                        (if (e.minutosExtra > 0) " · ${formatoDuracion(e.minutosExtra)} extra" else "") + ".",
                ),
            )
        }
        b.semanaResumida = lunes
    }

    // ---------- Bloqueo con huella al abrir ----------

    /** Momento (reloj del sistema) en que la app pasó a segundo plano. */
    private var enSegundoPlanoDesde = 0L

    /** Abre la app con la huella o el rostro elegido; no hace falta escribir nada. */
    fun desbloquear(activity: FragmentActivity) = ejecutar {
        val metodo = sesion.metodo ?: return@ejecutar _estado.update { it.copy(bloqueado = false) }
        Biometria.autenticar(activity, metodo, "Entrar a Mesón Asiste")
        _estado.update { it.copy(bloqueado = false) }
    }

    fun alSalirDeLaApp() {
        enSegundoPlanoDesde = android.os.SystemClock.elapsedRealtime()
    }

    /** Si la app estuvo más de un minuto en segundo plano, se vuelve a pedir la huella. */
    fun alVolverALaApp() {
        val e = _estado.value
        val fuera = android.os.SystemClock.elapsedRealtime() - enSegundoPlanoDesde
        if (enSegundoPlanoDesde > 0 && fuera > 60_000 && e.listo && e.entraConHuella && !e.ocupado) {
            _estado.update { it.copy(bloqueado = true, resultado = null) }
        }
    }

    fun cambiarPedirHuella(activo: Boolean) {
        sesion.pedirHuella = activo
        _estado.update { it.copy(pedirHuella = activo) }
        mostrarMensaje(
            Mensaje(
                if (activo) "Iniciar sesión con huella: activado." else "Iniciar sesión con huella: desactivado. Al marcar se sigue pidiendo.",
                error = false,
            )
        )
    }

    /** Paso 1: guarda los datos en el teléfono. No necesita internet. */
    fun registrar(nombre: String, correo: String) {
        val correoLimpio = correo.trim().lowercase()
        val nombreLimpio = nombre.trim().replace(Regex("\\s+"), " ")
        when {
            nombreLimpio.length < 3 -> return mostrarError("Escribe tu nombre completo.")
            !Regex("^[a-z0-9._%+-]+@${Regex.escape(Config.DOMINIO)}$").matches(correoLimpio) ->
                return mostrarError("Usa tu correo institucional @${Config.DOMINIO}.")
        }
        sesion.abrir(correoLimpio)
        sesion.nombre = nombreLimpio
        sesion.enviadoAlServidor = false
        elegirMetodoAutomatico()
        if (ubicacionConcedida(getApplication())) sesion.permisosListos = true
        // Si se conservaron permisos y método (por ejemplo, tras cerrar sesión), entra directo, sin pedir la huella.
        _estado.value = estadoGuardado().copy(
            bloqueado = false,
            mensaje = Mensaje("Registro guardado. ¡Bienvenido(a), $nombreLimpio!", error = false),
        )
        Recordatorios.programarTodos(getApplication())
        viewModelScope.launch { sincronizar() }
    }

    fun permisosConcedidos() {
        sesion.permisosListos = true
        _estado.update { it.copy(permisosListos = true, mensaje = null) }
        Recordatorios.programarTodos(getApplication())
    }

    /** Confirma una vez el método elegido para comprobar que el sensor funciona. */
    fun elegirMetodo(activity: FragmentActivity, metodo: MetodoBiometrico) = ejecutar {
        Biometria.autenticar(activity, metodo, "Confirmar ${metodo.etiqueta.lowercase()}")
        sesion.metodo = metodo
        _estado.update { it.copy(metodo = metodo, mensaje = Mensaje("Listo. Ya puedes marcar tu asistencia.", error = false)) }
    }

    /**
     * Toma la marca, la guarda en el teléfono y luego intenta enviarla a la hoja.
     * [actividad] = la persona está en una actividad especial fuera del colegio (requiere observación).
     */
    fun marcar(activity: FragmentActivity, tipo: TipoRegistro, observacion: String, actividad: Boolean) = ejecutar {
        if (!sesion.perfil.marca) throw IllegalStateException("Tu rol no marca asistencia.")
        if (actividad && observacion.isBlank()) {
            throw IllegalStateException("Describe la actividad especial (por ejemplo: excursión al museo con 3.º básico).")
        }
        validarSecuencia(tipo)
        // Se pide huella, rostro o PIN si el teléfono los tiene (aunque no se pidan para entrar); si no, solo ubicación.
        val metodo = sesion.metodo?.takeIf { Biometria.disponible(getApplication(), it) }
        if (metodo != null) Biometria.autenticar(activity, metodo, "Registrar ${tipo.etiqueta.lowercase()}")

        val lectura = Ubicacion.obtener(getApplication())
        if (lectura.simulada) {
            throw UbicacionException("Se detectó una ubicación simulada. Desactiva cualquier app de GPS falso.")
        }

        // Prueba de presencia que pide la sede más cercana: QR de la entrada o Wi-Fi del colegio.
        val sede = sesion.perfil.sedes.minByOrNull {
            Ubicacion.distanciaMetros(lectura.latitud, lectura.longitud, it.latitud, it.longitud)
        }
        val verificacion = if (actividad) MetodoVerificacion.UBICACION else MetodoVerificacion.de(sede?.metodo)
        var codigoQr = ""
        var wifi = emptyList<String>()
        when (verificacion) {
            MetodoVerificacion.QR -> {
                val texto = CodigoQr.escanear(activity)
                    ?: throw IllegalStateException("Para marcar, escanea el código QR que se muestra en la entrada.")
                codigoQr = CodigoQr.leer(texto)
                    ?: throw IllegalStateException("Ese no es el código QR de asistencia. Escanea el de la entrada.")
            }
            MetodoVerificacion.WIFI -> wifi = WifiCercano.vistos(getApplication())
            MetodoVerificacion.UBICACION -> Unit
        }

        val marca = Marca(
            id = UUID.randomUUID().toString(),
            tipo = tipo.name,
            fechaHora = System.currentTimeMillis(),
            metodo = metodo?.etiqueta ?: "Sin biometría (el teléfono no tiene)",
            latitud = lectura.latitud,
            longitud = lectura.longitud,
            precision = lectura.precisionMetros.toDouble(),
            observacion = observacion.trim(),
            actividad = actividad,
            codigoQr = codigoQr,
            wifi = wifi,
        )
        marcas.agregar(marca)
        _estado.update { it.copy(marcas = visibles(), marcasGuardadas = it.marcasGuardadas + 1) }

        enviarPendientes()
        val final = marcas.todas().first { it.id == marca.id }
        viewModelScope.launch { runCatching { sincronizar() } }
        if (final.estado == EstadoMarca.RECHAZADA) {
            mostrarError("${tipo.etiqueta} rechazada: ${final.detalle}")
        } else {
            _estado.update { it.copy(resultado = resultadoDe(final)) }
        }
    }

    fun cerrarResultado() = _estado.update { it.copy(resultado = null) }

    /** Ranking de horas de un mes ("2026-11"; null = el mes actual). No muestra errores: es solo informativo. */
    fun cargarRanking(mes: String? = null) = viewModelScope.launch {
        if (sesion.correo == null || _estado.value.cargandoRanking) return@launch
        _estado.update { it.copy(cargandoRanking = true) }
        try {
            val datos = datosDelTelefono(getApplication())
            if (mes != null) datos.put("mes", mes)
            val r = Ranking.deJson(SheetsApi.enviar("ranking", datos))
            _estado.update { it.copy(ranking = r) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Sin conexión o servidor sin actualizar: se queda el ranking que ya había.
        } finally {
            _estado.update { it.copy(cargandoRanking = false) }
        }
    }

    /**
     * Actualización silenciosa (al volver a la app y cada pocos minutos): envía pendientes, descarga el perfil
     * y el ranking. Así se ven los cambios hechos en la hoja de Google sin tocar "Actualizar".
     */
    fun refrescar() = viewModelScope.launch {
        val e = _estado.value
        if (e.correo == null || e.bloqueado || e.ocupado) return@launch
        try {
            sincronizar()
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            // Silencioso.
        }
        // El ranking llega con el perfil; solo se pide aparte si el servidor publicado es anterior.
        if (_estado.value.ranking == null) cargarRanking()
    }

    /** Envía pendientes y vuelve a descargar rol, jornada y calendario. */
    fun reintentarEnvio() = ejecutar {
        sincronizar()
        val pendientes = _estado.value.pendientes
        mostrarMensaje(
            if (pendientes == 0) Mensaje("Todo está al día.", error = false)
            else Mensaje("Quedan $pendientes registros pendientes. No hay conexión con el servidor.", error = true)
        )
    }

    fun cambiarRecordatorios(activos: Boolean) {
        sesion.recordatorios = activos
        _estado.update { it.copy(recordatorios = activos) }
        Recordatorios.programarTodos(getApplication())
        mostrarMensaje(
            Mensaje(if (activos) "Recordatorios activados." else "Recordatorios desactivados.", error = false)
        )
    }

    /** Cambia entre huella y rostro, si el teléfono tiene los dos; se confirma con el nuevo. */
    fun cambiarMetodo(activity: FragmentActivity) = ejecutar {
        val disponibles = MetodoBiometrico.entries.filter { Biometria.disponible(getApplication(), it) }
        if (disponibles.size < 2) {
            throw IllegalStateException(
                if (disponibles.isEmpty()) "Este teléfono no tiene huella, rostro ni PIN configurado."
                else "Este teléfono solo permite ${disponibles.first().etiqueta.lowercase()}."
            )
        }
        val siguiente = disponibles[(disponibles.indexOf(sesion.metodo) + 1) % disponibles.size]
        Biometria.autenticar(activity, siguiente, "Confirmar ${siguiente.etiqueta.lowercase()}")
        sesion.metodo = siguiente
        _estado.update { it.copy(metodo = siguiente, mensaje = Mensaje("Ahora confirmas con ${siguiente.etiqueta.lowercase()}.", error = false)) }
    }

    /**
     * Sale de la cuenta y vuelve a la pantalla de inicio de sesión. La cuenta queda guardada (con su historial
     * y sus marcas pendientes, que se envían al volver a entrar): basta tocarla y poner la huella.
     */
    fun cerrarSesion() {
        sesion.salir()
        Recordatorios.cancelarTodos(getApplication())
        _estado.value = estadoGuardado()
    }

    /** Entra a una cuenta guardada con la huella o el rostro (sin escribir nada ni repetir pasos). */
    fun entrarConCuenta(activity: FragmentActivity, correo: String) = ejecutar {
        val app = getApplication<Application>()
        elegirMetodoAutomatico()
        if (sesion.pedirHuella) sesion.metodo?.let { Biometria.autenticar(activity, it, "Entrar a Mesón Asiste") }
        sesion.abrir(correo)
        if (ubicacionConcedida(app)) sesion.permisosListos = true
        _estado.value = estadoGuardado().copy(bloqueado = false)
        sincronizar()
    }

    /**
     * Iniciar sesión con una cuenta que ya existe en el colegio: solo el correo.
     * El servidor devuelve su nombre y rol; si no existe, se avisa para crearla.
     */
    fun iniciarSesion(correo: String) = iniciarSesion(correo, null)

    /** Cierra el cuadro de la clave del super administrador sin entrar. */
    fun cancelarClave() = _estado.update { it.copy(pedirClave = null) }

    fun iniciarSesion(correo: String, clave: String?) = ejecutar {
        val app = getApplication<Application>()
        val correoLimpio = correo.trim().lowercase()
        if (!Regex("^[a-z0-9._%+-]+@${Regex.escape(Config.DOMINIO)}$").matches(correoLimpio)) {
            throw IllegalStateException("Escribe tu correo del colegio (@${Config.DOMINIO}).")
        }
        val r = try {
            SheetsApi.enviar("iniciarSesion", datosDelTelefono(app, correoLimpio).apply { if (clave != null) put("clave", clave) })
        } catch (e: IOException) {
            throw IllegalStateException("Para iniciar sesión se necesita internet. Revisa la conexión e inténtalo de nuevo.")
        } catch (e: ServidorException) {
            // Super administrador en un dispositivo nuevo: se pide su clave secreta.
            if (e.codigo == "CLAVE_SUPERADMIN") {
                _estado.update { it.copy(pedirClave = correoLimpio to e.message.orEmpty()) }
                return@ejecutar
            }
            // Versión anterior del servidor: no conoce "iniciarSesion".
            if (e.message == "Acción no válida.") {
                throw IllegalStateException(
                    "El servidor del colegio aún no está actualizado. Publica la nueva versión del servidor " +
                        "(Implementar → Gestionar implementaciones → Nueva versión)."
                )
            }
            throw e
        }
        if (!r.optBoolean("encontrado")) {
            throw IllegalStateException("No existe una cuenta con $correoLimpio. Toca \"Crear cuenta nueva\".")
        }
        _estado.update { it.copy(pedirClave = null) }
        sesion.abrir(correoLimpio)
        sesion.nombre = r.optString("nombre", correoLimpio)
        sesion.enviadoAlServidor = true
        elegirMetodoAutomatico()
        if (ubicacionConcedida(app)) sesion.permisosListos = true
        _estado.value = estadoGuardado().copy(
            bloqueado = false,
            mensaje = Mensaje("¡Hola, ${sesion.nombre}!", error = false),
        )
        sincronizar()
    }

    /**
     * La persona libera este teléfono para usar su cuenta en otro, sin pedírselo al administrador.
     * Primero envía las marcas pendientes (después ya no se aceptarían) y luego quita la cuenta de este teléfono.
     */
    fun liberarTelefono() = ejecutar {
        val correo = sesion.correo ?: return@ejecutar
        val app = getApplication<Application>()
        sincronizar()
        if (_estado.value.pendientes > 0) {
            throw IllegalStateException("Tienes marcas sin enviar. Conéctate a internet para enviarlas antes de liberar el teléfono.")
        }
        val r = try {
            SheetsApi.enviar("liberarMiTelefono", datosDelTelefono(app))
        } catch (e: IOException) {
            throw IllegalStateException("Para liberar el teléfono se necesita internet.")
        } catch (e: ServidorException) {
            if (e.message == "Acción no válida.") {
                throw IllegalStateException("El servidor aún no está actualizado. Publica la nueva versión del servidor.")
            }
            throw e
        }
        sesion.quitarCuenta(correo)
        Recordatorios.cancelarTodos(app)
        _estado.value = estadoGuardado().copy(mensaje = Mensaje(r.optString("mensaje", "Teléfono liberado."), error = false))
    }

    /** Quita una cuenta de este teléfono (no la borra del colegio: puede volver a agregarse). */
    fun quitarCuenta(correo: String) {
        if (Marcas(getApplication(), correo).todas().any { it.estado == EstadoMarca.PENDIENTE }) {
            return mostrarError("Esa cuenta tiene marcas sin enviar. Entra a ella con internet para enviarlas antes de quitarla.")
        }
        val eraLaAbierta = sesion.correo == correo
        sesion.quitarCuenta(correo)
        if (eraLaAbierta) Recordatorios.cancelarTodos(getApplication())
        _estado.value = estadoGuardado().copy(mensaje = Mensaje("Cuenta quitada de este teléfono.", error = false))
    }

    /**
     * Elige solo cómo confirmar la identidad (sin pedirlo como un paso): huella si el teléfono tiene,
     * si no, rostro con el PIN o patrón del teléfono como respaldo. Se puede cambiar en Perfil.
     */
    private fun elegirMetodoAutomatico() {
        if (sesion.metodo != null) return
        val app = getApplication<Application>()
        sesion.metodo = MetodoBiometrico.entries.firstOrNull { Biometria.disponible(app, it) }
    }

    fun mostrarError(texto: String) = mostrarMensaje(Mensaje(texto, error = true))

    fun mostrarAviso(texto: String) = mostrarMensaje(Mensaje(texto, error = false))

    /** La pantalla avisa que ya mostró el mensaje (solo se borra si sigue siendo el mismo). */
    fun mensajeMostrado(mensaje: Mensaje) = _estado.update { if (it.mensaje === mensaje) it.copy(mensaje = null) else it }

    private fun mostrarMensaje(mensaje: Mensaje) = _estado.update { it.copy(mensaje = mensaje) }

    /**
     * Notificación cuando la dirección le asigna vacaciones o un asueto nuevos (aprobados o puestos directamente en el
     * calendario), para que se entere aunque no abra la pantalla de Permisos. La primera vez (sin datos anteriores) no avisa.
     */
    private fun avisarVacacionesNuevas(anterior: Perfil, nuevo: Perfil) {
        val antes = anterior.diasLibres ?: return
        nuevo.diasLibres.orEmpty()
            .filter { d -> antes.none { it.id == d.id } }
            .forEach { d ->
                val titulo = if (d.esVacaciones) "Tienes vacaciones" else "Tienes un asueto"
                val fechas = (if (d.desde == d.hasta) fechaLegible(d.desde) else
                    "${fechaLegible(d.desde)} al ${fechaLegible(d.hasta).lowercase()}") + " (${textoDiasLaborales(d.dias)})"
                Notificaciones.mostrar(
                    getApplication(), 900 + (d.id.hashCode() and 0x7fffffff) % 1000,
                    "🌴 $titulo", "$fechas. Míralo en Permisos.",
                )
                // También en la tarjeta "Novedades", por si no vio la notificación.
                agregarNovedad(Novedad("libre-${d.id}", "🌴", titulo, "$fechas." + if (d.detalle.isNotBlank()) " ${d.detalle}" else ""))
            }
    }

    /** Notificación al llegar a 3, 5, 7, 10, 15, 20, 30… llegadas puntuales seguidas. */
    private fun avisarLogroDeRacha(racha: Int) {
        val logro = racha in setOf(3, 5, 7, 10, 15, 20) || (racha >= 30 && racha % 10 == 0)
        if (!logro) return
        val mejor = sesion.perfil.destacados?.takeIf { it.racha > 0 }
        val texto = "Sigue así, cada día puntual suma." +
            if (mejor != null && racha >= mejor.racha) " ¡Ya alcanzas la mejor racha de la semana!"
            else if (mejor != null) " La mejor racha de la semana es de ${mejor.racha} (${mejor.rachaNombre})." else ""
        Notificaciones.mostrar(getApplication(), 100 + racha, "🔥 ¡Racha de $racha llegadas puntuales!", texto)
        agregarNovedad(
            Novedad("racha-$racha-${claveDia(System.currentTimeMillis())}", "🔥", "¡Racha de $racha llegadas puntuales!", texto),
        )
    }

    /** Felicitación o alerta según la hora de la marca. Se calcula en el teléfono, también sin conexión. */
    private fun resultadoDe(marca: Marca): Resultado {
        val perfil = sesion.perfil
        val todas = visibles()
        val minuto = minutoDelDia(marca.fechaHora)
        val hora = formatoHora(minuto)
        val nota = when {
            marca.estado == EstadoMarca.PENDIENTE ->
                "Sin conexión: quedó guardada en el teléfono y se enviará a la hoja automáticamente."
            marca.revision.isNotBlank() -> "Quedó por revisar: ${marca.revision}. La administración la revisará."
            else -> null
        }
        // El turno de la marca (una salida de madrugada pertenece al turno de ayer).
        val dia = agruparPorDia(todas, perfil).firstOrNull { j -> j.marcas.any { it.id == marca.id } }

        if (marca.tipo == TipoRegistro.ENTRADA.name) {
            if (marca.actividad) {
                return Resultado(
                    TipoResultado.ACTIVIDAD,
                    "Actividad especial registrada",
                    "Entrada a las $hora fuera del colegio. No cuenta como llegada tarde; la dirección revisará " +
                        "las horas de este día.",
                    nota,
                )
            }
            val esperada = perfil.horaEntrada(marca.fechaHora)
            val limite = perfil.limiteEntrada(marca.fechaHora)
            if (minuto <= limite) {
                val racha = calcularEstadisticas(todas, perfil, Periodo.SEMANA, marca.fechaHora).rachaActual
                avisarLogroDeRacha(racha)
                val antes = limite - minuto
                return Resultado(
                    TipoResultado.PUNTUAL,
                    "¡Felicidades, eres puntual!",
                    "Entrada a las $hora" +
                        (if (antes > 0) ", ${formatoDuracion(antes.toLong())} antes de tu hora ($esperada)." else ".") +
                        (if (racha >= 2) "\n🔥 Llevas $racha llegadas puntuales seguidas." else ""),
                    nota,
                )
            }
            return Resultado(
                TipoResultado.TARDE,
                "Llegada tarde",
                "Entrada a las $hora, ${formatoDuracion((minuto - limite).toLong())} después de tu hora ($esperada)." +
                    "\nMañana es una nueva oportunidad para empezar tu racha. 💪",
                nota,
            )
        }

        val trabajado = dia?.minutosTrabajados(marca.fechaHora) ?: 0
        if (dia?.actividad == true) {
            return Resultado(
                TipoResultado.ACTIVIDAD,
                "Jornada con actividad especial",
                "Salida a las $hora. Registraste ${formatoDuracion(trabajado)}. La dirección revisará estas horas.",
                nota,
            )
        }
        // Las horas de un permiso por horas cuentan para la meta.
        val metaDia = dia?.meta ?: perfil.metaDelDia(marca.fechaHora)
        val textoMeta = formatoDuracion(metaDia.toLong())
        val faltan = metaDia - (dia?.minutosPermiso ?: 0) - trabajado
        if (faltan <= 0) {
            val extra = dia?.minutosExtra(marca.fechaHora) ?: 0
            val semana = calcularEstadisticas(todas, perfil, Periodo.SEMANA, marca.fechaHora).minutosExtra
            return Resultado(
                TipoResultado.META_CUMPLIDA,
                "¡Cumpliste tu meta de $textoMeta!",
                "Salida a las $hora. Trabajaste ${formatoDuracion(trabajado)}. ¡Buen trabajo!" +
                    (if (extra > 0) "\n⏱ Horas extra hoy: ${formatoDuracion(extra)} (esta semana: ${formatoDuracion(semana)})." else ""),
                nota,
            )
        }
        return Resultado(
            TipoResultado.META_INCOMPLETA,
            "No completaste la meta de $textoMeta",
            "Salida a las $hora con ${formatoDuracion(trabajado)} trabajadas. Te faltaron ${formatoDuracion(faltan)}.",
            nota,
        )
    }

    /**
     * Revisa en el teléfono que no se repita la entrada o la salida del mismo turno. Una salida de madrugada
     * cierra la entrada de ayer (turno nocturno).
     */
    private fun validarSecuencia(tipo: TipoRegistro) {
        val ahora = System.currentTimeMillis()
        val turnos = agruparPorDia(visibles(), sesion.perfil)
        val deHoy = turnos.firstOrNull { it.dia == inicioDelDia(ahora) }
        when (tipo) {
            TipoRegistro.ENTRADA -> if (deHoy?.entrada != null) throw IllegalStateException("Ya registraste tu entrada hoy.")
            TipoRegistro.SALIDA -> {
                if (deHoy?.entrada != null && deHoy.salida != null) throw IllegalStateException("Ya registraste tu salida hoy.")
                if (turnos.none { it.enCurso(ahora) }) throw IllegalStateException("Primero registra tu entrada.")
            }
        }
    }

    /** Turno abierto (entrada sin salida, también el de ayer si es nocturno), o null. */
    fun turnoAbierto(): com.example.asistencia.datos.Jornada? {
        val ahora = System.currentTimeMillis()
        return agruparPorDia(visibles(), sesion.perfil).firstOrNull { it.enCurso(ahora) }
    }

    /**
     * Al marcar: envía solo las marcas pendientes (sin descargar el perfil antes), para mostrar el resultado rápido.
     * Si algo no cuadra (teléfono no reconocido o registro sin enviar), hace la sincronización completa.
     */
    private suspend fun enviarPendientes() {
        if (!sesion.enviadoAlServidor) return sincronizar()
        sincronizando.withLock {
            val app = getApplication<Application>()
            for (marca in marcas.todas().filter { it.estado == EstadoMarca.PENDIENTE }.reversed()) {
                val actualizada = try {
                    val r = SheetsApi.enviar("registrar", datosDelTelefono(app).put("marca", marca.aJson()))
                    marca.copy(estado = EstadoMarca.ENVIADA, detalle = "", revision = r.optString("revision"))
                } catch (e: IOException) {
                    return // sin conexión: queda pendiente
                } catch (e: ServidorException) {
                    if (e.noRegistrado || e.accesoCerrado) break // lo resuelve la sincronización completa
                    marca.copy(estado = EstadoMarca.RECHAZADA, detalle = e.message.orEmpty())
                }
                marcas.actualizar(actualizada)
                _estado.update { it.copy(marcas = visibles()) }
            }
        }
        if (marcas.todas().any { it.estado == EstadoMarca.PENDIENTE }) sincronizar()
    }

    /**
     * Envía el registro (si falta) y las marcas pendientes, en orden, y luego descarga el perfil.
     * Si no hay conexión, se detiene y lo deja pendiente para la próxima vez.
     */
    private suspend fun sincronizar() = sincronizando.withLock {
        if (sesion.correo == null) return@withLock
        val app = getApplication<Application>()
        val registrarEnServidor = suspend {
            SheetsApi.enviar("registrarDocente", datosDelTelefono(app).put("nombre", sesion.nombre))
            sesion.enviadoAlServidor = true
        }
        try {
            if (!sesion.enviadoAlServidor) registrarEnServidor()
            // Primero el perfil: si el servidor no reconoce este teléfono (por ejemplo, porque se cambió a una
            // hoja nueva), se vuelve a registrar solo, antes de enviar marcas que serían rechazadas.
            // En la misma respuesta vienen el historial de la hoja y el ranking: una sola llamada en vez de tres.
            val pedirPerfil = suspend {
                SheetsApi.enviar("perfil", datosDelTelefono(app).put("historial", true).put("ranking", true)
                    .apply { _estado.value.ranking?.mes?.let { put("mes", it) } })
            }
            val respuesta = try {
                pedirPerfil()
            } catch (e: ServidorException) {
                if (!e.noRegistrado) throw e
                registrarEnServidor()
                pedirPerfil()
            }
            val perfil = Perfil.deJson(respuesta)
            respuesta.optJSONObject("ranking")?.let { r ->
                runCatching { Ranking.deJson(r) }.getOrNull()?.let { rk -> _estado.update { it.copy(ranking = rk) } }
            }
            // El servidor respondió: el acceso está abierto.
            if (sesion.accesoCerrado != null) {
                sesion.accesoCerrado = null
                _estado.update { it.copy(accesoCerrado = null) }
            }
            if (perfil != sesion.perfil) {
                avisarVacacionesNuevas(sesion.perfil, perfil)
                sesion.perfil = perfil
                _estado.update { it.copy(perfil = perfil) }
            }
            Recordatorios.programarTodos(app)
            // Si el colegio usa QR, se pide a Google Play que deje listo el lector.
            if (perfil.sedes.any { it.metodo == MetodoVerificacion.QR.name }) CodigoQr.prepararLector(app)
            val pendientes = marcas.todas().filter { it.estado == EstadoMarca.PENDIENTE }
            // El historial que vino con el perfil todavía no tiene las pendientes: se guarda solo si no hay.
            respuesta.optJSONArray("dias")?.takeIf { pendientes.isEmpty() }?.let { dias ->
                sesion.historialHoja = dias.toString()
                _estado.update { it.copy(marcas = visibles()) }
            }
            pendientes.reversed().forEach { marca ->
                val actualizada = try {
                    val r = SheetsApi.enviar("registrar", datosDelTelefono(app).put("marca", marca.aJson()))
                    marca.copy(estado = EstadoMarca.ENVIADA, detalle = "", revision = r.optString("revision"))
                } catch (e: ServidorException) {
                    marca.copy(estado = EstadoMarca.RECHAZADA, detalle = e.message.orEmpty())
                }
                marcas.actualizar(actualizada)
                _estado.update { it.copy(marcas = visibles()) }
            }
            // Historial de la hoja: si la administración corrigió una hora a mano, la app muestra la corrección.
            if (perfil.rol.marca && (pendientes.isNotEmpty() || !respuesta.has("dias"))) {
                runCatching { SheetsApi.enviar("misMarcas", datosDelTelefono(app)) }.getOrNull()
                    ?.optJSONArray("dias")?.let { dias ->
                        sesion.historialHoja = dias.toString()
                        _estado.update { it.copy(marcas = visibles()) }
                    }
            }
            revisarResumenSemanal()
        } catch (e: IOException) {
            // Sin conexión o servidor sin configurar: queda pendiente.
            revisarResumenSemanal()
        } catch (e: ServidorException) {
            if (e.accesoCerrado) {
                // El administrador desactivó o eliminó a esta persona: se cierra el acceso a la app.
                sesion.accesoCerrado = e.message.orEmpty()
                _estado.update { it.copy(accesoCerrado = e.message.orEmpty(), bloqueado = false) }
                Recordatorios.cancelarTodos(app)
            } else {
                // El registro fue rechazado (por ejemplo, el correo ya está en otro teléfono).
                mostrarError(e.message.orEmpty())
            }
        }
    }

    private fun ejecutar(bloque: suspend () -> Unit) = viewModelScope.launch {
        if (_estado.value.ocupado) return@launch
        _estado.update { it.copy(ocupado = true, mensaje = null) }
        try {
            bloque()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            mostrarError(e.message ?: "Ocurrió un error inesperado.")
        } finally {
            _estado.update { it.copy(ocupado = false) }
        }
    }
}
