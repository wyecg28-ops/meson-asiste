/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 *
 * Versión web instalable (PWA) para iPhone y cualquier teléfono. Usa el mismo servidor (Google Apps Script)
 * y la misma hoja que la app de Android. La administración se hace desde la app de Android.
 */
'use strict';

// ---------------------------------------------------------------- Utilidades

const $ = id => document.getElementById(id);
const DIA_MS = 86400000;
const LIMITE_TURNO = 20 * 3600000; // máximo entre entrada y salida de un turno (también nocturno)
const NOMBRES_DIAS = { 1: 'Lunes', 2: 'Martes', 3: 'Miércoles', 4: 'Jueves', 5: 'Viernes', 6: 'Sábado', 7: 'Domingo' };
const LETRAS_DIAS = { 1: 'L', 2: 'M', 3: 'X', 4: 'J', 5: 'V', 6: 'S', 7: 'D' };
const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre',
  'noviembre', 'diciembre'];

function esc(t) {
  return String(t == null ? '' : t).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
}

const almacen = {
  leer(clave, defecto) {
    try { const v = localStorage.getItem('ma.' + clave); return v == null ? defecto : JSON.parse(v); } catch (e) { return defecto; }
  },
  guardar(clave, valor) {
    try {
      if (valor == null) localStorage.removeItem('ma.' + clave);
      else localStorage.setItem('ma.' + clave, JSON.stringify(valor));
    } catch (e) { /* almacenamiento lleno o bloqueado */ }
  },
};

function uuid() {
  if (crypto.randomUUID) return crypto.randomUUID();
  const b = crypto.getRandomValues(new Uint8Array(16));
  b[6] = (b[6] & 15) | 64; b[8] = (b[8] & 63) | 128;
  const h = Array.from(b, x => x.toString(16).padStart(2, '0')).join('');
  return h.slice(0, 8) + '-' + h.slice(8, 12) + '-' + h.slice(12, 16) + '-' + h.slice(16, 20) + '-' + h.slice(20);
}

const dos = n => String(n).padStart(2, '0');
const claveDia = fecha => fecha.getFullYear() + '-' + dos(fecha.getMonth() + 1) + '-' + dos(fecha.getDate());
const hhmm = fecha => dos(fecha.getHours()) + ':' + dos(fecha.getMinutes());
const aMinutos = h => { const p = String(h || '0:0').split(':'); return Number(p[0]) * 60 + Number(p[1] || 0); };
const fechaDeClave = clave => { const p = clave.split('-').map(Number); return new Date(p[0], p[1] - 1, p[2]); };
const diaIso = fecha => fecha.getDay() === 0 ? 7 : fecha.getDay();
const terminaDiaSiguiente = (e, s) => !!e && !!s && aMinutos(s) <= aMinutos(e);

/** "13:05" → "1:05 p. m." */
function horaAmigable(h) {
  if (!/^\d{1,2}:\d{2}/.test(h || '')) return '--:--';
  const m = aMinutos(h);
  const hora = Math.floor(m / 60) % 24;
  return (hora % 12 === 0 ? 12 : hora % 12) + ':' + dos(m % 60) + (hora < 12 ? ' a. m.' : ' p. m.');
}

/** "45 min", "7 h", "7 h 50 min" */
function duracion(min) {
  min = Math.max(0, Math.round(min || 0));
  const h = Math.floor(min / 60), m = min % 60;
  return h === 0 ? m + ' min' : (m === 0 ? h + ' h' : h + ' h ' + m + ' min');
}

function fechaLegible(clave) {
  const f = fechaDeClave(clave);
  return NOMBRES_DIAS[diaIso(f)] + ' ' + f.getDate() + ' de ' + MESES[f.getMonth()];
}

function textoHorario(e, s) {
  return horaAmigable(e) + ' – ' + horaAmigable(s) + (terminaDiaSiguiente(e, s) ? ' (día sig.)' : '');
}

function distanciaMetros(lat1, lon1, lat2, lon2) {
  const R = 6371000, rad = x => x * Math.PI / 180;
  const a = Math.sin(rad(lat2 - lat1) / 2) ** 2 + Math.cos(rad(lat1)) * Math.cos(rad(lat2)) * Math.sin(rad(lon2 - lon1) / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(a));
}

const esIOS = /iPad|iPhone|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
const instalada = window.matchMedia('(display-mode: standalone)').matches || navigator.standalone === true;

function modelo() {
  if (esIOS) return 'iPhone (web)';
  if (/Android/i.test(navigator.userAgent)) return 'Android (web)';
  return 'Navegador web';
}

// ---------------------------------------------------------------- Estado

const estado = {
  cuenta: almacen.leer('cuenta', null),          // { correo, nombre }
  perfil: almacen.leer('perfil', null),
  dias: almacen.leer('dias', []),                // historial evaluado por el servidor
  pendientes: almacen.leer('pendientes', []),    // marcas sin enviar (sin conexión)
  solicitudes: null,
  pestana: 'inicio',
  subResumen: 'resumen',
  modoAcceso: 'iniciar',
  ocupado: false,
  accesoCerrado: null,
  // Se pide Face ID / huella al abrir solo si está registrado en el teléfono y la persona lo quiere al iniciar sesión.
  bloqueado: !!almacen.leer('credencial', null) && almacen.leer('loginHuella', true) !== false,
  observacion: '',
  ranking: almacen.leer('ranking', null), // el último descargado: se ve al instante al abrir
  cargandoRanking: false,
  actividad: false,
  conObservacion: false,
};

// Enlace directo a una pestaña: index.html?pestana=resumen
const pestanaInicial = new URLSearchParams(location.search).get('pestana');
if (['inicio', 'resumen', 'permisos', 'admin', 'perfil'].indexOf(pestanaInicial) >= 0) estado.pestana = pestanaInicial;

// Identificador de este teléfono para el servidor (se crea una sola vez y no se borra al cerrar sesión).
let dispositivo = almacen.leer('dispositivo', null);
if (!dispositivo) { dispositivo = 'web-' + uuid(); almacen.guardar('dispositivo', dispositivo); }

// ---------------------------------------------------------------- Servidor

class ErrorServidor extends Error {
  constructor(mensaje, codigo) { super(mensaje); this.codigo = codigo || ''; }
}
class SinConexion extends Error {}

async function api(accion, extra, correo) {
  const cuerpo = Object.assign({
    accion: accion,
    correo: correo || (estado.cuenta && estado.cuenta.correo),
    dispositivo: dispositivo,
    modelo: modelo(),
  }, extra || {});
  let r;
  try {
    // text/plain evita la consulta previa (CORS) que Apps Script no responde.
    r = await fetch(CONFIG.SCRIPT_URL, { method: 'POST', body: JSON.stringify(cuerpo), headers: { 'Content-Type': 'text/plain;charset=utf-8' } });
  } catch (e) {
    throw new SinConexion('Sin conexión con el servidor.');
  }
  if (!r.ok) throw new SinConexion('El servidor no respondió (' + r.status + ').');
  let j;
  try { j = await r.json(); } catch (e) { throw new SinConexion('Respuesta no válida del servidor.'); }
  if (j.ok === false) {
    const msg = j.mensaje === 'Acción no válida.'
      ? 'El servidor aún no está actualizado. Publica la nueva versión del servidor (Gestionar implementaciones → Nueva versión).'
      : j.mensaje;
    throw new ErrorServidor(msg, j.codigo);
  }
  return j;
}

/** Llama al servidor; si este teléfono ya no está registrado (hoja nueva), lo registra de nuevo y repite. */
async function apiAutenticada(accion, extra) {
  try {
    return await api(accion, extra);
  } catch (e) {
    if (e instanceof ErrorServidor && e.codigo === 'NO_REGISTRADO' && estado.cuenta) {
      await api('registrarDocente', { nombre: estado.cuenta.nombre });
      return await api(accion, extra);
    }
    throw e;
  }
}

function manejarErrorAcceso(e) {
  if (e instanceof ErrorServidor && e.codigo === 'ACCESO_CERRADO') {
    estado.accesoCerrado = e.message;
    render();
    return true;
  }
  return false;
}

let sincronizando = null;

