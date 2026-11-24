/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 *
 * Panel de administración de la versión web (igual que la app de Android): Hoy, Solicitudes, Miembros, Jornadas,
 * Roles, Calendario, Por revisar, Reportes (PDF y Excel) y Ajustes (reglas, ubicación del colegio y QR).
 * Cada sección aparece solo si el rol tiene esa función. Se carga antes de app.js y usa sus funciones al ejecutarse.
 */
'use strict';

// ---------------------------------------------------------------- Permisos

const SECCIONES_ADMIN = [
  { id: 'hoy', texto: 'Hoy', permiso: 'HOY' },
  { id: 'solicitudes', texto: 'Solicitudes', permiso: 'SOLICITUDES' },
  { id: 'miembros', texto: 'Miembros', permiso: 'MIEMBROS' },
  { id: 'jornadas', texto: 'Jornadas', permiso: 'JORNADAS' },
  { id: 'roles', texto: 'Roles', permiso: 'ROLES' },
  { id: 'calendario', texto: 'Calendario', permiso: 'CALENDARIO' },
  { id: 'revision', texto: 'Por revisar', permiso: 'REVISION' },
  { id: 'reportes', texto: 'Reportes', permiso: 'REPORTES' },
  { id: 'ajustes', texto: 'Ajustes', permiso: 'AJUSTES' },
];
const NOMBRES_PERMISOS = {
  HOY: 'Ver Hoy', SOLICITUDES: 'Revisar solicitudes de permiso', MIEMBROS: 'Gestionar miembros',
  JORNADAS: 'Gestionar jornadas', CALENDARIO: 'Gestionar calendario', REVISION: 'Revisar marcas por revisar',
  REPORTES: 'Ver reportes', AJUSTES: 'Ajustes del colegio', ROLES: 'Gestionar roles',
};
const TIPOS_EVENTO_WEB = { VACACIONES: 'Vacaciones', FERIADO: 'Feriado o asueto', PERMISO: 'Permiso', ENTRADA_ESPECIAL: 'Entrada y salida especial' };

function rolActual() { return (estado.perfil && estado.perfil.rolInfo) || { codigo: 'DOCENTE', permisos: [] }; }
function esSuperAdminWeb() { return rolActual().codigo === 'SUPERADMIN'; }
function puedeWeb(p) { return esSuperAdminWeb() || (rolActual().permisos || []).indexOf(p) >= 0; }
function gestionaWeb() { return esSuperAdminWeb() || (rolActual().permisos || []).length > 0; }
const rolProtegido = r => r.codigo === 'SUPERADMIN' || r.codigo === 'ADMINISTRADOR';
const rolGestiona = r => r.codigo === 'SUPERADMIN' || (r.permisos || []).length > 0;

function descripcionRolWeb(r) {
  const marca = r.marca ? 'Marca entrada, salida y permisos' : 'No marca asistencia';
  const funciones = r.codigo === 'SUPERADMIN' ? 'todas las funciones'
    : !(r.permisos || []).length ? 'sin funciones de administración'
      : r.permisos.map(p => (NOMBRES_PERMISOS[p] || p).toLowerCase()).join(', ');
  return marca + ' · ' + funciones + '.';
}

// ---------------------------------------------------------------- Estado y carga

const adm = {
  seccion: null, cargando: false,
  hoy: null, miembros: null, roles: [], jornadasLista: [], rolesEnUso: null, jornadas: null, eventos: null,
  actividades: null, solicitudes: null, config: null, reporte: null, anual: null,
  rep: { anio: String(new Date().getFullYear()), mes: claveMesActual(), persona: '', vista: 'resumen' },
};

function claveMesActual() { const d = new Date(); return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0'); }
const NOMBRES_MES_WEB = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];
function nombreMesWeb(clave) {
  const p = String(clave).split('-');
  const m = Number(p[1]);
  return m ? NOMBRES_MES_WEB[m - 1].charAt(0).toUpperCase() + NOMBRES_MES_WEB[m - 1].slice(1) + ' ' + p[0] : clave;
}

/** Llama al servidor para el panel; "Acción no válida" = servidor sin actualizar. */
async function pedirAdmin(accion, extra) {
  try {
    return await apiAutenticada(accion, extra);
  } catch (e) {
    if (e instanceof SinConexion) throw new Error('Sin conexión con el servidor. El panel necesita internet.');
    throw e;
  }
}

const CAMPOS_PANEL = ['hoy', 'solicitudes', 'miembros', 'roles', 'jornadasLista', 'jornadas', 'rolesEnUso', 'eventos',
  'actividades', 'config', 'reporte', 'anual'];

/** Lo último que se vio en el panel se guarda en el teléfono (por cuenta) para mostrarlo al instante al volver. */
function guardarPanel() {
  if (!estado.cuenta) return;
  const datos = {};
  CAMPOS_PANEL.forEach(c => { datos[c] = adm[c]; });
  try { almacen.guardar('panel', { correo: estado.cuenta.correo, fecha: claveDia(new Date()), datos: datos }); } catch (e) { /* sin espacio */ }
}

/** Al cerrar sesión: el panel en memoria vuelve a estar vacío (otra cuenta no ve nada de la anterior). */
function reiniciarPanel() {
  CAMPOS_PANEL.forEach(c => { adm[c] = null; });
  adm.roles = []; adm.jornadasLista = []; adm.seccion = null; adm.restaurado = false;
}

function restaurarPanel() {
  if (adm.restaurado || !estado.cuenta) return;
  adm.restaurado = true;
  const p = almacen.leer('panel', null);
  if (!p || p.correo !== estado.cuenta.correo || !p.datos) return;
  CAMPOS_PANEL.forEach(c => { if (adm[c] == null && p.datos[c] != null) adm[c] = p.datos[c]; });
  if (p.fecha !== claveDia(new Date())) adm.hoy = null; // "Hoy" de otro día no sirve
}

async function cargarSeccionAdmin(silencioso) {
  const s = adm.seccion;
  if (!s || adm.cargando) return;
  adm.cargando = true;
  if (!silencioso) render();
  try {
    if (s === 'hoy') adm.hoy = (await pedirAdmin('adminHoy')).miembros || [];
    else if (s === 'solicitudes') adm.solicitudes = (await pedirAdmin('adminSolicitudes')).solicitudes || [];
    else if (s === 'miembros') await leerMiembrosAdmin();
    else if (s === 'jornadas') await leerJornadasAdmin();
    else if (s === 'roles') await leerRolesAdmin();
    else if (s === 'calendario') {
      adm.eventos = ((await pedirAdmin('adminEventos')).eventos || []).sort((a, b) => a.desde.localeCompare(b.desde));
      if (!adm.miembros) await leerMiembrosAdmin();
    } else if (s === 'revision') adm.actividades = (await pedirAdmin('adminActividades')).actividades || [];
    else if (s === 'reportes') await cargarReporteAdmin();
    else if (s === 'ajustes') adm.config = await pedirAdmin('adminConfig');
    guardarPanel();
  } catch (e) {
    if (!manejarErrorAcceso(e)) mostrarAviso(e.message, true);
  } finally {
    adm.cargando = false;
    render();
  }
}

async function leerMiembrosAdmin() {
  const r = await pedirAdmin('adminMiembros');
  adm.miembros = (r.miembros || []).sort((a, b) => String(a.nombre).localeCompare(String(b.nombre)));
  adm.roles = (r.roles || []).filter(x => typeof x === 'object');
  adm.jornadasLista = r.jornadas || [];
}
async function leerJornadasAdmin() {
  const r = await pedirAdmin('adminJornadas');
  adm.jornadasLista = r.jornadas || [];
  adm.jornadas = adm.jornadasLista.map(j => ({ j: j, enUso: (r.enUso || {})[j.codigo] || 0 }));
}
async function leerRolesAdmin() {
  const r = await pedirAdmin('adminRoles');
  adm.roles = r.roles || [];
  adm.rolesEnUso = r.enUso || {};
}
async function cargarReporteAdmin() {
  const rep = adm.rep;
  if (rep.mes) {
    const r = await pedirAdmin('adminReporte', { mes: rep.mes });
    adm.reporte = { mes: rep.mes, filas: r.resumen || [], detalle: r.detalle || [] };
  } else {
    const r = await pedirAdmin('adminResumenAnual', { anio: rep.anio });
    adm.anual = { anio: r.anio || rep.anio, meses: r.meses || [], filas: r.filas || [] };
  }
}

/** Hace una acción del panel (guardar, eliminar…), avisa y recarga la sección. */
function hacerAdmin(accion, extra, despues) {
  conOcupado(async () => {
    const r = await pedirAdmin(accion, extra);
    cerrarDialogo();
    mostrarAviso(r.mensaje || 'Listo.');
    if (despues) await despues();
    await cargarSeccionAdmin(true);
  });
}

// ---------------------------------------------------------------- Pantalla

function pantallaAdmin() {
  restaurarPanel();
  const secciones = SECCIONES_ADMIN.filter(s => puedeWeb(s.permiso));
  if (!secciones.length) return '<h1>Administración</h1><p class="chico">Tu rol no tiene funciones de administración.</p>';
  if (!secciones.some(s => s.id === adm.seccion)) { adm.seccion = secciones[0].id; setTimeout(() => cargarSeccionAdmin(), 0); }
  let html = '<div class="fila"><div class="crece"><h1>Administración</h1><p class="chico">' +
    esc(esSuperAdminWeb() ? 'Control total del colegio.' : rolActual().nombre + ': tus funciones de administración.') +
    '</p></div><button class="boton texto" data-accion="admRecargar">Actualizar</button></div>';
  html += '<div class="chips">' + secciones.map(s => {
    const n = s.id === 'solicitudes' ? (adm.solicitudes || []).filter(x => x.estado === 'PENDIENTE' && !esMio(x.correo)).length
      : s.id === 'revision' ? (adm.actividades || []).filter(x => !esMio(x.correo)).length : 0;
    return '<button class="chip' + (s.id === adm.seccion ? ' activo' : '') + '" data-accion="admSeccion" data-valor="' + s.id + '">' +
      esc(s.texto) + (n ? ' (' + n + ')' : '') + '</button>';
  }).join('') + '</div>';
  if (adm.cargando) html += '<div class="barra"><span style="width:100%;opacity:.5"></span></div>';
  const f = { hoy: seccionHoy, solicitudes: seccionSolicitudes, miembros: seccionMiembros, jornadas: seccionJornadas,
    roles: seccionRoles, calendario: seccionCalendario, revision: seccionRevision, reportes: seccionReportes, ajustes: seccionAjustes }[adm.seccion];
  const entrada = adm.entrada || '';
  adm.entrada = '';
  return html + '<div id="admin-contenido" class="' + entrada + '">' + (f ? f() : '') + '</div>';
}

/** Es del usuario que tiene la sesión abierta (nadie revisa lo suyo). */
function esMio(correo) {
  return !!(estado.cuenta && correo && String(correo).toLowerCase() === String(estado.cuenta.correo).toLowerCase());
}

/** Deslizar a los lados en el panel: pasa a la pestaña anterior o siguiente. */
function deslizarAdmin(direccion) {
  const secciones = SECCIONES_ADMIN.filter(s => puedeWeb(s.permiso));
  const i = secciones.findIndex(s => s.id === adm.seccion) + direccion;
  if (i < 0 || i >= secciones.length) return;
  adm.seccion = secciones[i].id;
  adm.entrada = direccion > 0 ? 'desde-derecha' : 'desde-izquierda';
  render();
  const activo = document.querySelector('.chips .chip.activo');
  if (activo && activo.scrollIntoView) activo.scrollIntoView({ inline: 'center', block: 'nearest', behavior: 'smooth' });
  cargarSeccionAdmin();
}

