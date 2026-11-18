# Control de Asistencia — Guía de configuración

**Mesón Asiste** · Creado por 31YC0ND3 · Licencia Apache 2.0 (ver `LICENSE` y `NOTICE`). Toda copia o versión
modificada debe conservar el aviso "Creado por 31YC0ND3".

**Versión para iPhone:** carpeta `web/` (app web instalable). Usa el mismo servidor y la misma hoja. Los pasos para
publicarla e instalarla están en `web/LEEME.md`.

## Cómo funciona

**La primera vez que se abre la app:**
1. **Registro:** el docente escribe su nombre completo y su correo institucional. Se guarda en el teléfono al instante (no necesita internet). Cuando hay conexión con el servidor, se anota en la hoja `Docentes` y se le crea **una hoja con su nombre**.
2. **Permisos:** la app pide el permiso de ubicación precisa.
3. **Identidad:** elige huella o reconocimiento facial y lo confirma una vez.

**Después:** abre la app (ya no se registra de nuevo), escribe una observación si la necesita y toca **ENTRADA** o **SALIDA**, confirmando con su huella o rostro.

Cada marca se guarda primero en el teléfono, con la hora y la ubicación del momento, y aparece en "Mis registros". Si no hay conexión, queda **pendiente** y se envía sola la próxima vez que se abra la app (o con el botón "Enviar pendientes"). En la hoja se usa la hora en que se marcó y se anota que llegó sin conexión. Las marcas con más de 3 días sin enviarse se rechazan.

## La app por dentro (pestañas)

Las pestañas dependen del rol: quien marca asistencia ve *Inicio*, *Resumen* y *Permisos*; quien tiene funciones de administración ve *Admin*; todos ven *Perfil*.

- **Inicio:** reloj, resumen del turno (entrada, salida, horas, horas extra) y **un solo botón** que cambia solo: *Marcar entrada* → *Marcar salida* → *Jornada completa*. Al marcar aparece un aviso: **"¡Felicidades, eres puntual!"** (con la racha de llegadas puntuales) o **"Llegada tarde"** (con los minutos de retraso); en la salida, las horas trabajadas y las horas extra. Abajo, **Destacados de la semana**: la mejor racha y quien sumó más horas entre todo el personal.
- **Resumen** (semana o mes), con dos vistas:
  - *Resumen:* % de puntualidad, días asistidos, horas trabajadas, **horas extra**, llegadas tarde, racha, entrada y jornada promedio, días y salidas sin marcar, y gráfica de horas por día.
  - *Historial:* un día por tarjeta, con su estado de envío.
- **Perfil:** horario de cada día, recordatorios, método de identidad, cuentas. Los *Ajustes del colegio* aparecen si el rol tiene esa función.
- **Admin:** se ven solo las secciones de las funciones del rol: *Hoy*, *Solicitudes*, *Miembros*, *Jornadas*, *Roles*, *Calendario*, *Por revisar* y *Reportes*.

**Notificaciones (se pueden apagar en Perfil), según el horario de cada día:**
- **15 minutos antes de la entrada** si no ha marcado.
- Una alerta si pasa la hora de entrada y sigue sin marcar.
- **10 minutos antes de la salida**, con lo que llevará y si cumple la meta.
- Un aviso de **logro de racha** a los 3, 5, 7, 10, 15, 20, 30… días puntuales seguidos.
- El **resumen semanal** el último día laboral, con sus horas extra y los destacados de la semana.

No se avisa si ya marcó o si tiene el día libre en el calendario.

## Funciones traídas de AsistenciaClientes (solo un establecimiento, todo en la hoja)

