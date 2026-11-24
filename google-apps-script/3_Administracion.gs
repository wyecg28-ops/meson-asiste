/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 */
/**
 * Mesón Asiste — parte 3 de 3: panel de administración, reporte mensual, ajustes y utilidades.
 */

// ---------------------------------------------------------------- Administración

function administrar(admin, d) {
  // Solo un super administrador ve los registros de los super administradores.
  _verSuperAdmin = esSuperAdmin(admin.rol);
  switch (d.accion) {
    case 'adminHoy': return adminHoy();
    case 'adminMiembros': return {
      ok: true,
      miembros: leerMiembros(),
      roles: rolesVisibles(admin),
      jornadas: Object.keys(leerJornadas()).map(jornadaJson),
    };
    case 'adminAsignar': return adminAsignar(admin, d);
    case 'adminEstado': return adminEstado(admin, d);
    case 'adminEliminarMiembro': return adminEliminarMiembro(admin, d);
    case 'adminLiberar': return adminLiberar(admin, d);
    case 'adminJornadas': return { ok: true, jornadas: Object.keys(leerJornadas()).map(jornadaJson), enUso: jornadasEnUso() };
    case 'adminGuardarJornada': return adminGuardarJornada(d.jornada || {});
    case 'adminEliminarJornada': return adminEliminarJornada(d.codigo);
    case 'adminEventos': return { ok: true, eventos: leerEventos().filter(e => e.hasta >= fechaDesplazada(-30)) };
    case 'adminAgregarEvento': return adminAgregarEvento(admin, d.evento || {});
    case 'adminEliminarEvento': return adminEliminarEvento(d.id);
    case 'adminVistaVacaciones': return adminVistaVacaciones(d);
    case 'adminActividades': return adminActividades();
    case 'adminRevisarActividad': return adminRevisarActividad(admin, d);
    case 'adminSolicitudes': {
      const ocultos = correosOcultos();
      // Con el rol de cada persona (sale en el PDF de la solicitud).
      const roles = {};
      const miembros = {};
      leerMiembros().forEach(m => { roles[m.correo] = m.rolNombre; miembros[m.correo] = m; });
      const eventos = leerEventos();
      return { ok: true, solicitudes: leerSolicitudes().filter(s => s.estado !== 'CANCELADA' && !ocultos[s.correo]).slice(0, 80)
        .map(s => {
          const correo = String(s.correo).toLowerCase();
          // Vacaciones pendientes: cuántos días laborales son y si ya tiene vacaciones en esas fechas.
          const vista = s.tipo === 'VACACIONES' && s.estado === 'PENDIENTE' && miembros[correo]
            ? vistaVacaciones(miembros[correo], s.desde, s.hasta, eventos) : undefined;
          return Object.assign(s, { rol: roles[correo] || '', vista: vista });
        }) };
    }
    case 'adminRevisarSolicitud': return adminRevisarSolicitud(admin, d);
    case 'adminReporte': return adminReporte(d);
    case 'adminResumenAnual': return adminResumenAnual(d);
    case 'adminActualizarReportes': return actualizarReportes();
    case 'adminConfig': return adminConfig();
    case 'adminGuardarReglas': return adminGuardarReglas(d);
    case 'adminAgregarSede': return adminAgregarSede(d.sede || {});
    case 'adminEditarSede': return adminEditarSede(d.sede || {});
    case 'adminEliminarSede': return adminEliminarSede(d.id);
    case 'adminGuardarVerificacion': return adminGuardarVerificacion(d);
    case 'adminSecretoQr': return adminSecretoQr(d.id);
    case 'adminRoles': return adminRoles(admin);
    case 'adminGuardarRol': return adminGuardarRol(admin, d.rol || {});
    case 'adminEliminarRol': return adminEliminarRol(admin, d.codigo);
    default: throw new Error('Acción no válida.');
  }
}

/** Correos de los super administradores, que se ocultan a quien no es super administrador ({correo: true}). */
function correosOcultos() {
  if (_verSuperAdmin) return {};
  const ocultos = {};
  const valores = obtenerHoja(DOCENTES.nombre, DOCENTES.encabezados).getDataRange().getValues();
  valores.slice(1).filter(v => v[DOC.CORREO]).map(miembroDe).forEach(m => { if (esSuperAdmin(m.rol)) ocultos[m.correo] = true; });
  return ocultos;
}

/** Roles que se pueden ver y asignar. El de super administrador es interno: no aparece nunca. */
function rolesVisibles(admin) {
  return Object.keys(leerRoles()).filter(c => c !== 'SUPERADMIN').map(rolJson);
}

/** Solo el super administrador puede tocar a otro super administrador o a quien tiene un rol fijo. */
function protegerMiembro(admin, m) {
  const objetivo = miembroDe(m.fila.valores);
  if (esSuperAdmin(objetivo.rol) && !esSuperAdmin(admin.rol)) throw new Error('No se encontró a ' + m.correo + '.');
  if (objetivo.rol === 'ADMINISTRADOR' && !esSuperAdmin(admin.rol)) {
    throw new Error('Solo el super administrador puede cambiar a un administrador.');
  }
  return objetivo;
}

/** Quién ha marcado hoy, quién llegó tarde, quién falta y quién tiene el día libre (solo roles que marcan y activos). */
function adminHoy() {
  const fecha = fechaDesplazada(0);
  const eventos = leerEventos();
  const libro = libroAsistencia();
  const ayer = fechaDesplazada(-1);
  const filas = leerMiembros().filter(m => marcaAsistencia(m.rol) && m.estado === 'ACTIVO').map(m => {
    const libre = diaLibre(m, fecha, eventos);
    const permiso = permisosPorHoras(m, fecha, eventos)[0];
    const laboral = esDiaLaboral(m, fecha);
    let entrada = '', salida = '', horas = '', actividad = false, revision = '';
    const hoja = libro.getSheetByName(m.hoja);
    let fila = hoja && buscarFilaDelDia(hoja, fecha);
    // Turno nocturno: entró ayer y todavía no marca la salida.
    if (!fila && hoja) {
      const filaAyer = buscarFilaDelDia(hoja, ayer);
      if (filaAyer && hoja.getRange(filaAyer, COL.SALIDA).getValue() === '') fila = filaAyer;
    }
    if (fila) {
      const v = hoja.getRange(fila, 1, 1, ENCABEZADOS_DOCENTE.length).getValues()[0];
      entrada = textoHora(v[COL.ENTRADA - 1]).substring(0, 5);
      salida = v[COL.SALIDA - 1] ? textoHora(v[COL.SALIDA - 1]).substring(0, 5) : '';
      // Se calcula al momento (sin permiso ni almuerzo), no con lo que quedó guardado al marcar la salida.
      const minutos = minutosDeFila(m, v, eventos);
      horas = minutos === null ? '' : textoDuracion(minutos);
      actividad = v[COL.ACTIVIDAD - 1] === 'Sí';
      revision = String(v[COL.REVISION - 1] || '');
    }
    let estado = 'SIN_MARCAR';
    if (actividad) estado = 'ACTIVIDAD';
    else if (entrada) estado = aMinutos(entrada) > limiteEntrada(m, fecha, eventos) ? 'TARDE' : 'PUNTUAL';
    else if (libre || !laboral) estado = 'LIBRE';
    let detalle = libre ? TIPOS_EVENTO[libre.tipo] + (libre.detalle ? ': ' + libre.detalle : '') : '';
    if (!laboral && !libre) detalle = 'No es día laboral de su jornada';
    if (permiso) detalle = 'Permiso de ' + permiso.horaInicio + ' a ' + permiso.horaFin;
    if (revision === POR_REVISAR && !actividad) detalle = (detalle ? detalle + ' · ' : '') + 'Marca por revisar';
    return {
      correo: m.correo, nombre: m.nombre, rol: m.rolNombre, jornada: jornadaJson(m.jornada).nombre,
      entrada: entrada, salida: salida, horas: horas, estado: estado, detalle: detalle,
    };
  });
  return { ok: true, fecha: fecha, miembros: filas };
}

function filaMiembro(correo) {
  const hoja = obtenerHoja(DOCENTES.nombre, DOCENTES.encabezados);
  const objetivo = String(correo || '').toLowerCase();
  const fila = buscarFila(hoja, DOC.CORREO, objetivo);
  if (!fila || estadoDe(fila.valores) === 'ELIMINADO') throw new Error('No se encontró a ' + objetivo + '.');
  return { hoja: hoja, fila: fila, correo: objetivo };
}

function adminAsignar(admin, d) {
  const m = filaMiembro(d.objetivo);
  const actual = protegerMiembro(admin, m).rol;
  if (d.rol && d.rol !== actual) {
    const roles = leerRoles();
    if (!roles[d.rol] || esSuperAdmin(d.rol)) throw new Error('Rol no válido.');
    if (ROLES_PROTEGIDOS.indexOf(d.rol) >= 0 && !esSuperAdmin(admin.rol)) {
      throw new Error('Solo el super administrador puede asignar el rol "' + roles[d.rol].nombre + '".');
    }
    if ((esGestor(d.rol) || esGestor(actual)) && !puede(admin.rol, 'ROLES')) {
      throw new Error('Tu rol no puede dar o quitar roles con funciones de administración.');
    }
    if (m.correo === admin.correo) throw new Error('No puedes cambiar tu propio rol.');
    if (esRolFijo(m.correo)) throw new Error(m.correo + ' tiene un rol fijo (definido en CONFIG del script).');
    m.hoja.getRange(m.fila.numero, DOC.ROL + 1).setValue(d.rol);
  }
  if (d.jornada) {
    if (!leerJornadas()[d.jornada]) throw new Error('Jornada no válida.');
    m.hoja.getRange(m.fila.numero, DOC.JORNADA + 1).setValue(d.jornada);
  }
  return { ok: true, mensaje: 'Cambios guardados para ' + m.fila.valores[DOC.NOMBRE] + '.' };
}

