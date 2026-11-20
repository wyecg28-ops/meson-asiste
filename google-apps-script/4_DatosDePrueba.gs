/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 *
 * TEMPORAL — Datos de prueba para ver la app y los reportes con información.
 *   cargarDatosDePrueba()  → llena las últimas dos semanas: marcas, llegadas tarde, salidas anticipadas, horas
 *                            extra, una falta, actividades especiales, feriado, horario especial, vacaciones,
 *                            permisos (aprobados, pendientes y rechazados) y marcas rechazadas.
 *   borrarDatosDePrueba()  → limpieza final: quita los datos cargados y TODAS las cuentas de prueba (correos con
 *                            ".prueba@"), con sus hojas y todo lo que dejaron. El super administrador no se toca.
 * Cuando terminen las pruebas: ejecutar borrarDatosDePrueba y después eliminar este archivo del proyecto.
 */

const CLAVE_DATOS_PRUEBA = 'DATOS_PRUEBA';

/** Personas inventadas (no tienen teléfono: no pueden iniciar sesión). */
const PERSONAS_PRUEBA = [
  { correo: 'ana.lopez.prueba@colegiomeson.edu.gt', nombre: 'Ana López', rol: 'DOCENTE', jornada: 'A', perfil: 'puntual' },
  { correo: 'carlos.mendez.prueba@colegiomeson.edu.gt', nombre: 'Carlos Méndez', rol: 'ADMINISTRATIVO', jornada: 'B', perfil: 'tarde' },
  { correo: 'maria.perez.prueba@colegiomeson.edu.gt', nombre: 'María Pérez', rol: 'APOYO', jornada: 'A', perfil: 'extra' },
  { correo: 'luis.garcia.prueba@colegiomeson.edu.gt', nombre: 'Luis García', rol: 'DOCENTE', jornada: 'A', perfil: 'irregular' },
];

