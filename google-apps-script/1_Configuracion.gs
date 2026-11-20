/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 */
/**
 * Servidor de Mesón Asiste — parte 1 de 3: configuración, hojas, jornadas y sedes.
 * Se pega en Extensiones > Apps Script de la hoja de cálculo y se implementa como "Aplicación web".
 *
 * Hojas:
 *  - "Personal" (antes "Docentes"): un registro por miembro (correo, teléfono, rol, jornada y estado). El administrador asigna rol y
 *    jornada, y puede desactivar o eliminar miembros (se les cierra el acceso).
 *  - "Jornadas": horarios de trabajo (entrada, salida y días laborales) que el administrador crea y edita en la app.
 *  - "Configuración": meta de horas y minutos de tolerancia (se cambian desde la app).
 *  - "Sedes": ubicación del colegio (geocerca) y cómo se comprueba la presencia: solo ubicación, Wi-Fi o QR.
 *  - Una hoja por miembro, con su nombre: una fila por día con entrada, salida, horas y observaciones.
 *  - "Calendario": vacaciones, feriados, permisos (por día o por horas) y entradas/salidas especiales.
 *  - "Solicitudes": permisos y vacaciones que el personal pide desde la app; al aprobarse pasan al calendario.
 *  - "Actividades especiales": días por revisar (actividad fuera del colegio, QR o Wi-Fi no verificados).
 *  - "Rechazados": intentos de marcaje no aceptados y su motivo.
 *
 * Roles: se administran en la hoja "Roles" (desde la app, Admin → Roles). Cada rol indica si marca asistencia y qué
 * funciones de administración tiene. El Administrador tiene todas las funciones.
 */

const CONFIG = {
  // ID de la hoja de cálculo. Vacío = se usa la hoja donde está este script (Extensiones → Apps Script) o,
  // si el script no está dentro de una hoja, se crea "Mesón Asiste — Asistencia" en el Drive de quien lo publicó.
  ID_HOJA: '',
  DOMINIO: 'colegiomeson.edu.gt',
  ZONA_HORARIA: 'America/Guatemala',
  // Valores iniciales de la hoja "Configuración" (después se cambian desde la app, en Admin → Ajustes).
  HORAS_META: 8,
  TOLERANCIA_MINUTOS: 0,
  // Jornadas que se crean la primera vez: código, nombre, entrada, salida y días (1 = lunes … 7 = domingo).
  JORNADAS_INICIALES: [
    ['A', 'Jornada A', '07:00', '16:00', '1,2,3,4,5'],
    ['B', 'Jornada B', '09:30', '18:30', '1,2,3,4,5'],
  ],
  // Correo del SUPER ADMINISTRADOR (uno solo). Controla todo y decide qué funciones tiene el Administrador.
  // No se registra: entra con "Iniciar sesión" y este correo en cualquier dispositivo, sin restricciones.
  // No aparece en la hoja (ni en "Personal" ni en "Roles").
  SUPER_ADMINISTRADOR: 'superadmin.31yc0nd3@colegiomeson.edu.gt',
  // Correos que siempre son Administradores (opcional). Lo normal es que el super administrador dé el rol desde
  // Admin → Miembros a quien corresponda.
  ADMINISTRADORES: [],
  // Correos que entran como Supervisor la primera vez que se registran (opcional; el rol se puede cambiar después).
  // Quien no está en ninguna lista entra como Docente.
  SUPERVISORES: [],
  // Se rechazan marcas guardadas en el teléfono hace más de estos días.
  DIAS_MAXIMOS_PENDIENTE: 3,
  // Se rechazan lecturas GPS con un margen de error mayor a este valor (metros).
  PRECISION_MAXIMA: 100,
  // Pestaña "Bitácora": anota cada acción del personal (marcas, permisos, cambios de administración) y los errores.
  // Útil para pruebas y auditoría. false = no se anota nada.
  BITACORA: true,
  // Filas máximas de la bitácora (se borran las más viejas).
  BITACORA_MAXIMO: 3000,
};