/** Activa o desactiva a un miembro. Desactivado = no puede usar la app. */
function adminEstado(admin, d) {
  const m = filaMiembro(d.objetivo);
  protegerMiembro(admin, m);
  const estado = d.estado === 'INACTIVO' ? 'INACTIVO' : 'ACTIVO';
  if (estado === 'INACTIVO' && m.correo === admin.correo) throw new Error('No puedes desactivarte a ti mismo.');
  if (estado === 'INACTIVO' && esRolFijo(m.correo)) throw new Error('Esta cuenta tiene un rol fijo y no se puede desactivar.');
  m.hoja.getRange(m.fila.numero, DOC.ESTADO + 1).setValue(ESTADOS[estado]);
  return { ok: true, mensaje: m.fila.valores[DOC.NOMBRE] + (estado === 'ACTIVO' ? ' está activo.' : ' quedó desactivado.') };
}

/** Elimina a un miembro: se le cierra el acceso y deja de aparecer. Su hoja de asistencia se conserva. */
function adminEliminarMiembro(admin, d) {
  const m = filaMiembro(d.objetivo);
  protegerMiembro(admin, m);
  if (m.correo === admin.correo) throw new Error('No puedes eliminarte a ti mismo.');
  if (esRolFijo(m.correo)) throw new Error('Esta cuenta tiene un rol fijo y no se puede eliminar.');
  m.hoja.getRange(m.fila.numero, DOC.ESTADO + 1).setValue(ESTADOS.ELIMINADO);
  m.hoja.getRange(m.fila.numero, DOC.DISPOSITIVO + 1).setValue('');
  return { ok: true, mensaje: m.fila.valores[DOC.NOMBRE] + ' fue eliminado.' };
}

function adminLiberar(admin, d) {
  const m = filaMiembro(d.objetivo);
  if (m.correo !== admin.correo) protegerMiembro(admin, m);
  m.hoja.getRange(m.fila.numero, DOC.DISPOSITIVO + 1).setValue('');
  return { ok: true, mensaje: 'Teléfono liberado. ' + m.fila.valores[DOC.NOMBRE] + ' ya puede registrarse en otro teléfono.' };
}

/** Cuántos miembros (no eliminados) tiene cada jornada. */
function jornadasEnUso() {
  const conteo = {};
  leerMiembros().filter(m => marcaAsistencia(m.rol)).forEach(m => { conteo[m.jornada] = (conteo[m.jornada] || 0) + 1; });
  return conteo;
}

/** Crea (sin código) o edita (con código) una jornada. */
function adminGuardarJornada(j) {
  const nombre = String(j.nombre || '').trim();
  const entrada = String(j.entrada || '').trim();
  const salida = String(j.salida || '').trim();
  const dias = (j.dias || []).map(Number).filter(n => n >= 1 && n <= 7).sort();
  // Minutos de almuerzo (no cuentan como trabajo). Si la app no lo manda (versión anterior), se conserva el que tenía.
  const anterior = leerJornadas()[String(j.codigo || '').trim()];
  const almuerzo = j.almuerzo === undefined || j.almuerzo === null ? (anterior ? anterior.almuerzo : 0)
    : minutosAlmuerzoValidos(j.almuerzo);
  if (nombre.length < 2) throw new Error('Escribe el nombre de la jornada.');
  if (!/^\d{2}:\d{2}$/.test(entrada) || !/^\d{2}:\d{2}$/.test(salida)) throw new Error('Elige la hora de entrada y de salida.');
  // Salida menor que la entrada = termina al día siguiente (turno nocturno, p. ej. 18:00 → 02:00).
  if (salida === entrada) throw new Error('La salida no puede ser igual a la entrada.');
  if (!dias.length) throw new Error('Elige al menos un día laboral.');
  // Días con horario distinto al general: { 6: {entrada: '08:00', salida: '12:00'} }.
  const porDia = [];
  Object.keys(j.porDia || {}).forEach(dia => {
    const h = j.porDia[dia] || {};
    const n = Number(dia);
    if (dias.indexOf(n) < 0) return;
    if (!/^\d{2}:\d{2}$/.test(h.entrada) || !/^\d{2}:\d{2}$/.test(h.salida) || h.entrada === h.salida) {
      throw new Error('Revisa el horario del día ' + n + '.');
    }
    if (h.entrada !== entrada || h.salida !== salida) porDia.push(n + '=' + h.entrada + '-' + h.salida);
  });
  const hoja = obtenerHoja(JORNADAS.nombre, JORNADAS.encabezados);
  const jornadas = leerJornadas();
  let codigo = String(j.codigo || '').trim();
  const fila = [codigo, nombre, "'" + entrada, "'" + salida, "'" + dias.join(','), porDia.sort().join(';'), almuerzo];
  if (codigo && jornadas[codigo]) {
    hoja.getRange(buscarFila(hoja, 0, codigo.toLowerCase()).numero, 1, 1, fila.length).setValues([fila]);
  } else {
    const letras = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split('');
    fila[0] = letras.filter(l => !jornadas[l])[0] || ('J' + Date.now());
    hoja.appendRow(fila);
  }
  _jornadas = null;
  // Cambió el almuerzo: unos segundos después se recalculan las horas guardadas en la hoja de cada persona.
  if (anterior && (anterior.almuerzo || 0) !== almuerzo) programarRecalculoHoras();
  return { ok: true, mensaje: 'Jornada "' + nombre + '" guardada.' };
}

function adminEliminarJornada(codigo) {
  const jornadas = leerJornadas();
  if (!jornadas[codigo]) throw new Error('La jornada ya no existe.');
  if (Object.keys(jornadas).length <= 1) throw new Error('Debe quedar al menos una jornada.');
  const enUso = jornadasEnUso()[codigo] || 0;
  if (enUso) throw new Error(enUso + ' miembro(s) tienen esta jornada. Cámbialos a otra antes de eliminarla.');
  const hoja = obtenerHoja(JORNADAS.nombre, JORNADAS.encabezados);
  hoja.deleteRow(buscarFila(hoja, 0, codigo.toLowerCase()).numero);
  _jornadas = null;
  return { ok: true, mensaje: 'Jornada "' + jornadas[codigo].nombre + '" eliminada.' };
}

/** Agrega un evento al calendario. Devuelve el ID del evento creado. */
function agregarEvento(creador, e) {
  const fechaValida = /^\d{4}-\d{2}-\d{2}$/;
  const horaValida = /^\d{2}:\d{2}$/;
  if (!TIPOS_EVENTO[e.tipo]) throw new Error('Tipo de evento no válido.');
  if (!fechaValida.test(e.desde) || !fechaValida.test(e.hasta || e.desde)) throw new Error('Fechas no válidas.');
  const hasta = e.hasta || e.desde;
  if (hasta < e.desde) throw new Error('La fecha final es anterior a la inicial.');
  const correos = String(e.aplicaA || 'Todos').split(',').map(c => c.trim().toLowerCase()).filter(String);
  const paraTodos = correos.length === 0 || correos.indexOf('todos') >= 0;
  if (!paraTodos) {
    const docentes = obtenerHoja(DOCENTES.nombre, DOCENTES.encabezados);
    correos.forEach(c => { if (!buscarFila(docentes, DOC.CORREO, c)) throw new Error('No se encontró a ' + c + '.'); });
  }
  const aplica = paraTodos ? 'Todos' : correos.join(', ');
  // No se dan vacaciones dos veces en las mismas fechas a la misma persona (los días se contarían doble).
  if (e.tipo === 'VACACIONES' && !paraTodos) {
    const eventos = leerEventos();
    leerMiembros().filter(m => correos.indexOf(m.correo) >= 0).forEach(m => {
      const choque = vacacionesQueChocan(m, e.desde, hasta, eventos)[0];
      if (choque) throw new Error(m.nombre + ' ya tiene vacaciones del ' + fechaCorta(choque.desde) + ' al ' + fechaCorta(choque.hasta) + '.');
    });
  }
  const hora = String(e.horaEntrada || '').trim();
  if (e.tipo === 'ENTRADA_ESPECIAL' && !horaValida.test(hora)) {
    throw new Error('Elige la hora de entrada especial.');
  }
  const horaSalida = e.tipo === 'ENTRADA_ESPECIAL' ? String(e.horaSalida || '').trim() : '';
  if (horaSalida) {
    if (!horaValida.test(horaSalida)) throw new Error('Elige la hora de salida especial.');
    if (horaSalida === hora) throw new Error('La salida especial no puede ser igual a la entrada.');
  }
  const inicio = e.tipo === 'PERMISO' ? String(e.horaInicio || '').trim() : '';
  const fin = e.tipo === 'PERMISO' ? String(e.horaFin || '').trim() : '';
  if (inicio || fin) {
    if (!horaValida.test(inicio) || !horaValida.test(fin)) throw new Error('Elige las horas del permiso.');
    if (aMinutos(fin) <= aMinutos(inicio)) throw new Error('La hora final del permiso debe ser después de la inicial.');
  }
  const id = Utilities.getUuid();
  obtenerHoja(CALENDARIO.nombre, CALENDARIO.encabezados).appendRow([id, "'" + e.desde, "'" + hasta,
    TIPOS_EVENTO[e.tipo], aplica, hora ? "'" + hora : '', String(e.detalle || '').trim(), creador,
    inicio ? "'" + inicio : '', fin ? "'" + fin : '', horaSalida ? "'" + horaSalida : '']);
  return id;
}

function adminAgregarEvento(admin, e) {
  agregarEvento(admin.correo, e);
  let mensaje = TIPOS_EVENTO[e.tipo] + ' agregado al calendario.';
  if (e.tipo === 'VACACIONES') mensaje = 'Vacaciones agregadas al calendario. Ya aparecen en la app de cada persona.';
  if (e.tipo === 'FERIADO') mensaje = 'Asueto agregado al calendario. Ya aparece en la app de cada persona.';
  return { ok: true, mensaje: mensaje };
}

/**
 * Antes de dar vacaciones: para cada persona (o todo el personal que marca), cuántos días laborales son y si ya tiene
 * vacaciones en esas fechas. d: { aplicaA: 'Todos' | 'correo1, correo2', desde, hasta }.
 */
function adminVistaVacaciones(d) {
  const desde = String(d.desde || ''), hasta = String(d.hasta || d.desde || '');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(desde) || !/^\d{4}-\d{2}-\d{2}$/.test(hasta) || hasta < desde) return { ok: true, vistas: [] };
  const correos = String(d.aplicaA || 'Todos').split(',').map(c => c.trim().toLowerCase()).filter(String);
  const todos = !correos.length || correos.indexOf('todos') >= 0;
  const eventos = leerEventos();
  const vistas = leerMiembros()
    .filter(m => m.estado === 'ACTIVO' && marcaAsistencia(m.rol) && (todos || correos.indexOf(m.correo) >= 0))
    .map(m => vistaVacaciones(m, desde, hasta, eventos));
  return { ok: true, vistas: vistas };
}

