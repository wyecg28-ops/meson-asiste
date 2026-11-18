/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 */
/**
 * Mesón Asiste — parte 2 de 3: registro, inicio de sesión, perfil, marcas, calendario y solicitudes.
 * Usa las constantes y funciones de 1_Configuracion.gs y 3_Administracion.gs (Apps Script une los archivos).
 */

// ---------------------------------------------------------------- Registro y autenticación

/**
 * Primer registro: lo anota en "Docentes" y crea su hoja con su nombre.
 * Un correo queda atado al primer teléfono con que se registró. Para cambiarlo, el administrador libera el teléfono.
 */
function registrarDocente(d) {
  const correo = correoInstitucional(d.correo);
  const nombre = String(d.nombre || '').trim();
  if (nombre.length < 3) throw new Error('Falta el nombre completo.');
  if (!d.dispositivo) throw new Error('No se pudo identificar el teléfono.');

  const hoja = obtenerHoja(DOCENTES.nombre, DOCENTES.encabezados);
  const existente = buscarFila(hoja, DOC.CORREO, correo);
  if (existente) validarEstado(existente.valores);
  if (existente && existente.valores[DOC.DISPOSITIVO] && existente.valores[DOC.DISPOSITIVO] !== d.dispositivo) {
    throw new Error('El correo ' + correo + ' ya está registrado en otro teléfono (' + existente.valores[DOC.MODELO] +
      '). Pide al administrador que libere el teléfono.');
  }
  const nombreHoja = existente ? existente.valores[DOC.HOJA] : nombreHojaLibre(nombre, correo);
  obtenerHoja(nombreHoja, ENCABEZADOS_DOCENTE);

  const v = existente ? existente.valores : [];
  const fila = [correo, existente ? v[DOC.NOMBRE] : nombre, nombreHoja, d.dispositivo, d.modelo,
    existente ? v[DOC.METODO] : '',
    existente ? v[DOC.REGISTRADO] : new Date(),
    v[DOC.ROL] || (esAdministradorFijo(correo) ? 'ADMINISTRADOR'
      : (CONFIG.SUPERVISORES.map(c => c.toLowerCase()).indexOf(correo) >= 0 ? 'SUPERVISOR' : 'DOCENTE')),
    v[DOC.JORNADA] || jornadaPorDefecto(),
    v[DOC.ESTADO] || ESTADOS.ACTIVO];
  if (existente) {
    hoja.getRange(existente.numero, 1, 1, fila.length).setValues([fila]);
  } else {
    hoja.appendRow(fila);
  }
  return { ok: true, mensaje: 'Registro completado.' };
}

/**
 * Si se reinstaló la app (o se borraron sus datos), el teléfono ya no recuerda su registro.
 * Con el identificador del teléfono se devuelve a quién pertenece, para no repetir el registro.
 */
function recuperar(d) {
  if (!d.dispositivo) throw new Error('No se pudo identificar el teléfono.');
  const valores = obtenerHoja(DOCENTES.nombre, DOCENTES.encabezados).getDataRange().getValues();
  for (let i = 1; i < valores.length; i++) {
    if (valores[i][DOC.DISPOSITIVO] === d.dispositivo && estadoDe(valores[i]) !== 'ELIMINADO') {
      return { ok: true, encontrado: true, correo: valores[i][DOC.CORREO], nombre: valores[i][DOC.NOMBRE],
        metodo: valores[i][DOC.METODO] || '' };
    }
  }
  return { ok: true, encontrado: false };
}

/**
 * Iniciar sesión con una cuenta que ya existe (solo el correo). Si la cuenta no tiene teléfono, queda atada a este.
 * Devuelve { encontrado: false } si no existe, para que la app ofrezca "Crear cuenta nueva".
 */
function iniciarSesion(d) {
  const correo = correoInstitucional(d.correo);
  if (!d.dispositivo) throw new Error('No se pudo identificar el teléfono.');
  const hoja = obtenerHoja(DOCENTES.nombre, DOCENTES.encabezados);
  const fila = buscarFila(hoja, DOC.CORREO, correo);
  if (!fila || estadoDe(fila.valores) === 'ELIMINADO') return { ok: true, encontrado: false };
  validarEstado(fila.valores);
  const dispositivo = fila.valores[DOC.DISPOSITIVO];
  if (dispositivo && dispositivo !== d.dispositivo) {
    throw new Error('La cuenta ' + correo + ' está registrada en otro teléfono (' + fila.valores[DOC.MODELO] +
      '). Pide al administrador que libere el teléfono en Admin → Miembros.');
  }
  if (!dispositivo) {
    hoja.getRange(fila.numero, DOC.DISPOSITIVO + 1, 1, 2).setValues([[d.dispositivo, d.modelo || '']]);
  }
  const m = miembroDe(fila.valores);
  return { ok: true, encontrado: true, correo: m.correo, nombre: m.nombre, rol: m.rol, rolNombre: m.rolNombre };
}