/** Funciones de administración que se pueden activar en cada rol. */
const PERMISOS = {
  HOY: 'Ver Hoy',
  SOLICITUDES: 'Revisar solicitudes de permiso',
  MIEMBROS: 'Gestionar miembros',
  JORNADAS: 'Gestionar jornadas',
  CALENDARIO: 'Gestionar calendario',
  REVISION: 'Revisar marcas por revisar',
  REPORTES: 'Ver reportes',
  AJUSTES: 'Ajustes del colegio',
  ROLES: 'Gestionar roles',
};

// Roles que se crean la primera vez: código, nombre, marca asistencia, funciones (separadas por coma).
const ROLES_INICIALES = [
  ['ADMINISTRADOR', 'Administrador', 'No', Object.keys(PERMISOS).join(',')],
  ['SUPERVISOR', 'Supervisor', 'Sí', 'HOY,SOLICITUDES,CALENDARIO,REVISION,REPORTES'],
  ['DOCENTE', 'Docente', 'Sí', ''],
  ['ADMINISTRATIVO', 'Personal administrativo', 'Sí', ''],
  ['APOYO', 'Personal de apoyo', 'Sí', ''],
];
// Roles que no se pueden eliminar.
const ROLES_FIJOS = ['ADMINISTRADOR', 'DOCENTE'];
// Solo el super administrador puede editar estos roles o asignarlos a alguien.
const ROLES_PROTEGIDOS = ['ADMINISTRADOR'];
// Rol interno del super administrador: no está en la hoja "Roles" y no se puede asignar ni editar.
const ROL_SUPERADMIN = { codigo: 'SUPERADMIN', nombre: 'Super administrador', marca: false, permisos: Object.keys(PERMISOS) };
const ROLES_HOJA = { nombre: 'Roles', encabezados: ['Código', 'Nombre', 'Marca asistencia', 'Funciones'] };

const ESTADOS = { ACTIVO: 'Activo', INACTIVO: 'Inactivo', ELIMINADO: 'Eliminado' };

const TIPOS_EVENTO = {
  VACACIONES: 'Vacaciones',
  FERIADO: 'Feriado o asueto',
  PERMISO: 'Permiso',
  ENTRADA_ESPECIAL: 'Entrada y salida especial',
};

const METODOS = { UBICACION: 'Solo ubicación', WIFI: 'Wi-Fi', QR: 'Código QR' };

const DOCENTES = {
  nombre: 'Personal',
  // Nombre que tenía antes: si existe, se renombra sola a "Personal" (conserva todo su contenido).
  nombreAnterior: 'Docentes',
  encabezados: ['Correo', 'Nombre', 'Hoja', 'Dispositivo', 'Modelo', 'Método', 'Registrado el', 'Rol', 'Jornada',
    'Estado'],
};
// Columnas (base 0) de la hoja "Personal".
const DOC = { CORREO: 0, NOMBRE: 1, HOJA: 2, DISPOSITIVO: 3, MODELO: 4, METODO: 5, REGISTRADO: 6, ROL: 7, JORNADA: 8,
  ESTADO: 9 };

const JORNADAS = { nombre: 'Jornadas', encabezados: ['Código', 'Nombre', 'Entrada', 'Salida', 'Días', 'Horario por día'] };
const CONFIGURACION = { nombre: 'Configuración', encabezados: ['Clave', 'Valor', 'Descripción'] };
const SEDES = {
  nombre: 'Sedes',
  encabezados: ['ID', 'Nombre', 'Latitud', 'Longitud', 'Radio (m)', 'Verificación', 'Wi-Fi registrados', 'Clave QR'],
};
const SED = { ID: 0, NOMBRE: 1, LAT: 2, LON: 3, RADIO: 4, METODO: 5, WIFI: 6, SECRETO: 7 };