function adminEliminarEvento(id) {
  const hoja = obtenerHoja(CALENDARIO.nombre, CALENDARIO.encabezados);
  const fila = buscarFila(hoja, CAL.ID, String(id || '').toLowerCase());
  if (!fila) throw new Error('El evento ya no existe.');
  hoja.deleteRow(fila.numero);
  return { ok: true, mensaje: 'Evento eliminado.' };
}

/** Aprueba (y agrega al calendario) o rechaza una solicitud de permiso. */
function adminRevisarSolicitud(admin, d) {
  const hoja = obtenerHoja(SOLICITUDES.nombre, SOLICITUDES.encabezados);
  const fila = buscarFila(hoja, SOL.ID, String(d.id || '').toLowerCase());
  if (!fila) throw new Error('La solicitud ya no existe.');
  const s = solicitudDe(fila.valores);
  if (s.estado !== 'PENDIENTE') throw new Error('Esta solicitud ya fue revisada o cancelada.');
  // Nadie revisa lo suyo: la solicitud de un supervisor la revisa otro supervisor o el administrador.
  if (s.correo.toLowerCase() === String(admin.correo).toLowerCase()) {
    throw new Error('No puedes aprobar ni rechazar tu propia solicitud. Debe revisarla otro supervisor o el administrador.');
  }
  const aprobar = d.aprobar === true;
  let idEvento = '';
  if (aprobar) {
    idEvento = agregarEvento(admin.correo, {
      tipo: s.tipo, desde: s.desde, hasta: s.hasta, aplicaA: s.correo,
      horaInicio: s.horaInicio, horaFin: s.horaFin, detalle: 'Solicitud aprobada: ' + s.motivo,
    });
  }
  hoja.getRange(fila.numero, SOL.ESTADO + 1, 1, 5).setValues([[
    aprobar ? ESTADOS_SOLICITUD.APROBADA : ESTADOS_SOLICITUD.RECHAZADA,
    String(d.respuesta || '').trim(), admin.nombre, new Date(), idEvento,
  ]]);
  return { ok: true, mensaje: aprobar ? 'Solicitud aprobada y agregada al calendario de ' + s.nombre + '.' : 'Solicitud rechazada.' };
}

function adminActividades() {
  const valores = obtenerHoja(ACTIVIDADES.nombre, ACTIVIDADES.encabezados).getDataRange().getValues();
  const ocultos = correosOcultos();
  const pendientes = valores.slice(1).filter(v => v[ACT.ID] && v[ACT.ESTADO] === POR_REVISAR &&
    !ocultos[String(v[ACT.CORREO]).toLowerCase()]).map(v => ({
    id: String(v[ACT.ID]),
    fecha: textoFecha(v[ACT.FECHA]),
    correo: v[ACT.CORREO],
    nombre: v[ACT.NOMBRE],
    entrada: v[ACT.ENTRADA] ? textoHora(v[ACT.ENTRADA]).substring(0, 5) : '',
    salida: v[ACT.SALIDA] ? textoHora(v[ACT.SALIDA]).substring(0, 5) : '',
    descripcion: String(v[ACT.DESCRIPCION] || ''),
    ubicacion: String(v[ACT.UBIC_E] || v[ACT.UBIC_S] || ''),
  }));
  return { ok: true, actividades: pendientes.reverse() };
}

function adminRevisarActividad(admin, d) {
  const hoja = obtenerHoja(ACTIVIDADES.nombre, ACTIVIDADES.encabezados);
  const fila = buscarFila(hoja, ACT.ID, String(d.id || '').toLowerCase());
  if (!fila) throw new Error('La actividad ya no existe.');
  if (String(fila.valores[ACT.CORREO]).toLowerCase() === String(admin.correo).toLowerCase()) {
    throw new Error('No puedes revisar tu propia actividad especial. Debe revisarla otro supervisor o el administrador.');
  }
  const estado = d.aprobar === true ? 'Aprobada' : 'Rechazada';
  hoja.getRange(fila.numero, ACT.ESTADO + 1, 1, 3).setValues([[estado, admin.nombre, new Date()]]);

  // También se anota en la hoja de la persona.
  const docentes = obtenerHoja(DOCENTES.nombre, DOCENTES.encabezados);
  const persona = buscarFila(docentes, DOC.CORREO, String(fila.valores[ACT.CORREO]).toLowerCase());
  const hojaPersona = persona && libroAsistencia().getSheetByName(persona.valores[DOC.HOJA]);
  const filaDia = hojaPersona && buscarFilaDelDia(hojaPersona, textoFecha(fila.valores[ACT.FECHA]), 400);
  if (filaDia) hojaPersona.getRange(filaDia, COL.REVISION).setValue(estado);
  return { ok: true, mensaje: 'Marca ' + (d.aprobar === true ? 'aprobada.' : 'rechazada.') };
}

// ---------------------------------------------------------------- Reporte mensual

/**
 * Resumen del mes por persona (días, puntuales, tardes, faltas, actividades, horas, horas extra verificadas,
 * días por revisar) y detalle por día. La app arma con esto el archivo para Excel.
 */
function adminReporte(d) {
  const mes = /^\d{4}-\d{2}$/.test(String(d.mes || '')) ? String(d.mes) : fechaDesplazada(0).substring(0, 7);
  return conLibroDelAnio(mes.substring(0, 4), () => reporteDelMes(mes));
}

function reporteDelMes(mes) {
  const hoy = fechaDesplazada(0);
  const eventos = leerEventos();
  const libro = libroAsistencia();
  const resumen = [];
  const detalle = [];
  const excepciones = [];
  leerMiembros().filter(m => marcaAsistencia(m.rol)).forEach(m => {
    const e = evaluarMes(m, filasDeMiembro(libro, m), mes, eventos, hoy);
    resumen.push(e.r);
    e.detalle.forEach(x => detalle.push(x));
    e.excepciones.forEach(x => excepciones.push(x));
  });
  const porNombreYFecha = (a, b) => String(a.nombre).localeCompare(String(b.nombre)) || a.fecha.localeCompare(b.fecha);
  resumen.sort((a, b) => String(a.nombre).localeCompare(String(b.nombre)));
  detalle.sort(porNombreYFecha);
  excepciones.sort(porNombreYFecha);
  return { ok: true, mes: mes, resumen: resumen, detalle: detalle, excepciones: excepciones };
}

/** Filas de la hoja de un miembro, por fecha ("yyyy-MM-dd" → fila). Se lee una sola vez. */
function filasDeMiembro(libro, m) {
  const filas = {};
  const hoja = libro.getSheetByName(m.hoja);
  if (hoja && hoja.getLastRow() > 1) {
    hoja.getRange(2, 1, hoja.getLastRow() - 1, ENCABEZADOS_DOCENTE.length).getValues().forEach(v => {
      const f = textoFecha(v[COL.FECHA - 1]);
      if (f) filas[f] = v;
    });
  }
  return filas;
}