/**
 * La persona libera su propio teléfono para iniciar sesión en otro, sin pedírselo al administrador.
 * Solo funciona desde el teléfono registrado (autenticar lo comprueba): nadie puede liberar el de otra persona.
 */
function liberarMiTelefono(u) {
  const hoja = obtenerHoja(DOCENTES.nombre, DOCENTES.encabezados);
  const fila = buscarFila(hoja, DOC.CORREO, u.correo);
  hoja.getRange(fila.numero, DOC.DISPOSITIVO + 1, 1, 2).setValues([['', '']]);
  return { ok: true, mensaje: 'Teléfono liberado. Ya puedes iniciar sesión con ' + u.correo + ' en tu otro teléfono.' };
}

/** Comprueba que el correo esté registrado con este teléfono y que su acceso esté activo. */
function autenticar(d) {
  const correo = String(d.correo || '').toLowerCase();
  const hoja = obtenerHoja(DOCENTES.nombre, DOCENTES.encabezados);
  const fila = buscarFila(hoja, DOC.CORREO, correo);
  if (fila) validarEstado(fila.valores);
  if (!fila || fila.valores[DOC.DISPOSITIVO] !== d.dispositivo) {
    throw errorCon('NO_REGISTRADO',
      'Este teléfono no está registrado con tu correo. Pide al administrador que revise la hoja "Docentes".');
  }
  const metodo = d.marca && d.marca.metodo;
  if (metodo && fila.valores[DOC.METODO] !== metodo) {
    hoja.getRange(fila.numero, DOC.METODO + 1).setValue(metodo);
  }
  return miembroDe(fila.valores);
}

/** Un miembro inactivo o eliminado no puede usar la app. */
function validarEstado(valores) {
  const estado = estadoDe(valores);
  if (estado === 'INACTIVO') throw errorCon('ACCESO_CERRADO', 'Tu acceso está desactivado. Habla con la dirección.');
  if (estado === 'ELIMINADO') throw errorCon('ACCESO_CERRADO', 'Tu acceso fue eliminado. Habla con la dirección.');
}

/** Comprueba que la persona tenga la función que pide la acción (según su rol en la hoja "Roles"). */
function autenticarGestor(d, accion) {
  const usuario = autenticar(d);
  const necesarios = PERMISO_DE_ACCION[accion];
  if (!necesarios) throw new Error('Acción no válida.');
  if (!necesarios.some(p => puede(usuario.rol, p))) throw new Error('Tu rol no tiene permiso para hacer esto.');
  return usuario;
}
function esAdministradorFijo(correo) {
  return CONFIG.ADMINISTRADORES.map(c => c.toLowerCase()).indexOf(String(correo).toLowerCase()) >= 0;
}

function estadoDe(v) {
  return claveDe(ESTADOS, v[DOC.ESTADO], x => x) || 'ACTIVO';
}

/** Convierte una fila de "Docentes" en un miembro con su rol, jornada y estado (acepta clave o nombre en la hoja). */
function miembroDe(v) {
  const correo = String(v[DOC.CORREO]).toLowerCase();
  const rol = esAdministradorFijo(correo) ? 'ADMINISTRADOR' : (claveDe(leerRoles(), v[DOC.ROL], r => r.nombre) || 'DOCENTE');
  const jornada = claveDe(leerJornadas(), v[DOC.JORNADA], j => j.nombre) || jornadaPorDefecto();
  return {
    correo: correo,
    nombre: v[DOC.NOMBRE],
    hoja: v[DOC.HOJA],
    rol: rol,
    rolNombre: rolJson(rol).nombre,
    jornada: jornada,
    estado: estadoDe(v),
    modelo: v[DOC.MODELO] || '',
    conTelefono: !!v[DOC.DISPOSITIVO],
    registrado: v[DOC.REGISTRADO] ? textoFecha(v[DOC.REGISTRADO]) : '',
  };
}