const CALENDARIO = {
  nombre: 'Calendario',
  encabezados: ['ID', 'Desde', 'Hasta', 'Tipo', 'Aplica a', 'Hora de entrada', 'Detalle', 'Creado por',
    'Permiso desde (hora)', 'Permiso hasta (hora)', 'Hora de salida'],
};
const CAL = { ID: 0, DESDE: 1, HASTA: 2, TIPO: 3, APLICA: 4, HORA: 5, DETALLE: 6, CREADO: 7, H_INICIO: 8, H_FIN: 9,
  H_SALIDA: 10 };

const SOLICITUDES = {
  nombre: 'Solicitudes',
  encabezados: ['ID', 'Creada el', 'Correo', 'Nombre', 'Tipo', 'Desde', 'Hasta', 'Hora inicio', 'Hora fin', 'Motivo',
    'Estado', 'Respuesta', 'Revisada por', 'Revisada el', 'ID evento'],
};
const SOL = { ID: 0, CREADA: 1, CORREO: 2, NOMBRE: 3, TIPO: 4, DESDE: 5, HASTA: 6, H_INICIO: 7, H_FIN: 8, MOTIVO: 9,
  ESTADO: 10, RESPUESTA: 11, REVISOR: 12, REVISADA: 13, EVENTO: 14 };
const ESTADOS_SOLICITUD = { PENDIENTE: 'Pendiente', APROBADA: 'Aprobada', RECHAZADA: 'Rechazada', CANCELADA: 'Cancelada' };

const ACTIVIDADES = {
  nombre: 'Actividades especiales',
  encabezados: ['ID', 'Fecha', 'Correo', 'Nombre', 'Entrada', 'Salida', 'Descripción', 'Ubicación entrada',
    'Ubicación salida', 'Estado', 'Revisado por', 'Revisado el'],
};
const ACT = { ID: 0, FECHA: 1, CORREO: 2, NOMBRE: 3, ENTRADA: 4, SALIDA: 5, DESCRIPCION: 6, UBIC_E: 7, UBIC_S: 8,
  ESTADO: 9, REVISOR: 10, REVISADO: 11 };

const RECHAZADOS = {
  nombre: 'Rechazados',
  encabezados: ['Fecha', 'Hora', 'Correo', 'Tipo', 'Motivo', 'Ubicación', 'Distancia al colegio (m)'],
};
const ENCABEZADOS_DOCENTE = ['Fecha', 'Hora de entrada', 'Correo', 'Hora de salida', 'Observación',
  'Ubicación entrada', 'Ubicación salida', 'Jornada', 'Horas en el colegio', 'Cumplió meta', 'Actividad especial',
  'Revisión', 'Estado', 'Ent status', 'Min tarde', 'Min antes', 'Horas extra'];

// Columnas (base 1) de la hoja de cada miembro. Las 5 últimas son como en el sistema anterior (Google Form):
// Estado (Asistió / Sin salida / Actividad especial), Ent status (Puntual / Tarde), minutos tarde,
// minutos que salió antes de su hora y horas extra.
const COL = { FECHA: 1, ENTRADA: 2, CORREO: 3, SALIDA: 4, OBSERVACION: 5, UBIC_ENTRADA: 6, UBIC_SALIDA: 7,
  JORNADA: 8, HORAS: 9, CUMPLIO: 10, ACTIVIDAD: 11, REVISION: 12, ESTADO: 13, ENT_STATUS: 14, MIN_TARDE: 15,
  MIN_ANTES: 16, EXTRA: 17 };

const ETIQUETAS = { ENTRADA: 'Entrada', SALIDA: 'Salida' };
const POR_REVISAR = 'Por revisar';
const HOJAS_BASE = [DOCENTES, ROLES_HOJA, JORNADAS, CONFIGURACION, SEDES, CALENDARIO, SOLICITUDES, ACTIVIDADES, RECHAZADOS];

