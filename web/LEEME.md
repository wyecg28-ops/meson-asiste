# Mesón Asiste — versión web (iPhone y cualquier teléfono)

Creado por 31YC0ND3 · Licencia Apache 2.0 (ver `LICENSE` y `NOTICE`).

Es la misma app de asistencia, pero se abre desde el navegador y se instala en la pantalla de inicio del iPhone
como una app. Usa **el mismo servidor y la misma hoja de cálculo** que la app de Android.

## Qué incluye

- Iniciar sesión o crear cuenta con el correo del colegio.
- **Marcar entrada y salida** con la ubicación (GPS), incluido el turno nocturno.
- **Código QR** de la entrada (con la cámara), si el colegio lo usa.
- **Actividad especial** y observaciones.
- **Face ID, huella o código del teléfono** (opcional, en Perfil) al abrir y al marcar.
- Marcas **sin internet**: se guardan y se envían solas después.
- **Resumen** de la semana o el mes (puntualidad, horas, horas extra, racha), **historial** y **destacados de la semana**.
- **Permisos**: pedir permisos o vacaciones y ver su estado.
- Pantalla **Acerca de** con el crédito del creador.

## Qué no incluye

- La **administración** (miembros, roles, jornadas, calendario, reportes, ajustes): se hace desde la app de Android.
- La **verificación por Wi-Fi**: los navegadores no pueden leer las redes Wi-Fi. Si el colegio usa Wi-Fi, las marcas
  desde el iPhone quedan *Por revisar*; para el iPhone conviene usar *Solo ubicación* o *Código QR*.
- Los **recordatorios programados** (15 min antes de la entrada, etc.): por ahora solo existen en Android.

## Dónde está publicada

La carpeta `web` se publica sola en **GitHub Pages** desde el repositorio del proyecto:

**https://wyecg28-ops.github.io/meson-asiste/**

Cada vez que se suben cambios de la carpeta `web` (`git push`), GitHub la vuelve a publicar en 1 o 2 minutos
(archivo `.github/workflows/pages.yml`). El avance se ve en la pestaña **Actions** del repositorio.

## Cómo instalarla en el iPhone

1. Abre el enlace en **Safari** (tiene que ser Safari).
2. Toca **Compartir** (el cuadro con la flecha ⬆️) → **Agregar a inicio** → **Agregar**.
3. Ábrela desde el ícono nuevo, inicia sesión y permite la ubicación cuando la pida.

En Android también funciona: en Chrome aparece el botón **Instalar**.

## Importante

- Cada cuenta queda atada a **un solo teléfono**, igual que en Android. Si alguien usaba la app de Android y se pasa
  al iPhone (o al revés), el administrador debe tocar **Liberar teléfono** en *Admin → Miembros*.
- No borrar los datos de Safari ni quitar la app de la pantalla de inicio: el teléfono perdería su registro y habría
  que liberarlo de nuevo.
- Al cambiar la web, sube también el número de `VERSION` en `sw.js` (por ejemplo, de `v1` a `v2`) para que los
  teléfonos descarguen la nueva versión.
- La URL del servidor está en `config.js`. Si algún día cambia, cámbiala ahí y en `Config.kt` de Android.
