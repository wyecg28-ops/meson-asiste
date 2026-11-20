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
 * Primer registro: lo anota en "Personal" y crea su hoja con su nombre.
 * Un correo queda atado al primer teléfono con que se registró. Para cambiarlo, el administrador libera el teléfono.
 */
function registrarDocente(d) {
  const correo = correoInstitucional(d.correo);
  if (esSuperAdminFijo(correo)) return registrarSuperAdmin(correo, d);
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

  const v = existente ? existente.valores : [];
  const fila = [correo, existente ? v[DOC.NOMBRE] : nombre, nombreHoja, d.dispositivo, d.modelo,
    existente ? v[DOC.METODO] : '',
    existente ? v[DOC.REGISTRADO] : new Date(),
    v[DOC.ROL] || (esSuperAdminFijo(correo) ? 'SUPERADMIN' : esAdministradorFijo(correo) ? 'ADMINISTRADOR'
      : (CONFIG.SUPERVISORES.map(c => c.toLowerCase()).indexOf(correo) >= 0 ? 'SUPERVISOR' : 'DOCENTE')),
    v[DOC.JORNADA] || jornadaPorDefecto(),
    v[DOC.ESTADO] || ESTADOS.ACTIVO];
  if (existente) {
    hoja.getRange(existente.numero, 1, 1, fila.length).setValues([fila]);
  } else {
    hoja.appendRow(fila);
  }
  // Solo quien marca asistencia tiene hoja personal (el super administrador, por ejemplo, no).
  // Si después su rol cambia a uno que marca, la hoja se crea sola con su primera marca.
  if (marcaAsistencia(miembroDe(fila).rol)) obtenerHoja(nombreHoja, ENCABEZADOS_DOCENTE);
  ajustarColumnas(hoja);
  return { ok: true, mensaje: 'Registro completado.' };
}

/**
 * Si se reinstaló la app (o se borraron sus datos), el teléfono ya no recuerda su registro.
 * Con el identificador del teléfono se devuelve a quién pertenece, para no repetir el registro.
 */
function recuperar(d) {
  if (!d.dispositivo) throw new Error('No se pudo identificar el teléfono.');
  const sa = superAdminPorDispositivo(d.dispositivo);
  if (sa) return { ok: true, encontrado: true, correo: sa.correo, nombre: sa.nombre, metodo: '' };
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
  if (esSuperAdminFijo(correo)) return iniciarSesionSuperAdmin(correo, d);
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
  if (esSuperAdmin(u.rol)) {
    const datos = leerSuperAdmin(u.correo) || {};
    datos.dispositivos = dispositivosSuperAdmin(datos).filter(x => x.id !== u._dispositivo);
    delete datos.dispositivo;
    guardarSuperAdmin(u.correo, datos);
    return { ok: true, mensaje: 'Este dispositivo quedó liberado. Tus otros dispositivos siguen funcionando.' };
  }
  const hoja = obtenerHoja(DOCENTES.nombre, DOCENTES.encabezados);
  const fila = buscarFila(hoja, DOC.CORREO, u.correo);
  hoja.getRange(fila.numero, DOC.DISPOSITIVO + 1, 1, 2).setValues([['', '']]);
  return { ok: true, mensaje: 'Teléfono liberado. Ya puedes iniciar sesión con ' + u.correo + ' en tu otro teléfono.' };
}

/** Comprueba que el correo esté registrado con este teléfono y que su acceso esté activo. */
function autenticar(d) {
  const correo = String(d.correo || '').toLowerCase();
  if (esSuperAdminFijo(correo)) return autenticarSuperAdmin(correo, d);
  const hoja = obtenerHoja(DOCENTES.nombre, DOCENTES.encabezados);
  const fila = buscarFila(hoja, DOC.CORREO, correo);
  if (fila) validarEstado(fila.valores);
  if (!fila || fila.valores[DOC.DISPOSITIVO] !== d.dispositivo) {
    throw errorCon('NO_REGISTRADO',
      'Este teléfono no está registrado con tu correo. Pide al administrador que revise la hoja "Personal".');
  }
  const metodo = d.marca && d.marca.metodo;
  if (metodo && fila.valores[DOC.METODO] !== metodo) {
    hoja.getRange(fila.numero, DOC.METODO + 1).setValue(metodo);
  }
  return miembroDe(fila.valores);
}