// Función (permiso) que necesita cada acción de administración. Basta con tener una de la lista.
const PERMISO_DE_ACCION = {
  adminHoy: ['HOY'],
  adminMiembros: ['MIEMBROS', 'CALENDARIO', 'HOY'],
  adminAsignar: ['MIEMBROS'], adminEstado: ['MIEMBROS'], adminEliminarMiembro: ['MIEMBROS'], adminLiberar: ['MIEMBROS'],
  adminJornadas: ['JORNADAS', 'MIEMBROS'], adminGuardarJornada: ['JORNADAS'], adminEliminarJornada: ['JORNADAS'],
  adminEventos: ['CALENDARIO'], adminAgregarEvento: ['CALENDARIO'], adminEliminarEvento: ['CALENDARIO'],
  adminActividades: ['REVISION'], adminRevisarActividad: ['REVISION'],
  adminSolicitudes: ['SOLICITUDES'], adminRevisarSolicitud: ['SOLICITUDES'],
  adminReporte: ['REPORTES'], adminActualizarReportes: ['REPORTES'], adminResumenAnual: ['REPORTES'],
  adminConfig: ['AJUSTES'], adminGuardarReglas: ['AJUSTES'], adminAgregarSede: ['AJUSTES'], adminEditarSede: ['AJUSTES'], adminEliminarSede: ['AJUSTES'],
  adminGuardarVerificacion: ['AJUSTES'], adminSecretoQr: ['AJUSTES', 'HOY'],
  adminRoles: ['ROLES', 'MIEMBROS'], adminGuardarRol: ['ROLES'], adminEliminarRol: ['ROLES'],
};

// ---------------------------------------------------------------- Funciones para ejecutar desde el editor

/** Ejecutar una vez desde el editor: crea las hojas base, fija la zona horaria y pide los permisos. */
function configurarHojas() {
  const libro = libroAsistencia();
  libro.setSpreadsheetTimeZone(CONFIG.ZONA_HORARIA);
  HOJAS_BASE.forEach(h => obtenerHoja(h.nombre, h.encabezados));
  leerRoles();
  leerJornadas();
  leerConfig();
  // Quita la hoja vacía que Google crea por defecto.
  libro.getSheets()
    .filter(h => /^(Hoja|Sheet) ?1$/.test(h.getName()) && h.getLastRow() === 0 && libro.getSheets().length > 1)
    .forEach(h => libro.deleteSheet(h));
  Logger.log('Listo. Hoja de asistencia: ' + libro.getUrl());
}

/**
 * ⚠️ Deja la hoja VACÍA: borra miembros, sus hojas de asistencia, calendario, solicitudes, sedes y actividades.
 * Las jornadas y la configuración vuelven a las iniciales. Solo se ejecuta a mano desde el editor.
 */
function reiniciarTodo() {
  const libro = libroAsistencia();
  const temporal = libro.insertSheet('temporal-' + Date.now());
  libro.getSheets().filter(h => h.getSheetId() !== temporal.getSheetId()).forEach(h => libro.deleteSheet(h));
  HOJAS_BASE.forEach(h => obtenerHoja(h.nombre, h.encabezados));
  _jornadas = null;
  _config = null;
  _roles = null;
  leerRoles();
  leerJornadas();
  leerConfig();
  libro.deleteSheet(temporal);
  libro.setSpreadsheetTimeZone(CONFIG.ZONA_HORARIA);
  Logger.log('Listo: la hoja quedó vacía, solo con los encabezados, las jornadas y la configuración iniciales.');
}

let _libro = null;

/**
 * Hoja de cálculo de la asistencia. Orden: la guardada en las propiedades del script, CONFIG.ID_HOJA,
 * la hoja que contiene este script, o una hoja nueva en el Drive de quien publicó el script.
 * La elegida se guarda en las propiedades para usar siempre la misma.
 */