/** Evalúa un mes de un miembro: resumen (r), detalle por día y excepciones. */
function evaluarMes(m, filas, mes, eventos, hoy) {
  const partes = mes.split('-').map(Number);
  const diasMes = new Date(partes[0], partes[1], 0).getDate();
  const jornada = jornadaJson(m.jornada);
  const detalle = [];
  const excepciones = [];
  const r = { correo: m.correo, nombre: m.nombre, rol: m.rolNombre, jornada: jornada.nombre, estado: ESTADOS[m.estado],
    dias: 0, puntuales: 0, tardes: 0, minutosTarde: 0, salidasAnticipadas: 0, faltas: 0, incompletos: 0,
    actividades: 0, libres: 0, minutos: 0, extra: 0, permiso: 0, porRevisar: 0 };
    const contarDesde = leerConfig().contarDesde;
    for (let dia = 1; dia <= diasMes; dia++) {
      const fecha = mes + '-' + ('0' + dia).slice(-2);
      if (fecha > hoy) break;
      if (contarDesde && fecha < contarDesde) continue; // antes de empezar a contar (días de prueba): no cuenta
      const v = filas[fecha];
      let meta = metaDelDia(m, fecha, eventos);
      if (v) {
        r.dias++;
        // Cada día se evalúa con la jornada con la que se marcó (un cambio de jornada cuenta desde ese momento).
        const mf = conJornadaDeFila(m, v);
        meta = metaDelDia(mf, fecha, eventos);
        const entrada = textoHora(v[COL.ENTRADA - 1]).substring(0, 5);
        const salida = v[COL.SALIDA - 1] ? textoHora(v[COL.SALIDA - 1]).substring(0, 5) : '';
        const esActividad = v[COL.ACTIVIDAD - 1] === 'Sí';
        const revision = String(v[COL.REVISION - 1] || '');
        const limite = limiteEntrada(mf, fecha, eventos);
        const minutosTarde = esActividad ? 0 : Math.max(0, aMinutos(entrada) - limite);
        let estado;
        if (esActividad) { r.actividades++; estado = 'Actividad especial'; }
        else if (minutosTarde > 0) { r.tardes++; r.minutosTarde += minutosTarde; estado = 'Tarde'; }
        else { r.puntuales++; estado = 'Puntual'; }
        let minutos = 0, extra = 0, minutosAntes = 0;
        const permisos = permisosPorHoras(mf, fecha, eventos);
        const permiso = minutosDePermisos(permisos);
        r.permiso += permiso;
        if (salida) {
          minutos = aMinutos(salida) - aMinutos(entrada);
          if (minutos < 0) minutos += 1440; // turno que pasa la medianoche
          // El permiso dentro de la jornada no se trabajó: se descuenta. El almuerzo tampoco cuenta como trabajo.
          minutos = sinAlmuerzo(Math.max(0, minutos - minutosPermisoDentro(permisos, entrada, salida)), mf, fecha, eventos);
          r.minutos += minutos;
          // Horas extra: lo que pasa de la meta del día (solo marcas verificadas).
          if (revision !== POR_REVISAR && revision !== 'Rechazada') extra = Math.max(0, minutos - meta);
          r.extra += extra;
          if (!esActividad) minutosAntes = minutosAntesDeSalida(mf, fecha, eventos, entrada, salida);
          if (minutosAntes > 0) r.salidasAnticipadas++;
        }
        if (revision === POR_REVISAR) r.porRevisar++;
        // Estado del día (como en el sistema anterior): Asistió, Parcial (falta la salida), En curso o Actividad especial.
        let estadoDia = esActividad ? 'Actividad especial' : (salida ? 'Asistió' : (fecha === hoy ? 'En curso' : 'Parcial'));
        if (estadoDia === 'Parcial') r.incompletos++;
        const obs = String(v[COL.OBSERVACION - 1] || '');
        detalle.push({ nombre: m.nombre, fecha: fecha, entrada: entrada, salida: salida,
          horas: salida ? textoDuracion(minutos) : '', estado: estado, revision: revision || 'No aplica',
          minutos: salida ? minutos : '', estadoDia: estadoDia, minutosTarde: minutosTarde, minutosAntes: salida ? minutosAntes : '',
          meta: textoDuracion(meta), extra: extra ? textoDuracion(extra) : '', permiso: permiso ? textoDuracion(permiso) : '',
          observacion: obs });
        if (estadoDia === 'Parcial') excepciones.push({ nombre: m.nombre, fecha: fecha, tipo: 'Registro incompleto',
          detalle: 'Falta la salida (entrada a las ' + entrada + ').' });
        if (minutosTarde > 0) excepciones.push({ nombre: m.nombre, fecha: fecha, tipo: 'Llegada tarde',
          detalle: 'Entrada a las ' + entrada + ' (' + minutosTarde + ' min tarde).' });
        if (minutosAntes > 0) excepciones.push({ nombre: m.nombre, fecha: fecha, tipo: 'Salida anticipada',
          detalle: 'Salida a las ' + salida + ' (' + minutosAntes + ' min antes de su hora).' });
        if (revision === POR_REVISAR) excepciones.push({ nombre: m.nombre, fecha: fecha, tipo: 'Por revisar',
          detalle: esActividad ? 'Actividad especial fuera del colegio.' : 'Marca por revisar (QR o Wi-Fi no verificados).' });
      } else if (fecha < hoy && fecha >= m.registrado && esDiaLaboral(m, fecha)) {
        const libre = diaLibre(m, fecha, eventos);
        if (libre) {
          r.libres++;
          detalle.push({ nombre: m.nombre, fecha: fecha, entrada: '', salida: '', horas: '', estado: 'Día libre', revision: '',
            minutos: '', estadoDia: TIPOS_EVENTO[libre.tipo], minutosTarde: '', minutosAntes: '', meta: '', extra: '',
            observacion: libre.detalle || '' });
        } else {
          r.faltas++;
          detalle.push({ nombre: m.nombre, fecha: fecha, entrada: '', salida: '', horas: '', estado: 'Falta', revision: '',
            minutos: '', estadoDia: 'Inasistencia', minutosTarde: '', minutosAntes: '', meta: textoDuracion(meta), extra: '',
            observacion: 'Sin registros' });
          excepciones.push({ nombre: m.nombre, fecha: fecha, tipo: 'Inasistencia', detalle: 'Día laboral sin registros ni permiso.' });
        }
      }
    }
  return { r: r, detalle: detalle, excepciones: excepciones };
}

/**
 * Resumen de un año por persona y por mes (Admin → Reportes). Cada hoja se lee una sola vez.
 * Devuelve filas { mes, correo, nombre, rol, jornada, dias, puntuales, tardes, minutos, extra, faltas, … }.
 */
function adminResumenAnual(d) {
  const anioPedido = /^\d{4}$/.test(String(d.anio || '')) ? String(d.anio) : fechaDesplazada(0).substring(0, 4);
  return conLibroDelAnio(anioPedido, () => resumenDelAnio(anioPedido));
}

function resumenDelAnio(anio) {
  const hoy = fechaDesplazada(0);
  const ultimoMes = anio === hoy.substring(0, 4) ? Number(hoy.substring(5, 7)) : (anio < hoy.substring(0, 4) ? 12 : 0);
  const meses = [];
  for (let i = 1; i <= ultimoMes; i++) meses.push(anio + '-' + ('0' + i).slice(-2));
  const eventos = leerEventos();
  const libro = libroAsistencia();
  const filas = [];
  leerMiembros().filter(m => marcaAsistencia(m.rol)).forEach(m => {
    const registros = filasDeMiembro(libro, m);
    meses.forEach(mes => {
      // Meses antes de que la persona se registrara: no cuentan (ni como faltas).
      if (m.registrado && mes < m.registrado.substring(0, 7)) return;
      const r = evaluarMes(m, registros, mes, eventos, hoy).r;
      r.mes = mes;
      filas.push(r);
    });
  });
  filas.sort((a, b) => String(a.nombre).localeCompare(String(b.nombre)) || a.mes.localeCompare(b.mes));
  return { ok: true, anio: anio, meses: meses, filas: filas };
}

/**
 * Ranking del mes por horas trabajadas (lo ve todo el personal en la pantalla de inicio).
 * No incluye a los super administradores ni a quien no marca asistencia. Se guarda 5 minutos en caché.
 */
function rankingDelMes(u, mesPedido) {
  const mes = /^\d{4}-\d{2}$/.test(String(mesPedido || '')) ? String(mesPedido) : fechaDesplazada(0).substring(0, 7);
  // Normalmente ya está calculado (cada hora y unos segundos después de cada cambio): abrir la app no espera.
  const guardado = CacheService.getScriptCache().get('ranking:' + mes);
  let lista = guardado ? JSON.parse(guardado) : null;
  if (!lista) {
    lista = listaRankingDe(adminReporte({ mes: mes }).resumen);
    guardarRanking(mes, lista);
  }
  let miPosicion = 0;
  const publica = lista.map((x, i) => {
    if (x.correo === u.correo) miPosicion = i + 1;
    // No se envían los correos de los demás.
    return { posicion: i + 1, nombre: x.nombre, minutos: x.minutos, dias: x.dias, puntuales: x.puntuales,
      tardes: x.tardes, extra: x.extra, yo: x.correo === u.correo };
  });
  return { ok: true, mes: mes, lista: publica, miPosicion: miPosicion };
}

/** Ranking a partir del resumen del mes: por horas, sin inactivos ni quien no tiene registros. */
function listaRankingDe(resumen) {
  return resumen
    .filter(r => r.estado !== ESTADOS.INACTIVO && (r.dias > 0 || r.minutos > 0))
    .map(r => ({ correo: r.correo, nombre: r.nombre, minutos: r.minutos, dias: r.dias, puntuales: r.puntuales,
      tardes: r.tardes, extra: r.extra }))
    .sort((a, b) => b.minutos - a.minutos || b.dias - a.dias || String(a.nombre).localeCompare(String(b.nombre)));
}

/** Guarda el ranking listo para 6 horas (se renueva antes, cada hora y tras cada cambio). */
function guardarRanking(mes, lista) {
  CacheService.getScriptCache().put('ranking:' + mes, JSON.stringify(lista), 21600);
}

/**
 * Minutos que salió antes de su hora de salida ese día (0 si no). Tiene en cuenta la entrada especial
 * y los turnos que terminan al día siguiente.
 */
function minutosAntesDeSalida(m, fecha, eventos, entrada, salida) {
  const especial = horarioEspecial(m, fecha, eventos);
  const horario = horarioDelDia(jornadaJson(m.jornada), diaIso(fecha));
  const entradaEsperada = aMinutos(especial ? especial.horaEntrada : horario.entrada);
  const salidaEsperada = especial && especial.horaSalida ? especial.horaSalida : (especial ? '' : horario.salida);
  if (!salidaEsperada) return 0;
  let fin = aMinutos(salidaEsperada);
  if (fin <= entradaEsperada) fin += 1440;
  permisosPorHoras(m, fecha, eventos).forEach(p => {
    let i = aMinutos(p.horaInicio), f = aMinutos(p.horaFin);
    if (fin > 1440 && i < entradaEsperada) { i += 1440; f += 1440; }
    if (i < fin && f >= fin) fin = i; // con permiso hasta el final, puede salir al empezar el permiso
  });
  let real = aMinutos(salida);
  if (real < aMinutos(entrada)) real += 1440;
  return Math.max(0, fin - real);
}

// ---------------------------------------------------------------- Cierre anual

/**
 * Cierre anual (se hace solo el 2 de enero): copia todo el año que terminó a un archivo nuevo en el Drive
 * ("Mesón Asiste — Archivo 2026") y limpia de la hoja principal las marcas, actividades, rechazos, solicitudes y
 * eventos de ese año. El personal, los roles, las jornadas, la configuración y las sedes NO se tocan.
 * Los reportes de ese año siguen funcionando: se leen del archivo.
 */
function cierreAnual(anioForzado) {
  const hoy = fechaDesplazada(0);
  const anio = anioForzado ? String(anioForzado) : String(Number(hoy.substring(0, 4)) - 1);
  const props = PropertiesService.getScriptProperties();
  if (props.getProperty('ARCHIVO_' + anio)) return { ok: true, mensaje: 'El año ' + anio + ' ya estaba archivado.' };
  if (anio >= hoy.substring(0, 4)) throw new Error('Solo se pueden archivar años que ya terminaron.');
  const libro = libroAsistencia();
  const fin = anio + '-12-31';

  // 1. Archivo con una copia de todas las pestañas.
  const archivo = SpreadsheetApp.create('Mesón Asiste — Archivo ' + anio);
  const vacia = archivo.getSheets()[0];
  libro.getSheets().forEach(h => h.copyTo(archivo).setName(h.getName()));
  archivo.deleteSheet(vacia);
  archivo.setSpreadsheetTimeZone(CONFIG.ZONA_HORARIA);
  props.setProperty('ARCHIVO_' + anio, archivo.getId());

  // 2. Limpieza de la hoja principal (solo lo de ese año o antes).
  let filas = 0;
  const personal = obtenerHoja(DOCENTES.nombre, DOCENTES.encabezados).getDataRange().getValues().slice(1)
    .map(v => String(v[DOC.HOJA] || '')).filter(String);
  personal.forEach(n => { filas += borrarFilasHasta(libro.getSheetByName(n), COL.FECHA - 1, fin); });
  filas += borrarFilasHasta(libro.getSheetByName(ACTIVIDADES.nombre), ACT.FECHA, fin);
  filas += borrarFilasHasta(libro.getSheetByName(RECHAZADOS.nombre), 0, fin);
  filas += borrarFilasHasta(libro.getSheetByName(SOLICITUDES.nombre), SOL.HASTA, fin);
  filas += borrarFilasHasta(libro.getSheetByName(CALENDARIO.nombre), CAL.HASTA, fin);
  CacheService.getScriptCache().removeAll(['destacados']);
  return { ok: true, mensaje: 'Cierre anual ' + anio + ': archivo "' + archivo.getName() + '" creado y ' + filas +
    ' filas limpiadas de la hoja principal.' };
}