(function () {
  let inicio = null;
  document.addEventListener('touchstart', e => {
    if (estado.pestana !== 'admin' || e.touches.length !== 1 || !$('dialogo').hidden) { inicio = null; return; }
    // No se cambia de pestaña al deslizar una tabla, las pestañas de arriba o un campo.
    if (e.target.closest('.tabla-envoltura, .chips, input, select, textarea')) { inicio = null; return; }
    inicio = { x: e.touches[0].clientX, y: e.touches[0].clientY, t: Date.now() };
  }, { passive: true });
  document.addEventListener('touchend', e => {
    if (!inicio) return;
    const dx = e.changedTouches[0].clientX - inicio.x;
    const dy = e.changedTouches[0].clientY - inicio.y;
    const rapido = Date.now() - inicio.t < 600;
    inicio = null;
    if (rapido && Math.abs(dx) > 60 && Math.abs(dx) > Math.abs(dy) * 1.8) deslizarAdmin(dx < 0 ? 1 : -1);
  }, { passive: true });
})();

const vacio = (t, d) => '<div class="tarjeta centro"><h3>' + esc(t) + '</h3><p class="chico">' + esc(d) + '</p></div>';
const botonAgregar = (texto, accion) => '<button class="boton" data-accion="' + accion + '">' + esc(texto) + '</button>';

// ----- Hoy
const COLOR_ESTADO = { PUNTUAL: 'exito', TARDE: 'alerta', ACTIVIDAD: '', LIBRE: 'chico', SIN_MARCAR: 'error' };
const TEXTO_ESTADO = { PUNTUAL: 'Puntual', TARDE: 'Tarde', ACTIVIDAD: 'Actividad', LIBRE: 'Día libre', SIN_MARCAR: 'Sin marcar' };
function seccionHoy() {
  let html = '';
  (perfil().sedes || []).filter(s => s.metodo === 'QR').forEach(s => {
    html += '<button class="boton secundario" data-accion="admMostrarQr" data-valor="' + esc(s.id) + '">Mostrar QR de la entrada · ' + esc(s.nombre) + '</button>';
  });
  if (!adm.hoy) return html;
  if (!adm.hoy.length) return html + vacio('Aún no hay miembros', 'Cuando alguien se registre en la app aparecerá aquí.');
  const cuenta = e => adm.hoy.filter(x => x.estado === e).length;
  html += '<div class="rejilla">' + ['PUNTUAL', 'TARDE', 'SIN_MARCAR', 'ACTIVIDAD'].map(e =>
    '<div class="tarjeta" style="flex-direction:row;align-items:center;gap:10px"><span style="font-size:1.4rem;font-weight:600" class="' +
    COLOR_ESTADO[e] + '">' + cuenta(e) + '</span><span class="chico">' + TEXTO_ESTADO[e] + '</span></div>').join('') + '</div>';
  const orden = ['SIN_MARCAR', 'TARDE', 'PUNTUAL', 'ACTIVIDAD', 'LIBRE'];
  adm.hoy.slice().sort((a, b) => orden.indexOf(a.estado) - orden.indexOf(b.estado) || String(a.nombre).localeCompare(String(b.nombre))).forEach(a => {
    html += '<div class="tarjeta"><div class="fila"><div class="avatar" style="width:40px;height:40px">' + esc(iniciales(a.nombre)) + '</div>' +
      '<div class="crece"><h3>' + esc(a.nombre) + '</h3><p class="chico">' + esc(a.rol + ' · ' + a.jornada) + '</p>' +
      (a.entrada ? '<p class="chico">Entrada ' + esc(a.entrada) + ' · Salida ' + esc(a.salida || '—') + (a.horas ? ' · ' + esc(a.horas) + ' h' : '') + '</p>' : '') +
      (a.detalle ? '<p class="chico">' + esc(a.detalle) + '</p>' : '') + '</div>' +
      '<span class="etiqueta ' + (COLOR_ESTADO[a.estado] || '') + '">' + esc(TEXTO_ESTADO[a.estado] || a.estado) + '</span></div></div>';
  });
  return html;
}

// ----- Solicitudes
function seccionSolicitudes() {
  if (!adm.solicitudes) return '';
  if (!adm.solicitudes.length) return vacio('Sin solicitudes', 'Aquí aparecen los permisos y vacaciones que pide el personal.');
  const etq = { PENDIENTE: ['Pendiente', 'alerta'], APROBADA: ['Aprobada', 'exito'], RECHAZADA: ['Rechazada', 'error'] };
  return '<p class="chico">Al aprobar, el permiso se agrega al calendario de la persona. Descarga el PDF para imprimirlo, firmarlo y archivarlo.</p>' +
    '<div class="rejilla"><button class="boton secundario" data-accion="admSolicitudesPdf">Lista en PDF</button>' +
    '<button class="boton secundario" data-accion="admSolicitudesExcel">Lista en Excel</button></div>' + adm.solicitudes.slice().sort((a, b) => (a.estado === 'PENDIENTE' ? 0 : 1) - (b.estado === 'PENDIENTE' ? 0 : 1)).map(s => {
    const e = etq[s.estado] || ['', ''];
    return '<div class="tarjeta"><div class="fila"><h3 class="crece">' + esc(s.nombre) + '</h3><span class="etiqueta ' + e[1] + '">' + e[0] + '</span></div>' +
      '<p>' + esc(s.tipo === 'VACACIONES' ? 'Vacaciones' : (s.horaInicio ? 'Permiso por horas' : 'Permiso')) + ' · ' +
      esc(s.desde === s.hasta ? fechaLegible(s.desde) : fechaLegible(s.desde) + ' al ' + fechaLegible(s.hasta)) +
      (s.horaInicio ? ' · ' + esc(horaAmigable(s.horaInicio) + ' a ' + horaAmigable(s.horaFin)) : '') + '</p>' +
      '<p class="chico">“' + esc(s.motivo) + '”</p>' + (s.respuesta ? '<p class="chico">Respuesta: ' + esc(s.respuesta) + '</p>' : '') +
      (s.vista ? htmlVistaVacaciones(s.vista, false) : '') +
      (s.estado !== 'PENDIENTE' ? '' : esMio(s.correo)
        ? '<p class="chico alerta"><b>Es tu solicitud: debe revisarla otro supervisor o el administrador.</b></p>'
        : '<div class="rejilla"><button class="boton secundario" data-accion="admSolicitud" data-valor="' + esc(s.id) +
        '" data-aprobar="no">Rechazar</button><button class="boton" data-accion="admSolicitud" data-valor="' + esc(s.id) +
        '" data-aprobar="si">Aprobar</button></div>') +
      '<button class="boton texto" style="align-self:flex-start" data-accion="solicitudPdf" data-valor="' + esc(s.id) + '">Descargar PDF para firmar</button></div>';
  }).join('');
}

// ----- Miembros
function seccionMiembros() {
  if (!adm.miembros) return '';
  const editable = puedeWeb('MIEMBROS');
  let html = '<p class="chico">' + (editable ? 'Toca "Editar" para cambiar rol, jornada o acceso.' : 'Tu rol no puede cambiar roles, jornadas o accesos.') + '</p>';
  if (!adm.miembros.length) return html + vacio('Aún no hay miembros', 'Cuando alguien se registre en la app aparecerá aquí.');
  adm.miembros.forEach(m => {
    const rol = adm.roles.find(r => r.codigo === m.rol);
    const jornada = adm.jornadasLista.find(j => j.codigo === m.jornada);
    html += '<div class="tarjeta"><div class="fila"><div class="avatar" style="width:40px;height:40px">' + esc(iniciales(m.nombre)) + '</div>' +
      '<div class="crece"><h3>' + esc(m.nombre) + '</h3><p class="chico">' + esc(m.correo) + '</p><p class="chico">' +
      esc(m.rolNombre || m.rol) + (rol && rol.marca === false ? '' : ' · ' + esc(jornada ? jornada.nombre + ' (' + textoHorario(jornada.entrada, jornada.salida) + ')' : m.jornada)) + '</p></div>' +
      (m.estado !== 'ACTIVO' ? '<span class="etiqueta error">Inactivo</span>' : !m.conTelefono ? '<span class="etiqueta chico">Sin teléfono</span>' : '') + '</div>' +
      (editable ? '<button class="boton texto" style="align-self:flex-start" data-accion="admEditarMiembro" data-valor="' + esc(m.correo) + '">Editar</button>' : '') + '</div>';
  });
  return html;
}

/** Cuántos días laborales son unas vacaciones para una persona y si ya tiene vacaciones en esas fechas. */
function htmlVistaVacaciones(v, conNombre) {
  return '<div class="tarjeta" style="gap:4px">' + (conNombre ? '<b>' + esc(v.nombre) + '</b>' : '') +
    '<p>' + textoDiasLaborales(v.dias) + '</p>' +
    (v.avisos || []).map(a => '<p class="chico alerta">⚠️ ' + esc(a) + '</p>').join('') + '</div>';
}

// ----- Jornadas
function seccionJornadas() {
  let html = botonAgregar('+ Nueva jornada', 'admNuevaJornada');
  (adm.jornadas || []).forEach(({ j, enUso }) => {
    html += '<div class="tarjeta"><h3>' + esc(j.nombre) + '</h3><p>' + esc('Entrada ' + horaAmigable(j.entrada) + ' · Salida ' + horaAmigable(j.salida)) +
      (terminaDiaSiguiente(j.entrada, j.salida) ? ' (día siguiente)' : '') + '</p><p class="chico">Días: ' + esc(textoDias(j.dias || [])) + '</p>' +
      '<p class="chico">' + (j.almuerzo ? 'Almuerzo: ' + esc(duracion(j.almuerzo)) + ' (no cuenta como trabajo)' : 'Sin almuerzo (jornada continua)') + '</p>' +
      Object.keys(j.porDia || {}).filter(d => (j.dias || []).indexOf(Number(d)) >= 0).map(d =>
        '<p class="chico">' + esc(NOMBRES_DIAS[d] + ': ' + textoHorario(j.porDia[d].entrada, j.porDia[d].salida)) + '</p>').join('') +
      '<p class="chico">' + (enUso === 1 ? '1 miembro' : enUso + ' miembros') + '</p><div class="fila">' +
      '<button class="boton texto" data-accion="admEditarJornada" data-valor="' + esc(j.codigo) + '">Editar</button>' +
      '<button class="boton texto peligro" data-accion="admEliminarJornada" data-valor="' + esc(j.codigo) + '">Eliminar</button></div></div>';
  });
  return html;
}

// ----- Roles
function seccionRoles() {
  const superAdmin = esSuperAdminWeb();
  let html = botonAgregar('+ Nuevo rol', 'admNuevoRol') + '<p class="chico">Elige qué hace cada rol: si marca asistencia y qué funciones tiene. ' +
    (superAdmin ? 'Tú decides también las funciones del Administrador.' : 'El rol Administrador solo lo cambia el super administrador.') + '</p>';
  if (!adm.rolesEnUso) return html;
  adm.roles.forEach(r => {
    const n = adm.rolesEnUso[r.codigo] || 0;
    html += '<div class="tarjeta"><div class="fila"><h3 class="crece">' + esc(r.nombre) + '</h3><span class="etiqueta ' + (r.marca ? 'exito' : 'chico') + '">' +
      (r.marca ? 'Marca' : 'No marca') + '</span></div><p class="chico">' + esc(descripcionRolWeb(r)) + '</p><p class="chico">' +
      (n === 1 ? '1 persona' : n + ' personas') + '</p><div class="fila">' +
      (!rolProtegido(r) || superAdmin ? '<button class="boton texto" data-accion="admEditarRol" data-valor="' + esc(r.codigo) + '">Editar</button>'
        : '<span class="chico">Solo lo cambia el super administrador</span>') +
      (!rolProtegido(r) && r.codigo !== 'DOCENTE' ? '<button class="boton texto peligro" data-accion="admEliminarRol" data-valor="' + esc(r.codigo) + '">Eliminar</button>' : '') +
      '</div></div>';
  });
  return html;
}