// ---------------------------------------------------------------- Super administrador (interno)
//
// El super administrador NO está en la hoja "Personal" ni su rol en "Roles": así nadie puede verlo, cambiarlo ni
// dárselo a otra persona desde la hoja. Solo lo es el correo de CONFIG.SUPER_ADMINISTRADOR, y el teléfono con el
// que se registró se guarda en las propiedades del script (Configuración del proyecto → Propiedades), no en la hoja.

const CLAVE_SUPERADMIN = 'SUPERADMIN_';

function leerSuperAdmin(correo) {
  const v = PropertiesService.getScriptProperties().getProperty(CLAVE_SUPERADMIN + correo);
  return v ? JSON.parse(v) : null;
}

/** Guarda (o borra, con datos = null) el registro interno de un super administrador. */
function guardarSuperAdmin(correo, datos) {
  const props = PropertiesService.getScriptProperties();
  if (datos) props.setProperty(CLAVE_SUPERADMIN + correo, JSON.stringify(datos));
  else props.deleteProperty(CLAVE_SUPERADMIN + correo);
}

/**
 * Dispositivos donde el super administrador ya entró: [{ id, modelo, desde }]. Una versión anterior guardaba uno solo
 * (datos.dispositivo); se convierte a la lista.
 */
function dispositivosSuperAdmin(datos) {
  if (!datos) return [];
  if (Array.isArray(datos.dispositivos)) return datos.dispositivos;
  return datos.dispositivo ? [{ id: datos.dispositivo, modelo: datos.modelo || '', desde: datos.registrado || '' }] : [];
}

function superAdminPorDispositivo(dispositivo) {
  if (!CONFIG.SUPER_ADMINISTRADOR) return null;
  const correo = CONFIG.SUPER_ADMINISTRADOR.toLowerCase();
  const s = leerSuperAdmin(correo);
  return s && dispositivosSuperAdmin(s).some(x => x.id === dispositivo) ? Object.assign({ correo: correo }, s) : null;
}

function miembroSuperAdmin(correo, datos) {
  return {
    correo: correo, nombre: datos.nombre || 'Super administrador', hoja: '', rol: 'SUPERADMIN',
    rolNombre: rolJson('SUPERADMIN').nombre, jornada: jornadaPorDefecto(), estado: 'ACTIVO',
    modelo: datos.modelo || '', conTelefono: dispositivosSuperAdmin(datos).length > 0, registrado: datos.registrado || '',
  };
}

/**
 * Versiones anteriores lo guardaban en "Personal": se pasa su teléfono al registro interno y se borran su fila
 * y su hoja personal (si está vacía). Devuelve el registro interno o null.
 */
function migrarSuperAdmin(correo) {
  const hoja = obtenerHoja(DOCENTES.nombre, DOCENTES.encabezados);
  const fila = buscarFila(hoja, DOC.CORREO, correo);
  if (!fila) return null;
  const v = fila.valores;
  const datos = { dispositivo: String(v[DOC.DISPOSITIVO] || ''), modelo: String(v[DOC.MODELO] || ''),
    nombre: String(v[DOC.NOMBRE] || ''), registrado: v[DOC.REGISTRADO] ? textoFecha(v[DOC.REGISTRADO]) : fechaDesplazada(0) };
  const libro = libroAsistencia();
  const personal = v[DOC.HOJA] && libro.getSheetByName(String(v[DOC.HOJA]));
  if (personal && personal.getLastRow() <= 1 && libro.getSheets().length > 1) libro.deleteSheet(personal);
  hoja.deleteRow(fila.numero);
  if (!datos.dispositivo) return null;
  datos.dispositivos = dispositivosSuperAdmin(datos);
  delete datos.dispositivo;
  guardarSuperAdmin(correo, datos);
  return datos;
}

function registrarSuperAdmin(correo, d) {
  iniciarSesionSuperAdmin(correo, d);
  return { ok: true, mensaje: 'Listo.' };
}

/**
 * El super administrador no se registra: entra directo con su correo y puede usar varios dispositivos (teléfonos,
 * computadora, iPhone). Para que nadie más entre solo con saber el correo, cada dispositivo NUEVO pide la clave
 * secreta guardada en las propiedades del script (CLAVE_SUPERADMIN). Los dispositivos donde ya entró no la vuelven
 * a pedir. Sin clave configurada, solo puede entrar en el primer dispositivo.
 */