/**
 * Ejecutar UNA vez desde el editor para empezar a contar desde hoy (por ejemplo, después de días de prueba):
 *  1. Hace una copia completa de la hoja en tu Drive ("Mesón Asiste — Respaldo antes de empezar el …").
 *  2. Borra de la hoja principal todo lo anterior a hoy: marcas de cada persona, actividades especiales, rechazados,
 *     solicitudes (y lo que su aprobación agregó al calendario) y bitácora.
 *  3. Fija "CONTAR_DESDE" en Configuración: los días anteriores a hoy no cuentan como faltas ni suman horas.
 * El personal, los roles, las jornadas, las sedes y el resto del calendario NO se tocan.
 */
function empezarDesdeHoy() {
  const libro = libroAsistencia();
  const hoy = fechaDesplazada(0);
  const ayer = fechaDesplazada(-1);
  const respaldo = libro.copy('Mesón Asiste — Respaldo antes de empezar el ' + fechaCorta(hoy));
  Logger.log('1. Copia de respaldo creada: ' + respaldo.getUrl());

  let filas = 0;
  const detalle = [];
  const contar = (nombre, n) => { filas += n; if (n) detalle.push(nombre + ': ' + n); };
  // Marcas de cada persona.
  obtenerHoja(DOCENTES.nombre, DOCENTES.encabezados).getDataRange().getValues().slice(1)
    .map(v => String(v[DOC.HOJA] || '')).filter(String)
    .forEach(n => contar(n, borrarFilasHasta(libro.getSheetByName(n), COL.FECHA - 1, ayer)));
  contar(ACTIVIDADES.nombre, borrarFilasHasta(libro.getSheetByName(ACTIVIDADES.nombre), ACT.FECHA, ayer));
  contar(RECHAZADOS.nombre, borrarFilasHasta(libro.getSheetByName(RECHAZADOS.nombre), 0, ayer));
  // Solicitudes pedidas antes de hoy, y lo que su aprobación agregó al calendario.
  const hojaSol = libro.getSheetByName(SOLICITUDES.nombre);
  if (hojaSol && hojaSol.getLastRow() > 1) {
    const eventosDePrueba = hojaSol.getRange(2, 1, hojaSol.getLastRow() - 1, SOLICITUDES.encabezados.length).getValues()
      .filter(v => textoFecha(v[SOL.CREADA]).substring(0, 10) <= ayer && v[SOL.EVENTO]).map(v => String(v[SOL.EVENTO]).toLowerCase());
    const cal = libro.getSheetByName(CALENDARIO.nombre);
    let quitados = 0;
    eventosDePrueba.forEach(id => {
      const fila = cal && buscarFila(cal, CAL.ID, id);
      if (fila) { cal.deleteRow(fila.numero); quitados++; }
    });
    contar('Calendario (de solicitudes de prueba)', quitados);
  }
  contar(SOLICITUDES.nombre, borrarFilasHasta(hojaSol, SOL.CREADA, ayer));
  // Bitácora: la fecha está como "dd/MM/yyyy HH:mm:ss".
  contar(BITACORA.nombre, borrarFilasHasta(libro.getSheetByName(BITACORA.nombre), 0, ayer,
    v => { const m = String(v).match(/^(\d{2})\/(\d{2})\/(\d{4})/); return m ? m[3] + '-' + m[2] + '-' + m[1] : textoFecha(v); }));
  Logger.log('2. Filas borradas: ' + filas + (detalle.length ? ' (' + detalle.join(', ') + ')' : ''));

  guardarConfig('CONTAR_DESDE', "'" + hoy);
  Logger.log('3. Desde el ' + fechaCorta(hoy) + ' se cuenta todo; los días anteriores no cuentan como faltas.');
  CacheService.getScriptCache().removeAll(['destacados', 'ranking:' + hoy.substring(0, 7)]);
  refrescarPestanas();
  Logger.log('Listo. Reportes, ranking y destacados actualizados.');
}

/**
 * Borra las filas cuya fecha (columna [col], base 0) es igual o anterior a [fin]. Borra por bloques (rápido).
 * [aFecha] convierte la celda a "yyyy-MM-dd" (por defecto, textoFecha).
 */
function borrarFilasHasta(hoja, col, fin, aFecha) {
  if (!hoja || hoja.getLastRow() < 2) return 0;
  const fechas = hoja.getRange(2, col + 1, hoja.getLastRow() - 1, 1).getValues().map(v => (aFecha || textoFecha)(v[0]));
  let borradas = 0;
  let i = fechas.length - 1;
  while (i >= 0) {
    const vieja = f => /^\d{4}-\d{2}-\d{2}/.test(f) && f.substring(0, 10) <= fin;
    if (!vieja(fechas[i])) { i--; continue; }
    let inicio = i;
    while (inicio - 1 >= 0 && vieja(fechas[inicio - 1])) inicio--;
    hoja.deleteRows(inicio + 2, i - inicio + 1);
    borradas += i - inicio + 1;
    i = inicio - 1;
  }
  return borradas;
}

/**
 * Ejecuta [fn] leyendo del archivo de ese año si ya se hizo su cierre anual (reportes de años pasados).
 * Si no hay archivo, usa la hoja principal.
 */
function conLibroDelAnio(anio, fn) {
  const id = anio < fechaDesplazada(0).substring(0, 4) &&
    PropertiesService.getScriptProperties().getProperty('ARCHIVO_' + anio);
  if (!id) return fn();
  const antes = { libro: _libro, jornadas: _jornadas, config: _config, roles: _roles };
  _libro = SpreadsheetApp.openById(id); _jornadas = null; _config = null; _roles = null;
  try {
    return fn();
  } finally {
    _libro = antes.libro; _jornadas = antes.jornadas; _config = antes.config; _roles = antes.roles;
  }
}

// ---------------------------------------------------------------- Pestañas de reporte en la hoja

const RESUMEN_GLOBAL = {
  nombre: 'Resumen Global',
  encabezados: ['Empleado', 'Rol', 'Jornada', 'Estado', 'Total horas', 'Días asistidos', 'Puntuales', 'Llegadas tarde',
    'Min tarde', 'Salidas anticipadas', 'Registros incompletos', 'Inasistencias', 'Días libres', 'Actividades especiales',
    'Horas extra', 'Por revisar', 'Horas permiso'],
};
const ESTADO_GLOBAL = {
  nombre: 'Estado Global',
  encabezados: ['Fecha', 'Empleado', 'Entrada', 'Salida', 'Horas', 'Min trab', 'Estado', 'Ent status', 'Min ret',
    'Sal ant', 'Min antes', 'Meta', 'Horas extra', 'Revisión', 'Obs'],
};
const EXCEPCIONES = { nombre: 'Excepciones', encabezados: ['Empleado', 'Fecha', 'Tipo excepción', 'Detalles'] };

/**
 * Actualiza las pestañas "Resumen Global", "Estado Global" y "Excepciones" con el mes actual.
 * Se ejecuta sola cada hora (ver activarActualizacionAutomatica) y desde la app en Admin → Reportes.
 */
function actualizarReportes() {
  try { limpiarRastrosSuperAdmin(); } catch (e) { Logger.log('Limpieza del super administrador: ' + e.message); }
  // Del 2 de enero en adelante: si el año pasado aún no se archivó, se hace el cierre anual.
  const hoyTexto = fechaDesplazada(0);
  if (hoyTexto.substring(5, 7) === '01' && hoyTexto.substring(8, 10) >= '02') {
    try { Logger.log(cierreAnual().mensaje); } catch (e) { Logger.log('Cierre anual pendiente: ' + e.message); }
  }
  const resultado = refrescarPestanas();
  try { const url = respaldoSemanal(false); if (url) Logger.log('Respaldo semanal: ' + url); } catch (e) { Logger.log('Respaldo semanal: ' + e.message); }
  // Ajusta el ancho de las columnas de todas las pestañas al contenido.
  libroAsistencia().getSheets().forEach(h => { try { ajustarColumnas(h); } catch (e) { /* pestaña sin datos */ } });
  return resultado;
}

/** Escribe "Resumen Global", "Estado Global" y "Excepciones" con el mes actual (rápido: sin limpiezas). */
function refrescarPestanas() {
  const mes = fechaDesplazada(0).substring(0, 7);
  const r = adminReporte({ mes: mes });
  guardarRanking(mes, listaRankingDe(r.resumen));
  try { calcularDestacados(); } catch (e) { console.log('Destacados: ' + e.message); }
  const horas = min => Math.round(min / 60 * 100) / 100;
  const fecha = f => f.split('-').reverse().join('-'); // 2026-11-03 → 03-11-2026, como en el sistema anterior
  escribirReporte(RESUMEN_GLOBAL, r.resumen.map(x => [x.nombre, x.rol, x.jornada, x.estado, horas(x.minutos), x.dias,
    x.puntuales, x.tardes, x.minutosTarde, x.salidasAnticipadas, x.incompletos, x.faltas, x.libres, x.actividades,
    horas(x.extra), x.porRevisar, horas(x.permiso || 0)]), mes);
  escribirReporte(ESTADO_GLOBAL, r.detalle.slice().sort((a, b) => a.fecha.localeCompare(b.fecha) ||
    String(a.nombre).localeCompare(String(b.nombre))).map(d => [fecha(d.fecha), d.nombre, d.entrada, d.salida, d.horas,
    d.minutos, d.estadoDia, d.estado === 'Tarde' || d.estado === 'Puntual' ? d.estado : '—', d.minutosTarde,
    d.minutosAntes === '' ? '—' : (d.minutosAntes > 0 ? 'Sí' : 'No'), d.minutosAntes, d.meta, d.extra,
    d.revision === 'No aplica' ? '' : d.revision, d.observacion]), mes);
  escribirReporte(EXCEPCIONES, r.excepciones.map(e => [e.nombre, fecha(e.fecha), e.tipo, e.detalle]), mes);
  return { ok: true, mensaje: 'Reportes de la hoja actualizados (' + r.resumen.length + ' personas).' };
}