// ----- Calendario
function seccionCalendario() {
  let html = botonAgregar('+ Agregar al calendario', 'admNuevoEvento');
  if (!adm.eventos) return html;
  if (!adm.eventos.length) return html + vacio('Calendario vacío', 'Los días libres no cuentan como falta y la entrada especial cambia la hora de entrada.');
  const nombre = c => { const m = (adm.miembros || []).find(x => x.correo === c); return m ? m.nombre : c.split('@')[0]; };
  adm.eventos.forEach(e => {
    const porHorasE = e.tipo === 'PERMISO' && e.horaInicio && e.horaFin;
    html += '<div class="tarjeta"><div class="fila"><span class="etiqueta">' + esc(porHorasE ? 'Permiso por horas' : (TIPOS_EVENTO_WEB[e.tipo] || e.tipo)) + '</span>' +
      '<span class="crece"></span><button class="boton texto peligro" data-accion="admEliminarEvento" data-valor="' + esc(e.id) + '">Eliminar</button></div>' +
      '<h3>' + esc(e.desde === e.hasta ? fechaLegible(e.desde) : fechaLegible(e.desde) + ' al ' + fechaLegible(e.hasta)) + '</h3>' +
      (porHorasE ? '<p>' + esc('De ' + horaAmigable(e.horaInicio) + ' a ' + horaAmigable(e.horaFin)) + '</p>' : '') +
      '<p class="chico">Para: ' + esc(/todos/i.test(e.aplicaA) ? 'todo el personal' : e.aplicaA.split(',').map(x => x.trim()).filter(String).map(nombre).join(', ')) + '</p>' +
      (e.horaEntrada ? '<p>' + esc('Entrada ' + horaAmigable(e.horaEntrada) + (e.horaSalida ? ' · Salida ' + horaAmigable(e.horaSalida) : '')) + '</p>' : '') +
      (e.detalle ? '<p class="chico">' + esc(e.detalle) + '</p>' : '') + '</div>';
  });
  return html;
}

// ----- Por revisar
function seccionRevision() {
  if (!adm.actividades) return '';
  if (!adm.actividades.length) return vacio('Nada por revisar', 'Aquí aparecen los días en que alguien marcó fuera del colegio por una actividad especial.');
  return '<p class="chico">Al aprobar, las horas de ese día quedan aceptadas en la hoja de la persona.</p>' + adm.actividades.map(a =>
    '<div class="tarjeta"><h3>' + esc(a.nombre) + '</h3><p class="chico">' + esc(fechaLegible(a.fecha)) + '</p><p>' +
    esc('Entrada ' + (a.entrada || '—') + ' · Salida ' + (a.salida || '—')) + '</p>' +
    (a.descripcion ? '<p>“' + esc(a.descripcion) + '”</p>' : '') +
    (a.ubicacion ? '<a class="boton texto" style="align-self:flex-start" href="' + esc(a.ubicacion) + '" target="_blank" rel="noopener">Ver ubicación en el mapa</a>' : '') +
    (esMio(a.correo) ? '<p class="chico alerta"><b>Es tu actividad: debe revisarla otro supervisor o el administrador.</b></p></div>' :
    '<div class="rejilla"><button class="boton secundario" data-accion="admRevisar" data-valor="' + esc(a.id) + '" data-aprobar="no">Rechazar</button>' +
    '<button class="boton" data-accion="admRevisar" data-valor="' + esc(a.id) + '" data-aprobar="si">Aprobar</button></div></div>')).join('');
}

// ----- Reportes
function seccionReportes() {
  const rep = adm.rep;
  const anioActual = String(new Date().getFullYear());
  const ultimoMes = rep.anio === anioActual ? new Date().getMonth() + 1 : 12;
  const meses = [];
  for (let i = ultimoMes; i >= 1; i--) meses.push(rep.anio + '-' + String(i).padStart(2, '0'));
  const filas = rep.mes ? (adm.reporte && adm.reporte.mes === rep.mes ? adm.reporte.filas : []) : (adm.anual && adm.anual.anio === rep.anio ? adm.anual.filas : []);
  const nombres = Array.from(new Set(filas.map(f => f.nombre))).sort();
  let html = '<div class="fila"><div class="chips crece">' + [anioActual, String(Number(anioActual) - 1)].map(a =>
      '<button class="chip' + (rep.anio === a ? ' activo' : '') + '" data-accion="admRepAnio" data-valor="' + a + '">' + a + '</button>').join('') + '</div>' +
    '<div class="chips">' + [['resumen', 'Resumen'], ['tabla', 'Tabla']].map(v =>
      '<button class="chip' + (rep.vista === v[0] ? ' activo' : '') + '" data-accion="admRepVista" data-valor="' + v[0] + '">' + v[1] + '</button>').join('') + '</div></div>' +
    '<div class="rejilla"><label>Mes<select id="rep-mes" data-cambio="admRepMes"><option value="">Todo el año ' + esc(rep.anio) + '</option>' +
    meses.map(m => '<option value="' + m + '"' + (rep.mes === m ? ' selected' : '') + '>' + esc(nombreMesWeb(m)) + '</option>').join('') + '</select></label>' +
    '<label>Persona<select id="rep-persona" data-cambio="admRepPersona"><option value="">Todo el personal</option>' +
    nombres.map(n => '<option' + (rep.persona === n ? ' selected' : '') + '>' + esc(n) + '</option>').join('') + '</select></label></div>';
  const tabla = tablaReporteActual();
  html += '<div class="rejilla"><button class="boton" data-accion="admDescargarPdf"' + (tabla ? '' : ' disabled') + '>Descargar PDF</button>' +
    '<button class="boton secundario" data-accion="admDescargarExcel"' + (tabla ? '' : ' disabled') + '>Descargar Excel</button></div>';
  if (tabla) {
    html += '<h2>' + esc(tabla.titulo) + '</h2>';
    if (!tabla.filas.length) html += vacio('Sin datos', 'No hay registros para lo que elegiste.');
    else if (rep.vista === 'tabla') html += '<p class="chico" style="white-space:pre-line">' + esc(tabla.subtitulo) + '</p>' + tablaHtml(tabla);
    else html += reporteEnTarjetas();
    if (rep.persona) html += '<button class="boton texto" style="align-self:flex-start" data-accion="admRepPersonaTocar" data-valor="">← Ver todo el personal</button>';
  }
  html += '<button class="boton secundario" data-accion="admActualizarHoja">Actualizar pestañas de la hoja</button>';
  return html;
}

/** Reporte en tarjetas: totales en mosaicos y una tarjeta por persona, día o mes (todo a lo alto). */
function reporteEnTarjetas() {
  const rep = adm.rep;
  const mosaicos = t => {
    const evaluados = (t.puntuales || 0) + (t.tardes || 0);
    const datos = [['Horas', duracionCorta(t.minutos), ''], ['Días', t.dias, ''],
      ['Puntualidad', evaluados ? Math.round(t.puntuales * 100 / evaluados) + '%' : '—', 'exito'],
      ['Tardes', t.tardes, t.tardes ? 'alerta' : ''], ['Faltas', t.faltas, t.faltas ? 'error' : ''], ['Extra', duracionCorta(t.extra), '']];
    return '<div class="mosaicos">' + datos.map(d => '<div class="tarjeta mosaico"><b class="' + d[2] + '">' + esc(String(d[1])) +
      '</b><span class="chico">' + d[0] + '</span></div>').join('') + '</div>';
  };
  const tarjeta = (f, titulo, accion, valor) => {
    const etq = [];
    if (f.puntuales) etq.push(['exito', f.puntuales + ' puntuales']);
    if (f.tardes) etq.push(['alerta', f.tardes + ' tardes · ' + f.minutosTarde + ' min']);
    if (f.faltas) etq.push(['error', f.faltas + ' faltas']);
    if (f.salidasAnticipadas) etq.push(['alerta', f.salidasAnticipadas + ' salidas antes']);
    if (f.extra) etq.push(['', '+' + duracionCorta(f.extra) + ' extra']);
    if (f.permiso) etq.push(['chico', duracionCorta(f.permiso) + ' de permiso']);
    if (f.porRevisar) etq.push(['', f.porRevisar + ' por revisar']);
    return '<button class="tarjeta tarjeta-reporte" data-accion="' + accion + '" data-valor="' + esc(valor) + '"><div class="fila"><div class="crece">' +
      '<h3>' + esc(titulo) + '</h3>' + (titulo === f.nombre ? '<p class="chico">' + esc((f.rol || '') + ' · ' + (f.jornada || '')) + '</p>' : '') +
      '</div><div class="derecha"><b>' + esc(duracionCorta(f.minutos)) + '</b><p class="chico">' + f.dias + ' días</p></div></div>' +
      '<div class="etiquetas">' + etq.map(e => '<span class="etiqueta ' + e[0] + '">' + esc(e[1]) + '</span>').join('') + '</div></button>';
  };
  if (rep.mes) {
    const r = adm.reporte;
    if (!rep.persona) {
      const filas = r.filas.slice().sort((a, b) => b.minutos - a.minutos);
      return mosaicos(totalFilas('Total', filas)) + filas.map(f => tarjeta(f, f.nombre, 'admRepPersonaTocar', f.nombre)).join('');
    }
    const res = r.filas.find(f => f.nombre === rep.persona);
    const dias = r.detalle.filter(d => d.nombre === rep.persona).sort((a, b) => a.fecha.localeCompare(b.fecha));
    const DIAS = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'];
    return (res ? mosaicos(res) : '') + '<div class="tarjeta lista-dias">' + dias.map(d => {
      const est = d.estado === 'Falta' ? ['error', 'Falta'] : d.estado === 'Día libre' ? ['chico', d.estadoDia] :
        d.estado === 'Actividad especial' ? ['', 'Actividad'] : d.estado === 'Tarde' ? ['alerta', 'Tarde ' + d.minutosTarde + ' min'] : ['exito', 'Puntual'];
      const detalle = [d.horas ? d.horas + ' h' : '', Number(d.minutosAntes) > 0 ? 'salió ' + d.minutosAntes + ' min antes' : '',
        d.extra && d.extra !== '0:00' ? '+' + d.extra + ' extra' : '', d.permiso ? 'permiso ' + d.permiso : '', d.observacion || ''].filter(Boolean).join(' · ');
      const f = new Date(d.fecha + 'T12:00:00');
      return '<div class="fila-dia"><b class="fecha-dia">' + DIAS[f.getDay()] + ' ' + d.fecha.slice(8) + '</b><div class="crece"><span>' +
        esc(d.entrada ? d.entrada + ' – ' + (d.salida || 'sin salida') : '—') + '</span>' + (detalle ? '<p class="chico">' + esc(detalle) + '</p>' : '') +
        '</div><span class="etiqueta ' + est[0] + '">' + esc(est[1]) + '</span></div>';
    }).join('') + '</div>';
  }
  const r = adm.anual;
  if (!rep.persona) {
    const grupos = {};
    r.filas.forEach(f => { (grupos[f.nombre] = grupos[f.nombre] || []).push(f); });
    const personas = Object.keys(grupos).map(n => Object.assign(totalFilas(n, grupos[n]),
      { rol: grupos[n][grupos[n].length - 1].rol, jornada: grupos[n][grupos[n].length - 1].jornada })).sort((a, b) => b.minutos - a.minutos);
    return mosaicos(totalFilas('Total', personas)) + personas.map(f => tarjeta(f, f.nombre, 'admRepPersonaTocar', f.nombre)).join('');
  }
  const meses = r.filas.filter(f => f.nombre === rep.persona).sort((a, b) => a.mes.localeCompare(b.mes));
  return mosaicos(totalFilas('Total', meses)) + meses.map(f => tarjeta(f, nombreMesWeb(f.mes), 'admRepMesTocar', f.mes)).join('');
}

const duracionCorta = min => { min = Math.round(min || 0); return min < 60 ? min + 'm' : Math.floor(min / 60) + 'h ' + (min % 60) + 'm'; };