function iniciarSesionSuperAdmin(correo, d) {
  limpiarRastrosSuperAdmin();
  const actual = leerSuperAdmin(correo) || {};
  const lista = dispositivosSuperAdmin(actual);
  if (!lista.some(x => x.id === d.dispositivo)) {
    if (lista.length > 0 || claveSuperAdmin()) comprobarClaveSuperAdmin(d.clave);
    lista.push({ id: d.dispositivo, modelo: d.modelo || '', desde: fechaDesplazada(0) });
    actual.dispositivos = lista.slice(-10); // como máximo los últimos 10 dispositivos
    delete actual.dispositivo;
    actual.modelo = d.modelo || actual.modelo || '';
    actual.nombre = 'Super administrador';
    actual.registrado = actual.registrado || fechaDesplazada(0);
    guardarSuperAdmin(correo, actual);
  }
  const m = miembroSuperAdmin(correo, actual);
  return { ok: true, encontrado: true, correo: correo, nombre: m.nombre, rol: m.rol, rolNombre: m.rolNombre };
}

function claveSuperAdmin() {
  return String(PropertiesService.getScriptProperties().getProperty('CLAVE_SUPERADMIN') || '').trim();
}

/** Comprueba la clave del super administrador. Tras 5 intentos fallidos se bloquea 15 minutos. */
function comprobarClaveSuperAdmin(clave) {
  const guardada = claveSuperAdmin();
  if (!guardada) {
    throw new Error('Esta cuenta ya se usa en otro dispositivo. Para entrar también en este, el dueño del sistema debe ' +
      'configurar la clave del super administrador (propiedad CLAVE_SUPERADMIN en Apps Script).');
  }
  const cache = CacheService.getScriptCache();
  const fallos = Number(cache.get('fallosSuperAdmin') || 0);
  if (fallos >= 5) throw new Error('Demasiados intentos con una clave incorrecta. Espera 15 minutos.');
  if (!clave) throw errorCon('CLAVE_SUPERADMIN', 'Es la primera vez que entras en este dispositivo: escribe tu clave de super administrador.');
  if (String(clave) !== guardada) {
    cache.put('fallosSuperAdmin', String(fallos + 1), 900);
    throw errorCon('CLAVE_SUPERADMIN', 'Clave incorrecta.');
  }
  cache.remove('fallosSuperAdmin');
}

function autenticarSuperAdmin(correo, d) {
  const actual = leerSuperAdmin(correo) || migrarSuperAdmin(correo);
  if (!actual || !dispositivosSuperAdmin(actual).some(x => x.id === d.dispositivo)) {
    throw errorCon('NO_REGISTRADO', 'Este dispositivo no está registrado con tu correo.');
  }
  return Object.assign(miembroSuperAdmin(correo, actual), { _dispositivo: d.dispositivo });
}

/**
 * Ejecutar desde el editor si el super administrador perdió o cambió su teléfono: libera su registro para que
 * pueda volver a registrarse desde el teléfono nuevo.
 */
function liberarSuperAdmin() {
  const correo = CONFIG.SUPER_ADMINISTRADOR.toLowerCase();
  const datos = leerSuperAdmin(correo) || {};
  datos.dispositivos = [];
  delete datos.dispositivo;
  guardarSuperAdmin(correo, datos);
  Logger.log('Listo: se quitaron todos los dispositivos del super administrador. Al volver a entrar, cada uno pedirá ' +
    'la clave (CLAVE_SUPERADMIN).');
}

/**
 * Borra todo rastro del super administrador en la hoja: su fila en "Personal", su hoja personal, el rol en "Roles"
 * y cualquier fila con su correo en Rechazados, Solicitudes y Actividades especiales. Se ejecuta sola cada hora
 * y al iniciar sesión; también se puede ejecutar desde el editor.
 */
