/*
 * MesÃ³n Asiste â€” Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raÃ­z del proyecto.
 *
 * Guarda la app en el telÃ©fono para que abra rÃ¡pido y sin internet. Las llamadas al servidor no se guardan.
 * Al publicar cambios, sube el nÃºmero de VERSION para que los telÃ©fonos descarguen la nueva versiÃ³n.
 */
const VERSION = 'meson-asiste-v9';
const ARCHIVOS = [
  './', 'index.html', 'estilos.css', 'config.js', 'admin.js', 'app.js', 'vendor/jsQR.js', 'vendor/qrcode.js',
  'vendor/jspdf.umd.min.js', 'vendor/jspdf.plugin.autotable.min.js', 'manifest.webmanifest',
  'iconos/icono-192.png', 'iconos/icono-512.png', 'iconos/apple-touch-icon.png', 'iconos/logo.png',
];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(VERSION).then(c => c.addAll(ARCHIVOS)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', e => {
  e.waitUntil(caches.keys()
    .then(claves => Promise.all(claves.filter(k => k !== VERSION).map(k => caches.delete(k))))
    .then(() => self.clients.claim()));
});

// Abre al instante con lo guardado en el telÃ©fono y, a la vez, descarga la versiÃ³n nueva para la prÃ³xima vez.
// Sin nada guardado (primera vez), espera a la red. Sin internet, usa lo guardado.
self.addEventListener('fetch', e => {
  const url = new URL(e.request.url);
  if (e.request.method !== 'GET' || url.origin !== self.location.origin) return;
  e.respondWith(caches.open(VERSION).then(cache => cache.match(e.request).then(guardado => {
    const red = fetch(e.request)
      .then(r => { if (r.ok) cache.put(e.request, r.clone()); return r; })
      .catch(() => guardado || cache.match('index.html'));
    if (guardado) e.waitUntil(red.catch(() => {}));
    return guardado || red;
  })));
});