function claveDe(mapa, valor, nombre) {
  const buscado = String(valor || '').trim().toLowerCase();
  if (!buscado) return null;
  return Object.keys(mapa).filter(k => k.toLowerCase() === buscado ||
    String(nombre(mapa[k])).toLowerCase() === buscado)[0] || null;
}

/** Miembros que no fueron eliminados. */
function leerMiembros() {
  const valores = obtenerHoja(DOCENTES.nombre, DOCENTES.encabezados).getDataRange().getValues();
  return valores.slice(1).filter(v => v[DOC.CORREO]).map(miembroDe).filter(m => m.estado !== 'ELIMINADO');
}

// ---------------------------------------------------------------- Perfil (lo que la app descarga)

function perfil(u) {
  const desde = fechaDesplazada(-60);
  const jornadas = leerJornadas();
  const config = leerConfig();
  return {
    ok: true,
    rol: u.rol,
    jornada: jornadaJson(u.jornada),
    jornadas: Object.keys(jornadas).map(jornadaJson),
    tolerancia: config.tolerancia,
    horasMeta: config.horasMeta,
    sedes: leerSedes().map(sedeJson),
    eventos: leerEventos().filter(e => aplicaA(e, u.correo) && e.hasta >= desde),
    rolInfo: rolJson(u.rol),
    destacados: destacadosSemana(),
  };
}

// ---------------------------------------------------------------- Marcas

/**
 * Historial de la persona (últimos 60 días) ya evaluado: puntualidad, minutos, meta y horas extra de cada día.
 * Lo usa la versión web, que no guarda el historial en el teléfono.
 */
function misMarcas(u) {
  const hoja = libroAsistencia().getSheetByName(u.hoja);
  if (!hoja || hoja.getLastRow() < 2) return { ok: true, dias: [] };
  const eventos = leerEventos();
  const desde = fechaDesplazada(-60);
  const n = Math.min(80, hoja.getLastRow() - 1);
  const dias = hoja.getRange(hoja.getLastRow() - n + 1, 1, n, ENCABEZADOS_DOCENTE.length).getValues()
    .map(v => {
      const fecha = textoFecha(v[COL.FECHA - 1]);
      const entrada = v[COL.ENTRADA - 1] ? textoHora(v[COL.ENTRADA - 1]).substring(0, 5) : '';
      const salida = v[COL.SALIDA - 1] ? textoHora(v[COL.SALIDA - 1]).substring(0, 5) : '';
      const actividad = v[COL.ACTIVIDAD - 1] === 'Sí';
      let minutos = null;
      if (entrada && salida) {
        minutos = aMinutos(salida) - aMinutos(entrada);
        if (minutos < 0) minutos += 1440; // turno que pasa la medianoche
      }
      const meta = metaDelDia(u, fecha, eventos);
      const limite = limiteEntrada(u, fecha, eventos);
      return {
        fecha: fecha,
        entrada: entrada,
        salida: salida,
        minutos: minutos,
        meta: meta,
        extra: minutos === null ? 0 : Math.max(0, minutos - meta),
        actividad: actividad,
        puntual: actividad || !entrada ? null : aMinutos(entrada) <= limite,
        minutosTarde: actividad || !entrada ? 0 : Math.max(0, aMinutos(entrada) - limite),
        revision: String(v[COL.REVISION - 1] || ''),
        observacion: String(v[COL.OBSERVACION - 1] || ''),
      };
    })
    .filter(d => d.fecha >= desde && d.entrada)
    .reverse();
  return { ok: true, dias: dias };
}