/** Envía las marcas pendientes y descarga perfil e historial. */
function sincronizar() {
  if (!estado.cuenta) return Promise.resolve();
  if (sincronizando) return sincronizando;
  sincronizando = (async () => {
    try {
      const pendientes = estado.pendientes.slice().sort((a, b) => a.fechaHora - b.fechaHora);
      for (const marca of pendientes) {
        try {
          await apiAutenticada('registrar', { marca: marca });
          quitarPendiente(marca.id);
        } catch (e) {
          if (e instanceof SinConexion) throw e;
          if (manejarErrorAcceso(e)) return;
          quitarPendiente(marca.id);
          mostrarAviso((marca.tipo === 'ENTRADA' ? 'Entrada' : 'Salida') + ' guardada sin conexión fue rechazada: ' + e.message, true);
        }
      }
      // Perfil, historial y ranking llegan en una sola respuesta (más rápido que tres llamadas).
      const pedido = { historial: true, ranking: true };
      if (estado.ranking && estado.ranking.mes) pedido.mes = estado.ranking.mes;
      const perfil = await apiAutenticada('perfil', pedido);
      const { dias, ranking } = perfil;
      delete perfil.dias;
      delete perfil.ranking;
      estado.perfil = perfil;
      almacen.guardar('perfil', perfil);
      estado.accesoCerrado = null;
      if (ranking && Array.isArray(ranking.lista)) {
        estado.ranking = ranking;
        almacen.guardar('ranking', ranking);
      }
      if (perfil.rolInfo && perfil.rolInfo.marca === false) {
        estado.dias = [];
      } else {
        // Servidor publicado anterior (sin historial en el perfil): se pide aparte.
        estado.dias = Array.isArray(dias) ? dias : ((await apiAutenticada('misMarcas')).dias || []);
        almacen.guardar('dias', estado.dias);
      }
    } catch (e) {
      if (!manejarErrorAcceso(e) && !(e instanceof SinConexion)) mostrarAviso(e.message, true);
    } finally {
      sincronizando = null;
      render();
      if (!estado.ranking) cargarRanking();
    }
  })();
  return sincronizando;
}

/** Ranking de horas del mes ("2026-11"; vacío = el mes actual). Si falla, se queda el que había. */
async function cargarRanking(mes) {
  if (!estado.cuenta || estado.cargandoRanking) return;
  estado.cargandoRanking = true;
  try {
    const r = await apiAutenticada('ranking', mes ? { mes: mes } : {});
    if (Array.isArray(r.lista)) { estado.ranking = r; almacen.guardar('ranking', r); }
  } catch (e) { /* solo informativo */ }
  estado.cargandoRanking = false;
  if (estado.pestana === 'inicio') render();
}

const MEDALLAS = { 1: '🥇', 2: '🥈', 3: '🥉' };