function limpiarRastrosSuperAdmin() {
  if (!CONFIG.SUPER_ADMINISTRADOR) return;
  const correo = CONFIG.SUPER_ADMINISTRADOR.toLowerCase();
  if (!leerSuperAdmin(correo)) migrarSuperAdmin(correo);
  const libro = libroAsistencia();
  // Fila en "Personal" (y su hoja personal).
  const personal = obtenerHoja(DOCENTES.nombre, DOCENTES.encabezados);
  let fila;
  while ((fila = buscarFila(personal, DOC.CORREO, correo))) {
    const hojaPersonal = fila.valores[DOC.HOJA] && libro.getSheetByName(String(fila.valores[DOC.HOJA]));
    if (hojaPersonal && libro.getSheets().length > 1) libro.deleteSheet(hojaPersonal);
    personal.deleteRow(fila.numero);
  }
  // Rol en "Roles" (leerRoles borra esa fila).
  _roles = null;
  leerRoles();
  // Filas con su correo en otras pestañas.
  [[RECHAZADOS, 2], [SOLICITUDES, SOL.CORREO], [ACTIVIDADES, ACT.CORREO]].forEach(([def, col]) => {
    const hoja = libro.getSheetByName(def.nombre);
    if (!hoja || hoja.getLastRow() < 2) return;
    const valores = hoja.getRange(2, col + 1, hoja.getLastRow() - 1, 1).getValues();
    for (let i = valores.length - 1; i >= 0; i--) {
      if (String(valores[i][0]).trim().toLowerCase() === correo) hoja.deleteRow(i + 2);
    }
  });
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
  return (CONFIG.ADMINISTRADORES || []).map(c => c.toLowerCase()).indexOf(String(correo).toLowerCase()) >= 0;
}

/** El correo de CONFIG.SUPER_ADMINISTRADOR (uno solo) es el super administrador. */
function esSuperAdminFijo(correo) {
  return !!CONFIG.SUPER_ADMINISTRADOR && String(correo).toLowerCase() === CONFIG.SUPER_ADMINISTRADOR.toLowerCase();
}

/** Correos con rol fijo (definido en CONFIG): no se les puede cambiar el rol, desactivar ni eliminar. */
const esRolFijo = correo => esSuperAdminFijo(correo) || esAdministradorFijo(correo);

// Los registros del super administrador solo los ve otro super administrador. Se activa en administrar().
let _verSuperAdmin = false;

function estadoDe(v) {
  return claveDe(ESTADOS, v[DOC.ESTADO], x => x) || 'ACTIVO';
}

/** Convierte una fila de "Personal" en un miembro con su rol, jornada y estado (acepta clave o nombre en la hoja). */
function miembroDe(v) {
  const correo = String(v[DOC.CORREO]).toLowerCase();
  let rol = esSuperAdminFijo(correo) ? 'SUPERADMIN' : esAdministradorFijo(correo) ? 'ADMINISTRADOR'
    : (claveDe(leerRoles(), v[DOC.ROL], r => r.nombre) || 'DOCENTE');
  // El rol de super administrador no se puede dar escribiéndolo en la hoja: solo los correos de CONFIG lo tienen.
  if (esSuperAdmin(rol) && !esSuperAdminFijo(correo)) rol = 'DOCENTE';
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

/** Miembros que no fueron eliminados. Los super administradores solo aparecen para otro super administrador. */
function leerMiembros() {
  const valores = obtenerHoja(DOCENTES.nombre, DOCENTES.encabezados).getDataRange().getValues();
  return valores.slice(1).filter(v => v[DOC.CORREO]).map(miembroDe)
    .filter(m => m.estado !== 'ELIMINADO' && (_verSuperAdmin || !esSuperAdmin(m.rol)));
}

// ---------------------------------------------------------------- Perfil (lo que la app descarga)

/** Quien no marca asistencia no necesita hoja personal: si tiene una vacía (solo encabezados), se borra. */
function quitarHojaSinUso(u) {
  if (marcaAsistencia(u.rol) || !u.hoja) return;
  const libro = libroAsistencia();
  const hoja = libro.getSheetByName(u.hoja);
  if (hoja && hoja.getLastRow() <= 1 && libro.getSheets().length > 1) libro.deleteSheet(hoja);
}

/**
 * Lo que la app descarga al abrirse. Para que cargue rápido, en la misma respuesta puede venir el historial
 * (d.historial) y el ranking del mes (d.ranking, d.mes): así es una sola llamada en vez de tres.
 */
function perfil(u, d) {
  quitarHojaSinUso(u);
  d = d || {};
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
    dias: d.historial && marcaAsistencia(u.rol) ? misMarcas(u).dias : undefined,
    ranking: d.ranking ? rankingDelMes(u, d.mes) : undefined,
  };
}

/**
 * La jornada con la que la persona marcó ese día (columna "Jornada" de la fila). Así, si después se le cambia
 * la jornada, los días anteriores se siguen evaluando con la que tenía; el cambio cuenta desde ese momento.
 * Para corregir un día, se cambia la jornada en esa fila de su hoja.
 */