function registrar(usuario, d) {
  // Si la app reenvía una marca que ya se guardó (se perdió la respuesta), se devuelve el mismo resultado.
  const cache = CacheService.getScriptCache();
  const claveCache = 'marca:' + d.id;
  const previa = d.id && cache.get(claveCache);
  if (previa) return JSON.parse(previa);

  const ahora = new Date();
  const tomada = isFinite(Number(d.fechaHora)) ? new Date(Number(d.fechaHora)) : ahora;
  const fecha = Utilities.formatDate(tomada, CONFIG.ZONA_HORARIA, 'yyyy-MM-dd');
  const hora = Utilities.formatDate(tomada, CONFIG.ZONA_HORARIA, 'HH:mm:ss');
  const minutosDeRetraso = (ahora - tomada) / 60000;
  const lat = Number(d.latitud);
  const lon = Number(d.longitud);
  const tieneUbicacion = isFinite(lat) && isFinite(lon);
  const sedes = leerSedes();
  // La sede más cercana decide la geocerca y la prueba de presencia (QR o Wi-Fi).
  let sede = null;
  let distancia = '';
  if (tieneUbicacion && sedes.length) {
    sedes.forEach(s => {
      const m = Math.round(distanciaMetros(lat, lon, s.latitud, s.longitud));
      if (distancia === '' || m < distancia) { distancia = m; sede = s; }
    });
  }
  const mapa = tieneUbicacion ? 'https://maps.google.com/?q=' + lat + ',' + lon : '';
  const actividad = d.actividad === true;
  const observacion = String(d.observacion || '').trim();
  const jornada = jornadaJson(usuario.jornada);
  const eventos = leerEventos();
  let motivoRevision = '';

  let hoja, filaHoy, actividadDelDia;
  let fechaFila = fecha; // día de la entrada (para un turno nocturno, la salida de madrugada es del día anterior)
  try {
    if (!ETIQUETAS[d.tipo]) throw new Error('Tipo de registro no válido.');
    if (!marcaAsistencia(usuario.rol)) throw new Error('Tu rol no marca asistencia.');
    if (minutosDeRetraso < -10) throw new Error('La hora del teléfono está adelantada. Corrígela en Ajustes.');
    if (minutosDeRetraso > CONFIG.DIAS_MAXIMOS_PENDIENTE * 1440) {
      throw new Error('La marca se guardó hace más de ' + CONFIG.DIAS_MAXIMOS_PENDIENTE + ' días y ya no se acepta.');
    }
    if (actividad && !observacion) throw new Error('Describe la actividad especial en la observación.');
    hoja = obtenerHoja(usuario.hoja, ENCABEZADOS_DOCENTE);
    filaHoy = buscarFilaDelDia(hoja, fecha);
    if (d.tipo === 'SALIDA' && !(filaHoy && hoja.getRange(filaHoy, COL.SALIDA).getValue() === '')) {
      const ayer = Utilities.formatDate(new Date(tomada.getTime() - 86400000), CONFIG.ZONA_HORARIA, 'yyyy-MM-dd');
      const filaAyer = buscarFilaDelDia(hoja, ayer);
      if (filaAyer && hoja.getRange(filaAyer, COL.SALIDA).getValue() === '') {
        const entradaAyer = aMinutos(textoHora(hoja.getRange(filaAyer, COL.ENTRADA).getValue()));
        if (aMinutos(hora) + 1440 - entradaAyer <= 20 * 60) {
          filaHoy = filaAyer;
          fechaFila = ayer;
        }
      }
    }
    actividadDelDia = !!filaHoy && hoja.getRange(filaHoy, COL.ACTIVIDAD).getValue() === 'Sí';
    const fuera = actividad || actividadDelDia;
    // En una actividad especial se puede marcar fuera del colegio; la ubicación igual queda anotada.
    validarUbicacion(tieneUbicacion, d.precision, sede, distancia, fuera);
    if (d.tipo === 'ENTRADA' && filaHoy) throw new Error('Ya registraste tu entrada hoy.');
    if (d.tipo === 'SALIDA' && !filaHoy) throw new Error('No tienes una entrada registrada hoy.');
    if (d.tipo === 'SALIDA' && hoja.getRange(filaHoy, COL.SALIDA).getValue() !== '') {
      throw new Error('Ya registraste tu salida hoy.');
    }
    if (sede && !fuera) motivoRevision = verificarPresencia(sede, d, tomada, ahora, minutosDeRetraso);
  } catch (err) {
    obtenerHoja(RECHAZADOS.nombre, RECHAZADOS.encabezados)
      .appendRow([fecha, hora, usuario.correo, ETIQUETAS[d.tipo] || d.tipo, err.message, mapa, distancia]);
    throw err;
  }

  const notas = [];
  if (actividad) notas.push('Actividad especial (' + ETIQUETAS[d.tipo].toLowerCase() + '): ' + observacion);
  else if (observacion) notas.push(ETIQUETAS[d.tipo] + ': ' + observacion);
  if (motivoRevision) notas.push(ETIQUETAS[d.tipo] + ' por revisar: ' + motivoRevision);
  if (minutosDeRetraso > 10) notas.push(ETIQUETAS[d.tipo] + ' enviada sin conexión, llegó a las ' +
    Utilities.formatDate(ahora, CONFIG.ZONA_HORARIA, 'dd/MM HH:mm'));
  const permisos = permisosPorHoras(usuario, fechaFila, eventos);

  if (d.tipo === 'ENTRADA') {
    const limite = limiteEntrada(usuario, fecha, eventos);
    if (!actividad && aMinutos(hora) > limite) {
      notas.unshift('Retraso: entrada a las ' + hora.substring(0, 5) + ' (' + (aMinutos(hora) - limite) + ' min)');
    }
    permisos.forEach(p => notas.push('Permiso de ' + p.horaInicio + ' a ' + p.horaFin +
      (p.detalle ? ' (' + p.detalle + ')' : '')));
    hoja.appendRow([fecha, "'" + hora, usuario.correo, '', notas.join(' | '), mapa, '', jornada.nombre, '', '',
      actividad ? 'Sí' : '', (actividad || motivoRevision) ? POR_REVISAR : '']);
  } else {
    let minutos = aMinutos(hora) - aMinutos(textoHora(hoja.getRange(filaHoy, COL.ENTRADA).getValue()));
    if (minutos < 0) minutos += 1440; // salida después de la medianoche
    const minutosPermiso = permisos.reduce((total, p) => total + Math.max(0, aMinutos(p.horaFin) - aMinutos(p.horaInicio)), 0);
    const meta = metaDelDia(usuario, fechaFila, eventos);
    const esActividad = actividad || actividadDelDia;
    const cumplio = minutos + minutosPermiso >= meta;
    if (!esActividad && !cumplio) {
      notas.push('Salida con ' + textoDuracion(minutos) + ' en el colegio' +
        (minutosPermiso ? ' + ' + textoDuracion(minutosPermiso) + ' de permiso' : '') +
        ' (meta ' + textoDuracion(meta) + ')');
    }
    const anterior = hoja.getRange(filaHoy, COL.OBSERVACION).getValue();
    hoja.getRange(filaHoy, COL.SALIDA).setValue("'" + hora);
    hoja.getRange(filaHoy, COL.UBIC_SALIDA).setValue(mapa);
    hoja.getRange(filaHoy, COL.HORAS).setValue("'" + textoDuracion(minutos));
    hoja.getRange(filaHoy, COL.CUMPLIO).setValue(cumplio ? 'Sí' : 'No');
    if (actividad) hoja.getRange(filaHoy, COL.ACTIVIDAD).setValue('Sí');
    if ((actividad || motivoRevision) && !hoja.getRange(filaHoy, COL.REVISION).getValue()) {
      hoja.getRange(filaHoy, COL.REVISION).setValue(POR_REVISAR);
    }
    if (notas.length) hoja.getRange(filaHoy, COL.OBSERVACION).setValue([anterior].concat(notas).filter(String).join(' | '));
    actividadDelDia = esActividad;
  }
  if (actividad || actividadDelDia) anotarActividad(usuario, fechaFila, d.tipo, hora, actividad ? observacion : '', mapa);
  if (motivoRevision) anotarActividad(usuario, fechaFila, d.tipo, hora, 'Por revisar: ' + motivoRevision, mapa);

  const respuesta = {
    ok: true,
    hora: hora,
    revision: motivoRevision,
    mensaje: ETIQUETAS[d.tipo] + ' registrada a las ' + hora + '. ¡Gracias, ' + usuario.nombre + '!',
  };
  if (d.id) cache.put(claveCache, JSON.stringify(respuesta), 21600);
  return respuesta;
}