- **Solicitudes de permiso:** pestaña *Permisos* del personal (permiso de día completo o por horas, o vacaciones, con motivo; se puede cancelar mientras está pendiente). La administración aprueba o rechaza en *Admin → Solicitudes*; al aprobar se agrega solo al *Calendario*. Hoja `Solicitudes`.
- **Reportes del mes:** *Admin → Reportes* (este mes, el pasado o hace dos). Resumen por persona y detalle por día; botón *Compartir para Excel* (CSV con `;`).
- **Ajustes desde la app (solo administrador):** meta de horas y minutos de gracia (hoja `Configuración`), y la **ubicación del colegio** con *Usar mi ubicación actual* y radio (hoja `Sedes`). Sin ubicación, se puede marcar desde cualquier lugar.
- **Verificación de presencia por sede:** *Solo ubicación*, *Wi-Fi* (si el teléfono no ve la red registrada, la marca queda *Por revisar*) o *Código QR* (QR que cambia cada 30 s en un teléfono o tablet de la entrada, *Mostrar QR*; una foto no sirve). Las marcas por revisar aparecen en *Admin → Por revisar*.
- **Días laborales por jornada:** cada jornada tiene sus días (L a V, L a S…). Afecta faltas, estadísticas, recordatorios y *Hoy*.
- **Roles configurables** (*Admin → Roles*, hoja `Roles`): el administrador crea, edita y elimina roles. En cada rol elige:
  - si **marca asistencia** (entrada, salida, permisos y recordatorios);
  - qué **funciones de administración** tiene: Hoy, Solicitudes, Miembros, Jornadas, Calendario, Por revisar, Reportes y Ajustes.

  Los roles iniciales son Administrador (no marca, todas las funciones), Supervisor (marca, con Hoy, Solicitudes, Calendario, Por revisar y Reportes), Docente, Personal administrativo y Personal de apoyo (marcan, sin funciones). Administrador y Docente no se pueden eliminar, y un rol en uso tampoco.
- **Selector de hora con ruedas** (1:05 p. m.) en jornadas, calendario y permisos, y **PIN o patrón del teléfono** como respaldo de la huella o el rostro.

## Roles, jornadas y reglas (en `1_Configuracion.gs` → `CONFIG`)

- **Roles:** Administrador, Docente, Personal administrativo, Personal de apoyo. El administrador asigna los roles desde la app (o en la columna `Rol` de `Docentes`).
- **Administrador:** la cuenta genérica `admin.prueba@colegiomeson.edu.gt` (en `ADMINISTRADORES`) siempre es administradora. Desde *Admin → Miembros* puede dar el rol a otras personas. No se usan correos personales en el código.
- **Empezar desde cero:** *Ejecutar → `reiniciarTodo`* deja la hoja vacía (solo encabezados). No se puede deshacer. En cada teléfono, *Perfil → Cerrar sesión* borra el historial local.
- **Permisos para varias personas:** en *Admin → Calendario*, "Solo algunas personas" permite marcar a varios miembros a la vez.
- **Roles que no marcan asistencia** (como el Administrador): en la app no ven *Inicio*, *Resumen* ni *Permisos*, no reciben recordatorios y no aparecen en *Admin → Hoy* ni en los reportes.
- **Jornadas:** se crean, editan y eliminan en *Admin → Jornadas* (hoja `Jornadas`). La primera vez se crean A (07:00–16:00) y B (09:30–18:30) desde `JORNADAS_INICIALES`. La gente nueva entra con la primera jornada de la lista. Una jornada con miembros asignados no se puede eliminar.
  - **Horario distinto por día:** con el interruptor *Horario distinto por día* cada día puede tener su propia entrada y salida, por ejemplo el sábado de 08:00 a 12:00 (columna `Horario por día`).
  - **Turno nocturno:** si la salida es más temprano que la entrada (18:00 a 02:00), termina al día siguiente. La salida de madrugada cierra la entrada del día anterior, con un máximo de 20 h entre las dos. Lo mismo vale para la *Entrada y salida especial* del calendario.
  - **Meta de cada día:** `HORAS_META`, o lo que dure el horario de ese día si es menos (un sábado de 4 h pide 4 h).
  - **Horas extra:** todo lo trabajado después de la meta del día. Se suma en Inicio, en Resumen y en *Admin → Reportes*.
- **Miembros activos, inactivos o eliminados:** en *Admin → Miembros*. Un miembro inactivo o eliminado ve "Acceso cerrado" y no puede usar la app (columna `Estado` de `Docentes`). Eliminar conserva su hoja de asistencia.
- **Permisos por horas:** en el calendario, un *Permiso* puede ser de día completo o por horas (columnas `Permiso desde/hasta (hora)`). Las horas del permiso suman para la meta, y si el permiso cubre la hora de entrada, no cuenta como tarde.
- **Puntualidad:** entrada a la hora de su jornada (más `TOLERANCIA_MINUTOS`) o antes. Una *entrada especial* del calendario cambia esa hora ese día.
- **Meta:** `HORAS_META` (8) entre entrada y salida. La hoja anota las horas y si cumplió.
- **Actividad especial:** el miembro activa "Estoy en una actividad especial" y describe la actividad; puede marcar fuera del colegio, no cuenta como tarde, y queda *Por revisar* en `Actividades especiales` hasta que un administrador la apruebe.
- **Calendario:** vacaciones, feriados y permisos no cuentan como falta ni generan recordatorios.

