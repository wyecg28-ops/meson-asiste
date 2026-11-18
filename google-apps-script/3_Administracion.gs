/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 */
/**
 * Mesón Asiste — parte 3 de 3: panel de administración, reporte mensual, ajustes y utilidades.
 */

// ---------------------------------------------------------------- Administración

function administrar(admin, d) {
  switch (d.accion) {
    case 'adminHoy': return adminHoy();
    case 'adminMiembros': return {
      ok: true,
      miembros: leerMiembros(),
      roles: Object.keys(leerRoles()).map(rolJson),
      jornadas: Object.keys(leerJornadas()).map(jornadaJson),
    };
    case 'adminAsignar': return adminAsignar(admin, d);
    case 'adminEstado': return adminEstado(admin, d);
    case 'adminEliminarMiembro': return adminEliminarMiembro(admin, d);
    case 'adminLiberar': return adminLiberar(d);
    case 'adminJornadas': return { ok: true, jornadas: Object.keys(leerJornadas()).map(jornadaJson), enUso: jornadasEnUso() };
    case 'adminGuardarJornada': return adminGuardarJornada(d.jornada || {});
    case 'adminEliminarJornada': return adminEliminarJornada(d.codigo);
    case 'adminEventos': return { ok: true, eventos: leerEventos().filter(e => e.hasta >= fechaDesplazada(-30)) };
    case 'adminAgregarEvento': return adminAgregarEvento(admin, d.evento || {});
    case 'adminEliminarEvento': return adminEliminarEvento(d.id);
    case 'adminActividades': return adminActividades();
    case 'adminRevisarActividad': return adminRevisarActividad(admin, d);
    case 'adminSolicitudes': return { ok: true, solicitudes: leerSolicitudes().filter(s => s.estado !== 'CANCELADA').slice(0, 80) };
    case 'adminRevisarSolicitud': return adminRevisarSolicitud(admin, d);
    case 'adminReporte': return adminReporte(d);
    case 'adminConfig': return adminConfig();
    case 'adminGuardarReglas': return adminGuardarReglas(d);
    case 'adminAgregarSede': return adminAgregarSede(d.sede || {});
    case 'adminEditarSede': return adminEditarSede(d.sede || {});
    case 'adminEliminarSede': return adminEliminarSede(d.id);
    case 'adminGuardarVerificacion': return adminGuardarVerificacion(d);
    case 'adminSecretoQr': return adminSecretoQr(d.id);
    case 'adminRoles': return adminRoles();
    case 'adminGuardarRol': return adminGuardarRol(d.rol || {});
    case 'adminEliminarRol': return adminEliminarRol(d.codigo);
    default: throw new Error('Acción no válida.');
  }
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
      horas = String(v[COL.HORAS - 1] || '');
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
  if (d.rol) {
    const roles = leerRoles();
    if (!roles[d.rol]) throw new Error('Rol no válido.');
    const actual = miembroDe(m.fila.valores).rol;
    if ((esGestor(d.rol) || esGestor(actual)) && !puede(admin.rol, 'ROLES')) {
      throw new Error('Solo el administrador puede dar o quitar roles con funciones de administración.');
    }
    if (m.correo === admin.correo && d.rol !== 'ADMINISTRADOR') {
      throw new Error('No puedes quitarte tu propio rol de administrador; pídeselo a otro administrador.');
    }
    if (esAdministradorFijo(m.correo) && d.rol !== 'ADMINISTRADOR') {
      throw new Error(m.correo + ' es administrador fijo (CONFIG.ADMINISTRADORES en el script).');
    }
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
  const estado = d.estado === 'INACTIVO' ? 'INACTIVO' : 'ACTIVO';
  if (estado === 'INACTIVO' && m.correo === admin.correo) throw new Error('No puedes desactivarte a ti mismo.');
  if (estado === 'INACTIVO' && esAdministradorFijo(m.correo)) throw new Error('El administrador fijo no se puede desactivar.');
  m.hoja.getRange(m.fila.numero, DOC.ESTADO + 1).setValue(ESTADOS[estado]);
  return { ok: true, mensaje: m.fila.valores[DOC.NOMBRE] + (estado === 'ACTIVO' ? ' está activo.' : ' quedó desactivado.') };
}

/** Elimina a un miembro: se le cierra el acceso y deja de aparecer. Su hoja de asistencia se conserva. */
function adminEliminarMiembro(admin, d) {
  const m = filaMiembro(d.objetivo);
  if (m.correo === admin.correo) throw new Error('No puedes eliminarte a ti mismo.');
  if (esAdministradorFijo(m.correo)) throw new Error('El administrador fijo no se puede eliminar.');
  m.hoja.getRange(m.fila.numero, DOC.ESTADO + 1).setValue(ESTADOS.ELIMINADO);
  m.hoja.getRange(m.fila.numero, DOC.DISPOSITIVO + 1).setValue('');
  return { ok: true, mensaje: m.fila.valores[DOC.NOMBRE] + ' fue eliminado.' };
}

function adminLiberar(d) {
  const m = filaMiembro(d.objetivo);
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
  const fila = [codigo, nombre, "'" + entrada, "'" + salida, "'" + dias.join(','), porDia.sort().join(';')];
  if (codigo && jornadas[codigo]) {
    hoja.getRange(buscarFila(hoja, 0, codigo.toLowerCase()).numero, 1, 1, 6).setValues([fila]);
  } else {
    const letras = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split('');
    fila[0] = letras.filter(l => !jornadas[l])[0] || ('J' + Date.now());
    hoja.appendRow(fila);
  }
  _jornadas = null;
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
  return { ok: true, mensaje: TIPOS_EVENTO[e.tipo] + ' agregado al calendario.' };
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
  return { ok: true, mensaje: aprobar ? 'Solicitud aprobada y agregada al calendario.' : 'Solicitud rechazada.' };
}

function adminActividades() {
  const valores = obtenerHoja(ACTIVIDADES.nombre, ACTIVIDADES.encabezados).getDataRange().getValues();
  const pendientes = valores.slice(1).filter(v => v[ACT.ID] && v[ACT.ESTADO] === POR_REVISAR).map(v => ({
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
  const partes = mes.split('-').map(Number);
  const diasMes = new Date(partes[0], partes[1], 0).getDate();
  const hoy = fechaDesplazada(0);
  const eventos = leerEventos();
  const libro = libroAsistencia();
  const resumen = [];
  const detalle = [];

  leerMiembros().filter(m => marcaAsistencia(m.rol)).forEach(m => {
    const jornada = jornadaJson(m.jornada);
    const filas = {};
    const hoja = libro.getSheetByName(m.hoja);
    if (hoja && hoja.getLastRow() > 1) {
      hoja.getRange(2, 1, hoja.getLastRow() - 1, ENCABEZADOS_DOCENTE.length).getValues().forEach(v => {
        const f = textoFecha(v[COL.FECHA - 1]);
        if (f.indexOf(mes) === 0) filas[f] = v;
      });
    }
    const r = { nombre: m.nombre, rol: m.rolNombre, jornada: jornada.nombre, estado: ESTADOS[m.estado],
      dias: 0, puntuales: 0, tardes: 0, faltas: 0, actividades: 0, minutos: 0, extra: 0, porRevisar: 0 };
    for (let dia = 1; dia <= diasMes; dia++) {
      const fecha = mes + '-' + ('0' + dia).slice(-2);
      if (fecha > hoy) break;
      const v = filas[fecha];
      if (v) {
        r.dias++;
        const entrada = textoHora(v[COL.ENTRADA - 1]).substring(0, 5);
        const salida = v[COL.SALIDA - 1] ? textoHora(v[COL.SALIDA - 1]).substring(0, 5) : '';
        const esActividad = v[COL.ACTIVIDAD - 1] === 'Sí';
        const revision = String(v[COL.REVISION - 1] || '');
        let estado;
        if (esActividad) { r.actividades++; estado = 'Actividad especial'; }
        else if (aMinutos(entrada) > limiteEntrada(m, fecha, eventos)) { r.tardes++; estado = 'Tarde'; }
        else { r.puntuales++; estado = 'Puntual'; }
        let minutos = 0;
        if (salida) {
          minutos = aMinutos(salida) - aMinutos(entrada);
          if (minutos < 0) minutos += 1440; // turno que pasa la medianoche
          r.minutos += minutos;
          // Horas extra: lo que pasa de la meta del día (solo marcas verificadas).
          if (revision !== POR_REVISAR && revision !== 'Rechazada') r.extra += Math.max(0, minutos - metaDelDia(m, fecha, eventos));
        }
        if (revision === POR_REVISAR) r.porRevisar++;
        detalle.push({ nombre: m.nombre, fecha: fecha, entrada: entrada, salida: salida,
          horas: salida ? textoDuracion(minutos) : '', estado: estado, revision: revision || 'No aplica' });
      } else if (fecha < hoy && fecha >= m.registrado && esDiaLaboral(m, fecha) && !diaLibre(m, fecha, eventos)) {
        r.faltas++;
        detalle.push({ nombre: m.nombre, fecha: fecha, entrada: '', salida: '', horas: '', estado: 'Falta', revision: '' });
      }
    }
    resumen.push(r);
  });
  resumen.sort((a, b) => String(a.nombre).localeCompare(String(b.nombre)));
  detalle.sort((a, b) => String(a.nombre).localeCompare(String(b.nombre)) || a.fecha.localeCompare(b.fecha));
  return { ok: true, mes: mes, resumen: resumen, detalle: detalle };
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
function adminRoles() {
  const enUso = {};
  leerMiembros().forEach(m => { enUso[m.rol] = (enUso[m.rol] || 0) + 1; });
  return {
    ok: true,
    roles: Object.keys(leerRoles()).map(rolJson),
    permisos: Object.keys(PERMISOS).filter(p => p !== 'ROLES').map(p => ({ codigo: p, nombre: PERMISOS[p] })),
    enUso: enUso,
  };
}

/** Crea (sin código) o edita un rol: nombre, si marca asistencia y sus funciones. */
function adminGuardarRol(r) {
  const nombre = String(r.nombre || '').trim();
  if (nombre.length < 2) throw new Error('Escribe el nombre del rol.');
  const roles = leerRoles();
  const repetido = Object.keys(roles).filter(c => roles[c].nombre.toLowerCase() === nombre.toLowerCase() && c !== r.codigo)[0];
  if (repetido) throw new Error('Ya existe un rol llamado "' + nombre + '".');
  const marca = r.marca === true ? 'Sí' : 'No';
  const permisos = (r.permisos || []).map(p => String(p).toUpperCase()).filter(p => PERMISOS[p] && p !== 'ROLES');
  const hoja = obtenerHoja(ROLES_HOJA.nombre, ROLES_HOJA.encabezados);
  let codigo = String(r.codigo || '').trim().toUpperCase();
  if (codigo && roles[codigo]) {
    // El Administrador conserva siempre todas sus funciones; solo cambia el nombre y si marca.
    const funciones = codigo === 'ADMINISTRADOR' ? Object.keys(PERMISOS).join(',') : permisos.join(',');
    const fila = buscarFila(hoja, 0, codigo.toLowerCase());
    hoja.getRange(fila.numero, 1, 1, 4).setValues([[codigo, nombre, marca, funciones]]);
  } else {
    const base = nombre.toUpperCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^A-Z0-9]+/g, '_')
      .replace(/^_|_$/g, '').substring(0, 24) || 'ROL';
    codigo = base;
    for (let i = 2; roles[codigo]; i++) codigo = base + '_' + i;
    hoja.appendRow([codigo, nombre, marca, permisos.join(',')]);
  }
  _roles = null;
  return { ok: true, mensaje: 'Rol "' + nombre + '" guardado.' };
}

function adminEliminarRol(codigo) {
  const roles = leerRoles();
  const c = String(codigo || '').toUpperCase();
  if (!roles[c]) throw new Error('El rol ya no existe.');
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
  return hoja;
}

function buscarFila(hoja, columna, valor) {
  const valores = hoja.getDataRange().getValues();
  for (let i = 1; i < valores.length; i++) {
    if (String(valores[i][columna]).toLowerCase() === valor) return { numero: i + 1, valores: valores[i] };
  }
  return null;
}

function json(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}