/**
 * Prueba de presencia de la sede: QR (obligatorio) o Wi-Fi.
 * Devuelve '' si todo está bien, o el motivo por el que la marca queda "Por revisar".
 * Lanza un error (marca rechazada) si el QR falta o es falso en una marca reciente.
 */
function verificarPresencia(sede, d, tomada, ahora, minutosDeRetraso) {
  if (sede.metodo === 'QR' && sede.secreto) {
    const codigo = String(d.codigoQr || '').trim();
    if (!codigo) throw new Error('Para marcar, escanea el código QR de la entrada.');
    if (qrValido(sede.secreto, codigo, [tomada, ahora])) return '';
    // Marca enviada tarde (sin internet): no se puede saber si el QR era de ese momento.
    if (minutosDeRetraso > 2) return 'el código QR no se pudo comprobar';
    throw new Error('El código QR no es válido o ya caducó. Escanea el que se muestra ahora en la entrada.');
  }
  if (sede.metodo === 'WIFI' && sede.wifi.length) {
    const vistos = (d.wifi || []).map(x => String(x).toLowerCase());
    if (!vistos.some(b => sede.wifi.indexOf(b) >= 0)) return 'el teléfono no detectó el Wi-Fi del colegio';
  }
  return '';
}

/** Entrada y salida especial de un miembro en una fecha (o undefined). */
function horarioEspecial(miembro, fecha, eventos) {
  return (eventos || leerEventos()).filter(e => e.tipo === 'ENTRADA_ESPECIAL' && e.horaEntrada &&
    aplicaA(e, miembro.correo) && e.desde <= fecha && fecha <= e.hasta)[0];
}