// ----- Solicitudes: lista para archivar y formato para firmar
function tablaSolicitudesWeb(lista) {
  const f = x => String(x || '').split('-').reverse().join('/');
  const tipo = s => s.tipo === 'VACACIONES' ? 'Vacaciones' : (s.horaInicio ? 'Permiso por horas' : 'Permiso');
  const est = { PENDIENTE: 'Pendiente', APROBADA: 'Aprobada', RECHAZADA: 'Rechazada', CANCELADA: 'Cancelada' };
  return { titulo: 'Solicitudes de permiso y vacaciones',
    subtitulo: 'Colegio Mesón · ' + lista.length + ' solicitudes · ' + lista.filter(s => s.estado === 'APROBADA').length + ' aprobadas · ' +
      lista.filter(s => s.estado === 'PENDIENTE').length + ' pendientes · ' + lista.filter(s => s.estado === 'RECHAZADA').length + ' rechazadas',
    encabezados: ['Pedida el', 'Nombre', 'Rol', 'Tipo', 'Fechas', 'Horario', 'Motivo', 'Estado', 'Revisada por', 'Respuesta'],
    filas: lista.map(s => [celda(f(s.creada)), celda(s.nombre), celda(s.rol), celda(tipo(s)),
      celda(s.desde === s.hasta ? f(s.desde) : f(s.desde) + ' al ' + f(s.hasta)), celda(s.horaInicio ? s.horaInicio + '–' + s.horaFin : 'Día completo'),
      celda(s.motivo), celda(est[s.estado] || s.estado), celda(s.revisadaPor), celda(s.respuesta)]),
    firmas: ['Elaborado por', 'Firma del Director o Supervisor'] };
}

/** Líneas de firma a lo ancho de la página, con el texto debajo. */
function dibujarFirmasPdf(doc, firmas, x0, ancho, y) {
  const espacio = ancho / firmas.length;
  doc.setDrawColor(28); doc.setLineWidth(0.8);
  firmas.forEach((texto, i) => {
    const partes = texto.split('\n');
    const centro = x0 + espacio * i + espacio / 2;
    const mitad = Math.min(espacio * 0.38, 130);
    doc.line(centro - mitad, y, centro + mitad, y);
    doc.setFont('helvetica', 'normal'); doc.setFontSize(9); doc.setTextColor(28);
    doc.text(partes[0], centro, y + 12, { align: 'center' });
    doc.setFontSize(8); doc.setTextColor(94);
    partes.slice(1).forEach((p, k) => doc.text(p, centro, y + 23 + k * 10, { align: 'center' }));
  });
}

/** Formato de una solicitud (A4 vertical) para imprimir, firmar y archivar. Igual al de la app de Android. */
function solicitudPdfWeb(s) {
  const { jsPDF } = window.jspdf;
  const doc = new jsPDF({ orientation: 'portrait', unit: 'pt', format: 'a4' });
  const ancho = 595, alto = 842, m = 48, util = ancho - 2 * m;
  const f = x => String(x || '').split('-').reverse().join('/');
  const tipoTexto = s.tipo === 'VACACIONES' ? 'Vacaciones' : (s.horaInicio ? 'Permiso por horas' : 'Permiso');
  const texto = (t, x, y, tam, negrita, color, opciones) => {
    doc.setFont('helvetica', negrita ? 'bold' : 'normal'); doc.setFontSize(tam);
    doc.setTextColor.apply(doc, color || [28, 28, 28]); doc.text(String(t), x, y, opciones || {});
  };
  const gris = [94, 94, 93], vino = [107, 20, 18];
  doc.setFillColor(166, 32, 34); doc.rect(0, 0, ancho, 8, 'F');
  let y = m + 4;
  if (logoColegio) doc.addImage(logoColegio, 'PNG', m, 22, 56 * LOGO_PROPORCION, 56, 'logo', 'FAST');
  else texto('COLEGIO MESÓN', m, y, 18, true, vino);
  texto('No. ' + String(s.id).slice(0, 8).toUpperCase(), ancho - m, y, 10, true, null, { align: 'right' });
  texto('Control de asistencia · Mesón Asiste', ancho - m, y + 16, 9.5, false, gris, { align: 'right' });
  if (s.creada) texto('Solicitada el ' + f(s.creada), ancho - m, y + 30, 9.5, false, gris, { align: 'right' });
  y = 112;
  texto(s.tipo === 'VACACIONES' ? 'SOLICITUD DE VACACIONES' : 'SOLICITUD DE PERMISO', ancho / 2, y, 15, true, null, { align: 'center' });
  y += 26;
  doc.setDrawColor(189); doc.setLineWidth(0.8);
  const seccion = nombre => { doc.setFillColor(246, 241, 241); doc.rect(m, y, util, 18, 'F'); texto(nombre.toUpperCase(), m + 8, y + 12.5, 9, true, vino); y += 30; };
  const campo = (etq, valor, x, w) => { texto(etq, x, y, 8.5, false, gris); texto(valor || '—', x, y + 14, 11); doc.setDrawColor(189); doc.line(x, y + 19, x + w - 12, y + 19); };
  const parrafo = (t, min) => {
    doc.setFont('helvetica', 'normal'); doc.setFontSize(10.5);
    const lineas = doc.splitTextToSize(t || '—', util - 16);
    const h = Math.max(min, lineas.length * 14 + 14);
    doc.setDrawColor(189); doc.rect(m, y, util, h);
    doc.setTextColor(28); lineas.forEach((l, i) => doc.text(l, m + 8, y + 16 + i * 14));
    y += h + 16;
  };
  seccion('Datos del solicitante');
  campo('Nombre completo', s.nombre, m, util * 0.6); campo('Puesto o rol', s.rol, m + util * 0.6, util * 0.4);
  y += 34; campo('Correo institucional', s.correo, m, util); y += 40;
  seccion('Detalle de la solicitud');
  const unDia = s.desde === s.hasta;
  const dias = Math.round((new Date(s.hasta + 'T12:00:00') - new Date(s.desde + 'T12:00:00')) / 86400000) + 1;
  campo('Tipo', tipoTexto, m, util / 3); campo(unDia ? 'Fecha' : 'Desde', f(s.desde), m + util / 3, util / 3);
  if (s.horaInicio) campo('Horario', s.horaInicio + ' a ' + s.horaFin, m + 2 * util / 3, util / 3);
  else if (unDia) campo('Duración', '1 día', m + 2 * util / 3, util / 3);
  else campo('Hasta', f(s.hasta) + ' (' + dias + ' días)', m + 2 * util / 3, util / 3);
  y += 40; texto('Motivo', m, y, 8.5, false, gris); y += 6; parrafo(s.motivo, 60);
  seccion('Resolución');
  let x = m;
  [['APROBADA', 'Aprobada'], ['RECHAZADA', 'Rechazada'], ['PENDIENTE', 'Pendiente']].forEach(([clave, t]) => {
    doc.setDrawColor(189); doc.rect(x, y - 10, 11, 11);
    if (s.estado === clave) { doc.setDrawColor(166, 32, 34); doc.setLineWidth(1.8); doc.line(x + 2, y - 4, x + 5, y - 1); doc.line(x + 5, y - 1, x + 10, y - 9); doc.setLineWidth(0.8); }
    texto(t, x + 17, y, 10.5); x += 110;
  });
  y += 24;
  campo('Revisada por', s.revisadaPor, m, util * 0.6); campo('Fecha de revisión', f(s.revisadaEl), m + util * 0.6, util * 0.4);
  y += 40; texto('Observaciones', m, y, 8.5, false, gris); y += 6; parrafo(s.respuesta, 46);
  dibujarFirmasPdf(doc, ['Firma del solicitante\n' + s.nombre, 'Firma del Director o Supervisor' + (s.revisadaPor ? '\n' + s.revisadaPor : '')],
    m, util, Math.max(y + 56, alto - 150));
  texto(pieReporte(), m, alto - 24, 7.5, false, [153, 153, 153]);
  const limpio = t => String(t || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^A-Za-z0-9-]+/g, '_');
  guardarBlob(doc.output('blob'), ['Solicitud', limpio(tipoTexto), limpio(s.nombre), s.desde].join('_') + '.pdf', 'application/pdf');
}

function tablaHtml(t) {
  const fila = (celdas, tag) => '<tr>' + celdas.map(c => '<' + tag + '>' + esc(c.texto) + '</' + tag + '>').join('') + '</tr>';
  return '<div class="tabla-envoltura"><table class="tabla"><thead>' + fila(t.encabezados.map(h => ({ texto: h })), 'th') + '</thead><tbody>' +
    t.filas.map(f => fila(f, 'td')).join('') + '</tbody>' + (t.totales ? '<tfoot>' + fila(t.totales, 'th') + '</tfoot>' : '') + '</table></div>';
}

// Tablas (iguales a las de la app de Android)
const celdaHoras = min => ({ texto: Math.floor(min / 60) + ':' + String(min % 60).padStart(2, '0'), numero: Math.round(min / 60 * 100) / 100 });
const celdaNum = n => ({ texto: String(n || 0), numero: Number(n || 0) });
const celda = t => ({ texto: t == null ? '' : String(t) });
const ENC_RESUMEN = ['Días', 'Puntuales', 'Tardes', 'Min tarde', 'Salidas antic.', 'Faltas', 'Incompletos', 'Actividades', 'Horas', 'Horas extra', 'Por revisar', 'Horas permiso'];
const celdasResumenWeb = f => [celdaNum(f.dias), celdaNum(f.puntuales), celdaNum(f.tardes), celdaNum(f.minutosTarde), celdaNum(f.salidasAnticipadas),
  celdaNum(f.faltas), celdaNum(f.incompletos), celdaNum(f.actividades), celdaHoras(f.minutos || 0), celdaHoras(f.extra || 0), celdaNum(f.porRevisar), celdaHoras(f.permiso || 0)];
function totalFilas(nombre, filas) {
  const t = { nombre: nombre };
  ['dias', 'puntuales', 'tardes', 'minutosTarde', 'salidasAnticipadas', 'faltas', 'incompletos', 'actividades', 'libres', 'minutos', 'extra', 'permiso', 'porRevisar']
    .forEach(k => { t[k] = filas.reduce((s, f) => s + Number(f[k] || 0), 0); });
  return t;
}
const textoTotales = t => 'Total: ' + duracion(t.minutos) + ' trabajadas · ' + t.dias + ' días · ' + t.puntuales + ' puntuales · ' +
  t.tardes + ' tardes · ' + t.faltas + ' faltas · ' + duracion(t.extra) + ' extra';