function libroAsistencia() {
  if (_libro) return _libro;
  const props = PropertiesService.getScriptProperties();
  const guardada = props.getProperty('ID_HOJA');
  if (guardada) {
    _libro = SpreadsheetApp.openById(guardada);
    return _libro;
  }
  if (CONFIG.ID_HOJA) {
    try {
      _libro = SpreadsheetApp.openById(CONFIG.ID_HOJA);
    } catch (e) {
      _libro = null; // sin permiso o borrada: se usa otra
    }
  }
  if (!_libro) _libro = SpreadsheetApp.getActiveSpreadsheet() || SpreadsheetApp.create('Mesón Asiste — Asistencia');
  props.setProperty('ID_HOJA', _libro.getId());
  return _libro;
}

/**
 * Ejecutar UNA vez desde el editor: la app vuelve a trabajar en su propia hoja ("Mesón Asiste — Asistencia").
 * Trae de vuelta las pestañas de la app (miembros, roles, jornadas, configuración, sedes, calendario, solicitudes y la
 * hoja de cada persona, con las marcas que se hicieron mientras estuvo en el otro documento). No borra el otro documento.
 */
function volverAMesonAsiste() {
  const props = PropertiesService.getScriptProperties();
  const idAnterior = props.getProperty('ID_HOJA_ANTERIOR');
  if (!idAnterior) return Logger.log('La app ya trabaja en su propia hoja: ' + libroAsistencia().getUrl());
  const actual = libroAsistencia();
  const destino = SpreadsheetApp.openById(idAnterior);
  const nombres = HOJAS_BASE.map(h => h.nombre);
  const docentes = actual.getSheetByName(DOCENTES.nombre);
  if (docentes && docentes.getLastRow() > 1) {
    docentes.getRange(2, DOC.HOJA + 1, docentes.getLastRow() - 1, 1).getValues()
      .forEach(v => { if (v[0] && nombres.indexOf(String(v[0])) < 0) nombres.push(String(v[0])); });
  }
  const traidas = [];
  nombres.forEach(nombre => {
    const origen = actual.getSheetByName(nombre);
    if (!origen) return;
    // Se reemplaza la pestaña vieja por la más reciente (copia completa: conserva formatos y horas como texto).
    const vieja = destino.getSheetByName(nombre);
    const nueva = origen.copyTo(destino);
    if (vieja) destino.deleteSheet(vieja);
    nueva.setName(nombre).showSheet();
    traidas.push(nombre);
  });
  props.setProperty('ID_HOJA', destino.getId());
  props.deleteProperty('ID_HOJA_ANTERIOR');
  _libro = null; _jornadas = null; _config = null; _roles = null;
  configurarHojas();
  Logger.log('Listo: la app volvió a "' + destino.getName() + '". Pestañas actualizadas: ' + traidas.join(', ') +
    '. Publica una Nueva versión del servidor.');
}

/** Ejecutar desde el editor para ver en el "Registro de ejecución" el enlace de la hoja que usa el servidor. */
function verHoja() {
  Logger.log('Hoja de asistencia: ' + libroAsistencia().getUrl());
}

/** Abrir la URL /exec en el navegador (como dueño) autoriza el script y prepara las hojas. */
function doGet() {
  configurarHojas();
  return ContentService.createTextOutput('✅ Servidor de Mesón Asiste listo. Ya puedes cerrar esta pestaña.');
}

function doPost(e) {
  let datos = {};
  try {
    datos = JSON.parse(e.postData.contents);
  } catch (err) {
    return json({ ok: false, mensaje: 'Petición no válida.', codigo: '' });
  }
  const accion = String(datos.accion || '');
  // Las consultas no esperan turno (varias personas pueden abrir la app a la vez); solo lo que escribe en la hoja.
  const escribe = SOLO_CONSULTA.indexOf(accion) < 0;
  const candado = escribe ? LockService.getScriptLock() : null;
  try {
    if (candado) candado.waitLock(15000);
    const respuesta = atender(datos, accion);
    anotarBitacora(datos, true, respuesta);
    if (escribe && AFECTA_REPORTES.indexOf(accion) >= 0) cambiaronDatos(accion);
    return json(respuesta);
  } catch (err) {
    anotarBitacora(datos, false, { mensaje: err.message });
    return json({ ok: false, mensaje: err.message, codigo: err.codigo || '' });
  } finally {
    if (candado) candado.releaseLock();
  }
}