/** Minutos de la meta de ese día: la de Configuración, o lo que dure el horario de ese día (o el especial) si es menos. */
function metaDelDia(miembro, fecha, eventos) {
  const meta = leerConfig().horasMeta * 60;
  const especial = horarioEspecial(miembro, fecha, eventos);
  if (especial && especial.horaSalida) return Math.min(meta, duracionHorario(especial.horaEntrada, especial.horaSalida));
  const horario = horarioDelDia(jornadaJson(miembro.jornada), diaIso(fecha));
  return Math.min(meta, duracionHorario(horario.entrada, horario.salida));
}

/** Permisos por horas de un miembro en una fecha. */
function permisosPorHoras(miembro, fecha, eventos) {
  return (eventos || leerEventos()).filter(e => e.tipo === 'PERMISO' && e.horaInicio && e.horaFin &&
    aplicaA(e, miembro.correo) && e.desde <= fecha && fecha <= e.hasta);
}

/** Vacaciones, feriado o permiso de día completo de un miembro en una fecha (o undefined). */
function diaLibre(miembro, fecha, eventos) {
  return (eventos || leerEventos()).filter(e => e.tipo !== 'ENTRADA_ESPECIAL' && !(e.tipo === 'PERMISO' && e.horaInicio) &&
    aplicaA(e, miembro.correo) && e.desde <= fecha && fecha <= e.hasta)[0];
}

/**
 * Hora límite de entrada (en minutos) de un miembro un día: la de su jornada, la de una entrada especial,
 * o el final de un permiso por horas que cubra la hora de entrada.
 */
function limiteEntrada(miembro, fecha, eventos) {
  const lista = eventos || leerEventos();
  const especial = horarioEspecial(miembro, fecha, lista);
  let base = aMinutos(especial ? especial.horaEntrada : horarioDelDia(jornadaJson(miembro.jornada), diaIso(fecha)).entrada);
  permisosPorHoras(miembro, fecha, lista).forEach(p => {
    if (aMinutos(p.horaInicio) <= base && aMinutos(p.horaFin) > base) base = aMinutos(p.horaFin);
  });
  return base + leerConfig().tolerancia;
}

/** Una fila por persona y día en "Actividades especiales", para que la administración la revise. */
function anotarActividad(usuario, fecha, tipo, hora, descripcion, mapa) {
  const hoja = obtenerHoja(ACTIVIDADES.nombre, ACTIVIDADES.encabezados);
  const valores = hoja.getDataRange().getValues();
  let numero = null;
  for (let i = valores.length - 1; i >= 1; i--) {
    if (textoFecha(valores[i][ACT.FECHA]) === fecha && valores[i][ACT.CORREO] === usuario.correo) {
      numero = i + 1;
      break;
    }
  }
  const entrada = tipo === 'ENTRADA';
  if (!numero) {
    hoja.appendRow([Utilities.getUuid(), "'" + fecha, usuario.correo, usuario.nombre,
      entrada ? "'" + hora : '', entrada ? '' : "'" + hora, descripcion,
      entrada ? mapa : '', entrada ? '' : mapa, POR_REVISAR, '', '']);
    return;
  }
  hoja.getRange(numero, (entrada ? ACT.ENTRADA : ACT.SALIDA) + 1).setValue("'" + hora);
  hoja.getRange(numero, (entrada ? ACT.UBIC_E : ACT.UBIC_S) + 1).setValue(mapa);
  // Si ya estaba revisada y aparece algo nuevo por revisar, vuelve a la bandeja.
  if (descripcion) hoja.getRange(numero, ACT.ESTADO + 1).setValue(POR_REVISAR);
  const previa = String(valores[numero - 1][ACT.DESCRIPCION] || '');
  if (descripcion && previa.indexOf(descripcion) < 0) {
    hoja.getRange(numero, ACT.DESCRIPCION + 1).setValue([previa, descripcion].filter(String).join(' | '));
  }
}