// ---------------------------------------------------------------- Todo enlazado

/** Acciones de la app que cambian lo que muestran los reportes y el ranking. */
const AFECTA_REPORTES = ['registrar', 'registrarDocente', 'adminAsignar', 'adminEstado', 'adminEliminarMiembro',
  'adminGuardarJornada', 'adminEliminarJornada', 'adminAgregarEvento', 'adminEliminarEvento', 'adminRevisarActividad',
  'adminRevisarSolicitud', 'adminGuardarReglas', 'adminGuardarRol', 'adminEliminarRol'];

/**
 * Algo cambió desde la app: en unos segundos (sin hacer esperar a nadie) se actualizan las pestañas de reporte,
 * el ranking y los destacados. Varios cambios seguidos se juntan en una sola actualización.
 */
function cambiaronDatos(accion) {
  try {
    // No se borra el ranking: unos segundos después se recalcula en segundo plano (nadie espera ese cálculo).
    const props = PropertiesService.getScriptProperties();
    const pendiente = Number(props.getProperty('REFRESCO_REPORTES') || 0);
    if (pendiente && Date.now() - pendiente < 10 * 60 * 1000) return; // ya hay una actualización en camino
    props.setProperty('REFRESCO_REPORTES', String(Date.now()));
    ScriptApp.newTrigger('refrescoProgramado').timeBased().after(20 * 1000).create();
  } catch (e) {
    console.log('No se pudo programar la actualización de los reportes: ' + e.message);
  }
}

/** Recalcula en segundo plano (sin hacer esperar a nadie) las horas guardadas en la hoja de cada persona. */
function programarRecalculoHoras() {
  try {
    if (ScriptApp.getProjectTriggers().some(t => t.getHandlerFunction() === 'recalculoProgramado')) return;
    ScriptApp.newTrigger('recalculoProgramado').timeBased().after(20 * 1000).create();
  } catch (e) {
    console.log('No se pudo programar el recálculo de horas: ' + e.message);
  }
}

/** Lo ejecuta Google unos segundos después de cambiar el almuerzo de una jornada. */
function recalculoProgramado() {
  ScriptApp.getProjectTriggers().filter(t => t.getHandlerFunction() === 'recalculoProgramado')
    .forEach(t => ScriptApp.deleteTrigger(t));
  recalcularHoras();
}

/** Lo ejecuta Google unos segundos después de un cambio hecho desde la app. */
function refrescoProgramado() {
  PropertiesService.getScriptProperties().deleteProperty('REFRESCO_REPORTES');
  ScriptApp.getProjectTriggers().filter(t => t.getHandlerFunction() === 'refrescoProgramado')
    .forEach(t => ScriptApp.deleteTrigger(t));
  refrescarPestanas();
}

/** Reemplaza el contenido de una pestaña de reporte (encabezados en la fila 1). */
function escribirReporte(def, filas, mes) {
  const hoja = obtenerHoja(def.nombre, def.encabezados);
  hoja.getRange(1, 1, 1, def.encabezados.length).setValues([def.encabezados]).setFontWeight('bold');
  if (hoja.getLastRow() > 1) hoja.getRange(2, 1, hoja.getLastRow() - 1, Math.max(hoja.getLastColumn(), def.encabezados.length)).clearContent();
  // Números como números (para sumar en la hoja); horas y fechas como texto, para que Sheets no las convierta.
  const celda = c => c === null || c === undefined ? '' :
    (typeof c === 'string' && /^\d{1,2}[:-]\d{2}/.test(c) ? "'" + c : c);
  if (filas.length) hoja.getRange(2, 1, filas.length, def.encabezados.length).setValues(filas.map(f => f.map(celda)));
  hoja.getRange('A1').setNote('Mes: ' + mes + '. Actualizado: ' +
    Utilities.formatDate(new Date(), CONFIG.ZONA_HORARIA, 'dd/MM/yyyy HH:mm') +
    '. Se actualiza sola cada hora; no escribas en esta pestaña.');
  ajustarColumnas(hoja);
}

// ---------------------------------------------------------------- Respaldo semanal

/** Cuántas copias semanales se guardan (se van turnando: la más vieja se reemplaza). */
const RESPALDOS_SEMANALES = 4;

/**
 * Copia completa de la hoja una vez por semana, de madrugada (lo llama la actualización de cada hora). Se guardan
 * 4 copias en el Drive de quien publicó el servidor ("Mesón Asiste — Respaldo semanal 1…4"); cada semana se
 * reemplaza la más vieja, así siempre están las últimas 4 semanas sin llenar el Drive. [forzar] = hacerlo ya.
 * Devuelve el enlace de la copia, o null si aún no tocaba.
 */
function respaldoSemanal(forzar) {
  const props = PropertiesService.getScriptProperties();
  const ultimo = Number(props.getProperty('RESPALDO_ULTIMO') || 0);
  const hora = Number(Utilities.formatDate(new Date(), CONFIG.ZONA_HORARIA, 'H'));
  if (!forzar && (Date.now() - ultimo < 7 * 86400000 || hora >= 6)) return null;
  const libro = libroAsistencia();
  const turno = Number(props.getProperty('RESPALDO_TURNO') || 0) % RESPALDOS_SEMANALES;
  const clave = 'RESPALDO_' + turno;
  const nombre = 'Mesón Asiste — Respaldo semanal ' + (turno + 1) + ' (' + fechaCorta(fechaDesplazada(0)) + ')';
  let destino = null;
  try { if (props.getProperty(clave)) destino = SpreadsheetApp.openById(props.getProperty(clave)); } catch (e) { destino = null; }
  if (!destino) {
    destino = SpreadsheetApp.create(nombre);
    props.setProperty(clave, destino.getId());
  } else {
    destino.rename(nombre);
  }
  // Se reemplaza todo su contenido por una copia de cada pestaña de la hoja principal.
  const temporal = destino.insertSheet('temporal-' + Date.now());
  destino.getSheets().filter(h => h.getSheetId() !== temporal.getSheetId()).forEach(h => destino.deleteSheet(h));
  libro.getSheets().forEach(h => h.copyTo(destino).setName(h.getName()));
  destino.deleteSheet(temporal);
  destino.setSpreadsheetTimeZone(CONFIG.ZONA_HORARIA);
  props.setProperty('RESPALDO_ULTIMO', String(Date.now()));
  props.setProperty('RESPALDO_TURNO', String(turno + 1));
  return destino.getUrl();
}

/** Ejecutar desde el editor para hacer un respaldo en este momento (además del automático de cada semana). */
function hacerRespaldoAhora() {
  Logger.log('Respaldo creado: ' + respaldoSemanal(true));
}

/** Ejecutar una vez desde el editor: actualiza los reportes cada hora (y lo hace ahora). */
function activarActualizacionAutomatica() {
  ScriptApp.getProjectTriggers().filter(t => t.getHandlerFunction() === 'actualizarReportes')
    .forEach(t => ScriptApp.deleteTrigger(t));
  ScriptApp.newTrigger('actualizarReportes').timeBased().everyHours(1).create();
  Logger.log(actualizarReportes().mensaje + ' Desde ahora se actualizan solos cada hora.');
}

// ---------------------------------------------------------------- Ajustes (meta, tolerancia y sedes)

function adminConfig() {
  const config = leerConfig();
  return {
    ok: true,
    horasMeta: config.horasMeta,
    tolerancia: config.tolerancia,
    sedes: leerSedes().map(s => Object.assign(sedeJson(s), { wifi: s.wifi })),
  };
}

function adminGuardarReglas(d) {
  const meta = Number(d.horasMeta);
  const tolerancia = Number(d.tolerancia);
  if (!(meta >= 1 && meta <= 12)) throw new Error('La meta debe estar entre 1 y 12 horas.');
  if (!(tolerancia >= 0 && tolerancia <= 60)) throw new Error('La tolerancia debe estar entre 0 y 60 minutos.');
  guardarConfig('HORAS_META', meta);
  guardarConfig('TOLERANCIA_MINUTOS', tolerancia);
  return { ok: true, mensaje: 'Reglas guardadas.' };
}

/** Valida punto y radio (0 a 100 m) de una ubicación. */
function datosSede(s) {
  const lat = Number(s.latitud);
  const lon = Number(s.longitud);
  const radio = Math.round(Number(s.radio));
  if (!isFinite(lat) || !isFinite(lon) || (lat === 0 && lon === 0) || Math.abs(lat) > 90 || Math.abs(lon) > 180) {
    throw new Error('No se recibió una ubicación válida.');
  }
  if (!isFinite(radio) || radio < 0 || radio > 100) throw new Error('El radio debe estar entre 0 y 100 metros.');
  return { lat: lat, lon: lon, radio: radio, nombre: String(s.nombre || '').trim() || 'Colegio Mesón' };
}

/** Agrega la ubicación del colegio (donde está el administrador o un punto elegido) con un radio. */
function adminAgregarSede(s) {
  const v = datosSede(s);
  obtenerHoja(SEDES.nombre, SEDES.encabezados).appendRow([Utilities.getUuid(), v.nombre, v.lat, v.lon, v.radio,
    'UBICACION', '', nuevoSecreto()]);
  return { ok: true, mensaje: 'Ubicación "' + v.nombre + '" guardada.' };
}