function tablaReporteActual() {
  const rep = adm.rep;
  if (rep.mes) {
    const r = adm.reporte;
    if (!r || r.mes !== rep.mes) return null;
    if (!rep.persona) {
      const filas = r.filas.slice().sort((a, b) => b.minutos - a.minutos);
      const t = totalFilas('Total', filas);
      return { titulo: 'Resumen general · ' + nombreMesWeb(r.mes), subtitulo: 'Colegio Mesón · ' + filas.length + ' personas\n' + textoTotales(t),
        encabezados: ['Nombre', 'Rol', 'Jornada'].concat(ENC_RESUMEN),
        filas: filas.map(f => [celda(f.nombre), celda(f.rol), celda(f.jornada)].concat(celdasResumenWeb(f))),
        totales: [celda('Total'), celda(''), celda('')].concat(celdasResumenWeb(t)), firmas: FIRMAS_GENERAL };
    }
    const res = r.filas.find(f => f.nombre === rep.persona);
    const dias = r.detalle.filter(d => d.nombre === rep.persona).sort((a, b) => a.fecha.localeCompare(b.fecha));
    return { titulo: rep.persona + ' · ' + nombreMesWeb(r.mes),
      subtitulo: 'Colegio Mesón' + (res ? ' · ' + res.rol + ' · ' + res.jornada + '\n' + textoTotales(totalFilas('', [res])) : ''),
      encabezados: ['Fecha', 'Entrada', 'Salida', 'Horas', 'Estado', 'Ent. status', 'Min tarde', 'Min antes', 'Extra', 'Permiso', 'Revisión', 'Observación'],
      filas: dias.map(d => [celda(d.fecha.split('-').reverse().join('/')), celda(d.entrada), celda(d.salida), celda(d.horas),
        celda(d.estadoDia || d.estado), celda(d.estado === 'Puntual' || d.estado === 'Tarde' ? d.estado : '—'),
        { texto: String(d.minutosTarde === '' ? '' : d.minutosTarde), numero: d.minutosTarde === '' ? null : Number(d.minutosTarde) },
        { texto: String(d.minutosAntes === '' ? '' : d.minutosAntes), numero: d.minutosAntes === '' ? null : Number(d.minutosAntes) },
        celda(d.extra), celda(d.permiso || ''), celda(d.revision === 'No aplica' ? '' : d.revision), celda(d.observacion)]),
      totales: res ? [celda('Total'), celda(''), celda(''), celdaHoras(res.minutos), celda(res.dias + ' días'), celda(res.tardes + ' tardes'),
        celdaNum(res.minutosTarde), celda(res.salidasAnticipadas + ' salidas'), celdaHoras(res.extra), celdaHoras(res.permiso || 0), celda(''), celda('')] : null,
      firmas: firmasPersona(rep.persona) };
  }
  const r = adm.anual;
  if (!r || r.anio !== rep.anio) return null;
  if (!rep.persona) {
    const grupos = {};
    r.filas.forEach(f => { (grupos[f.nombre] = grupos[f.nombre] || []).push(f); });
    const personas = Object.keys(grupos).map(n => Object.assign(totalFilas(n, grupos[n]),
      { rol: grupos[n][grupos[n].length - 1].rol, jornada: grupos[n][grupos[n].length - 1].jornada })).sort((a, b) => b.minutos - a.minutos);
    const t = totalFilas('Total', personas);
    return { titulo: 'Resumen anual general · ' + r.anio, subtitulo: 'Colegio Mesón · ' + personas.length + ' personas · ' + r.meses.length + ' meses\n' + textoTotales(t),
      encabezados: ['Nombre', 'Rol', 'Jornada'].concat(ENC_RESUMEN),
      filas: personas.map(f => [celda(f.nombre), celda(f.rol), celda(f.jornada)].concat(celdasResumenWeb(f))),
      totales: [celda('Total'), celda(''), celda('')].concat(celdasResumenWeb(t)), firmas: FIRMAS_GENERAL };
  }
  const meses = r.filas.filter(f => f.nombre === rep.persona).sort((a, b) => a.mes.localeCompare(b.mes));
  const t = totalFilas('Total', meses);
  return { titulo: rep.persona + ' · Resumen anual ' + r.anio,
    subtitulo: 'Colegio Mesón' + (meses.length ? ' · ' + meses[meses.length - 1].rol + ' · ' + meses[meses.length - 1].jornada : '') + '\n' + textoTotales(t),
    encabezados: ['Mes'].concat(ENC_RESUMEN), filas: meses.map(f => [celda(nombreMesWeb(f.mes))].concat(celdasResumenWeb(f))),
    totales: [celda('Total')].concat(celdasResumenWeb(t)), firmas: firmasPersona(rep.persona) };
}
const FIRMAS_GENERAL = ['Elaborado por', 'Vo. Bo. Director'];
const firmasPersona = nombre => ['Firma del colaborador\n' + nombre, 'Firma del Director o Supervisor'];

function nombreArchivoReporte(ext) {
  const rep = adm.rep;
  const limpio = s => s.normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^A-Za-z0-9]+/g, '_');
  return 'Asistencia_' + (rep.mes ? limpio(nombreMesWeb(rep.mes)) : 'Ano_' + rep.anio) + '_' + (rep.persona ? limpio(rep.persona) : 'General') + '.' + ext;
}

/** Logo del colegio (PNG) para los PDF y Excel; se descarga una vez al abrir la app (queda guardado sin internet). */
let logoColegio = null;
const LOGO_PROPORCION = 1005 / 519;
if (typeof fetch === 'function') {
  fetch('iconos/logo.png').then(r => r.ok ? r.arrayBuffer() : null).then(b => { if (b) logoColegio = new Uint8Array(b); }).catch(() => {});
}

const pieReporte = () => 'Mesón Asiste · Creado por ' + CONFIG.CREADOR + ' · Generado el ' + new Date().toLocaleString('es-GT');

function descargarPdf(t, nombre) {
  const { jsPDF } = window.jspdf;
  const doc = new jsPDF({ orientation: 'landscape', unit: 'pt', format: 'a4' });
  doc.setFont('helvetica', 'bold'); doc.setFontSize(16); doc.setTextColor(107, 20, 18);
  doc.text(t.titulo, 32, 40);
  doc.setFont('helvetica', 'normal'); doc.setFontSize(9.5); doc.setTextColor(94, 94, 93);
  t.subtitulo.split('\n').forEach((l, i) => doc.text(l, 32, 58 + i * 12));
  doc.autoTable({
    startY: 64 + t.subtitulo.split('\n').length * 12,
    head: [t.encabezados], body: t.filas.map(f => f.map(c => c.texto)), foot: t.totales ? [t.totales.map(c => c.texto)] : undefined,
    styles: { fontSize: 8, cellPadding: 3 }, headStyles: { fillColor: [166, 32, 34] }, footStyles: { fillColor: [237, 227, 227], textColor: 20 },
    alternateRowStyles: { fillColor: [246, 241, 241] }, margin: { left: 32, right: 32, bottom: 30 },
    didDrawPage: () => {
      if (logoColegio) {
        const w = 40 * LOGO_PROPORCION;
        doc.addImage(logoColegio, 'PNG', doc.internal.pageSize.getWidth() - 32 - w, 22, w, 40, 'logo', 'FAST');
      }
      doc.setFontSize(7.5); doc.setTextColor(153);
      doc.text(pieReporte() + ' · Página ' + doc.internal.getNumberOfPages(), 32, doc.internal.pageSize.getHeight() - 14);
    },
  });
  if (t.firmas && t.firmas.length) {
    let y = (doc.lastAutoTable ? doc.lastAutoTable.finalY : 80) + 60;
    if (y + 40 > doc.internal.pageSize.getHeight() - 36) {
      doc.addPage(); y = 100;
      doc.setFontSize(7.5); doc.setTextColor(153);
      doc.text(pieReporte() + ' · Página ' + doc.internal.getNumberOfPages(), 32, doc.internal.pageSize.getHeight() - 14);
    }
    dibujarFirmasPdf(doc, t.firmas, 32, doc.internal.pageSize.getWidth() - 64, y);
  }
  guardarBlob(doc.output('blob'), nombre || nombreArchivoReporte('pdf'), 'application/pdf');
}

/** .xlsx sencillo (una hoja, sin compresión) que abre Excel, Google Sheets o Números del iPhone. */
function descargarExcel(t, nombre) {
  const xml = s => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  const col = i => { let s = ''; i++; while (i > 0) { const m = (i - 1) % 26; s = String.fromCharCode(65 + m) + s; i = Math.floor((i - 1) / 26); } return s; };
  const logo = logoColegio;
  const filas = (logo ? [[[], false], [[], false], [[], false], [[], false]] : []).concat([[[celda(t.titulo)], true]]).concat(t.subtitulo.split('\n').map(l => [[celda(l)], false]), [[[], false], [t.encabezados.map(celda), true]],
    t.filas.map(f => [f, false]), t.totales ? [[t.totales, true]] : [], [[[], false], [[celda(pieReporte())], false]]);
  const anchos = t.encabezados.map((h, c) => Math.min(45, Math.max(8, 2 + Math.max(h.length, ...t.filas.map(f => (f[c] ? f[c].texto.length : 0))))));
  let hoja = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><cols>' +
    anchos.map((w, i) => '<col min="' + (i + 1) + '" max="' + (i + 1) + '" width="' + w + '" customWidth="1"/>').join('') + '</cols><sheetData>';
  filas.forEach(([celdas, negrita], r) => {
    hoja += '<row r="' + (r + 1) + '">' + celdas.map((c, i) => {
      const ref = col(i) + (r + 1);
      const s = negrita ? ' s="1"' : '';
      return c.numero != null && isFinite(c.numero) && !negrita ? '<c r="' + ref + '"' + s + '><v>' + c.numero + '</v></c>'
        : '<c r="' + ref + '" t="inlineStr"' + s + '><is><t xml:space="preserve">' + xml(c.texto) + '</t></is></c>';
    }).join('') + '</row>';
  });
  hoja += '</sheetData>' + (logo ? '<drawing r:id="rId1"/>' : '') + '</worksheet>';
  const archivos = {
    '[Content_Types].xml': '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>',
    '_rels/.rels': '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>',
    'xl/workbook.xml': '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Reporte" sheetId="1" r:id="rId1"/></sheets></workbook>',
    'xl/_rels/workbook.xml.rels': '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>',
    'xl/styles.xml': '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs></styleSheet>',
    'xl/worksheets/sheet1.xml': hoja,
  };
  if (logo) {
    const alto = 685800, ancho = Math.round(alto * LOGO_PROPORCION); // 0.75 pulgadas de alto (EMU)
    archivos['[Content_Types].xml'] = archivos['[Content_Types].xml'].replace('<Default Extension="xml" ContentType="application/xml"/>',
      '<Default Extension="xml" ContentType="application/xml"/><Default Extension="png" ContentType="image/png"/>' +
      '<Override PartName="/xl/drawings/drawing1.xml" ContentType="application/vnd.openxmlformats-officedocument.drawing+xml"/>');
    archivos['xl/worksheets/_rels/sheet1.xml.rels'] = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing" Target="../drawings/drawing1.xml"/></Relationships>';
    archivos['xl/drawings/_rels/drawing1.xml.rels'] = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/logo.png"/></Relationships>';
    archivos['xl/drawings/drawing1.xml'] = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><xdr:wsDr xmlns:xdr="http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><xdr:oneCellAnchor><xdr:from><xdr:col>0</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>0</xdr:row><xdr:rowOff>38100</xdr:rowOff></xdr:from><xdr:ext cx="' + ancho + '" cy="' + alto + '"/><xdr:pic><xdr:nvPicPr><xdr:cNvPr id="2" name="Logo"/><xdr:cNvPicPr><a:picLocks noChangeAspect="1"/></xdr:cNvPicPr></xdr:nvPicPr><xdr:blipFill><a:blip r:embed="rId1"/><a:stretch><a:fillRect/></a:stretch></xdr:blipFill><xdr:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="' + ancho + '" cy="' + alto + '"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></xdr:spPr></xdr:pic><xdr:clientData/></xdr:oneCellAnchor></xdr:wsDr>';
    archivos['xl/media/logo.png'] = logo;
  }
  guardarBlob(zipSinCompresion(archivos), nombre || nombreArchivoReporte('xlsx'), 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
}

/** Arma un .zip (sin compresión) con los archivos dados { nombre: texto }. */
function zipSinCompresion(archivos) {
  const enc = new TextEncoder();
  const tabla = []; for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1; tabla[n] = c >>> 0; }
  const crc32 = b => { let c = 0xFFFFFFFF; for (let i = 0; i < b.length; i++) c = tabla[(c ^ b[i]) & 0xFF] ^ (c >>> 8); return (c ^ 0xFFFFFFFF) >>> 0; };
  const partes = [], central = [];
  let offset = 0;
  Object.keys(archivos).forEach(nombre => {
    const contenido = archivos[nombre];
    const datos = contenido instanceof Uint8Array ? contenido : enc.encode(contenido), nom = enc.encode(nombre), crc = crc32(datos);
    const local = new DataView(new ArrayBuffer(30));
    local.setUint32(0, 0x04034b50, true); local.setUint16(4, 20, true); local.setUint16(6, 0x0800, true); local.setUint16(8, 0, true);
    local.setUint32(14, crc, true); local.setUint32(18, datos.length, true); local.setUint32(22, datos.length, true); local.setUint16(26, nom.length, true);
    partes.push(new Uint8Array(local.buffer), nom, datos);
    const cen = new DataView(new ArrayBuffer(46));
    cen.setUint32(0, 0x02014b50, true); cen.setUint16(4, 20, true); cen.setUint16(6, 20, true); cen.setUint16(8, 0x0800, true);
    cen.setUint32(16, crc, true); cen.setUint32(20, datos.length, true); cen.setUint32(24, datos.length, true); cen.setUint16(28, nom.length, true);
    cen.setUint32(42, offset, true);
    central.push(new Uint8Array(cen.buffer), nom);
    offset += 30 + nom.length + datos.length;
  });
  const tamCentral = central.reduce((s, p) => s + p.length, 0);
  const fin = new DataView(new ArrayBuffer(22));
  fin.setUint32(0, 0x06054b50, true); fin.setUint16(8, Object.keys(archivos).length, true); fin.setUint16(10, Object.keys(archivos).length, true);
  fin.setUint32(12, tamCentral, true); fin.setUint32(16, offset, true);
  return new Blob(partes.concat(central, [new Uint8Array(fin.buffer)]), { type: 'application/zip' });
}