function validarUbicacion(tieneUbicacion, precisionRecibida, sede, distancia, fuera) {
  if (!tieneUbicacion) throw new Error('No se recibió la ubicación.');
  if (fuera) return;
  const precision = Math.round(Number(precisionRecibida));
  if (precision > CONFIG.PRECISION_MAXIMA) {
    throw new Error('La señal GPS es imprecisa (±' + precision + ' m). Intenta de nuevo en un lugar abierto.');
  }
  if (sede && distancia > sede.radio) {
    throw new Error('Estás a ' + distancia + ' m de ' + sede.nombre + '. Debes marcar dentro del establecimiento ' +
      '(o usar "Actividad especial" si estás en una).');
  }
}

// ---------------------------------------------------------------- Calendario

function leerEventos() {
  const valores = obtenerHoja(CALENDARIO.nombre, CALENDARIO.encabezados).getDataRange().getValues();
  return valores.slice(1).filter(v => v[CAL.ID]).map(v => ({
    id: String(v[CAL.ID]),
    desde: textoFecha(v[CAL.DESDE]),
    hasta: textoFecha(v[CAL.HASTA] || v[CAL.DESDE]),
    // "Entrada especial" (nombre anterior) también se reconoce.
    tipo: claveDe(TIPOS_EVENTO, v[CAL.TIPO], x => x) ||
      (/especial/i.test(String(v[CAL.TIPO])) ? 'ENTRADA_ESPECIAL' : 'PERMISO'),
    horaSalida: v[CAL.H_SALIDA] ? textoHora(v[CAL.H_SALIDA]).substring(0, 5) : '',
    aplicaA: String(v[CAL.APLICA] || 'Todos'),
    horaEntrada: v[CAL.HORA] ? textoHora(v[CAL.HORA]).substring(0, 5) : '',
    detalle: String(v[CAL.DETALLE] || ''),
    horaInicio: v[CAL.H_INICIO] ? textoHora(v[CAL.H_INICIO]).substring(0, 5) : '',
    horaFin: v[CAL.H_FIN] ? textoHora(v[CAL.H_FIN]).substring(0, 5) : '',
  }));
}

/** "Aplica a" puede ser "Todos" o uno o varios correos separados por comas. */
function aplicaA(evento, correo) {
  const lista = evento.aplicaA.split(',').map(c => c.trim().toLowerCase());
  return lista.indexOf('todos') >= 0 || lista.indexOf(correo) >= 0;
}

// ---------------------------------------------------------------- Solicitudes de permiso (personal)

function solicitudDe(v) {
  return {
    id: String(v[SOL.ID]),
    creada: v[SOL.CREADA] ? textoFecha(v[SOL.CREADA]) : '',
    correo: String(v[SOL.CORREO]),
    nombre: String(v[SOL.NOMBRE]),
    tipo: String(v[SOL.TIPO]) === 'VACACIONES' ? 'VACACIONES' : 'PERMISO',
    desde: textoFecha(v[SOL.DESDE]),
    hasta: textoFecha(v[SOL.HASTA] || v[SOL.DESDE]),
    horaInicio: v[SOL.H_INICIO] ? textoHora(v[SOL.H_INICIO]).substring(0, 5) : '',
    horaFin: v[SOL.H_FIN] ? textoHora(v[SOL.H_FIN]).substring(0, 5) : '',
    motivo: String(v[SOL.MOTIVO] || ''),
    estado: claveDe(ESTADOS_SOLICITUD, v[SOL.ESTADO], x => x) || 'PENDIENTE',
    respuesta: String(v[SOL.RESPUESTA] || ''),
  };
}

function leerSolicitudes() {
  const valores = obtenerHoja(SOLICITUDES.nombre, SOLICITUDES.encabezados).getDataRange().getValues();
  return valores.slice(1).filter(v => v[SOL.ID]).map(solicitudDe).reverse();
}

function misSolicitudes(u) {
  return { ok: true, solicitudes: leerSolicitudes().filter(s => s.correo === u.correo).slice(0, 50) };
}