/** Cambia el nombre, el punto o el radio de una ubicación (conserva su verificación, Wi-Fi y QR). */
function adminEditarSede(s) {
  const v = datosSede(s);
  const hoja = obtenerHoja(SEDES.nombre, SEDES.encabezados);
  const fila = buscarFila(hoja, SED.ID, String(s.id || '').toLowerCase());
  if (!fila) throw new Error('La ubicación ya no existe.');
  hoja.getRange(fila.numero, SED.NOMBRE + 1, 1, 4).setValues([[v.nombre, v.lat, v.lon, v.radio]]);
  return { ok: true, mensaje: 'Ubicación "' + v.nombre + '" actualizada.' };
}

function adminEliminarSede(id) {
  const hoja = obtenerHoja(SEDES.nombre, SEDES.encabezados);
  const fila = buscarFila(hoja, SED.ID, String(id || '').toLowerCase());
  if (!fila) throw new Error('La ubicación ya no existe.');
  hoja.deleteRow(fila.numero);
  return { ok: true, mensaje: 'Ubicación quitada.' };
}

/** Elige cómo se comprueba la presencia en una sede (UBICACION, WIFI o QR) y, para Wi-Fi, qué redes valen. */
function adminGuardarVerificacion(d) {
  const metodo = METODOS[d.metodo] ? d.metodo : 'UBICACION';
  const hoja = obtenerHoja(SEDES.nombre, SEDES.encabezados);
  const fila = buscarFila(hoja, SED.ID, String(d.id || '').toLowerCase());
  if (!fila) throw new Error('La ubicación ya no existe.');
  hoja.getRange(fila.numero, SED.METODO + 1).setValue(metodo);
  if (Array.isArray(d.wifi)) {
    const redes = d.wifi.map(x => String(x).trim().toLowerCase()).filter(String);
    if (metodo === 'WIFI' && !redes.length) {
      throw new Error('No se detectó ningún Wi-Fi. Enciende el Wi-Fi del teléfono y hazlo dentro del colegio.');
    }
    hoja.getRange(fila.numero, SED.WIFI + 1).setValue(redes.join(','));
  }
  if (!fila.valores[SED.SECRETO]) hoja.getRange(fila.numero, SED.SECRETO + 1).setValue(nuevoSecreto());
  return { ok: true, mensaje: 'Verificación guardada: ' + METODOS[metodo] + '.' };
}

/** Clave del QR de una sede y la hora del servidor (para mostrar el QR en la entrada). */
function adminSecretoQr(id) {
  const sede = leerSedes().filter(s => s.id === String(id))[0];
  if (!sede) throw new Error('La ubicación ya no existe.');
  if (!sede.secreto) throw new Error('Guarda primero la verificación por QR de esta ubicación.');
  return { ok: true, secreto: sede.secreto, ahora: Math.floor(Date.now() / 1000) };
}

// ---------------------------------------------------------------- Roles

/** Roles con sus funciones, las funciones disponibles y cuántas personas tiene cada rol. */
function adminRoles(admin) {
  const enUso = {};
  leerMiembros().forEach(m => { enUso[m.rol] = (enUso[m.rol] || 0) + 1; });
  return {
    ok: true,
    roles: rolesVisibles(admin),
    permisos: Object.keys(PERMISOS).map(p => ({ codigo: p, nombre: PERMISOS[p] })),
    enUso: enUso,
  };
}

/**
 * Crea (sin código) o edita un rol: nombre, si marca asistencia y sus funciones.
 * Reglas: el Super administrador y el Administrador solo los edita el super administrador (el Super administrador
 * conserva siempre todas sus funciones), y la función "Gestionar roles" solo la puede dar el super administrador.
 */
function adminGuardarRol(admin, r) {
  const nombre = String(r.nombre || '').trim();
  if (nombre.length < 2) throw new Error('Escribe el nombre del rol.');
  const roles = leerRoles();
  let codigo = String(r.codigo || '').trim().toUpperCase();
  if (codigo === 'SUPERADMIN') throw new Error('Ese rol no se puede cambiar.');
  const superAdmin = esSuperAdmin(admin.rol);
  if (ROLES_PROTEGIDOS.indexOf(codigo) >= 0 && !superAdmin) {
    throw new Error('Solo el super administrador puede cambiar el rol "' + roles[codigo].nombre + '".');
  }
  const repetido = Object.keys(roles).filter(c => roles[c].nombre.toLowerCase() === nombre.toLowerCase() && c !== codigo)[0];
  if (repetido) throw new Error('Ya existe un rol llamado "' + nombre + '".');
  const marca = r.marca === true ? 'Sí' : 'No';
  let permisos = (r.permisos || []).map(p => String(p).toUpperCase()).filter(p => PERMISOS[p]);
  // "Gestionar roles" solo la da el super administrador; si no lo es, se conserva lo que el rol ya tenía.
  if (!superAdmin) {
    permisos = permisos.filter(p => p !== 'ROLES');
    if (codigo && roles[codigo] && roles[codigo].permisos.indexOf('ROLES') >= 0) permisos.push('ROLES');
  }
  const hoja = obtenerHoja(ROLES_HOJA.nombre, ROLES_HOJA.encabezados);
  if (codigo && roles[codigo]) {
    const funciones = codigo === 'SUPERADMIN' ? Object.keys(PERMISOS).join(',') : permisos.join(',');
    const fila = buscarFila(hoja, 0, codigo.toLowerCase());
    hoja.getRange(fila.numero, 1, 1, 4).setValues([[codigo, nombre, marca, funciones]]);
  } else {
    const base = nombre.toUpperCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^A-Z0-9]+/g, '_')
      .replace(/^_|_$/g, '').substring(0, 24) || 'ROL';
    codigo = base;
    for (let i = 2; roles[codigo] || codigo === 'SUPERADMIN'; i++) codigo = base + '_' + i;
    hoja.appendRow([codigo, nombre, marca, permisos.join(',')]);
  }
  _roles = null;
  return { ok: true, mensaje: 'Rol "' + nombre + '" guardado.' };
}

function adminEliminarRol(admin, codigo) {
  const roles = leerRoles();
  const c = String(codigo || '').toUpperCase();
  if (!roles[c] || c === 'SUPERADMIN') throw new Error('El rol ya no existe.');
  if (ROLES_FIJOS.indexOf(c) >= 0) throw new Error('El rol "' + roles[c].nombre + '" no se puede eliminar.');
  const enUso = leerMiembros().filter(m => m.rol === c).length;
  if (enUso) throw new Error(enUso + ' persona(s) tienen este rol. Cámbialas a otro antes de eliminarlo.');
  const hoja = obtenerHoja(ROLES_HOJA.nombre, ROLES_HOJA.encabezados);
  hoja.deleteRow(buscarFila(hoja, 0, c.toLowerCase()).numero);
  _roles = null;
  return { ok: true, mensaje: 'Rol "' + roles[c].nombre + '" eliminado.' };
}

// ---------------------------------------------------------------- Utilidades

function correoInstitucional(valor) {
  const correo = String(valor || '').trim().toLowerCase();
  if (!/^[a-z0-9._%+-]+@/.test(correo) || correo.split('@')[1] !== CONFIG.DOMINIO) {
    throw new Error('Usa tu correo institucional @' + CONFIG.DOMINIO + '.');
  }
  return correo;
}

/** Busca de abajo hacia arriba la fila con esa fecha (en las últimas [filas] filas). Devuelve su número o null. */
function buscarFilaDelDia(hoja, fecha, filas) {
  const ultimaFila = hoja.getLastRow();
  if (ultimaFila < 2) return null;
  const desde = Math.max(2, ultimaFila - (filas || 10) + 1);
  const fechas = hoja.getRange(desde, COL.FECHA, ultimaFila - desde + 1, 1).getValues();
  for (let i = fechas.length - 1; i >= 0; i--) {
    if (textoFecha(fechas[i][0]) === fecha) return desde + i;
  }
  return null;
}

/** Nombre de la hoja del miembro; si ya existe una con ese nombre, se agrega el usuario del correo. */
function nombreHojaLibre(nombre, correo) {
  const limpio = nombre.replace(/[:\\\/?*\[\]]/g, '').trim().substring(0, 90) || correo;
  if (!libroAsistencia().getSheetByName(limpio)) return limpio;
  return limpio + ' (' + correo.split('@')[0] + ')';
}

function textoFecha(valor) {
  return valor instanceof Date ? Utilities.formatDate(valor, CONFIG.ZONA_HORARIA, 'yyyy-MM-dd') : String(valor);
}

function textoHora(valor) {
  return valor instanceof Date ? Utilities.formatDate(valor, CONFIG.ZONA_HORARIA, 'HH:mm:ss') : String(valor);
}

function aMinutos(hora) {
  const partes = String(hora).split(':');
  return Number(partes[0]) * 60 + Number(partes[1] || 0);
}

function textoDuracion(minutos) {
  const m = Math.round(minutos);
  return Math.floor(m / 60) + ':' + ('0' + (m % 60)).slice(-2);
}

/** Fecha (yyyy-MM-dd) de hoy más/menos [dias]. */
function fechaDesplazada(dias) {
  return Utilities.formatDate(new Date(Date.now() + dias * 86400000), CONFIG.ZONA_HORARIA, 'yyyy-MM-dd');
}

function distanciaMetros(lat1, lon1, lat2, lon2) {
  const R = 6371000;
  const rad = x => x * Math.PI / 180;
  const dLat = rad(lat2 - lat1);
  const dLon = rad(lon2 - lon1);
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(rad(lat1)) * Math.cos(rad(lat2)) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(a));
}

/** Devuelve la hoja; si no existe la crea, y si le faltan columnas nuevas agrega sus encabezados. */
function obtenerHoja(nombre, encabezados) {
  const libro = libroAsistencia();
  let hoja = libro.getSheetByName(nombre);
  // Pestaña que cambió de nombre (por ejemplo "Docentes" → "Personal"): se renombra y conserva todo.
  if (!hoja && nombre === DOCENTES.nombre && libro.getSheetByName(DOCENTES.nombreAnterior)) {
    hoja = libro.getSheetByName(DOCENTES.nombreAnterior).setName(nombre);
  }
  if (!hoja) {
    hoja = libro.insertSheet(nombre);
    hoja.appendRow(encabezados);
    hoja.setFrozenRows(1);
  } else if (hoja.getLastColumn() < encabezados.length) {
    hoja.getRange(1, 1, 1, encabezados.length).setValues([encabezados]);
  } else {
    return hoja;
  }
  hoja.getRange(1, 1, 1, encabezados.length).setFontWeight('bold');
  ajustarColumnas(hoja);
  return hoja;
}