function conJornadaDeFila(m, v) {
  const texto = String(v[COL.JORNADA - 1] || '').trim().toLowerCase();
  if (!texto) return m;
  const jornadas = leerJornadas();
  const codigo = Object.keys(jornadas).filter(k => k.toLowerCase() === texto ||
    String(jornadas[k].nombre).trim().toLowerCase() === texto)[0];
  return codigo && codigo !== m.jornada ? Object.assign({}, m, { jornada: codigo }) : m;
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
      const mf = conJornadaDeFila(u, v);
      const meta = metaDelDia(mf, fecha, eventos);
      const limite = limiteEntrada(mf, fecha, eventos);
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
    // El super administrador no deja rastro en la hoja.
    if (!esSuperAdmin(usuario.rol)) {
      obtenerHoja(RECHAZADOS.nombre, RECHAZADOS.encabezados)
        .appendRow([fecha, hora, usuario.correo, ETIQUETAS[d.tipo] || d.tipo, err.message, mapa, distancia]);
    }
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
    const minutosTarde = actividad ? 0 : Math.max(0, aMinutos(hora) - limite);
    hoja.appendRow([fecha, "'" + hora, usuario.correo, '', notas.join(' | '), mapa, '', jornada.nombre, '', '',
      actividad ? 'Sí' : '', (actividad || motivoRevision) ? POR_REVISAR : '',
      actividad ? 'Actividad especial' : 'Sin salida', actividad ? '—' : (minutosTarde > 0 ? 'Tarde' : 'Puntual'),
      actividad ? '' : minutosTarde, '', '']);
  } else {
    const horaEntradaFila = textoHora(hoja.getRange(filaHoy, COL.ENTRADA).getValue());
    let minutos = aMinutos(hora) - aMinutos(horaEntradaFila);
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
    // Columnas como en el sistema anterior: estado del día, salida anticipada y horas extra.
    const minutosAntes = esActividad ? 0 : minutosAntesDeSalida(usuario, fechaFila, eventos, horaEntradaFila.substring(0, 5), hora.substring(0, 5));
    hoja.getRange(filaHoy, COL.ESTADO).setValue(esActividad ? 'Actividad especial' : 'Asistió');
    hoja.getRange(filaHoy, COL.MIN_ANTES, 1, 2).setValues([[esActividad ? '' : minutosAntes,
      "'" + textoDuracion(Math.max(0, minutos - meta))]]);
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
    revisadaPor: String(v[SOL.REVISOR] || ''),
    revisadaEl: v[SOL.REVISADA] ? textoFecha(v[SOL.REVISADA]) : '',
  };
}

function leerSolicitudes() {
  const valores = obtenerHoja(SOLICITUDES.nombre, SOLICITUDES.encabezados).getDataRange().getValues();
  return valores.slice(1).filter(v => v[SOL.ID]).map(solicitudDe).reverse();
}