/** En iPhone abre "Compartir" (Guardar en Archivos, WhatsApp…); en computadora o Android, descarga el archivo. */
async function guardarBlob(blob, nombre, tipo) {
  const archivo = new File([blob], nombre, { type: tipo });
  if (esIOS && navigator.canShare && navigator.canShare({ files: [archivo] })) {
    try { await navigator.share({ files: [archivo], title: nombre }); return; } catch (e) { if (e.name === 'AbortError') return; }
  }
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url; a.download = nombre; document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 10000);
  mostrarAviso('Archivo descargado: ' + nombre);
}

// ----- Ajustes
function seccionAjustes() {
  const c = adm.config;
  if (!c) return '';
  const metodos = { UBICACION: 'Solo ubicación', WIFI: 'Wi-Fi', QR: 'Código QR' };
  let html = '<div class="tarjeta"><h2>Reglas de asistencia</h2><div class="rejilla">' +
    '<label>Meta de horas por día<select id="aj-meta">' + [4, 5, 6, 7, 8, 9, 10, 11, 12].map(h => '<option' + (c.horasMeta === h ? ' selected' : '') + '>' + h + '</option>').join('') + '</select></label>' +
    '<label>Minutos de gracia<select id="aj-tol">' + [0, 5, 10, 15, 20, 30].map(m => '<option value="' + m + '"' + (c.tolerancia === m ? ' selected' : '') + '>' + (m ? m + ' min' : 'Sin') + '</option>').join('') + '</select></label>' +
    '</div><button class="boton" data-accion="admGuardarReglas">Guardar reglas</button></div>';
  html += '<div class="tarjeta"><h2>Ubicación del colegio</h2><p class="chico">' + (c.sedes.length ? 'El personal solo puede marcar dentro del radio (salvo con "Actividad especial").'
    : 'Sin ubicación: el personal puede marcar desde cualquier lugar.') + '</p>';
  c.sedes.forEach(s => {
    html += '<hr class="separador"><div class="fila"><div class="crece"><h3>' + esc(s.nombre) + '</h3><p class="chico">Radio ' + s.radio + ' m · ' + esc(metodos[s.metodo] || s.metodo) + '</p></div>' +
      '<a class="boton texto" href="https://maps.google.com/?q=' + s.latitud + ',' + s.longitud + '" target="_blank" rel="noopener">Mapa</a></div>' +
      '<div class="fila"><button class="boton texto" data-accion="admEditarSede" data-valor="' + esc(s.id) + '">Editar</button>' +
      '<button class="boton texto" data-accion="admVerificacion" data-valor="' + esc(s.id) + '">Verificación</button>' +
      (s.metodo === 'QR' ? '<button class="boton texto" data-accion="admMostrarQr" data-valor="' + esc(s.id) + '">Mostrar QR</button>' : '') +
      '<button class="boton texto peligro" data-accion="admQuitarSede" data-valor="' + esc(s.id) + '">Quitar</button></div>';
  });
  html += '<button class="boton secundario" data-accion="admNuevaSede">+ Agregar ubicación del colegio</button></div>';
  return html;
}

// ---------------------------------------------------------------- Diálogos y acciones

const opcion = (valor, texto, elegido) => '<option value="' + esc(valor) + '"' + (elegido ? ' selected' : '') + '>' + esc(texto) + '</option>';
const casilla = (id, texto, marcada, extra) => '<label class="interruptor"><input type="checkbox" id="' + id + '"' + (marcada ? ' checked' : '') + (extra || '') + '><span>' + esc(texto) + '</span></label>';

function sumarMinutosHora(hora, min) {
  if (!/^\d{2}:\d{2}$/.test(hora || '')) return '';
  const t = ((aMinutos(hora) + min) % 1440 + 1440) % 1440;
  return String(Math.floor(t / 60)).padStart(2, '0') + ':' + String(t % 60).padStart(2, '0');
}

/** "14.63, -90.50" o un enlace de Google Maps → [lat, lon] (o null). */
function leerCoordenadasWeb(texto) {
  const t = String(texto || '').trim();
  const n = '(-?\\d{1,3}(?:\\.\\d+)?)';
  const patrones = [new RegExp('!3d' + n + '!4d' + n), new RegExp('@' + n + ',\\s*' + n), new RegExp('[?&](?:q|query|ll|center)=' + n + ',\\s*' + n),
    new RegExp('^' + n + '\\s*[,;\\s]\\s*' + n + '$')];
  for (const p of patrones) {
    const m = p.exec(t);
    if (!m) continue;
    const lat = Number(m[1]), lon = Number(m[2]);
    if (Math.abs(lat) <= 90 && Math.abs(lon) <= 180 && !(lat === 0 && lon === 0)) return [lat, lon];
  }
  return null;
}

let qrIntervalo = null;