/** Ejecuta la acción que pide la app y devuelve su respuesta. */
function atender(datos, accion) {
  if (accion === 'registrarDocente') return registrarDocente(datos);
  if (accion === 'recuperar') return recuperar(datos);
  if (accion === 'iniciarSesion') return iniciarSesion(datos);
  if (accion === 'registrar') return registrar(autenticar(datos), datos.marca || {});
  if (accion === 'perfil') return perfil(autenticar(datos), datos);
  if (accion === 'ranking') return rankingDelMes(autenticar(datos), datos.mes);
  if (accion === 'misMarcas') return misMarcas(autenticar(datos));
  if (accion === 'liberarMiTelefono') return liberarMiTelefono(autenticar(datos));
  if (accion === 'misSolicitudes') return misSolicitudes(autenticar(datos));
  if (accion === 'crearSolicitud') return crearSolicitud(autenticar(datos), datos.solicitud || {});
  if (accion === 'cancelarSolicitud') return cancelarSolicitud(autenticar(datos), datos.id);
  if (accion.indexOf('admin') === 0) return administrar(autenticarGestor(datos, accion), datos);
  throw new Error('Acción no válida.');
}

/** Error con un código que la app reconoce (NO_REGISTRADO, ACCESO_CERRADO). */
function errorCon(codigo, mensaje) {
  const err = new Error(mensaje);
  err.codigo = codigo;
  return err;
}

// ---------------------------------------------------------------- Configuración (meta y tolerancia)

let _config = null;

/** Valores de la hoja "Configuración"; si está vacía, se crean con los de CONFIG. */
function leerConfig() {
  if (_config) return _config;
  const hoja = obtenerHoja(CONFIGURACION.nombre, CONFIGURACION.encabezados);
  if (hoja.getLastRow() < 2) {
    hoja.appendRow(['HORAS_META', CONFIG.HORAS_META, 'Horas mínimas entre la entrada y la salida']);
    hoja.appendRow(['TOLERANCIA_MINUTOS', CONFIG.TOLERANCIA_MINUTOS, 'Minutos de gracia antes de contar como tarde']);
  }
  const valores = {};
  hoja.getDataRange().getValues().slice(1).forEach(v => { if (v[0]) valores[String(v[0]).trim()] = v[1]; });
  _config = {
    horasMeta: Number(valores.HORAS_META) > 0 ? Number(valores.HORAS_META) : CONFIG.HORAS_META,
    tolerancia: Number(valores.TOLERANCIA_MINUTOS) >= 0 ? Number(valores.TOLERANCIA_MINUTOS) : CONFIG.TOLERANCIA_MINUTOS,
  };
  return _config;
}

function guardarConfig(clave, valor) {
  const hoja = obtenerHoja(CONFIGURACION.nombre, CONFIGURACION.encabezados);
  leerConfig();
  const fila = buscarFila(hoja, 0, clave.toLowerCase());
  if (fila) hoja.getRange(fila.numero, 2).setValue(valor);
  else hoja.appendRow([clave, valor, '']);
  _config = null;
}

// ---------------------------------------------------------------- Sedes (geocerca, Wi-Fi y QR)

/** Sedes de la hoja "Sedes". Sin sedes no se valida la distancia (se puede marcar desde cualquier lugar). */
function leerSedes() {
  const valores = obtenerHoja(SEDES.nombre, SEDES.encabezados).getDataRange().getValues();
  return valores.slice(1).filter(v => v[SED.ID]).map(v => ({
    id: String(v[SED.ID]),
    nombre: String(v[SED.NOMBRE] || 'Sede'),
    latitud: Number(v[SED.LAT]),
    longitud: Number(v[SED.LON]),
    radio: v[SED.RADIO] === '' || isNaN(Number(v[SED.RADIO])) ? 80 : Number(v[SED.RADIO]),
    metodo: METODOS[String(v[SED.METODO]).toUpperCase()] ? String(v[SED.METODO]).toUpperCase()
      : (claveDe(METODOS, v[SED.METODO], x => x) || 'UBICACION'),
    wifi: String(v[SED.WIFI] || '').split(',').map(x => x.trim().toLowerCase()).filter(String),
    secreto: String(v[SED.SECRETO] || ''),
  }));
}