function misSolicitudes(u) {
  return { ok: true, solicitudes: leerSolicitudes().filter(s => s.correo === u.correo).slice(0, 50)
    .map(s => Object.assign(s, { rol: u.rolNombre })) };
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

// ---------------------------------------------------------------- Correcciones a mano en la hoja

/**
 * Activador simple de Google: corre solo cuando alguien EDITA la hoja a mano, para que todo quede enlazado.
 * - En la hoja de una persona: si se corrige la fecha, la entrada, la salida o la jornada, se recalculan las
 *   columnas de esa fila (Horas, Cumplió meta, Estado, Ent status, Min tarde, Min antes y Horas extra).
 * - En Personal, Jornadas, Calendario, Configuración o Roles: se vuelven a calcular los reportes.
 * En los dos casos se actualizan al momento "Resumen Global", "Estado Global" y "Excepciones", y la app lo ve
 * la próxima vez que se actualice.
 */
function onEdit(e) {
  try {
    if (!e || !e.range) return;
    const rango = e.range;
    const hoja = rango.getSheet();
    const nombre = hoja.getName();
    if (rango.getRow() + rango.getNumRows() - 1 < 2) return; // solo encabezados
    // Pestañas que escribe el sistema: no se recalcula nada si alguien las toca.
    if ([RESUMEN_GLOBAL.nombre, ESTADO_GLOBAL.nombre, EXCEPCIONES.nombre, BITACORA.nombre, RECHAZADOS.nombre]
      .indexOf(nombre) >= 0) return;
    _libro = e.source; // en un activador simple se trabaja con la hoja que se editó
    let cambio = [DOCENTES.nombre, JORNADAS.nombre, CALENDARIO.nombre, CONFIGURACION.nombre, ROLES_HOJA.nombre]
      .indexOf(nombre) >= 0;
    if (!cambio) {
      const primera = rango.getColumn();
      const ultima = primera + rango.getNumColumns() - 1;
      const tocaHoras = [COL.FECHA, COL.ENTRADA, COL.SALIDA, COL.JORNADA].some(c => c >= primera && c <= ultima);
      const miembro = tocaHoras ? leerMiembros().filter(m => m.hoja === nombre)[0] : null;
      if (!miembro) return; // no es la hoja de una persona, o se editó otra columna
      const eventos = leerEventos();
      for (let f = Math.max(2, rango.getRow()); f < rango.getRow() + rango.getNumRows(); f++) recalcularFila(hoja, f, miembro, eventos);
      cambio = true;
    }
    if (cambio) {
      limpiarCacheReportes();
      refrescarPestanas();
    }
  } catch (err) {
    console.log('No se pudo aplicar el cambio hecho en la hoja: ' + err.message);
  }
}

/** Recalcula las columnas de una fila de la hoja de una persona a partir de su fecha, entrada y salida. */
function recalcularFila(hoja, f, m, eventos) {
  const v = hoja.getRange(f, 1, 1, ENCABEZADOS_DOCENTE.length).getValues()[0];
  const fecha = textoFecha(v[COL.FECHA - 1]);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(fecha)) return;
  m = conJornadaDeFila(m, v);
  const hora = x => x === '' || x === null ? '' : textoHora(x).substring(0, 8);
  const entrada = hora(v[COL.ENTRADA - 1]);
  const salida = hora(v[COL.SALIDA - 1]);
  // Las horas quedan como texto (Sheets convierte lo que se escribe a mano en hora).
  if (v[COL.ENTRADA - 1] instanceof Date) hoja.getRange(f, COL.ENTRADA).setValue("'" + entrada);
  if (v[COL.SALIDA - 1] instanceof Date) hoja.getRange(f, COL.SALIDA).setValue("'" + salida);
  if (v[COL.FECHA - 1] instanceof Date) hoja.getRange(f, COL.FECHA).setValue("'" + fecha);

  const actividad = v[COL.ACTIVIDAD - 1] === 'Sí';
  const minutosTarde = entrada && !actividad ? Math.max(0, aMinutos(entrada) - limiteEntrada(m, fecha, eventos)) : '';
  let horas = '', cumplio = '', minutosAntes = '', extra = '', estado = entrada ? (actividad ? 'Actividad especial' : 'Sin salida') : '';
  if (entrada && salida) {
    let minutos = aMinutos(salida) - aMinutos(entrada);
    if (minutos < 0) minutos += 1440; // turno que pasa la medianoche
    const meta = metaDelDia(m, fecha, eventos);
    const permiso = permisosPorHoras(m, fecha, eventos).reduce((s, p) => s + Math.max(0, aMinutos(p.horaFin) - aMinutos(p.horaInicio)), 0);
    horas = "'" + textoDuracion(minutos);
    cumplio = minutos + permiso >= meta ? 'Sí' : 'No';
    minutosAntes = actividad ? '' : minutosAntesDeSalida(m, fecha, eventos, entrada.substring(0, 5), salida.substring(0, 5));
    extra = "'" + textoDuracion(Math.max(0, minutos - meta));
    if (!actividad) estado = 'Asistió';
  }
  const estadoEntrada = !entrada || actividad ? '—' : (minutosTarde > 0 ? 'Tarde' : 'Puntual');
  hoja.getRange(f, COL.HORAS, 1, 2).setValues([[horas, cumplio]]);
  hoja.getRange(f, COL.ESTADO, 1, 5).setValues([[estado, estadoEntrada, minutosTarde, minutosAntes, extra]]);

  // Observación: se quitan las notas automáticas que ya no aplican y se anota la corrección.
  const notas = String(v[COL.OBSERVACION - 1] || '').split(' | ').filter(String)
    .filter(n => !(/^Retraso:/.test(n) && !(minutosTarde > 0)))
    .filter(n => !(/^Salida con /.test(n) && cumplio === 'Sí'))
    .filter(n => n !== 'Corregido por la administración');
  notas.push('Corregido por la administración');
  hoja.getRange(f, COL.OBSERVACION).setValue(notas.join(' | '));
}