const accionesAdmin = {
  admSeccion(b) { adm.seccion = b.dataset.valor; render(); cargarSeccionAdmin(); },
  admRecargar() { cargarSeccionAdmin(); },

  // ----- Solicitudes y Por revisar
  admSolicitud(b) {
    const aprobar = b.dataset.aprobar === 'si';
    mostrarDialogo('', aprobar ? '¿Aprobar la solicitud?' : '¿Rechazar la solicitud?', aprobar ? 'Se agregará al calendario de la persona.' : '',
      [{ texto: aprobar ? 'Aprobar' : 'Rechazar', accion: 'admConfirmarSolicitud' }, { texto: 'Cancelar', accion: 'cerrarDialogo', secundario: true }],
      '<label>Respuesta (opcional)<textarea id="sol-respuesta" maxlength="200"></textarea></label><input type="hidden" id="sol-id" value="' + esc(b.dataset.valor) +
      '"><input type="hidden" id="sol-aprobar" value="' + (aprobar ? 'si' : 'no') + '">');
  },
  admConfirmarSolicitud() {
    hacerAdmin('adminRevisarSolicitud', { id: $('sol-id').value, aprobar: $('sol-aprobar').value === 'si', respuesta: $('sol-respuesta').value.trim() });
  },
  admRevisar(b) { hacerAdmin('adminRevisarActividad', { id: b.dataset.valor, aprobar: b.dataset.aprobar === 'si' }); },
  solicitudPdf(b) {
    const s = (adm.solicitudes || []).concat(estado.solicitudes || []).find(x => x.id === b.dataset.valor);
    if (s) solicitudPdfWeb(s);
  },
  admSolicitudesPdf() { descargarPdf(tablaSolicitudesWeb(adm.solicitudes || []), 'Solicitudes_Lista.pdf'); },
  admSolicitudesExcel() { descargarExcel(tablaSolicitudesWeb(adm.solicitudes || []), 'Solicitudes_Lista.xlsx'); },

  // ----- Miembros
  admEditarMiembro(b) {
    const m = adm.miembros.find(x => x.correo === b.dataset.valor);
    if (!m) return;
    const roles = adm.roles.filter(r => r.codigo === m.rol || (rolProtegido(r) ? esSuperAdminWeb() : puedeWeb('ROLES') || !rolGestiona(r)));
    mostrarDialogo('', m.nombre, m.correo, [
      { texto: 'Guardar', accion: 'admGuardarMiembro' },
      ...(m.conTelefono ? [{ texto: 'Liberar teléfono (' + (m.modelo || 'teléfono') + ')', accion: 'admLiberarMiembro', secundario: true }] : []),
      { texto: 'Eliminar miembro', accion: 'admEliminarMiembro', secundario: true }, { texto: 'Cancelar', accion: 'cerrarDialogo', secundario: true }],
      '<input type="hidden" id="mi-correo" value="' + esc(m.correo) + '">' + casilla('mi-activo', 'Acceso activo (puede usar la app)', m.estado === 'ACTIVO') +
      '<label>Rol<select id="mi-rol">' + roles.map(r => opcion(r.codigo, r.nombre, r.codigo === m.rol)).join('') + '</select></label>' +
      '<label>Jornada (si su rol marca asistencia)<select id="mi-jornada">' + adm.jornadasLista.map(j =>
        opcion(j.codigo, j.nombre + ' (' + textoHorario(j.entrada, j.salida) + ', ' + textoDias(j.dias || []) + ')', j.codigo === m.jornada)).join('') + '</select></label>');
  },
  admGuardarMiembro() {
    const m = adm.miembros.find(x => x.correo === $('mi-correo').value);
    const activo = $('mi-activo').checked;
    conOcupado(async () => {
      let r = await pedirAdmin('adminAsignar', { objetivo: m.correo, rol: $('mi-rol').value, jornada: $('mi-jornada').value });
      if (activo !== (m.estado === 'ACTIVO')) r = await pedirAdmin('adminEstado', { objetivo: m.correo, estado: activo ? 'ACTIVO' : 'INACTIVO' });
      cerrarDialogo();
      mostrarAviso(r.mensaje || 'Cambios guardados.');
      await cargarSeccionAdmin(true);
    });
  },
  admLiberarMiembro() { hacerAdmin('adminLiberar', { objetivo: $('mi-correo').value }); },
  admEliminarMiembro() {
    const correo = $('mi-correo').value;
    mostrarDialogo('⚠️', '¿Eliminar a este miembro?', 'Se le cierra el acceso y deja de aparecer. Su historial queda en la hoja.',
      [{ texto: 'Eliminar', accion: 'admConfirmarEliminarMiembro' }, { texto: 'Cancelar', accion: 'cerrarDialogo', secundario: true }],
      '<input type="hidden" id="mi-correo" value="' + esc(correo) + '">');
  },
  admConfirmarEliminarMiembro() { hacerAdmin('adminEliminarMiembro', { objetivo: $('mi-correo').value }); },

  // ----- Jornadas
  admNuevaJornada() { dialogoJornada(null); },
  admEditarJornada(b) { dialogoJornada((adm.jornadas || []).map(x => x.j).find(j => j.codigo === b.dataset.valor)); },
  admJorEntrada() {
    // La salida se calcula con la meta más el almuerzo (p. ej. 7:00 + 8 h + 1 h = 16:00).
    const meta = Math.round((perfil().horasMeta || 8) * 60) + Number($('jo-almuerzo').value || 0);
    if ($('jo-auto').value === 'si') $('jo-salida').value = sumarMinutosHora($('jo-entrada').value, meta);
    avisoJornada();
  },
  admJorSalida() { $('jo-auto').value = 'no'; avisoJornada(); },
  admJorDias() { for (let d = 1; d <= 7; d++) { const fila = $('jo-fila-' + d); if (fila) fila.hidden = !$('jo-dia-' + d).checked; } },
  admJorPorDia() { $('jo-pordia').hidden = !$('jo-distinto').checked; accionesAdmin.admJorDias(); },
  admGuardarJornada() {
    const entrada = $('jo-entrada').value, salida = $('jo-salida').value;
    const dias = [1, 2, 3, 4, 5, 6, 7].filter(d => $('jo-dia-' + d).checked);
    const porDia = {};
    if ($('jo-distinto').checked) dias.forEach(d => {
      const e = $('jo-e-' + d).value, s = $('jo-s-' + d).value;
      if (e && s && (e !== entrada || s !== salida)) porDia[d] = { entrada: e, salida: s };
    });
    if ($('jo-nombre').value.trim().length < 2) return mostrarAviso('Escribe el nombre de la jornada.', true);
    if (!entrada || !salida || entrada === salida) return mostrarAviso('Elige la entrada y la salida (no pueden ser iguales).', true);
    if (!dias.length) return mostrarAviso('Elige al menos un día.', true);
    hacerAdmin('adminGuardarJornada', { jornada: { codigo: $('jo-codigo').value, nombre: $('jo-nombre').value.trim(), entrada: entrada, salida: salida, dias: dias, porDia: porDia,
      almuerzo: Number($('jo-almuerzo').value || 0) } });
  },
  admEliminarJornada(b) {
    mostrarDialogo('⚠️', '¿Eliminar esta jornada?', 'Solo se puede eliminar si ningún miembro la tiene asignada.',
      [{ texto: 'Eliminar', accion: 'admConfirmarEliminarJornada' }, { texto: 'Cancelar', accion: 'cerrarDialogo', secundario: true }],
      '<input type="hidden" id="jo-eliminar" value="' + esc(b.dataset.valor) + '">');
  },
  admConfirmarEliminarJornada() { hacerAdmin('adminEliminarJornada', { codigo: $('jo-eliminar').value }); },

  // ----- Roles
  admNuevoRol() { dialogoRol(null); },
  admEditarRol(b) { dialogoRol(adm.roles.find(r => r.codigo === b.dataset.valor)); },
  admGuardarRol() {
    const permisos = Object.keys(NOMBRES_PERMISOS).filter(p => $('ro-p-' + p) && $('ro-p-' + p).checked);
    if ($('ro-nombre').value.trim().length < 2) return mostrarAviso('Escribe el nombre del rol.', true);
    hacerAdmin('adminGuardarRol', { rol: { codigo: $('ro-codigo').value, nombre: $('ro-nombre').value.trim(), marca: $('ro-marca').checked, permisos: permisos } });
  },
  admEliminarRol(b) {
    mostrarDialogo('⚠️', '¿Eliminar este rol?', 'Solo se puede eliminar si nadie lo tiene asignado.',
      [{ texto: 'Eliminar', accion: 'admConfirmarEliminarRol' }, { texto: 'Cancelar', accion: 'cerrarDialogo', secundario: true }],
      '<input type="hidden" id="ro-eliminar" value="' + esc(b.dataset.valor) + '">');
  },
  admConfirmarEliminarRol() { hacerAdmin('adminEliminarRol', { codigo: $('ro-eliminar').value }); },

  // ----- Calendario
  admNuevoEvento() {
    const hoy = claveDia(new Date());
    const marcan = (adm.miembros || []).filter(m => m.estado === 'ACTIVO' && (adm.roles.find(r => r.codigo === m.rol) || {}).marca !== false);
    mostrarDialogo('', 'Agregar al calendario', '', [{ texto: 'Agregar', accion: 'admGuardarEvento' }, { texto: 'Cancelar', accion: 'cerrarDialogo', secundario: true }],
      '<label>Tipo<select id="ev-tipo" data-cambio="admEvTipo">' + Object.keys(TIPOS_EVENTO_WEB).map(t => opcion(t, TIPOS_EVENTO_WEB[t])).join('') + '</select></label>' +
      '<div id="ev-permiso" hidden>' + casilla('ev-horas', 'Por horas (no todo el día)', false, ' data-cambio="admEvTipo"') +
      '<div class="rejilla" id="ev-horas-campos" hidden><label>Desde<input type="time" id="ev-hi"></label><label>Hasta<input type="time" id="ev-hf"></label></div></div>' +
      '<div class="rejilla" id="ev-especial" hidden><label>Entrada<input type="time" id="ev-he"></label><label>Salida (opcional)<input type="time" id="ev-hs"></label></div>' +
      '<div class="rejilla"><label>Desde<input type="date" id="ev-desde" value="' + hoy + '" data-cambio="admEvTipo"></label>' +
      '<label>Hasta<input type="date" id="ev-hasta" value="' + hoy + '" data-cambio="admEvTipo"></label></div>' +
      casilla('ev-todos', 'Para todo el personal', true, ' data-cambio="admEvTipo"') +
      '<div id="ev-personas" hidden style="max-height:180px;overflow:auto;display:flex;flex-direction:column;gap:6px">' +
      (marcan.length ? marcan.map(m => casilla('ev-p-' + m.correo, m.nombre, false, ' data-cambio="admEvTipo"')).join('') : '<p class="chico">No hay miembros registrados.</p>') + '</div>' +
      '<div id="ev-vista" style="display:flex;flex-direction:column;gap:6px"></div>' +
      '<label>Detalle (opcional)<input id="ev-detalle" maxlength="200" placeholder="Ej.: cita médica"></label>');
    accionesAdmin.admEvTipo();
  },
  admEvTipo() {
    const tipo = $('ev-tipo').value;
    $('ev-permiso').hidden = tipo !== 'PERMISO';
    $('ev-horas-campos').hidden = !(tipo === 'PERMISO' && $('ev-horas').checked);
    $('ev-especial').hidden = tipo !== 'ENTRADA_ESPECIAL';
    $('ev-personas').hidden = $('ev-todos').checked;
    if ($('ev-hasta').value < $('ev-desde').value) $('ev-hasta').value = $('ev-desde').value;
    vistaVacacionesEvento();
  },
  admGuardarEvento() {
    const tipo = $('ev-tipo').value;
    const porHorasE = tipo === 'PERMISO' && $('ev-horas').checked;
    const elegidos = (adm.miembros || []).filter(m => $('ev-p-' + m.correo) && $('ev-p-' + m.correo).checked).map(m => m.correo);
    if (!$('ev-todos').checked && !elegidos.length) return mostrarAviso('Elige al menos una persona.', true);
    if (porHorasE && (!$('ev-hi').value || !$('ev-hf').value || $('ev-hf').value <= $('ev-hi').value)) return mostrarAviso('La hora final del permiso debe ser después de la inicial.', true);
    if (tipo === 'ENTRADA_ESPECIAL' && !$('ev-he').value) return mostrarAviso('Elige la hora de entrada especial.', true);
    hacerAdmin('adminAgregarEvento', { evento: {
      tipo: tipo, desde: $('ev-desde').value, hasta: $('ev-hasta').value || $('ev-desde').value,
      aplicaA: $('ev-todos').checked ? 'Todos' : elegidos.join(', '),
      horaEntrada: tipo === 'ENTRADA_ESPECIAL' ? $('ev-he').value : '', horaSalida: tipo === 'ENTRADA_ESPECIAL' ? $('ev-hs').value : '',
      horaInicio: porHorasE ? $('ev-hi').value : '', horaFin: porHorasE ? $('ev-hf').value : '', detalle: $('ev-detalle').value.trim(),
    } });
  },
  admEliminarEvento(b) { hacerAdmin('adminEliminarEvento', { id: b.dataset.valor }); },

  // ----- Reportes
  admRepAnio(b) {
    adm.rep.anio = b.dataset.valor;
    if (adm.rep.mes && adm.rep.mes.indexOf(adm.rep.anio) !== 0) adm.rep.mes = '';
    cargarSeccionAdmin();
  },
  admRepMes() { adm.rep.mes = $('rep-mes').value; cargarSeccionAdmin(); },
  admRepPersona() { adm.rep.persona = $('rep-persona').value; render(); },
  admRepVista(b) { adm.rep.vista = b.dataset.valor; render(); },
  admRepPersonaTocar(b) { adm.rep.persona = b.dataset.valor; render(); window.scrollTo({ top: 0, behavior: 'smooth' }); },
  admRepMesTocar(b) { adm.rep.mes = b.dataset.valor; cargarSeccionAdmin(); },
  admDescargarPdf() { const t = tablaReporteActual(); if (t) descargarPdf(t); },
  admDescargarExcel() { const t = tablaReporteActual(); if (t) descargarExcel(t); },
  admActualizarHoja() { hacerAdmin('adminActualizarReportes', {}); },

  // ----- Ajustes
  admGuardarReglas() { hacerAdmin('adminGuardarReglas', { horasMeta: Number($('aj-meta').value), tolerancia: Number($('aj-tol').value) }); },
  admNuevaSede() { dialogoSede(null); },
  admEditarSede(b) { dialogoSede(adm.config.sedes.find(s => s.id === b.dataset.valor)); },
  admSedeOrigen() {
    const origen = document.querySelector('input[name="se-origen"]:checked').value;
    $('se-coords-campo').hidden = origen !== 'COORDS';
  },
  admSedeRadio() { $('se-radio-texto').textContent = $('se-radio').value + ' m'; $('se-radio-aviso').hidden = Number($('se-radio').value) >= 30; },
  admGuardarSede() {
    const id = $('se-id').value;
    const origen = document.querySelector('input[name="se-origen"]:checked').value;
    const nombre = $('se-nombre').value.trim() || 'Colegio Mesón';
    const radio = Number($('se-radio').value);
    conOcupado(async () => {
      let lat, lon;
      if (origen === 'COORDS') {
        const p = leerCoordenadasWeb($('se-coords').value);
        if (!p) throw new Error('No se reconocen las coordenadas. Ej.: 14.6349, -90.5069');
        [lat, lon] = p;
      } else if (origen === 'AQUI') {
        const u = await ubicacionActual();
        if (u.precision > 50) throw new Error('La señal GPS es imprecisa (±' + Math.round(u.precision) + ' m). Sal a un lugar abierto.');
        lat = u.latitud; lon = u.longitud;
      } else {
        const s = adm.config.sedes.find(x => x.id === id);
        lat = s.latitud; lon = s.longitud;
      }
      const sede = { nombre: nombre, radio: radio, latitud: lat, longitud: lon };
      const r = id ? await pedirAdmin('adminEditarSede', { sede: Object.assign(sede, { id: id }) }) : await pedirAdmin('adminAgregarSede', { sede: sede });
      cerrarDialogo();
      mostrarAviso(r.mensaje || 'Ubicación guardada.');
      await cargarSeccionAdmin(true);
    });
  },
  admQuitarSede(b) {
    mostrarDialogo('⚠️', '¿Quitar esta ubicación?', 'Ya no se exigirá marcar dentro de ella.',
      [{ texto: 'Quitar', accion: 'admConfirmarQuitarSede' }, { texto: 'Cancelar', accion: 'cerrarDialogo', secundario: true }],
      '<input type="hidden" id="se-quitar" value="' + esc(b.dataset.valor) + '">');
  },
  admConfirmarQuitarSede() { hacerAdmin('adminEliminarSede', { id: $('se-quitar').value }); },
  admVerificacion(b) {
    const s = adm.config.sedes.find(x => x.id === b.dataset.valor);
    mostrarDialogo('', 'Cómo se comprueba la presencia', s.nombre, [{ texto: 'Guardar', accion: 'admGuardarVerificacion' }, { texto: 'Cancelar', accion: 'cerrarDialogo', secundario: true }],
      '<input type="hidden" id="ve-id" value="' + esc(s.id) + '"><label>Método<select id="ve-metodo">' +
      opcion('UBICACION', 'Solo ubicación (GPS)', s.metodo === 'UBICACION') + opcion('QR', 'Código QR en la entrada', s.metodo === 'QR') +
      (s.metodo === 'WIFI' ? opcion('WIFI', 'Wi-Fi (configurado desde Android)', true) : '') + '</select></label>' +
      '<p class="chico">El Wi-Fi solo se puede registrar desde la app de Android: los navegadores no pueden leer las redes.</p>');
  },
  admGuardarVerificacion() { hacerAdmin('adminGuardarVerificacion', { id: $('ve-id').value, metodo: $('ve-metodo').value }); },

  // ----- QR de la entrada (cambia cada 30 segundos)
  admMostrarQr(b) {
    const id = b.dataset.valor;
    const sede = (perfil().sedes || []).concat(adm.config ? adm.config.sedes : []).find(s => s.id === id) || { id: id, nombre: 'Colegio' };
    conOcupado(async () => {
      const r = await pedirAdmin('adminSecretoQr', { id: id });
      const diferencia = r.ahora - Math.floor(Date.now() / 1000);
      const clave = await crypto.subtle.importKey('raw', new TextEncoder().encode(r.secreto), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
      mostrarDialogo('', 'QR de la entrada · ' + sede.nombre, 'El personal lo escanea al marcar. Cambia cada 30 segundos: una foto no sirve.',
        [{ texto: 'Cerrar', accion: 'admCerrarQr' }], '<div id="qr-imagen" style="background:#fff;padding:12px;border-radius:16px;align-self:center"></div><p class="chico centro" id="qr-cuenta"></p>');
      let ventanaActual = null;
      const dibujar = async () => {
        if (!$('qr-imagen')) { clearInterval(qrIntervalo); return; }
        const seg = Math.floor(Date.now() / 1000) + diferencia;
        const ventana = Math.floor(seg / 30);
        $('qr-cuenta').textContent = 'Cambia en ' + (30 - (seg % 30)) + ' s';
        if (ventana === ventanaActual) return;
        ventanaActual = ventana;
        const firma = new Uint8Array(await crypto.subtle.sign('HMAC', clave, new TextEncoder().encode(String(ventana))));
        const codigo = Array.from(firma, x => x.toString(16).padStart(2, '0')).join('').substring(0, 10);
        const qr = qrcode(0, 'M');
        qr.addData('MESON|' + id + '|' + codigo);
        qr.make();
        $('qr-imagen').innerHTML = qr.createSvgTag({ cellSize: 7, margin: 2, scalable: true });
        const svg = $('qr-imagen').querySelector('svg');
        if (svg) { svg.style.width = 'min(70vw, 300px)'; svg.style.height = 'auto'; }
      };
      clearInterval(qrIntervalo);
      await dibujar();
      qrIntervalo = setInterval(dibujar, 1000);
    });
  },
  admCerrarQr() { clearInterval(qrIntervalo); cerrarDialogo(); },
};

let vistaPedida = 0;

/**
 * Al dar vacaciones desde el calendario: muestra cuántos días laborales son para cada persona y avisa si alguien ya
 * tiene vacaciones en esas fechas, antes de guardar.
 */
function vistaVacacionesEvento() {
  const caja = $('ev-vista');
  if (!caja) return;
  const elegidos = (adm.miembros || []).filter(m => $('ev-p-' + m.correo) && $('ev-p-' + m.correo).checked).map(m => m.correo);
  const todos = $('ev-todos').checked;
  if ($('ev-tipo').value !== 'VACACIONES' || (!todos && !elegidos.length) || !$('ev-desde').value) { caja.innerHTML = ''; return; }
  const pedido = ++vistaPedida;
  caja.innerHTML = '<p class="chico">Contando días laborales…</p>';
  pedirAdmin('adminVistaVacaciones', { aplicaA: todos ? 'Todos' : elegidos.join(', '), desde: $('ev-desde').value, hasta: $('ev-hasta').value || $('ev-desde').value })
    .then(r => {
      if (pedido !== vistaPedida || !$('ev-vista')) return;
      const vistas = r.vistas || [];
      if (!vistas.length) { caja.innerHTML = ''; return; }
      if (todos) {
        // Todo el personal: los días (con la jornada de la primera persona) y solo quienes tienen avisos.
        const conAvisos = vistas.filter(v => (v.avisos || []).length);
        caja.innerHTML = '<p class="chico">Son ' + textoDiasLaborales(vistas[0].dias) + ' con la jornada de ' + esc(vistas[0].nombre) + ' (puede variar según la jornada de cada persona).</p>' +
          conAvisos.slice(0, 8).map(v => htmlVistaVacaciones(v, true)).join('') +
          (conAvisos.length > 8 ? '<p class="chico">Y ' + (conAvisos.length - 8) + ' persona(s) más con avisos.</p>' : '');
      } else {
        caja.innerHTML = vistas.map(v => htmlVistaVacaciones(v, vistas.length > 1)).join('');
      }
    })
    .catch(() => { if (pedido === vistaPedida && $('ev-vista')) caja.innerHTML = ''; });
}

function avisoJornada() {
  const e = $('jo-entrada').value, s = $('jo-salida').value;
  const total = ((aMinutos(s) - aMinutos(e)) + 1440) % 1440 || 1440;
  const almuerzo = Number($('jo-almuerzo').value || 0);
  // Como en el servidor: el almuerzo solo se descuenta en días de más de 6 horas.
  const conAlmuerzo = almuerzo && total > HORAS_ANTES_DEL_ALMUERZO * 60;
  $('jo-aviso').textContent = !e || !s ? '' : e === s ? 'La salida no puede ser igual a la entrada.'
    : (terminaDiaSiguiente(e, s) ? 'Termina al día siguiente (turno nocturno): ' : 'Duración: ') + duracion(total) +
      (conAlmuerzo ? ', ' + duracion(total - almuerzo) + ' de trabajo sin el almuerzo' : '') + '.';
}

const OPCIONES_ALMUERZO = [[0, 'Sin almuerzo (jornada continua)'], [30, '30 minutos'], [45, '45 minutos'], [60, '1 hora'], [90, '1 hora 30 minutos'], [120, '2 horas']];

function dialogoJornada(j) {
  const almuerzo = (j && j.almuerzo) || 0;
  const meta = Math.round((perfil().horasMeta || 8) * 60);
  const entrada = j ? j.entrada : '', salida = j ? j.salida : '';
  const auto = !j || salida === sumarMinutosHora(entrada, meta + almuerzo);
  const dias = j ? j.dias || [] : [1, 2, 3, 4, 5];
  const porDia = (j && j.porDia) || {};
  const distinto = Object.keys(porDia).length > 0;
  mostrarDialogo('', j ? 'Editar jornada' : 'Nueva jornada', '', [{ texto: 'Guardar', accion: 'admGuardarJornada' }, { texto: 'Cancelar', accion: 'cerrarDialogo', secundario: true }],
    '<input type="hidden" id="jo-codigo" value="' + esc(j ? j.codigo : '') + '"><input type="hidden" id="jo-auto" value="' + (auto ? 'si' : 'no') + '">' +
    '<label>Nombre<input id="jo-nombre" maxlength="40" value="' + esc(j ? j.nombre : '') + '" placeholder="Ej.: Jornada matutina"></label>' +
    '<div class="rejilla"><label>Entrada<input type="time" id="jo-entrada" value="' + esc(entrada) + '" data-cambio="admJorEntrada"></label>' +
    '<label>Salida<input type="time" id="jo-salida" value="' + esc(salida) + '" data-cambio="admJorSalida"></label></div>' +
    '<label>Almuerzo (no cuenta como trabajo)<select id="jo-almuerzo" data-cambio="admJorEntrada">' +
    OPCIONES_ALMUERZO.concat(OPCIONES_ALMUERZO.some(o => o[0] === almuerzo) ? [] : [[almuerzo, almuerzo + ' minutos']])
      .map(o => opcion(o[0], o[1], o[0] === almuerzo)).join('') + '</select></label>' +
    '<p class="chico">Cada quien almuerza a la hora que puede: se descuenta solo a quien pasa de 6 horas en el colegio, y solo en días de más de 6 horas.</p>' +
    '<p class="chico" id="jo-aviso"></p><p class="chico">La salida se calcula sola con la meta (' + duracion(meta) + ') más el almuerzo; puedes cambiarla.</p>' +
    '<p class="chico">Días laborales</p><div class="chips">' + [1, 2, 3, 4, 5, 6, 7].map(d =>
      '<label class="chip" style="display:flex;flex-direction:row;gap:4px;align-items:center"><input type="checkbox" id="jo-dia-' + d + '"' + (dias.indexOf(d) >= 0 ? ' checked' : '') +
      ' data-cambio="admJorDias" style="width:auto">' + LETRAS_DIAS[d] + '</label>').join('') + '</div>' +
    casilla('jo-distinto', 'Horario distinto por día', distinto, ' data-cambio="admJorPorDia"') +
    '<div id="jo-pordia"' + (distinto ? '' : ' hidden') + '>' + [1, 2, 3, 4, 5, 6, 7].map(d => {
      const h = porDia[d] || { entrada: entrada, salida: salida };
      return '<div class="rejilla" id="jo-fila-' + d + '"' + (dias.indexOf(d) >= 0 ? '' : ' hidden') + '><label>' + NOMBRES_DIAS[d] + ' entrada<input type="time" id="jo-e-' + d + '" value="' + esc(h.entrada) + '"></label>' +
        '<label>Salida<input type="time" id="jo-s-' + d + '" value="' + esc(h.salida) + '"></label></div>';
    }).join('') + '</div>');
  avisoJornada();
}

function dialogoRol(r) {
  const superAdmin = esSuperAdminWeb();
  const permisos = (r && r.permisos) || [];
  mostrarDialogo('', r ? 'Editar rol' : 'Nuevo rol', '', [{ texto: 'Guardar', accion: 'admGuardarRol' }, { texto: 'Cancelar', accion: 'cerrarDialogo', secundario: true }],
    '<input type="hidden" id="ro-codigo" value="' + esc(r ? r.codigo : '') + '">' +
    '<label>Nombre del rol<input id="ro-nombre" maxlength="40" value="' + esc(r ? r.nombre : '') + '" placeholder="Ej.: Coordinador"></label>' +
    casilla('ro-marca', 'Marca asistencia (entrada, salida, permisos y recordatorios)', r ? r.marca : true) +
    '<p class="chico">Funciones de administración</p>' +
    Object.keys(NOMBRES_PERMISOS).filter(p => p !== 'ROLES' || superAdmin).map(p => casilla('ro-p-' + p, NOMBRES_PERMISOS[p], permisos.indexOf(p) >= 0)).join(''));
}

function dialogoSede(s) {
  const radio = s ? Math.min(100, Math.max(0, s.radio)) : 80;
  mostrarDialogo('', s ? 'Editar ubicación' : 'Ubicación del colegio', '', [{ texto: 'Guardar', accion: 'admGuardarSede' }, { texto: 'Cancelar', accion: 'cerrarDialogo', secundario: true }],
    '<input type="hidden" id="se-id" value="' + esc(s ? s.id : '') + '">' +
    '<label>Nombre<input id="se-nombre" maxlength="60" value="' + esc(s ? s.nombre : 'Colegio Mesón') + '"></label>' +
    '<p class="chico">Punto del colegio</p>' +
    (s ? '<label class="interruptor"><input type="radio" name="se-origen" value="ACTUAL" checked data-cambio="admSedeOrigen" style="width:auto"><span>Dejar el punto como está</span></label>' : '') +
    '<label class="interruptor"><input type="radio" name="se-origen" value="AQUI"' + (s ? '' : ' checked') + ' data-cambio="admSedeOrigen" style="width:auto"><span>Donde estoy ahora (GPS)</span></label>' +
    '<label class="interruptor"><input type="radio" name="se-origen" value="COORDS" data-cambio="admSedeOrigen" style="width:auto"><span>Pegar coordenadas o enlace de Google Maps</span></label>' +
    '<label id="se-coords-campo" hidden>Coordenadas o enlace<input id="se-coords" placeholder="14.6349, -90.5069"></label>' +
    '<div class="fila"><span class="crece chico">Radio permitido</span><b id="se-radio-texto">' + radio + ' m</b></div>' +
    '<input type="range" id="se-radio" min="0" max="100" step="1" value="' + radio + '" data-entrada="admSedeRadio" style="padding:0">' +
    '<p class="chico alerta" id="se-radio-aviso"' + (radio >= 30 ? ' hidden' : '') + '>Ojo: el GPS de los teléfonos varía entre 5 y 30 m; con un radio tan pequeño algunas marcas podrían rechazarse.</p>');
}