/** Últimos 12 meses ("2026-11"…), el más reciente primero. */
function ultimosMeses() {
  const d = new Date();
  const lista = [];
  for (let i = 0; i < 12; i++) {
    lista.push(d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0'));
    d.setMonth(d.getMonth() - 1, 1);
  }
  return lista;
}

/** Tarjeta del ranking de horas: quién va en primer lugar y en qué lugar va cada uno. */
function tarjetaRanking() {
  const r = estado.ranking;
  const mes = (r && r.mes) || ultimosMeses()[0];
  let html = '<div class="tarjeta"><div class="fila"><div class="crece"><h2>🏆 Ranking de horas</h2>' +
    (r && r.miPosicion ? '<p class="chico" style="color:var(--rojo)">Vas en el lugar ' + r.miPosicion + ' de ' + r.lista.length + '</p>' : '') + '</div>' +
    '<select id="ranking-mes" data-cambio="rankingMes" style="width:auto">' + ultimosMeses().map(m =>
      '<option value="' + m + '"' + (m === mes ? ' selected' : '') + '>' + esc(nombreMesWeb(m)) + '</option>').join('') + '</select></div>';
  if (estado.cargandoRanking) html += '<div class="barra"><span style="width:100%;opacity:.5"></span></div>';
  if (!r) html += estado.cargandoRanking ? '' : '<p class="chico">No se pudo cargar el ranking. Revisa la conexión.</p>';
  else if (!r.lista.length) html += '<p class="chico">Todavía no hay horas registradas en ' + esc(nombreMesWeb(r.mes).toLowerCase()) + '.</p>';
  else r.lista.forEach(p => {
    html += '<div class="fila" style="padding:6px 8px;border-radius:12px;' + (p.yo ? 'background:color-mix(in srgb, var(--rojo) 12%, transparent)' : '') + '">' +
      '<span style="width:32px;text-align:center;font-size:' + (MEDALLAS[p.posicion] ? '1.4rem' : '1rem') + ';font-weight:600">' + (MEDALLAS[p.posicion] || p.posicion) + '</span>' +
      '<div class="crece"><p style="font-weight:' + (p.yo || p.posicion === 1 ? 600 : 400) + '">' + esc(p.nombre) + (p.yo ? ' (tú)' : '') + '</p>' +
      '<p class="chico">' + p.dias + (p.dias === 1 ? ' día' : ' días') + ' · ' + p.puntuales + ' puntuales' + (p.extra > 0 ? ' · ' + esc(duracion(p.extra)) + ' extra' : '') + '</p></div>' +
      '<b>' + esc(duracion(p.minutos)) + '</b></div>';
  });
  return html + '</div>';
}

function quitarPendiente(id) {
  estado.pendientes = estado.pendientes.filter(m => m.id !== id);
  almacen.guardar('pendientes', estado.pendientes);
}

// ---------------------------------------------------------------- Horario (igual que la app de Android)

const perfil = () => estado.perfil || {};
const marcaAsistencia = () => !(perfil().rolInfo && perfil().rolInfo.marca === false);
const meta = () => Math.round((perfil().horasMeta || 8) * 60);

function eventosDel(clave) {
  return (perfil().eventos || []).filter(e => clave >= e.desde && clave <= (e.hasta || e.desde));
}
const porHoras = e => e.tipo === 'PERMISO' && e.horaInicio && e.horaFin;
const diaLibre = clave => eventosDel(clave).find(e => ['VACACIONES', 'FERIADO', 'PERMISO'].indexOf(e.tipo) >= 0 && !porHoras(e));
const entradaEspecial = clave => eventosDel(clave).find(e => e.tipo === 'ENTRADA_ESPECIAL' && e.horaEntrada);

function horarioDelDia(clave) {
  const j = perfil().jornada || { entrada: '07:00', salida: '16:00', dias: [1, 2, 3, 4, 5] };
  const p = j.porDia && j.porDia[diaIso(fechaDeClave(clave))];
  return p && p.entrada ? p : { entrada: j.entrada, salida: j.salida };
}

function horaEntrada(clave) {
  const especial = entradaEspecial(clave);
  let hora = especial ? especial.horaEntrada : horarioDelDia(clave).entrada;
  eventosDel(clave).filter(porHoras).forEach(p => {
    if (aMinutos(p.horaInicio) <= aMinutos(hora) && aMinutos(p.horaFin) > aMinutos(hora)) hora = p.horaFin;
  });
  return hora;
}

function horaSalida(clave) {
  const especial = entradaEspecial(clave);
  return especial && especial.horaSalida ? especial.horaSalida : horarioDelDia(clave).salida;
}

function trabaja(clave) {
  const dias = (perfil().jornada && perfil().jornada.dias) || [1, 2, 3, 4, 5];
  return dias.indexOf(diaIso(fechaDeClave(clave))) >= 0 || !!entradaEspecial(clave);
}

// ---------------------------------------------------------------- Turnos (historial + marcas pendientes)

/** Historial del servidor más las marcas pendientes, el más reciente primero. */
function turnos() {
  const lista = estado.dias.map(d => Object.assign({}, d));
  estado.pendientes.slice().sort((a, b) => a.fechaHora - b.fechaHora).forEach(m => {
    const f = new Date(m.fechaHora);
    if (m.tipo === 'ENTRADA') {
      if (!lista.some(t => t.fecha === claveDia(f))) {
        lista.push({ fecha: claveDia(f), entrada: hhmm(f), salida: '', pendiente: true, actividad: m.actividad, puntual: null, meta: meta() });
      }
    } else {
      const abierto = lista.filter(t => !t.salida).sort((a, b) => inicioTurno(b) - inicioTurno(a))[0];
      if (abierto) { abierto.salida = hhmm(f); abierto.pendiente = true; abierto.minutos = Math.round((m.fechaHora - inicioTurno(abierto)) / 60000); }
    }
  });
  return lista.sort((a, b) => inicioTurno(b) - inicioTurno(a));
}

function inicioTurno(t) {
  const f = fechaDeClave(t.fecha);
  f.setHours(Math.floor(aMinutos(t.entrada) / 60), aMinutos(t.entrada) % 60, 0, 0);
  return f.getTime();
}

const turnoAbierto = (t, ahora) => t.entrada && !t.salida && ahora - inicioTurno(t) >= 0 && ahora - inicioTurno(t) < LIMITE_TURNO;

/** El turno abierto (también el de ayer si es nocturno) o el de hoy. */
function turnoActual(ahora) {
  const lista = turnos();
  return lista.find(t => turnoAbierto(t, ahora)) || lista.find(t => t.fecha === claveDia(new Date(ahora))) || null;
}

function minutosTrabajados(t, ahora) {
  if (!t || !t.entrada) return 0;
  if (t.salida) return t.minutos != null ? t.minutos : (aMinutos(t.salida) - aMinutos(t.entrada) + 1440) % 1440;
  return turnoAbierto(t, ahora) ? Math.round((ahora - inicioTurno(t)) / 60000) : 0;
}

// ---------------------------------------------------------------- Estadísticas

function lunesDe(fecha) {
  const f = new Date(fecha.getFullYear(), fecha.getMonth(), fecha.getDate());
  f.setDate(f.getDate() - (diaIso(f) - 1));
  return f;
}

function estadisticas(periodo) {
  const hoy = new Date();
  const desde = periodo === 'mes' ? new Date(hoy.getFullYear(), hoy.getMonth(), 1) : lunesDe(hoy);
  const claveDesde = claveDia(desde);
  const delPeriodo = estado.dias.filter(d => d.fecha >= claveDesde);
  const completos = delPeriodo.filter(d => d.salida && d.minutos != null);
  let actual = 0, mejor = 0;
  estado.dias.slice().sort((a, b) => a.fecha.localeCompare(b.fecha)).forEach(d => {
    if (d.puntual === true) actual++;
    else if (d.puntual === false) actual = 0;
    mejor = Math.max(mejor, actual);
  });
  const puntuales = delPeriodo.filter(d => d.puntual === true).length;
  const tardes = delPeriodo.filter(d => d.puntual === false).length;
  return {
    dias: delPeriodo.length,
    puntuales: puntuales,
    tardes: tardes,
    minutosTarde: delPeriodo.reduce((s, d) => s + (d.minutosTarde || 0), 0),
    puntualidad: puntuales + tardes ? Math.round(puntuales * 100 / (puntuales + tardes)) : null,
    minutos: completos.reduce((s, d) => s + d.minutos, 0),
    extra: completos.reduce((s, d) => s + (d.extra || 0), 0),
    metaCumplida: completos.filter(d => d.minutos >= d.meta).length,
    incompletos: completos.filter(d => d.minutos < d.meta).length,
    actividades: delPeriodo.filter(d => d.actividad).length,
    sinSalida: delPeriodo.filter(d => !d.salida && d.fecha < claveDia(hoy)).length,
    racha: actual,
    mejorRacha: mejor,
  };
}

// ---------------------------------------------------------------- Face ID / huella (WebAuthn, solo en el teléfono)

async function biometriaDisponible() {
  try {
    return !!(window.PublicKeyCredential && await PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable());
  } catch (e) { return false; }
}

const aBase64 = buf => btoa(String.fromCharCode.apply(null, new Uint8Array(buf))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
const deBase64 = t => Uint8Array.from(atob(t.replace(/-/g, '+').replace(/_/g, '/')), c => c.charCodeAt(0));

async function activarBiometria() {
  const credencial = await navigator.credentials.create({
    publicKey: {
      challenge: crypto.getRandomValues(new Uint8Array(32)),
      rp: { name: 'Mesón Asiste' },
      user: { id: crypto.getRandomValues(new Uint8Array(16)), name: estado.cuenta.correo, displayName: estado.cuenta.nombre },
      pubKeyCredParams: [{ type: 'public-key', alg: -7 }, { type: 'public-key', alg: -257 }],
      authenticatorSelection: { authenticatorAttachment: 'platform', userVerification: 'required', residentKey: 'discouraged' },
      timeout: 60000,
      attestation: 'none',
    },
  });
  almacen.guardar('credencial', aBase64(credencial.rawId));
}

/** Pide Face ID / huella / código del teléfono, si la persona lo activó. */
async function verificarIdentidad() {
  const id = almacen.leer('credencial', null);
  if (!id) return;
  try {
    await navigator.credentials.get({
      publicKey: {
        challenge: crypto.getRandomValues(new Uint8Array(32)),
        allowCredentials: [{ type: 'public-key', id: deBase64(id) }],
        userVerification: 'required',
        timeout: 60000,
      },
    });
  } catch (e) {
    throw new Error('No se pudo confirmar tu identidad. Inténtalo de nuevo.');
  }
}

// ---------------------------------------------------------------- Ubicación y QR

function ubicacionActual() {
  return new Promise((resolver, rechazar) => {
    if (!navigator.geolocation) return rechazar(new Error('Este navegador no permite usar la ubicación.'));
    navigator.geolocation.getCurrentPosition(
      p => resolver({ latitud: p.coords.latitude, longitud: p.coords.longitude, precision: p.coords.accuracy }),
      e => rechazar(new Error(e.code === 1
        ? 'Permite el acceso a la ubicación (Ajustes → Privacidad → Localización → Safari o Mesón Asiste).'
        : 'No se pudo obtener tu ubicación. Activa el GPS e inténtalo en un lugar abierto.')),
      { enableHighAccuracy: true, timeout: 25000, maximumAge: 0 });
  });
}

/** Abre la cámara y devuelve el código del QR de la entrada ("MESON|sede|código"), o null si se cancela. */
function escanearQr() {
  return new Promise(async (resolver, rechazar) => {
    const lector = $('lector'), video = $('lector-video'), lienzo = $('lector-lienzo');
    let flujo = null, activo = true;
    const terminar = valor => {
      activo = false;
      if (flujo) flujo.getTracks().forEach(t => t.stop());
      video.srcObject = null;
      lector.hidden = true;
      $('lector-cancelar').onclick = null;
      resolver(valor);
    };
    $('lector-cancelar').onclick = () => terminar(null);
    try {
      flujo = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' }, audio: false });
    } catch (e) {
      return rechazar(new Error('Permite el acceso a la cámara para escanear el código QR.'));
    }
    lector.hidden = false;
    video.srcObject = flujo;
    await video.play().catch(() => {});
    const ctx = lienzo.getContext('2d', { willReadFrequently: true });
    const leer = () => {
      if (!activo) return;
      if (video.readyState >= 2 && window.jsQR) {
        lienzo.width = video.videoWidth; lienzo.height = video.videoHeight;
        ctx.drawImage(video, 0, 0);
        const datos = ctx.getImageData(0, 0, lienzo.width, lienzo.height);
        const qr = jsQR(datos.data, datos.width, datos.height, { inversionAttempts: 'dontInvert' });
        if (qr && qr.data) {
          const partes = qr.data.trim().split('|');
          if (partes.length === 3 && partes[0] === 'MESON' && partes[2].length === 10) return terminar(partes[2]);
        }
      }
      requestAnimationFrame(leer);
    };
    requestAnimationFrame(leer);
  });
}

// ---------------------------------------------------------------- Acciones

async function conOcupado(tarea) {
  if (estado.ocupado) return;
  estado.ocupado = true;
  render();
  try {
    await tarea();
  } catch (e) {
    if (!manejarErrorAcceso(e)) mostrarAviso(e.message || 'Ocurrió un error.', true);
  } finally {
    estado.ocupado = false;
    render();
  }
}

function validarCorreo(correo) {
  const limpio = String(correo || '').trim().toLowerCase();
  const re = new RegExp('^[a-z0-9._%+-]+@' + CONFIG.DOMINIO.replace(/\./g, '\\.') + '$');
  if (!re.test(limpio)) throw new Error('Escribe tu correo del colegio (@' + CONFIG.DOMINIO + ').');
  return limpio;
}

function entrar(cuenta) {
  estado.cuenta = cuenta;
  almacen.guardar('cuenta', cuenta);
  estado.pestana = 'inicio';
  if (navigator.storage && navigator.storage.persist) navigator.storage.persist().catch(() => {});
  sincronizar();
}

async function iniciarSesionCon(correo, clave) {
  let r;
  try { r = await api('iniciarSesion', clave ? { clave: clave } : {}, correo); } catch (e) {
    if (e instanceof SinConexion) throw new Error('Para iniciar sesión se necesita internet.');
    if (e instanceof ErrorServidor && e.codigo === 'CLAVE_SUPERADMIN') {
      mostrarDialogo('🔒', 'Clave de super administrador', e.message,
        [{ texto: 'Entrar', accion: 'entrarConClave' }, { texto: 'Cancelar', accion: 'cerrarDialogo', secundario: true }],
        '<label>Clave<input type="password" id="sa-clave" autocomplete="current-password" maxlength="100"></label>' +
        '<input type="hidden" id="sa-correo" value="' + esc(correo) + '">');
      setTimeout(() => { const c = $('sa-clave'); if (c) c.focus(); }, 50);
      return;
    }
    throw e;
  }
  if (!r.encontrado) throw new Error('No existe una cuenta con ' + correo + '. Toca "Crear cuenta".');
  cerrarDialogo();
  entrar({ correo: correo, nombre: r.nombre || correo });
  mostrarAviso('¡Hola, ' + (r.nombre || '').split(' ')[0] + '!');
}

const acciones = {
  modoIniciar() { estado.modoAcceso = 'iniciar'; render(); },
  modoCrear() { estado.modoAcceso = 'crear'; render(); },

  iniciarSesion() {
    conOcupado(async () => {
      const correo = validarCorreo($('correo').value);
      await iniciarSesionCon(correo, null);
    });
  },

  // Super administrador en un dispositivo nuevo: se confirma con su clave secreta.
  entrarConClave() {
    conOcupado(async () => {
      const clave = $('sa-clave').value;
      if (!clave) throw new Error('Escribe tu clave.');
      await iniciarSesionCon($('sa-correo').value, clave);
    });
  },

  crearCuenta() {
    conOcupado(async () => {
      const nombre = $('nombre').value.trim().replace(/\s+/g, ' ');
      if (nombre.length < 3 || nombre.indexOf(' ') < 0) throw new Error('Escribe tu nombre completo (nombre y apellido).');
      const correo = validarCorreo($('correo').value);
      try { await api('registrarDocente', { nombre: nombre }, correo); } catch (e) {
        if (e instanceof SinConexion) throw new Error('Para crear la cuenta se necesita internet.');
        throw e;
      }
      entrar({ correo: correo, nombre: nombre });
      mostrarAviso('Cuenta creada. ¡Bienvenido, ' + nombre.split(' ')[0] + '!');
    });
  },

  desbloquear() {
    conOcupado(async () => {
      await verificarIdentidad();
      estado.bloqueado = false;
      sincronizar();
    });
  },

  pestana(b) { estado.pestana = b.dataset.valor; render(); window.scrollTo(0, 0); },
  rankingMes(s) { cargarRanking(s.value); },
  subResumen(b) { estado.subResumen = b.dataset.valor; render(); },
  periodo(b) { estado.periodo = b.dataset.valor; render(); },
  conObservacion() { estado.conObservacion = true; render(); },
  actualizar() { conOcupado(() => sincronizar()); },

  marcar(b) {
    const tipo = b.dataset.valor;
    conOcupado(async () => {
      if (!marcaAsistencia()) throw new Error('Tu rol no marca asistencia.');
      const observacion = estado.observacion.trim();
      if (estado.actividad && !observacion) {
        throw new Error('Describe la actividad especial (por ejemplo: excursión al museo con 3.º básico).');
      }
      await verificarIdentidad();
      const ubicacion = await ubicacionActual();
      // La sede más cercana decide si hay que escanear el QR de la entrada.
      const sedes = perfil().sedes || [];
      const sede = sedes.slice().sort((a, b2) =>
        distanciaMetros(ubicacion.latitud, ubicacion.longitud, a.latitud, a.longitud) -
        distanciaMetros(ubicacion.latitud, ubicacion.longitud, b2.latitud, b2.longitud))[0];
      let codigoQr = '';
      if (sede && sede.metodo === 'QR' && !estado.actividad) {
        codigoQr = await escanearQr();
        if (!codigoQr) throw new Error('Para marcar, escanea el código QR que se muestra en la entrada.');
      }
      const marca = {
        id: uuid(), tipo: tipo, fechaHora: Date.now(),
        metodo: almacen.leer('credencial', null) ? 'Web + Face ID / huella' : 'Web',
        latitud: ubicacion.latitud, longitud: ubicacion.longitud, precision: ubicacion.precision,
        observacion: observacion, actividad: estado.actividad, codigoQr: codigoQr, wifi: [],
      };
      let respuesta = null;
      try {
        respuesta = await apiAutenticada('registrar', { marca: marca });
      } catch (e) {
        if (!(e instanceof SinConexion)) throw new Error((tipo === 'ENTRADA' ? 'Entrada' : 'Salida') + ' rechazada: ' + e.message);
        estado.pendientes.push(marca);
        almacen.guardar('pendientes', estado.pendientes);
      }
      estado.observacion = ''; estado.actividad = false; estado.conObservacion = false;
      if (respuesta) {
        await sincronizar();
        mostrarResultado(tipo, respuesta);
      } else {
        mostrarDialogo('📶', 'Guardada sin conexión',
          'Tu ' + (tipo === 'ENTRADA' ? 'entrada' : 'salida') + ' quedó guardada en el teléfono a las ' +
          horaAmigable(hhmm(new Date(marca.fechaHora))) + ' y se enviará sola cuando vuelva el internet.');
      }
    });
  },

  loginHuella(b) {
    almacen.guardar('loginHuella', b.checked);
    mostrarAviso(b.checked ? 'Iniciar sesión con Face ID / huella: activado.' : 'Iniciar sesión con Face ID / huella: desactivado. Al marcar se sigue pidiendo.');
    render();
  },

  biometria(b) {
    const activar = b.checked;
    conOcupado(async () => {
      if (activar) {
        await activarBiometria().catch(() => { throw new Error('No se pudo activar. Revisa que el teléfono tenga Face ID, huella o código.'); });
        mostrarAviso('Listo: se pedirá Face ID / huella al marcar (y al abrir, si lo dejas activado).');
      } else {
        await verificarIdentidad();
        almacen.guardar('credencial', null);
        mostrarAviso('Ya no se pedirá Face ID / huella.');
      }
    });
  },

  cerrarSesion() {
    mostrarDialogo('👋', '¿Cerrar sesión?',
      'Para volver solo escribe tu correo. Tus registros siguen guardados en la hoja del colegio.',
      [{ texto: 'Cerrar sesión', accion: 'confirmarCerrarSesion' }, { texto: 'Cancelar', accion: 'cerrarDialogo', secundario: true }]);
  },

  confirmarCerrarSesion() {
    if (estado.pendientes.length) {
      cerrarDialogo();
      return mostrarAviso('Tienes marcas sin enviar. Conéctate a internet y espera a que se envíen antes de cerrar sesión.', true);
    }
    ['cuenta', 'perfil', 'dias', 'ranking', 'credencial'].forEach(c => almacen.guardar(c, null));
    Object.assign(estado, { cuenta: null, perfil: null, dias: [], solicitudes: null, bloqueado: false, accesoCerrado: null });
    cerrarDialogo();
    render();
  },

  cerrarDialogo() { cerrarDialogo(); },

  liberarTelefono() {
    mostrarDialogo('📱', '¿Liberar este teléfono?',
      'Tu cuenta deja de estar atada a este teléfono y se cierra aquí. Después podrás iniciar sesión con ' +
      estado.cuenta.correo + ' en otro teléfono (Android o iPhone). Tus registros siguen en la hoja del colegio.',
      [{ texto: 'Liberar', accion: 'confirmarLiberar' }, { texto: 'Cancelar', accion: 'cerrarDialogo', secundario: true }]);
  },

  confirmarLiberar() {
    cerrarDialogo();
    conOcupado(async () => {
      // Primero se envían las marcas pendientes: después de liberar ya no se aceptarían.
      await sincronizar();
      if (estado.pendientes.length) throw new Error('Tienes marcas sin enviar. Conéctate a internet para enviarlas antes de liberar el teléfono.');
      let r;
      try { r = await api('liberarMiTelefono'); } catch (e) {
        if (e instanceof SinConexion) throw new Error('Para liberar el teléfono se necesita internet.');
        throw e;
      }
      ['cuenta', 'perfil', 'dias', 'ranking', 'credencial'].forEach(c => almacen.guardar(c, null));
      Object.assign(estado, { cuenta: null, perfil: null, dias: [], solicitudes: null, bloqueado: false, accesoCerrado: null });
      mostrarAviso(r.mensaje || 'Teléfono liberado.');
    });
  },

  // ----- Permisos -----
  nuevaSolicitud() { mostrarFormularioSolicitud(); },
  tipoSolicitud() { actualizarFormularioSolicitud(); },
  enviarSolicitud() {
    conOcupado(async () => {
      const tipo = $('sol-tipo').value;
      const porHoras = tipo === 'PERMISO' && $('sol-horas').checked;
      const s = {
        tipo: tipo,
        desde: $('sol-desde').value,
        hasta: porHoras ? $('sol-desde').value : ($('sol-hasta').value || $('sol-desde').value),
        horaInicio: porHoras ? $('sol-inicio').value : '',
        horaFin: porHoras ? $('sol-fin').value : '',
        motivo: $('sol-motivo').value.trim(),
      };
      if (!s.desde) throw new Error('Elige la fecha.');
      if (s.hasta < s.desde) throw new Error('La fecha final es anterior a la inicial.');
      if (porHoras && (!s.horaInicio || !s.horaFin || aMinutos(s.horaFin) <= aMinutos(s.horaInicio))) {
        throw new Error('La hora final debe ser después de la inicial.');
      }
      if (s.motivo.length < 3) throw new Error('Escribe el motivo.');
      const r = await apiAutenticada('crearSolicitud', { solicitud: s });
      cerrarDialogo();
      mostrarAviso(r.mensaje || 'Solicitud enviada.');
      await cargarSolicitudes();
    });
  },
  cancelarSolicitud(b) {
    conOcupado(async () => {
      const r = await apiAutenticada('cancelarSolicitud', { id: b.dataset.valor });
      mostrarAviso(r.mensaje || 'Solicitud cancelada.');
      await cargarSolicitudes();
    });
  },

  instalarAndroid() {
    if (eventoInstalar) { eventoInstalar.prompt(); eventoInstalar = null; render(); }
  },
};

async function cargarSolicitudes() {
  try {
    const r = await apiAutenticada('misSolicitudes');
    estado.solicitudes = r.solicitudes || [];
  } catch (e) {
    if (!manejarErrorAcceso(e)) mostrarAviso(e instanceof SinConexion ? 'Las solicitudes necesitan internet.' : e.message, true);
    if (!estado.solicitudes) estado.solicitudes = [];
  }
  render();
}

// ---------------------------------------------------------------- Avisos y diálogos

let temporizadorAviso = null;
function mostrarAviso(texto, error) {
  const a = $('aviso');
  a.textContent = texto;
  a.className = 'aviso' + (error ? ' error' : '');
  a.hidden = false;
  clearTimeout(temporizadorAviso);
  temporizadorAviso = setTimeout(() => { a.hidden = true; }, error ? 6000 : 3500);
}

function mostrarDialogo(icono, titulo, texto, botones, html) {
  const lista = botones || [{ texto: 'Entendido', accion: 'cerrarDialogo' }];
  $('dialogo').innerHTML = '<div class="dialogo" role="dialog" aria-modal="true">' +
    (icono ? '<div class="icono-grande">' + icono + '</div>' : '') +
    '<h2 class="centro">' + esc(titulo) + '</h2>' +
    (texto ? '<p class="centro" style="white-space:pre-line">' + esc(texto) + '</p>' : '') +
    (html || '') +
    lista.map(b => '<button class="boton' + (b.secundario ? ' secundario' : '') + '" data-accion="' + b.accion + '">' +
      esc(b.texto) + '</button>').join('') +
    '</div>';
  $('dialogo').hidden = false;
}

function cerrarDialogo() { $('dialogo').hidden = true; $('dialogo').innerHTML = ''; }

/** Felicitación o alerta después de marcar (con los datos que evaluó el servidor). */
function mostrarResultado(tipo, respuesta) {
  const nota = respuesta.revision ? '\n\nQuedó por revisar: ' + respuesta.revision + '. La administración la revisará.' : '';
  const ahora = Date.now();
  if (tipo === 'ENTRADA') {
    const t = estado.dias.find(d => d.fecha === claveDia(new Date())) || {};
    if (t.actividad) return mostrarDialogo('🧭', 'Actividad especial registrada', 'No cuenta como llegada tarde; la dirección revisará las horas de este día.' + nota);
    if (t.puntual === false) {
      return mostrarDialogo('⏰', 'Llegada tarde', 'Entrada a las ' + horaAmigable(t.entrada) + ', ' + duracion(t.minutosTarde) +
        ' después de tu hora.\nMañana es una nueva oportunidad para empezar tu racha. 💪' + nota);
    }
    const racha = estadisticas('semana').racha;
    return mostrarDialogo('🎉', '¡Felicidades, eres puntual!', 'Entrada a las ' + horaAmigable(t.entrada || hhmm(new Date())) + '.' +
      (racha >= 2 ? '\n🔥 Llevas ' + racha + ' llegadas puntuales seguidas.' : '') + nota);
  }
  const t = estado.dias.filter(d => d.salida).sort((a, b) => inicioTurno(b) - inicioTurno(a))[0];
  if (!t) return mostrarDialogo('✅', 'Salida registrada', respuesta.mensaje + nota);
  if (t.actividad) return mostrarDialogo('🧭', 'Jornada con actividad especial', 'Registraste ' + duracion(t.minutos) + '. La dirección revisará estas horas.' + nota);
  if (t.minutos >= t.meta) {
    return mostrarDialogo('🏆', '¡Cumpliste tu meta de ' + duracion(t.meta) + '!', 'Estuviste ' + duracion(t.minutos) + ' en el colegio. ¡Buen trabajo!' +
      (t.extra > 0 ? '\n⏱ Horas extra hoy: ' + duracion(t.extra) + ' (esta semana: ' + duracion(estadisticas('semana').extra) + ').' : '') + nota);
  }
  return mostrarDialogo('⚠️', 'No completaste la meta de ' + duracion(t.meta), 'Salida con ' + duracion(t.minutos) + ' en el colegio. Te faltaron ' +
    duracion(t.meta - t.minutos) + '.' + nota);
}

// ---------------------------------------------------------------- Pantallas

let eventoInstalar = null;
window.addEventListener('beforeinstallprompt', e => { e.preventDefault(); eventoInstalar = e; render(); });

function tarjetaInstalar() {
  if (instalada) return '';
  if (esIOS) {
    return '<div class="tarjeta"><h3>📲 Instala la app en tu iPhone</h3>' +
      '<p class="chico">En Safari toca el botón <b>Compartir</b> (el cuadro con la flecha ⬆️) y luego <b>"Agregar a inicio"</b>. ' +
      'Así se abre como una app, con su ícono, y tu sesión queda guardada.</p></div>';
  }
  if (eventoInstalar) {
    return '<div class="tarjeta"><h3>📲 Instala la app</h3><button class="boton secundario" data-accion="instalarAndroid">Instalar Mesón Asiste</button></div>';
  }
  return '';
}

function pantallaAcceso() {
  const crear = estado.modoAcceso === 'crear';
  return '<img class="logo" src="iconos/logo.png" alt="Colegio Mesón">' +
    '<div class="tarjeta">' +
    '<h1>' + (crear ? 'Crear cuenta' : 'Iniciar sesión') + '</h1>' +
    '<p class="chico">' + (crear ? 'Primera vez en Mesón Asiste: escribe tu nombre y tu correo del colegio.'
      : 'Escribe tu correo del colegio. Si ya tienes cuenta, entras directo.') + '</p>' +
    (crear ? '<label>Nombre completo<input id="nombre" autocomplete="name" autocapitalize="words" placeholder="Ej.: Ana María López"></label>' : '') +
    '<label>Correo del colegio<input id="correo" type="email" inputmode="email" autocomplete="email" autocapitalize="off" ' +
    'placeholder="nombre@' + esc(CONFIG.DOMINIO) + '"></label>' +
    '<button class="boton" data-accion="' + (crear ? 'crearCuenta' : 'iniciarSesion') + '"' + (estado.ocupado ? ' disabled' : '') + '>' +
    (estado.ocupado ? 'Un momento…' : (crear ? 'Crear cuenta' : 'Entrar')) + '</button>' +
    '<button class="boton texto" data-accion="' + (crear ? 'modoIniciar' : 'modoCrear') + '">' +
    (crear ? 'Ya tengo cuenta: iniciar sesión' : '¿Primera vez? Crear cuenta') + '</button>' +
    '</div>' + tarjetaInstalar() + creditos();
}

function pantallaBloqueo() {
  return '<img class="logo" src="iconos/logo.png" alt="Colegio Mesón">' +
    '<div class="tarjeta centro"><h1>Hola, ' + esc((estado.cuenta.nombre || '').split(' ')[0]) + '</h1>' +
    '<p class="chico">Confirma que eres tú para entrar.</p>' +
    '<button class="boton" data-accion="desbloquear"' + (estado.ocupado ? ' disabled' : '') + '>🔒 Desbloquear con Face ID / huella</button></div>' +
    creditos();
}

function pantallaAccesoCerrado() {
  return '<div class="tarjeta centro"><div class="icono-grande" style="font-size:2.6rem">🔒</div><h1>Acceso cerrado</h1>' +
    '<p>' + esc(estado.accesoCerrado) + '</p>' +
    '<button class="boton" data-accion="actualizar">Volver a intentar</button>' +
    '<button class="boton texto" data-accion="confirmarCerrarSesion">Usar otra cuenta</button></div>';
}

function saludo(min) { return min < 720 ? 'Buenos días,' : (min < 1140 ? 'Buenas tardes,' : 'Buenas noches,'); }

function pantallaInicio() {
  const ahora = Date.now();
  const hoy = claveDia(new Date(ahora));
  const p = perfil();
  const nombre = (estado.cuenta.nombre || '').split(' ')[0];
  const ent = horaEntrada(hoy), sal = horaSalida(hoy);
  let html = '<div class="fila"><div class="crece"><p class="chico">' + saludo(new Date().getHours() * 60 + new Date().getMinutes()) +
    '</p><h1>' + esc(nombre) + '</h1></div><div class="avatar">' + esc(iniciales(estado.cuenta.nombre)) + '</div></div>';

  html += '<div class="tarjeta reloj"><div class="hora" id="reloj-hora"></div><p class="chico" id="reloj-fecha"></p>' +
    (p.jornada ? '<p><span class="etiqueta" style="color:var(--granate)">' + esc(p.jornada.nombre) + ' · ' + esc(textoHorario(ent, sal)) + '</span></p>' : '') +
    '</div>';

  if (!marcaAsistencia()) {
    return html + tarjetaRanking() + destacados() + tarjetaInstalar();
  }

  const aviso = diaLibre(hoy) || entradaEspecial(hoy) || eventosDel(hoy).find(porHoras);
  if (aviso) {
    html += '<div class="tarjeta"><h3 style="color:var(--granate)">' + esc(porHoras(aviso) ? 'Permiso por horas' : nombreTipo(aviso.tipo)) + '</h3><p>' +
      esc(porHoras(aviso) ? 'Tienes permiso de ' + horaAmigable(aviso.horaInicio) + ' a ' + horaAmigable(aviso.horaFin) + '; esas horas cuentan para tu meta. ' + (aviso.detalle || '')
        : aviso.tipo === 'ENTRADA_ESPECIAL' ? 'Hoy la entrada es a las ' + horaAmigable(aviso.horaEntrada) +
          (aviso.horaSalida ? ' y la salida a las ' + horaAmigable(aviso.horaSalida) : '') + '. ' + (aviso.detalle || '')
        : 'Hoy no se espera que marques. ' + (aviso.detalle || '')) + '</p></div>';
  }

  const t = turnoActual(ahora);
  const abierto = t && turnoAbierto(t, ahora);
  const siguiente = abierto ? 'SALIDA' : (t && t.fecha === hoy && t.salida ? null : 'ENTRADA');
  const trabajado = minutosTrabajados(t, ahora);
  const metaDia = (t && t.meta) || meta();
  const deAyer = t && t.fecha !== hoy;

  html += '<div class="tarjeta"><div class="fila"><h2 class="crece">' + (deAyer ? 'Turno de ayer (nocturno)' : 'Hoy') + '</h2>' +
    (t && t.actividad ? '<span class="etiqueta" style="color:var(--granate)">Actividad especial</span>'
      : t && t.puntual === true ? '<span class="etiqueta exito">Puntual</span>'
        : t && t.puntual === false ? '<span class="etiqueta alerta">Tarde</span>' : '') + '</div>' +
    '<div class="rejilla-3">' + dato('Entrada', t ? horaAmigable(t.entrada) : '--:--') +
    dato('Salida', t && t.salida ? horaAmigable(t.salida) : '--:--') +
    dato('Horas', t && t.entrada ? duracion(trabajado) : '—', 'horas-hoy') + '</div>';
  if (t && t.entrada) {
    const faltan = Math.max(0, metaDia - trabajado);
    html += '<div class="barra' + (faltan === 0 ? ' ok' : '') + '"><span style="width:' + Math.min(100, trabajado * 100 / Math.max(1, metaDia)) + '%"></span></div>' +
      '<p class="chico ' + (faltan === 0 ? 'exito' : '') + '">' + (faltan === 0 ? 'Meta de ' + duracion(metaDia) + ' cumplida ✓'
        : (t.salida ? 'Faltaron ' : 'Te faltan ') + duracion(faltan) + ' para la meta de ' + duracion(metaDia) + '.') + '</p>';
    const extra = t.salida ? Math.max(0, trabajado - metaDia) : 0;
    if (extra > 0) html += '<p class="chico exito">⏱ Horas extra: ' + duracion(extra) + '</p>';
    if (t.minutosTarde > 0) html += '<p class="chico alerta">Llegaste ' + duracion(t.minutosTarde) + ' después de tu hora.</p>';
    if (t.pendiente) html += '<p class="chico alerta">📶 Guardado sin conexión: se enviará solo.</p>';
  }
  html += '</div>';

  if (siguiente) {
    html += '<button class="boton grande' + (siguiente === 'SALIDA' ? ' granate' : '') + '" data-accion="marcar" data-valor="' + siguiente + '"' +
      (estado.ocupado ? ' disabled' : '') + '>' + (estado.ocupado ? 'Verificando…' : (siguiente === 'ENTRADA' ? '➜ Marcar entrada' : '⇠ Marcar salida')) +
      (estado.actividad && !estado.ocupado ? '<br><small style="font-size:.75rem">Actividad especial</small>' : '') + '</button>';
    html += '<div class="tarjeta"><label class="interruptor"><input type="checkbox" id="actividad"' + (estado.actividad ? ' checked' : '') + '>' +
      '<span class="crece"><b>Estoy en una actividad especial</b><br><span class="chico">Excursión, evento o capacitación fuera del colegio. La dirección revisará las horas.</span></span></label></div>';
    if (estado.conObservacion || estado.actividad) {
      html += '<label>' + (estado.actividad ? 'Describe la actividad (obligatorio)' : 'Observación') +
        '<textarea id="observacion" maxlength="300" placeholder="' + (estado.actividad ? 'Ej.: excursión al museo con 3.º básico' : 'Ej.: llegué tarde por tráfico…') + '">' +
        esc(estado.observacion) + '</textarea></label>';
    } else {
      html += '<button class="boton texto" style="align-self:center" data-accion="conObservacion">+ Agregar observación</button>';
    }
    html += '<p class="chico centro">Se usará tu ubicación actual' + (almacen.leer('credencial', null) ? ' y se pedirá Face ID / huella' : '') + '.</p>';
  } else {
    html += '<div class="tarjeta"><h2>✅ Jornada terminada</h2><p class="chico">Registraste ' + duracion(trabajado) + ' hoy. ¡Hasta mañana!</p></div>';
  }

  if (estado.pendientes.length) {
    html += '<div class="tarjeta"><div class="fila"><p class="crece">📶 ' + estado.pendientes.length + ' registro(s) sin enviar</p>' +
      '<button class="boton texto" data-accion="actualizar">Enviar</button></div></div>';
  }

  const e = estadisticas('semana');
  html += '<div class="fila"><h2 class="crece">Esta semana</h2><button class="boton texto" data-accion="pestana" data-valor="resumen">Ver más</button></div>' +
    '<div class="rejilla-3">' + mini('🔥 ' + e.racha, 'Racha puntual') + mini(e.puntualidad == null ? '—' : e.puntualidad + ' %', 'Puntualidad') +
    mini(e.extra ? duracion(e.extra) : '0', 'Horas extra') + '</div>';
  html += tarjetaRanking() + destacados() + tarjetaInstalar();
  return html;
}

function destacados() {
  const d = perfil().destacados;
  if (!d || (!d.mejorRacha && !d.masHoras)) return '';
  return '<div class="tarjeta"><h3 style="color:var(--granate)">🏆 Destacados de la semana</h3>' +
    (d.mejorRacha ? '<div class="fila"><span style="font-size:1.4rem">🔥</span><div class="crece"><p class="chico">Mejor racha</p><p><b>' +
      esc(d.mejorRacha.nombre) + '</b></p></div><b>' + d.mejorRacha.valor + (d.mejorRacha.valor === 1 ? ' día' : ' días') + '</b></div>' : '') +
    (d.masHoras ? '<div class="fila"><span style="font-size:1.4rem">⏱</span><div class="crece"><p class="chico">Más horas</p><p><b>' +
      esc(d.masHoras.nombre) + '</b></p></div><b>' + duracion(d.masHoras.minutos) + '</b></div>' : '') + '</div>';
}

function pantallaResumen() {
  const sub = estado.subResumen;
  let html = '<h1>Resumen</h1><div class="chips">' +
    '<button class="chip' + (sub === 'resumen' ? ' activo' : '') + '" data-accion="subResumen" data-valor="resumen">Resumen</button>' +
    '<button class="chip' + (sub === 'historial' ? ' activo' : '') + '" data-accion="subResumen" data-valor="historial">Historial</button></div>';
  if (sub === 'historial') return html + historial();

  const periodo = estado.periodo || 'semana';
  const e = estadisticas(periodo);
  html += '<div class="chips"><button class="chip' + (periodo === 'semana' ? ' activo' : '') + '" data-accion="periodo" data-valor="semana">Esta semana</button>' +
    '<button class="chip' + (periodo === 'mes' ? ' activo' : '') + '" data-accion="periodo" data-valor="mes">Este mes</button></div>';
  if (!e.dias) return html + '<div class="tarjeta centro"><h3>Aún no hay registros en este periodo</h3><p class="chico">Cuando marques tu entrada, aquí verás tu puntualidad, tus horas y tu racha.</p></div>';
  const pct = e.puntualidad || 0;
  html += '<div class="tarjeta"><p class="chico">Puntualidad</p><div class="fila"><span style="font-size:2.2rem;font-weight:600">' + pct + ' %</span>' +
    '<span class="chico">' + e.puntuales + ' de ' + (e.puntuales + e.tardes) + ' llegadas a tiempo</span></div>' +
    '<div class="barra' + (pct >= 80 ? ' ok' : '') + '"><span style="width:' + pct + '%"></span></div>' +
    '<p>' + (pct === 100 ? '¡Excelente! Llegaste puntual todos los días. 🎉' : pct >= 80 ? '¡Muy bien! Sigue así.'
      : pct >= 50 ? 'Vas por buen camino: intenta llegar unos minutos antes.' : 'Hay que mejorar: intenta llegar unos minutos antes.') + '</p></div>';
  html += '<div class="rejilla">' +
    mosaico('Días asistidos', e.dias) + mosaico('Horas en total', duracion(e.minutos)) +
    mosaico('Horas extra', e.extra ? duracion(e.extra) : '0', 'Después de la meta') + mosaico('Meta cumplida', e.metaCumplida, 'Días completos') +
    mosaico('Días incompletos', e.incompletos, e.incompletos ? '' : '¡Ninguno!', e.incompletos > 0) +
    mosaico('Llegadas tarde', e.tardes, e.tardes ? duracion(e.minutosTarde) + ' en total' : '¡Ninguna!', e.tardes > 0) +
    mosaico('Racha puntual', '🔥 ' + e.racha, 'Mejor: ' + e.mejorRacha) + mosaico('Actividades especiales', e.actividades, 'Fuera del colegio') +
    mosaico('Salidas sin marcar', e.sinSalida, 'Entrada sin salida', e.sinSalida > 0) + '</div>';
  return html + destacados();
}

function historial() {
  const lista = turnos();
  if (!lista.length) return '<div class="tarjeta centro"><h3>Sin registros todavía</h3><p class="chico">Tus entradas y salidas aparecerán aquí, un día por tarjeta.</p></div>';
  return lista.map(t => {
    const ahora = Date.now();
    const min = minutosTrabajados(t, ahora);
    const cumplio = t.salida ? min >= (t.meta || meta()) : null;
    return '<div class="tarjeta"><div class="fila"><h3 class="crece">' + esc(fechaLegible(t.fecha)) + '</h3>' +
      (t.actividad ? '<span class="etiqueta" style="color:var(--granate)">Actividad</span>'
        : t.puntual === true ? '<span class="etiqueta exito">Puntual</span>' : t.puntual === false ? '<span class="etiqueta alerta">Tarde</span>' : '') + '</div>' +
      '<div class="rejilla-3">' + dato('Entrada', horaAmigable(t.entrada)) +
      dato('Salida', t.salida ? horaAmigable(t.salida) + (terminaDiaSiguiente(t.entrada, t.salida) ? ' (+1)' : '') : '--:--') +
      dato('Horas', t.salida || turnoAbierto(t, ahora) ? duracion(min) : '—') + '</div>' +
      (cumplio === false && !t.actividad ? '<p class="chico alerta">No completó la meta de ' + duracion(t.meta || meta()) + '.</p>' : '') +
      (t.extra > 0 ? '<p class="chico exito">Horas extra: ' + duracion(t.extra) + '</p>' : '') +
      (t.revision ? '<p class="chico">Revisión: ' + esc(t.revision) + '</p>' : '') +
      (t.pendiente ? '<p class="chico alerta">📶 Pendiente de enviar</p>' : '') + '</div>';
  }).join('');
}

function pantallaPermisos() {
  if (estado.solicitudes === null) { cargarSolicitudes(); return '<h1>Permisos</h1><p class="cargando">Cargando…</p>'; }
  const etiquetas = { PENDIENTE: ['Pendiente', 'alerta'], APROBADA: ['Aprobada', 'exito'], RECHAZADA: ['Rechazada', 'error'], CANCELADA: ['Cancelada', 'chico'] };
  let html = '<h1>Permisos</h1><p class="chico">Pide un permiso o vacaciones. Cuando la dirección lo apruebe, se agrega a tu calendario.</p>' +
    '<button class="boton" data-accion="nuevaSolicitud">+ Nueva solicitud</button>';
  if (!estado.solicitudes.length) html += '<div class="tarjeta centro"><h3>Sin solicitudes</h3><p class="chico">Aquí verás el estado de tus solicitudes.</p></div>';
  estado.solicitudes.forEach(s => {
    const e = etiquetas[s.estado] || etiquetas.PENDIENTE;
    html += '<div class="tarjeta"><div class="fila"><h3 class="crece">' + (s.tipo === 'VACACIONES' ? 'Vacaciones' : (s.horaInicio ? 'Permiso por horas' : 'Permiso')) + '</h3>' +
      '<span class="etiqueta ' + e[1] + '">' + e[0] + '</span></div>' +
      '<p>' + esc(s.desde === s.hasta ? fechaLegible(s.desde) : fechaLegible(s.desde) + ' al ' + fechaLegible(s.hasta)) +
      (s.horaInicio ? ' · ' + horaAmigable(s.horaInicio) + ' a ' + horaAmigable(s.horaFin) : '') + '</p>' +
      '<p class="chico">“' + esc(s.motivo) + '”</p>' +
      (s.respuesta ? '<p class="chico">Respuesta: ' + esc(s.respuesta) + '</p>' : '') +
      (s.estado === 'PENDIENTE' ? '<button class="boton texto peligro" style="align-self:flex-start" data-accion="cancelarSolicitud" data-valor="' + esc(s.id) + '">Cancelar solicitud</button>' : '') +
      '<button class="boton texto" style="align-self:flex-start" data-accion="solicitudPdf" data-valor="' + esc(s.id) + '">Descargar PDF para firmar</button>' +
      '</div>';
  });
  return html;
}

function mostrarFormularioSolicitud() {
  const hoy = claveDia(new Date());
  mostrarDialogo('', 'Nueva solicitud', '', [
    { texto: 'Enviar solicitud', accion: 'enviarSolicitud' }, { texto: 'Cancelar', accion: 'cerrarDialogo', secundario: true }],
    '<label>Tipo<select id="sol-tipo" data-cambio="tipoSolicitud"><option value="PERMISO">Permiso</option><option value="VACACIONES">Vacaciones</option></select></label>' +
    '<label class="interruptor" id="sol-horas-fila"><input type="checkbox" id="sol-horas" data-cambio="tipoSolicitud"><span>Por horas (no todo el día)</span></label>' +
    '<label>Desde<input type="date" id="sol-desde" value="' + hoy + '"></label>' +
    '<label id="sol-hasta-fila">Hasta<input type="date" id="sol-hasta" value="' + hoy + '"></label>' +
    '<div class="rejilla" id="sol-horas-campos" hidden><label>De<input type="time" id="sol-inicio"></label><label>A<input type="time" id="sol-fin"></label></div>' +
    '<label>Motivo<textarea id="sol-motivo" maxlength="300" placeholder="Ej.: cita médica"></textarea></label>');
  actualizarFormularioSolicitud();
}

function actualizarFormularioSolicitud() {
  const permiso = $('sol-tipo').value === 'PERMISO';
  const horas = permiso && $('sol-horas').checked;
  $('sol-horas-fila').hidden = !permiso;
  $('sol-hasta-fila').hidden = horas;
  $('sol-horas-campos').hidden = !horas;
}

function pantallaPerfil() {
  const p = perfil();
  const j = p.jornada;
  let html = '<div class="centro" style="display:flex;flex-direction:column;align-items:center;gap:8px">' +
    '<div class="avatar" style="width:76px;height:76px;font-size:1.6rem">' + esc(iniciales(estado.cuenta.nombre)) + '</div>' +
    '<h1>' + esc(estado.cuenta.nombre) + '</h1><p class="chico">' + esc(estado.cuenta.correo) + '</p></div>';

  html += '<div class="tarjeta"><h2>' + (marcaAsistencia() ? 'Mi jornada' : 'Mi rol') + '</h2>' +
    filaDato('Rol', (p.rolInfo && p.rolInfo.nombre) || '—');
  if (marcaAsistencia() && j) {
    html += filaDato('Jornada', j.nombre) + filaDato('Horario', textoHorario(j.entrada, j.salida)) +
      filaDato('Días', textoDias(j.dias || [1, 2, 3, 4, 5]));
    Object.keys(j.porDia || {}).sort().forEach(d => {
      if ((j.dias || []).indexOf(Number(d)) >= 0) html += filaDato(NOMBRES_DIAS[d], textoHorario(j.porDia[d].entrada, j.porDia[d].salida));
    });
    html += filaDato('Meta diaria', (p.horasMeta || 8) + ' h (o lo que dure tu horario)') +
      '<p class="chico">El rol y la jornada los asigna la dirección. Lo que trabajes después de la meta suma como horas extra.</p>';
  } else if (!marcaAsistencia()) {
    html += '<p class="chico">Tus funciones de administración están en la pestaña Admin.</p>';
  }
  html += '<button class="boton texto" style="align-self:flex-start" data-accion="actualizar"' + (estado.ocupado ? ' disabled' : '') + '>Actualizar</button></div>';

  const conCredencial = !!almacen.leer('credencial', null);
  html += '<div class="tarjeta"><h2>Seguridad</h2><label class="interruptor"><input type="checkbox" id="biometria"' +
    (conCredencial ? ' checked' : '') + (biometria ? '' : ' disabled') + '>' +
    '<span class="crece">Confirmar con Face ID / huella<br><span class="chico">' +
    (biometria ? 'Se pide al marcar entrada o salida, para que nadie marque por ti.' : 'Este teléfono no tiene Face ID, huella ni código: se marca solo con la ubicación.') +
    '</span></span></label>' +
    '<label class="interruptor"><input type="checkbox" id="login-huella"' + (conCredencial && almacen.leer('loginHuella', true) !== false ? ' checked' : '') +
    (conCredencial ? '' : ' disabled') + ' data-cambio="loginHuella">' +
    '<span class="crece">Iniciar sesión con Face ID / huella<br><span class="chico">' +
    (conCredencial ? 'Al abrir la app. Si lo desactivas, entras sin confirmar (al marcar se sigue pidiendo).' : 'Activa primero "Confirmar con Face ID / huella".') +
    '</span></span></label></div>';

  html += tarjetaInstalar();
  html += '<div class="tarjeta"><h2>Cuenta</h2><p class="chico">Este teléfono queda registrado con tu correo. ' +
    '¿Cambiaste de teléfono? Libera este y luego inicia sesión en el nuevo. No borres los datos de Safari de esta app.</p>' +
    '<button class="boton secundario" data-accion="liberarTelefono"' + (estado.ocupado ? ' disabled' : '') + '>Liberar este teléfono</button>' +
    '<button class="boton texto" data-accion="cerrarSesion">Cerrar sesión</button></div>';
  return html + creditos(true);
}

/** Créditos. La licencia exige conservar "Creado por 31YC0ND3" en toda copia o versión. */
function creditos(completo) {
  if (!completo) return '<p class="chico centro">Mesón Asiste · Creado por ' + esc(CONFIG.CREADOR) + '</p>';
  return '<div class="tarjeta"><h2>Acerca de</h2>' + filaDato('Aplicación', 'Mesón Asiste ' + CONFIG.VERSION + ' (web)') +
    filaDato('Creado por', CONFIG.CREADOR) + filaDato('Licencia', 'Apache 2.0') +
    '<p class="chico">© 2026 ' + esc(CONFIG.CREADOR) + '. Todos los derechos de autor reservados según la licencia Apache 2.0.</p></div>';
}

// Piezas pequeñas
const iniciales = n => String(n || '?').trim().split(/\s+/).slice(0, 2).map(x => x[0]).join('').toUpperCase();
const dato = (nombre, valor, id) => '<div class="dato"><span class="valor"' + (id ? ' id="' + id + '"' : '') + '>' + esc(valor) + '</span><span class="nombre">' + esc(nombre) + '</span></div>';
const mini = (valor, nombre) => '<div class="tarjeta mini"><div class="valor">' + esc(valor) + '</div><div class="nombre">' + esc(nombre) + '</div></div>';
const filaDato = (a, b) => '<div class="lista-dato"><span>' + esc(a) + '</span><span>' + esc(b) + '</span></div>';
function mosaico(nombre, valor, detalle, alerta) {
  return '<div class="tarjeta" style="gap:4px"><span class="chico">' + esc(nombre) + '</span><span style="font-size:1.3rem;font-weight:600"' +
    (alerta ? ' class="alerta"' : '') + '>' + esc(valor) + '</span>' + (detalle ? '<span class="chico">' + esc(detalle) + '</span>' : '') + '</div>';
}
function textoDias(dias) {
  const o = dias.slice().sort((a, b) => a - b);
  const seguidos = o.length >= 3 && o.every((d, i) => i === 0 || d === o[i - 1] + 1);
  return seguidos ? LETRAS_DIAS[o[0]] + ' a ' + LETRAS_DIAS[o[o.length - 1]] : o.map(d => LETRAS_DIAS[d]).join(' ');
}
function nombreTipo(t) {
  return { VACACIONES: 'Vacaciones', FERIADO: 'Feriado o asueto', PERMISO: 'Permiso', ENTRADA_ESPECIAL: 'Entrada y salida especial' }[t] || 'Calendario';
}

// ---------------------------------------------------------------- Dibujar

const PESTANAS = [
  { id: 'inicio', texto: 'Inicio', icono: '🏠' },
  { id: 'resumen', texto: 'Resumen', icono: '📊', marca: true },
  { id: 'permisos', texto: 'Permisos', icono: '📝', marca: true },
  { id: 'admin', texto: 'Admin', icono: '🛠️', admin: true },
  { id: 'perfil', texto: 'Perfil', icono: '👤' },
];

let biometria = false;

function render() {
  const app = $('app'), nav = $('pestanas');
  let html;
  let conPestanas = false;
  if (!estado.cuenta) html = pantallaAcceso();
  else if (estado.bloqueado) html = pantallaBloqueo();
  else if (estado.accesoCerrado) html = pantallaAccesoCerrado();
  else if (!estado.perfil) html = '<p class="cargando">Cargando tu perfil…</p>' + (estado.ocupado ? '' : '<button class="boton secundario" data-accion="actualizar">Reintentar</button>');
  else {
    conPestanas = true;
    const visibles = PESTANAS.filter(p => (!p.marca || marcaAsistencia()) && (!p.admin || gestionaWeb()));
    if (!visibles.some(p => p.id === estado.pestana)) estado.pestana = 'inicio';
    html = ({ inicio: pantallaInicio, resumen: pantallaResumen, permisos: pantallaPermisos, admin: pantallaAdmin, perfil: pantallaPerfil })[estado.pestana]();
    nav.innerHTML = visibles.map(p => '<button class="' + (p.id === estado.pestana ? 'activa' : '') + '" data-accion="pestana" data-valor="' + p.id + '">' +
      '<span class="icono">' + p.icono + '</span>' + p.texto + '</button>').join('');
  }
  // Conserva lo escrito en el formulario de acceso al redibujar.
  const correoPrevio = $('correo') && $('correo').value, nombrePrevio = $('nombre') && $('nombre').value;
  app.innerHTML = html;
  if (correoPrevio && $('correo')) $('correo').value = correoPrevio;
  if (nombrePrevio && $('nombre')) $('nombre').value = nombrePrevio;
  nav.hidden = !conPestanas;
  actualizarReloj();
}

function actualizarReloj() {
  const h = $('reloj-hora');
  if (!h) return;
  const ahora = new Date();
  h.innerHTML = dos(ahora.getHours()) + ':' + dos(ahora.getMinutes()) + '<small>:' + dos(ahora.getSeconds()) + '</small>';
  $('reloj-fecha').textContent = NOMBRES_DIAS[diaIso(ahora)] + ' ' + ahora.getDate() + ' de ' + MESES[ahora.getMonth()];
}

// ---------------------------------------------------------------- Eventos

document.addEventListener('click', e => {
  const b = e.target.closest('[data-accion]');
  if (!b || b.tagName === 'INPUT' || b.tagName === 'SELECT') return;
  const accion = acciones[b.dataset.accion] || accionesAdmin[b.dataset.accion];
  if (accion) { e.preventDefault(); accion(b); }
});

document.addEventListener('change', e => {
  const t = e.target;
  if (t.id === 'actividad') { estado.actividad = t.checked; render(); }
  else if (t.id === 'biometria') acciones.biometria(t);
  else if (t.dataset && t.dataset.cambio) {
    const accion = acciones[t.dataset.cambio] || accionesAdmin[t.dataset.cambio];
    if (accion) accion(t);
  }
});

document.addEventListener('input', e => {
  if (e.target.id === 'observacion') estado.observacion = e.target.value;
  else if (e.target.dataset && e.target.dataset.entrada && accionesAdmin[e.target.dataset.entrada]) accionesAdmin[e.target.dataset.entrada](e.target);
});

document.addEventListener('keydown', e => {
  if (e.key !== 'Enter' || !['correo', 'nombre', 'sa-clave'].includes(e.target.id)) return;
  e.preventDefault();
  if (e.target.id === 'sa-clave') return acciones.entrarConClave();
  acciones[estado.modoAcceso === 'crear' ? 'crearCuenta' : 'iniciarSesion']();
});

setInterval(actualizarReloj, 1000);
// Cada minuto se actualizan las horas trabajadas (sin redibujar si la persona está escribiendo).
setInterval(() => {
  const activo = document.activeElement;
  if (estado.cuenta && !estado.ocupado && !(activo && ['INPUT', 'TEXTAREA', 'SELECT'].includes(activo.tagName)) && $('dialogo').hidden) render();
}, 60000);

setInterval(() => {
  const libre = document.visibilityState === 'visible' && estado.cuenta && !estado.bloqueado && !estado.ocupado && $('dialogo').hidden;
  if (libre) sincronizar();
}, 180000);
setInterval(() => {
  if (document.visibilityState === 'visible' && estado.pestana === 'admin' && $('dialogo').hidden && !estado.ocupado) cargarSeccionAdmin(true);
}, 120000);

document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'visible' && estado.cuenta && !estado.bloqueado) sincronizar();
});
window.addEventListener('online', () => { if (estado.cuenta && !estado.bloqueado) sincronizar(); });

if ('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js').catch(() => {});

(async () => {
  biometria = await biometriaDisponible();
  render();
  if (estado.cuenta && !estado.bloqueado) sincronizar();
})();