function crearSolicitud(u, s) {
  if (!marcaAsistencia(u.rol)) throw new Error('Tu rol no marca asistencia, así que no necesita pedir permisos.');
  const fechaValida = /^\d{4}-\d{2}-\d{2}$/;
  const horaValida = /^\d{2}:\d{2}$/;
  const tipo = s.tipo === 'VACACIONES' ? 'VACACIONES' : 'PERMISO';
  const motivo = String(s.motivo || '').trim();
  const desde = String(s.desde || '');
  const inicio = tipo === 'PERMISO' ? String(s.horaInicio || '').trim() : '';
  const fin = tipo === 'PERMISO' ? String(s.horaFin || '').trim() : '';
  const hasta = inicio ? desde : String(s.hasta || desde);
  if (motivo.length < 3) throw new Error('Escribe el motivo.');
  if (!fechaValida.test(desde) || !fechaValida.test(hasta)) throw new Error('Fechas no válidas.');
  if (hasta < desde) throw new Error('La fecha final es anterior a la inicial.');
  if (desde < fechaDesplazada(-30)) throw new Error('No se pueden pedir permisos de hace más de 30 días.');
  if (inicio || fin) {
    if (!horaValida.test(inicio) || !horaValida.test(fin)) throw new Error('Elige la hora de inicio y de fin.');
    if (aMinutos(fin) <= aMinutos(inicio)) throw new Error('La hora final debe ser después de la inicial.');
  }
  obtenerHoja(SOLICITUDES.nombre, SOLICITUDES.encabezados).appendRow([Utilities.getUuid(), new Date(), u.correo,
    u.nombre, tipo, "'" + desde, "'" + hasta, inicio ? "'" + inicio : '', fin ? "'" + fin : '', motivo,
    ESTADOS_SOLICITUD.PENDIENTE, '', '', '', '']);
  return { ok: true, mensaje: 'Solicitud enviada. Aquí verás cuando la revisen.' };
}

function cancelarSolicitud(u, id) {
  const hoja = obtenerHoja(SOLICITUDES.nombre, SOLICITUDES.encabezados);
  const fila = buscarFila(hoja, SOL.ID, String(id || '').toLowerCase());
  if (!fila || String(fila.valores[SOL.CORREO]).toLowerCase() !== u.correo) throw new Error('La solicitud ya no existe.');
  if (solicitudDe(fila.valores).estado !== 'PENDIENTE') throw new Error('Solo se pueden cancelar las solicitudes pendientes.');
  hoja.getRange(fila.numero, SOL.ESTADO + 1).setValue(ESTADOS_SOLICITUD.CANCELADA);
  return { ok: true, mensaje: 'Solicitud cancelada.' };
}

// ---------------------------------------------------------------- Destacados de la semana

/**
 * Mejor racha de llegadas puntuales y quien suma más horas en la semana (de lunes a hoy), entre el personal
 * que marca. Se guarda 30 minutos en caché para no leer todas las hojas en cada petición.
 */
function destacadosSemana() {
  const cache = CacheService.getScriptCache();
  const guardado = cache.get('destacados');
  if (guardado) return JSON.parse(guardado);
  const hoy = fechaDesplazada(0);
  const lunes = fechaDesplazada(-(diaIso(hoy) - 1));
  const eventos = leerEventos();
  const libro = libroAsistencia();
  let mejorRacha = null;
  let masHoras = null;
  leerMiembros().filter(m => m.estado === 'ACTIVO' && marcaAsistencia(m.rol)).forEach(m => {
    const hoja = libro.getSheetByName(m.hoja);
    if (!hoja || hoja.getLastRow() < 2) return;
    const n = Math.min(90, hoja.getLastRow() - 1);
    const filas = hoja.getRange(hoja.getLastRow() - n + 1, 1, n, ENCABEZADOS_DOCENTE.length).getValues();
    let racha = 0;
    for (let i = filas.length - 1; i >= 0; i--) {
      const v = filas[i];
      if (v[COL.ACTIVIDAD - 1] === 'Sí') continue;
      const entrada = textoHora(v[COL.ENTRADA - 1]);
      if (!entrada) continue;
      if (aMinutos(entrada) <= limiteEntrada(m, textoFecha(v[COL.FECHA - 1]), eventos)) racha++;
      else break;
    }
    let minutos = 0;
    filas.forEach(v => {
      const f = textoFecha(v[COL.FECHA - 1]);
      if (f >= lunes && f <= hoy && v[COL.HORAS - 1]) minutos += aMinutos(String(v[COL.HORAS - 1]));
    });
    if (racha > 0 && (!mejorRacha || racha > mejorRacha.valor)) mejorRacha = { nombre: m.nombre, valor: racha };
    if (minutos > 0 && (!masHoras || minutos > masHoras.minutos)) masHoras = { nombre: m.nombre, minutos: minutos };
  });
  const resultado = { semana: lunes, mejorRacha: mejorRacha, masHoras: masHoras };
  cache.put('destacados', JSON.stringify(resultado), 1800);
  return resultado;
}