/**
 * Ajusta el ancho de cada columna a su contenido, entre 70 y 320 píxeles (para que los enlaces largos de ubicación
 * no ocupen toda la pantalla). Se hace al crear cada pestaña y cada hora con la actualización de los reportes.
 */
function ajustarColumnas(hoja) {
  const n = hoja ? hoja.getLastColumn() : 0;
  if (n < 1) return;
  hoja.autoResizeColumns(1, n);
  for (let c = 1; c <= n; c++) {
    const ancho = hoja.getColumnWidth(c);
    if (ancho > 320) hoja.setColumnWidth(c, 320);
    else if (ancho < 70) hoja.setColumnWidth(c, 70);
  }
}

function buscarFila(hoja, columna, valor) {
  const valores = hoja.getDataRange().getValues();
  for (let i = 1; i < valores.length; i++) {
    if (String(valores[i][columna]).toLowerCase() === valor) return { numero: i + 1, valores: valores[i] };
  }
  return null;
}

/**
 * Respuesta para la app. Siempre lleva la hora del servidor ("horaServidor", en milisegundos): con ella la app pone
 * en hora su propio reloj, que no depende de la hora que la persona ponga en el teléfono.
 */
function json(obj) {
  if (obj && typeof obj === 'object') obj.horaServidor = Date.now();
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}

// ---------------------------------------------------------------- Bitácora (pruebas y auditoría)

const BITACORA = {
  nombre: 'Bitácora',
  encabezados: ['Fecha y hora', 'Correo', 'Nombre', 'Rol', 'Desde', 'Acción', 'Detalle', 'Resultado'],
};

const NOMBRES_ACCION = {
  registrarDocente: 'Crear cuenta', iniciarSesion: 'Iniciar sesión', recuperar: 'Recuperar cuenta', perfil: 'Abrir la app',
  ranking: 'Ver ranking', misMarcas: 'Ver su historial', liberarMiTelefono: 'Liberar su teléfono',
  misSolicitudes: 'Ver sus solicitudes', crearSolicitud: 'Pedir permiso o vacaciones', cancelarSolicitud: 'Cancelar su solicitud',
  adminHoy: 'Ver Hoy', adminMiembros: 'Ver miembros', adminAsignar: 'Cambiar rol o jornada',
  adminEstado: 'Activar o desactivar miembro', adminEliminarMiembro: 'Eliminar miembro', adminLiberar: 'Liberar teléfono de un miembro',
  adminJornadas: 'Ver jornadas', adminGuardarJornada: 'Guardar jornada', adminEliminarJornada: 'Eliminar jornada',
  adminEventos: 'Ver calendario', adminAgregarEvento: 'Agregar al calendario', adminEliminarEvento: 'Eliminar del calendario',
  adminVistaVacaciones: 'Ver días de vacaciones',
  adminActividades: 'Ver por revisar', adminRevisarActividad: 'Revisar actividad especial',
  adminSolicitudes: 'Ver solicitudes', adminRevisarSolicitud: 'Revisar solicitud de permiso',
  adminReporte: 'Ver reporte del mes', adminResumenAnual: 'Ver resumen anual', adminActualizarReportes: 'Actualizar pestañas de la hoja',
  adminConfig: 'Ver ajustes', adminGuardarReglas: 'Guardar reglas (meta y tolerancia)', adminAgregarSede: 'Agregar ubicación del colegio',
  adminEditarSede: 'Editar ubicación del colegio', adminEliminarSede: 'Quitar ubicación del colegio',
  adminGuardarVerificacion: 'Cambiar verificación (ubicación, Wi-Fi o QR)', adminSecretoQr: 'Mostrar QR de la entrada',
  adminRoles: 'Ver roles', adminGuardarRol: 'Guardar rol', adminEliminarRol: 'Eliminar rol',
};

// Consultas: solo se anotan si fallan (si no, la actualización automática llenaría la bitácora).
const SOLO_CONSULTA = ['recuperar', 'perfil', 'ranking', 'misMarcas', 'misSolicitudes', 'adminHoy', 'adminMiembros',
  'adminJornadas', 'adminEventos', 'adminActividades', 'adminSolicitudes', 'adminReporte', 'adminResumenAnual', 'adminConfig',
  'adminRoles', 'adminVistaVacaciones'];

/**
 * Anota en la pestaña "Bitácora" quién hizo qué, desde dónde y si salió bien o con qué error.
 * El super administrador nunca se anota. Nunca impide la acción aunque falle al anotar.
 */
function anotarBitacora(datos, ok, respuesta) {
  try {
    if (!CONFIG.BITACORA || !datos || !datos.accion) return;
    const accion = String(datos.accion);
    if (ok && SOLO_CONSULTA.indexOf(accion) >= 0) return;
    const correo = String(datos.correo || '').trim().toLowerCase();
    if (esSuperAdminFijo(correo)) return;
    const personal = libroAsistencia().getSheetByName(DOCENTES.nombre);
    const fila = correo && personal ? buscarFila(personal, DOC.CORREO, correo) : null;
    const m = fila ? miembroDe(fila.valores) : null;
    if (m && esSuperAdmin(m.rol)) return;
    const hoja = obtenerHoja(BITACORA.nombre, BITACORA.encabezados);
    const resultado = ok
      ? '✅ Correcto' + (respuesta && respuesta.revision ? ' (quedó por revisar: ' + respuesta.revision + ')' : '')
      : '❌ ' + ((respuesta && respuesta.mensaje) || 'Error');
    hoja.appendRow([Utilities.formatDate(new Date(), CONFIG.ZONA_HORARIA, 'dd/MM/yyyy HH:mm:ss'), correo,
      m ? m.nombre : String(datos.nombre || ''), m ? m.rolNombre : '', origenBitacora(datos.modelo), nombreAccionBitacora(datos),
      detalleBitacora(datos), resultado]);
    const maximo = CONFIG.BITACORA_MAXIMO || 3000;
    if (hoja.getLastRow() > maximo + 200) hoja.deleteRows(2, hoja.getLastRow() - 1 - maximo);
  } catch (e) {
    // La bitácora nunca debe impedir que la acción funcione.
  }
}

function origenBitacora(modelo) {
  const m = String(modelo || '');
  if (!m) return '';
  return /\(web\)/i.test(m) ? 'Web · ' + m.replace(/\s*\(web\)/i, '') : 'Android · ' + m;
}

function nombreAccionBitacora(d) {
  if (d.accion === 'registrar') {
    const marca = d.marca || {};
    return 'Marcar ' + (marca.tipo === 'SALIDA' ? 'salida' : 'entrada') + (marca.actividad ? ' (actividad especial)' : '');
  }
  return NOMBRES_ACCION[d.accion] || String(d.accion);
}

/** Resumen corto de lo que se pidió (a quién, qué rol o jornada, qué fechas…). */
function detalleBitacora(d) {
  const partes = [];
  const nombreDe = correo => {
    const personal = libroAsistencia().getSheetByName(DOCENTES.nombre);
    const f = personal && correo ? buscarFila(personal, DOC.CORREO, String(correo).toLowerCase()) : null;
    return f ? String(f.valores[DOC.NOMBRE]) : String(correo || '');
  };
  if (d.objetivo) partes.push('a ' + nombreDe(d.objetivo));
  if (typeof d.rol === 'string' && d.rol) partes.push('rol ' + d.rol);
  if (d.rol && typeof d.rol === 'object') {
    partes.push('rol "' + (d.rol.nombre || '') + '"' + (d.rol.marca ? ', marca' : ', no marca') +
      ((d.rol.permisos || []).length ? ', funciones: ' + d.rol.permisos.join(', ') : ', sin funciones'));
  }
  if (typeof d.jornada === 'string' && d.jornada) partes.push('jornada ' + d.jornada);
  if (d.jornada && typeof d.jornada === 'object') {
    partes.push('jornada "' + (d.jornada.nombre || '') + '" ' + (d.jornada.entrada || '') + '–' + (d.jornada.salida || '') +
      ' días ' + (d.jornada.dias || []).join(',') + (d.jornada.almuerzo ? ' almuerzo ' + d.jornada.almuerzo + ' min' : ''));
  }
  if (d.estado) partes.push('estado ' + d.estado);
  if (d.aprobar === true || d.aprobar === false) partes.push(d.aprobar ? 'aprobada' : 'rechazada');
  if (d.respuesta) partes.push('respuesta: ' + d.respuesta);
  if (d.evento) {
    const e = d.evento;
    partes.push((TIPOS_EVENTO[e.tipo] || e.tipo) + ' ' + e.desde + (e.hasta && e.hasta !== e.desde ? ' al ' + e.hasta : '') +
      (e.horaInicio ? ' ' + e.horaInicio + '–' + e.horaFin : '') + (e.horaEntrada ? ' entrada ' + e.horaEntrada : '') +
      (e.horaSalida ? ' salida ' + e.horaSalida : '') + ' · para ' + e.aplicaA);
  }
  if (d.solicitud) {
    const s = d.solicitud;
    partes.push((s.tipo === 'VACACIONES' ? 'Vacaciones' : 'Permiso') + ' ' + s.desde + (s.hasta && s.hasta !== s.desde ? ' al ' + s.hasta : '') +
      (s.horaInicio ? ' ' + s.horaInicio + '–' + s.horaFin : '') + ' · ' + (s.motivo || ''));
  }
  if (d.sede) partes.push('ubicación "' + (d.sede.nombre || '') + '" radio ' + d.sede.radio + ' m');
  if (d.metodo && d.accion === 'adminGuardarVerificacion') partes.push('método ' + d.metodo);
  if (d.horasMeta !== undefined) partes.push('meta ' + d.horasMeta + ' h, tolerancia ' + d.tolerancia + ' min');
  if (d.mes) partes.push('mes ' + d.mes);
  if (d.anio) partes.push('año ' + d.anio);
  if (d.codigo) partes.push('código ' + d.codigo);
  if (d.marca && d.marca.observacion) partes.push('observación: ' + d.marca.observacion);
  if (d.accion === 'registrarDocente' && d.nombre) partes.push('nombre ' + d.nombre);
  return partes.join(' · ').substring(0, 400);
}