function cargarDatosDePrueba() {
  if (PropertiesService.getScriptProperties().getProperty(CLAVE_DATOS_PRUEBA)) {
    throw new Error('Ya hay datos de prueba cargados. Ejecuta primero borrarDatosDePrueba.');
  }
  const libro = libroAsistencia();
  const registro = { personas: [], hojas: [], filas: {}, eventos: [], solicitudes: [], actividades: [], rechazados: [] };
  const guardarRegistro = () => PropertiesService.getScriptProperties().setProperty(CLAVE_DATOS_PRUEBA, JSON.stringify(registro));
  let semilla = 7;
  const azar = (min, max) => { semilla = (semilla * 9301 + 49297) % 233280; return min + Math.floor(semilla / 233280 * (max - min + 1)); };

  // Días laborales de las últimas dos semanas (sin hoy).
  const dias = [];
  for (let i = 14; i >= 1; i--) {
    const f = fechaDesplazada(-i);
    if (diaIso(f) <= 5) dias.push(f);
  }
  const hace = n => fechaDesplazada(-n);

  // 1. Personas inventadas, registradas el primer día de los datos (para que no aparezcan faltas antes).
  const personal = obtenerHoja(DOCENTES.nombre, DOCENTES.encabezados);
  PERSONAS_PRUEBA.forEach(p => {
    if (buscarFila(personal, DOC.CORREO, p.correo)) return;
    const hoja = nombreHojaLibre(p.nombre, p.correo);
    personal.appendRow([p.correo, p.nombre, hoja, '', '', '', new Date(dias[0] + 'T06:00:00'), p.rol, p.jornada, ESTADOS.ACTIVO]);
    obtenerHoja(hoja, ENCABEZADOS_DOCENTE);
    registro.personas.push(p.correo);
    registro.hojas.push(hoja);
  });
  guardarRegistro();
  _jornadas = null;

  // Quienes marcan: las inventadas y las cuentas de prueba que marcan asistencia (docente y supervisor).
  const miembros = leerMiembros().filter(m => marcaAsistencia(m.rol) &&
    (registro.personas.indexOf(m.correo) >= 0 || /\.prueba@/.test(m.correo)));
  const perfilDe = m => (PERSONAS_PRUEBA.filter(p => p.correo === m.correo)[0] || {}).perfil ||
    (/supervisor/.test(m.correo) ? 'puntual' : 'tarde');
  const ana = PERSONAS_PRUEBA[0].correo, carlos = PERSONAS_PRUEBA[1].correo, maria = PERSONAS_PRUEBA[2].correo,
    luis = PERSONAS_PRUEBA[3].correo;
  const docente = (miembros.filter(m => /^docente\.prueba@/.test(m.correo))[0] || {}).correo;
  const supervisor = (miembros.filter(m => /^supervisor\.prueba@/.test(m.correo))[0] || {}).correo;

  // 2. Calendario: un asueto para todos y un horario especial para la jornada A.
  const asueto = dias[dias.length - 4];
  const especial = dias[dias.length - 2];
  registro.eventos.push(agregarEvento('Datos de prueba', { tipo: 'FERIADO', desde: asueto, hasta: asueto, aplicaA: 'Todos',
    detalle: 'Asueto por actividad del colegio' }));
  registro.eventos.push(agregarEvento('Datos de prueba', { tipo: 'ENTRADA_ESPECIAL', desde: especial, hasta: especial,
    aplicaA: [ana, maria, luis].join(','), horaEntrada: '08:00', horaSalida: '13:00', detalle: 'Reunión de padres de familia' }));

  // 3. Solicitudes: aprobadas (van al calendario), pendientes y rechazadas.
  const hojaSol = obtenerHoja(SOLICITUDES.nombre, SOLICITUDES.encabezados);
  const solicitud = (correo, tipo, desde, hasta, inicio, fin, motivo, estado, respuesta) => {
    const m = miembros.filter(x => x.correo === correo)[0];
    if (!m) return;
    const id = Utilities.getUuid();
    let evento = '';
    if (estado === ESTADOS_SOLICITUD.APROBADA) {
      evento = agregarEvento('Administrador Prueba', { tipo: tipo, desde: desde, hasta: hasta, aplicaA: correo,
        horaInicio: inicio, horaFin: fin, detalle: 'Solicitud aprobada: ' + motivo });
      registro.eventos.push(evento);
    }
    hojaSol.appendRow([id, new Date(), correo, m.nombre, tipo, "'" + desde, "'" + hasta, inicio ? "'" + inicio : '',
      fin ? "'" + fin : '', motivo, estado, respuesta || '', estado === ESTADOS_SOLICITUD.PENDIENTE ? '' : 'Administrador Prueba',
      estado === ESTADOS_SOLICITUD.PENDIENTE ? '' : new Date(), evento]);
    registro.solicitudes.push(id);
  };
  solicitud(maria, 'VACACIONES', dias[3], dias[4], '', '', 'Viaje familiar', ESTADOS_SOLICITUD.APROBADA, 'Que lo disfrutes.');
  solicitud(carlos, 'PERMISO', dias[5], dias[5], '09:30', '11:30', 'Cita médica', ESTADOS_SOLICITUD.APROBADA, '');
  if (docente) solicitud(docente, 'PERMISO', dias[7], dias[7], '', '', 'Trámite personal', ESTADOS_SOLICITUD.APROBADA, 'Aprobado.');
  solicitud(carlos, 'PERMISO', dias[1], dias[1], '', '', 'Asunto personal', ESTADOS_SOLICITUD.RECHAZADA, 'Ese día hay entrega de notas.');
  solicitud(ana, 'PERMISO', fechaDesplazada(5), fechaDesplazada(5), '13:00', '15:00', 'Graduación de mi hijo', ESTADOS_SOLICITUD.PENDIENTE, '');
  if (supervisor) solicitud(supervisor, 'VACACIONES', fechaDesplazada(30), fechaDesplazada(34), '', '', 'Vacaciones de fin de año', ESTADOS_SOLICITUD.PENDIENTE, '');
  guardarRegistro();

  // 4. Marcas de cada día según el estilo de cada persona.
  const eventos = leerEventos();
  const sede = leerSedes()[0];
  const conUbicacion = sede && isFinite(sede.latitud) && isFinite(sede.longitud);
  const mapa = () => conUbicacion ? 'https://maps.google.com/?q=' + (sede.latitud + azar(-20, 20) / 100000).toFixed(6) + ',' +
    (sede.longitud + azar(-20, 20) / 100000).toFixed(6) : '';
  const hora = min => { min = ((min % 1440) + 1440) % 1440; return ('0' + Math.floor(min / 60)).slice(-2) + ':' + ('0' + (min % 60)).slice(-2) + ':' + ('0' + azar(0, 59)).slice(-2); };
  const hojaAct = obtenerHoja(ACTIVIDADES.nombre, ACTIVIDADES.encabezados);
  const hojaRech = obtenerHoja(RECHAZADOS.nombre, RECHAZADOS.encabezados);

  miembros.forEach(m => {
    const hoja = libro.getSheetByName(m.hoja) || obtenerHoja(m.hoja, ENCABEZADOS_DOCENTE);
    const existentes = filasDeMiembro(libro, m);
    const estilo = perfilDe(m);
    const jornada = jornadaJson(m.jornada);
    const filas = [];
    dias.forEach((fecha, i) => {
      if (existentes[fecha] || diaLibre(m, fecha, eventos)) return;
      if (estilo === 'irregular' && i === 2) return; // una falta
      const esp = horarioEspecial(m, fecha, eventos);
      const h = esp ? { entrada: esp.horaEntrada, salida: esp.horaSalida || horarioDelDia(jornada, diaIso(fecha)).salida }
        : horarioDelDia(jornada, diaIso(fecha));
      const E = aMinutos(h.entrada), S = E + duracionHorario(h.entrada, h.salida);
      let entrada = E - azar(2, 14), salida = S + azar(0, 20), obs = '', actividad = false, sinSalida = false;
      if (estilo === 'tarde' && i % 3 !== 2) entrada = E + azar(8, 35);
      if (estilo === 'extra' && i % 2 === 0) salida = S + azar(60, 110);
      if (estilo === 'irregular') {
        if (i % 4 === 1) { entrada = E + azar(10, 50); obs = 'Tráfico'; }
        if (i % 4 === 3) { salida = S - azar(30, 70); obs = 'Salida: cita médica'; }
        if (i === dias.length - 3) sinSalida = true;
      }
      if (estilo === 'puntual' && i === 4) { actividad = true; obs = 'Actividad especial (entrada): Excursión con 5.º grado'; }
      if (m.correo === docente && i === 1) { actividad = true; obs = 'Actividad especial (entrada): Capacitación docente'; }
      const ubic = mapa();
      filas.push({ fecha: fecha, valores: ["'" + fecha, "'" + hora(entrada), m.correo, sinSalida ? '' : "'" + hora(salida), obs,
        ubic, sinSalida ? '' : mapa(), jornada.nombre, '', '', actividad ? 'Sí' : '', actividad ? POR_REVISAR : '', '', '', '', '', ''],
        obs: obs, actividad: actividad });
      if (actividad) {
        const id = Utilities.getUuid();
        const revisada = m.correo === docente; // la de la docente ya aprobada; la de Ana por revisar
        hojaAct.appendRow([id, "'" + fecha, m.correo, m.nombre, "'" + hora(entrada).substring(0, 5),
          "'" + hora(salida).substring(0, 5), obs.replace(/^Actividad especial \(entrada\): /, ''), ubic, '',
          revisada ? 'Aprobada' : POR_REVISAR, revisada ? 'Administrador Prueba' : '', revisada ? new Date() : '']);
        registro.actividades.push(id);
        if (revisada) filas[filas.length - 1].valores[COL.REVISION - 1] = 'Aprobada';
      }
    });
    if (!filas.length) return;
    const inicio = hoja.getLastRow() + 1;
    hoja.getRange(inicio, 1, filas.length, ENCABEZADOS_DOCENTE.length).setValues(filas.map(f => f.valores));
    filas.forEach((f, k) => {
      recalcularFila(hoja, inicio + k, m, eventos);
      hoja.getRange(inicio + k, COL.OBSERVACION).setValue(f.obs); // sin la nota de "corregido"
    });
    // Ordena la hoja por fecha (los días de prueba quedan antes que los reales).
    const rango = hoja.getRange(2, 1, hoja.getLastRow() - 1, ENCABEZADOS_DOCENTE.length);
    // Fechas y horas siguen como texto (si no, Sheets las convierte al volver a escribirlas).
    const comoTexto = c => typeof c === 'string' && /^\d{1,4}[-:]\d/.test(c) ? "'" + c : c;
    rango.setValues(rango.getValues()
      .sort((a, b) => textoFecha(a[COL.FECHA - 1]).localeCompare(textoFecha(b[COL.FECHA - 1])))
      .map(fila => fila.map(comoTexto)));
    registro.filas[m.hoja] = filas.map(f => f.fecha);
    guardarRegistro();
  });

  // 5. Dos marcas rechazadas (fuera del colegio).
  [[luis, dias[6], '07:02:11', 'Entrada', 1709], [carlos, dias[8], '09:41:05', 'Entrada', 860]].forEach(r => {
    hojaRech.appendRow(["'" + r[1], "'" + r[2], r[0], r[3], 'Estás a ' + r[4] + ' m de ' + (sede ? sede.nombre : 'el colegio') +
      '. Debes marcar dentro del establecimiento (o usar "Actividad especial" si estás en una).', mapa(), r[4]]);
    registro.rechazados.push(r[0] + '|' + r[1]);
  });
  guardarRegistro();

  limpiarCacheReportes();
  refrescarPestanas();
  Logger.log('Datos de prueba cargados: ' + miembros.length + ' personas, ' + dias.length + ' días laborales (' +
    dias[0] + ' a ' + dias[dias.length - 1] + '). Para quitarlos: borrarDatosDePrueba.');
}