## Estructura de la hoja de cálculo

| Hoja | Contenido |
|---|---|
| `Docentes` | Correo, Nombre, Hoja, Dispositivo, Modelo, Método, Registrado el, Rol, Jornada |
| *Nombre del miembro* (una por persona) | Fecha, Hora de entrada, Correo, Hora de salida, Observación, Ubicación entrada, Ubicación salida, Jornada, Horas en el colegio, Cumplió meta, Actividad especial, Revisión |
| `Roles` | Código, Nombre, Marca asistencia, Funciones |
| `Jornadas` | Código, Nombre, Entrada, Salida, Días, Horario por día |
| `Calendario` | ID, Desde, Hasta, Tipo, Aplica a, Hora de entrada, Detalle, Creado por |
| `Actividades especiales` | ID, Fecha, Correo, Nombre, Entrada, Salida, Descripción, Ubicaciones, Estado, Revisado por, Revisado el |
| `Rechazados` | Intentos no aceptados con su motivo y ubicación |

- La **entrada** crea la fila del día; la **salida** completa esa misma fila.
- **Observación:** lo que escribe el docente al marcar. Si la entrada es después de la hora de su jornada se agrega "Retraso: entrada a las HH:MM (N min)"; si sale con menos de 8 h, "Salida con H:MM en el colegio".
- La hora la pone el servidor, no el teléfono.
- No cambie el nombre de las hojas de los docentes: la columna `Hoja` de `Docentes` las referencia.

## Paso 1 — Conectar la hoja con el script

1. Abra la hoja **Asistencia Docentes — Colegio Mesón** en Drive.
2. **Extensiones → Apps Script**. Cree tres archivos de secuencia de comandos con el mismo nombre y contenido que los de `google-apps-script/`: `1_Configuracion.gs`, `2_Asistencia.gs` y `3_Administracion.gs` (borre el `Código.gs` vacío). Apps Script los une solos.
3. En *Configuración del proyecto* active "Mostrar el archivo de manifiesto `appsscript.json`" y pegue el de esta carpeta.
4. En `1_Configuracion.gs` revise `CONFIG` (`ADMINISTRADORES`, `DOMINIO`, `JORNADAS_INICIALES`). `ID_HOJA` vacío = usa la hoja donde está el script. La ubicación del colegio, la meta y la tolerancia se configuran desde la app (Perfil → Ajustes del colegio).
5. Ejecute una vez la función `configurarHojas` (pedirá permiso para editar la hoja).

## Paso 2 — Publicar el servidor

1. En Apps Script: **Implementar → Nueva implementación → Aplicación web**.
   - Ejecutar como: *Yo*
   - Quién tiene acceso: *Cualquier persona* (el servidor solo acepta marcas de teléfonos registrados en `Docentes`).
2. Copie la URL que termina en `/exec` y péguela en `app/src/main/java/com/example/asistencia/Config.kt` → `SCRIPT_URL`.
3. Vuelva a instalar la app en el teléfono desde Android Studio.

Cada vez que modifique los archivos `.gs`: **Implementar → Gestionar implementaciones → Editar → Nueva versión** (la URL no cambia).

## Administración

- **Docente cambió de teléfono:** borre el valor de la columna `Dispositivo` en su fila de `Docentes`. Se registra de nuevo desde el teléfono nuevo y conserva su hoja.
- **Datos de prueba:** la hoja `Docente Prueba` y su fila en `Docentes` pueden borrarse cuando ya no se necesiten.

## Limitaciones

- Android no entrega la huella ni el rostro a las apps: solo confirma que quien desbloquea es una persona registrada en ese teléfono. La identidad se ata a **correo + teléfono registrado + biometría del teléfono**.
- El correo no se verifica: alguien podría registrarse con el correo de otra persona si lo hace antes que ella. Revise la hoja `Docentes`; si ocurre, borre esa fila y el docente se registra de nuevo.
- Android no deja elegir el sensor: si el teléfono tiene huella y rostro, puede aceptar cualquiera.
- La detección de GPS falso cubre las apps comunes, pero ningún sistema basado en GPS es infalible.
- Las marcas guardadas sin conexión usan la hora del teléfono; el servidor rechaza horas adelantadas.