/** Lo que el teléfono necesita saber de cada sede (sin la clave del QR). */
function sedeJson(s) {
  return { id: s.id, nombre: s.nombre, latitud: s.latitud, longitud: s.longitud, radio: s.radio, metodo: s.metodo,
    wifiRegistrados: s.wifi.length };
}

function nuevoSecreto() {
  return Utilities.getUuid().replace(/-/g, '') + Utilities.getUuid().replace(/-/g, '');
}

/** Código del QR para una ventana de 30 s: primeros 10 caracteres hex de HMAC-SHA256(ventana, clave). */
function codigoQr(secreto, ventana) {
  return Utilities.computeHmacSha256Signature(String(ventana), secreto)
    .map(b => ('0' + ((b + 256) % 256).toString(16)).slice(-2)).join('').substring(0, 10);
}

/** true si el código coincide con alguna de las ventanas de 30 s alrededor de los instantes dados. */
function qrValido(secreto, codigo, instantes) {
  const ventanas = {};
  instantes.forEach(t => {
    const v = Math.floor(t.getTime() / 1000 / 30);
    [v - 1, v, v + 1].forEach(x => { ventanas[x] = true; });
  });
  return Object.keys(ventanas).some(v => codigoQr(secreto, v) === String(codigo).toLowerCase());
}

// ---------------------------------------------------------------- Jornadas

let _jornadas = null;

/** Jornadas de la hoja "Jornadas" como { A: {codigo, nombre, entrada, salida, dias}, … }. Si está vacía, crea las iniciales. */
function leerJornadas() {
  if (_jornadas) return _jornadas;
  const hoja = obtenerHoja(JORNADAS.nombre, JORNADAS.encabezados);
  if (hoja.getLastRow() < 2) {
    CONFIG.JORNADAS_INICIALES.forEach(j => hoja.appendRow([j[0], j[1], "'" + j[2], "'" + j[3], "'" + j[4], '']));
  }
  const resultado = {};
  hoja.getDataRange().getValues().slice(1).filter(v => v[0]).forEach(v => {
    const codigo = String(v[0]).trim();
    const dias = String(v[4] || '1,2,3,4,5').split(',').map(Number).filter(n => n >= 1 && n <= 7);
    resultado[codigo] = {
      codigo: codigo,
      nombre: String(v[1] || codigo),
      entrada: textoHora(v[2]).substring(0, 5),
      salida: textoHora(v[3]).substring(0, 5),
      dias: dias.length ? dias : [1, 2, 3, 4, 5],
      porDia: leerHorarioPorDia(v[5]),
    };
  });
  _jornadas = resultado;
  return resultado;
}

function jornadaPorDefecto() {
  return Object.keys(leerJornadas())[0];
}

function jornadaJson(codigo) {
  const j = leerJornadas()[codigo] || leerJornadas()[jornadaPorDefecto()];
  return { codigo: j.codigo, nombre: j.nombre, entrada: j.entrada, salida: j.salida, dias: j.dias, porDia: j.porDia || {} };
}

/** "6=08:00-12:00;7=09:00-13:00" → { 6: {entrada, salida}, 7: {…} } (días con horario distinto al general). */
function leerHorarioPorDia(valor) {
  const resultado = {};
  String(valor || '').split(';').forEach(parte => {
    const m = parte.trim().match(/^([1-7])=(\d{2}:\d{2})-(\d{2}:\d{2})$/);
    if (m) resultado[m[1]] = { entrada: m[2], salida: m[3] };
  });
  return resultado;
}