/** Correo de una cuenta de prueba (admin.prueba@, docente.prueba@, ana.lopez.prueba@…). Nunca el super administrador. */
function esCorreoDePrueba(correo) {
  const c = String(correo || '').trim().toLowerCase();
  return !!c && !esSuperAdminFijo(c) && /\.prueba\.?@/.test(c);
}

/**
 * Limpieza final de las pruebas: quita los datos cargados y TODAS las cuentas de prueba con todo lo que dejaron
 * (fila en Personal, hoja personal, solicitudes, actividades, marcas rechazadas, calendario y bitácora).
 * El super administrador no se toca: no está en la hoja y su registro interno se queda como está.
 * Después hay que quitar admin.prueba y supervisor.prueba de CONFIG (1_Configuracion.gs) y publicar.
 */
function borrarDatosDePrueba() {
  const props = PropertiesService.getScriptProperties();
  const r = JSON.parse(props.getProperty(CLAVE_DATOS_PRUEBA) || '{"eventos":[],"solicitudes":[],"actividades":[]}');
  const libro = libroAsistencia();
  const borrarDonde = (hoja, condicion) => {
    if (!hoja || hoja.getLastRow() < 2) return 0;
    const valores = hoja.getDataRange().getValues();
    let n = 0;
    for (let i = valores.length - 1; i >= 1; i--) if (condicion(valores[i])) { hoja.deleteRow(i + 1); n++; }
    return n;
  };

  // 1. Cuentas de prueba: su hoja personal y su fila en Personal.
  const personal = obtenerHoja(DOCENTES.nombre, DOCENTES.encabezados);
  const cuentas = personal.getDataRange().getValues().slice(1).filter(v => esCorreoDePrueba(v[DOC.CORREO]));
  cuentas.forEach(v => {
    const hoja = v[DOC.HOJA] && libro.getSheetByName(String(v[DOC.HOJA]));
    if (hoja && libro.getSheets().length > 1) libro.deleteSheet(hoja);
  });
  borrarDonde(personal, v => esCorreoDePrueba(v[DOC.CORREO]));

  // 2. Lo que dejaron en las demás pestañas.
  borrarDonde(libro.getSheetByName(SOLICITUDES.nombre), v =>
    esCorreoDePrueba(v[SOL.CORREO]) || (r.solicitudes || []).indexOf(String(v[SOL.ID])) >= 0);
  borrarDonde(libro.getSheetByName(ACTIVIDADES.nombre), v =>
    esCorreoDePrueba(v[ACT.CORREO]) || (r.actividades || []).indexOf(String(v[ACT.ID])) >= 0);
  borrarDonde(libro.getSheetByName(RECHAZADOS.nombre), v => esCorreoDePrueba(v[2]));
  borrarDonde(libro.getSheetByName(BITACORA.nombre), v => esCorreoDePrueba(v[1]));

  // 3. Calendario: los eventos de prueba se borran; en los que son para varias personas, solo se quitan los correos
  //    de prueba (y si no queda nadie, se borra el evento).
  const calendario = libro.getSheetByName(CALENDARIO.nombre);
  borrarDonde(calendario, v => {
    if ((r.eventos || []).indexOf(String(v[CAL.ID])) >= 0) return true;
    const lista = String(v[CAL.APLICA] || 'Todos').split(',').map(c => c.trim()).filter(String);
    return lista.length > 0 && lista.every(esCorreoDePrueba);
  });
  if (calendario && calendario.getLastRow() > 1) {
    const valores = calendario.getRange(2, CAL.APLICA + 1, calendario.getLastRow() - 1, 1).getValues();
    valores.forEach((v, i) => {
      const lista = String(v[0] || '').split(',').map(c => c.trim()).filter(String);
      const quedan = lista.filter(c => !esCorreoDePrueba(c));
      if (quedan.length !== lista.length) calendario.getRange(i + 2, CAL.APLICA + 1).setValue(quedan.join(', '));
    });
  }

  props.deleteProperty(CLAVE_DATOS_PRUEBA);
  limpiarCacheReportes();
  refrescarPestanas();
  Logger.log('Pruebas borradas: ' + cuentas.length + ' cuentas de prueba (' + cuentas.map(v => v[DOC.CORREO]).join(', ') +
    ') y todos sus registros. El super administrador no se tocó. Falta quitar admin.prueba y supervisor.prueba de ' +
    'CONFIG en 1_Configuracion.gs y publicar una nueva versión.');
}