/** Entrada y salida de una jornada un día de la semana (1 = lunes … 7 = domingo). */
function horarioDelDia(jornada, iso) {
  return (jornada.porDia && jornada.porDia[iso]) || { entrada: jornada.entrada, salida: jornada.salida };
}

/** Minutos entre entrada y salida. Si la salida es igual o menor, termina al día siguiente (p. ej. 18:00 → 02:00). */
function duracionHorario(entrada, salida) {
  let minutos = aMinutos(salida) - aMinutos(entrada);
  if (minutos <= 0) minutos += 1440;
  return minutos;
}

/** Día de la semana de una fecha "yyyy-MM-dd": 1 = lunes … 7 = domingo. */
function diaIso(fecha) {
  const d = new Date(fecha + 'T12:00:00').getDay();
  return d === 0 ? 7 : d;
}

function esDiaLaboral(miembro, fecha) {
  return jornadaJson(miembro.jornada).dias.indexOf(diaIso(fecha)) >= 0;
}

// ---------------------------------------------------------------- Roles (se crean, editan y eliminan desde la app)

let _roles = null;

/** Roles de la hoja "Roles" como { CODIGO: {codigo, nombre, marca, permisos[]} }. Si está vacía, crea los iniciales. */
function leerRoles() {
  if (_roles) return _roles;
  const hoja = obtenerHoja(ROLES_HOJA.nombre, ROLES_HOJA.encabezados);
  if (hoja.getLastRow() < 2) ROLES_INICIALES.forEach(r => hoja.appendRow(r));
  const resultado = {};
  hoja.getDataRange().getValues().slice(1).filter(v => v[0]).forEach(v => {
    const codigo = String(v[0]).trim().toUpperCase();
    if (codigo === 'SUPERADMIN') return; // rol interno: si una versión anterior lo escribió en la hoja, se ignora
    // Las funciones del Administrador las decide el super administrador.
    const permisos = String(v[3] || '').split(',').map(p => p.trim().toUpperCase()).filter(p => PERMISOS[p]);
    resultado[codigo] = {
      codigo: codigo,
      nombre: String(v[1] || codigo),
      marca: /^(s[ií]|true|1)$/i.test(String(v[2]).trim()),
      permisos: permisos,
    };
  });
  // Versiones anteriores escribieron el rol de super administrador en la hoja: se borra esa fila.
  const filaSuper = buscarFila(hoja, 0, 'superadmin');
  if (filaSuper) hoja.deleteRow(filaSuper.numero);
  resultado.SUPERADMIN = ROL_SUPERADMIN;
  if (!resultado.ADMINISTRADOR) resultado.ADMINISTRADOR = { codigo: 'ADMINISTRADOR', nombre: 'Administrador', marca: false, permisos: Object.keys(PERMISOS) };
  if (!resultado.DOCENTE) resultado.DOCENTE = { codigo: 'DOCENTE', nombre: 'Docente', marca: true, permisos: [] };
  _roles = resultado;
  return resultado;
}

function rolJson(codigo) {
  const r = leerRoles()[codigo] || leerRoles().DOCENTE;
  return { codigo: r.codigo, nombre: r.nombre, marca: r.marca, permisos: r.permisos };
}

/** ¿El rol tiene esta función? El super administrador las tiene todas. */
function puede(rol, permiso) {
  const r = leerRoles()[rol];
  return !!r && (r.codigo === 'SUPERADMIN' || r.permisos.indexOf(permiso) >= 0);
}

const esSuperAdmin = rol => rol === 'SUPERADMIN';

/** ¿Las personas con este rol marcan asistencia? */
function marcaAsistencia(rol) {
  const r = leerRoles()[rol];
  return r ? r.marca : true;
}

/** Tiene alguna función de administración (ve el panel Admin). */
function esGestor(rol) {
  const r = leerRoles()[rol];
  return !!r && (r.codigo === 'SUPERADMIN' || r.permisos.length > 0);
}
