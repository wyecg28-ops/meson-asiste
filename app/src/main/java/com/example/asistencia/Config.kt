/*
 * Mesón Asiste — Copyright 2026 31YC0ND3. Creado por 31YC0ND3.
 * Licencia Apache 2.0: ver los archivos LICENSE y NOTICE en la raíz del proyecto.
 */
package com.example.asistencia

object Config {
    /** URL de la implementación web de Google Apps Script (termina en /exec). */
    const val SCRIPT_URL = "https://script.google.com/macros/s/AKfycbz06fz2gBxS3mUeyDcAzIE33aBgtVnwOj0dNVLBP-wicaLiFgzhAhMvvvJ6fIJc2BdV/exec"

    /** Solo se aceptan cuentas de este dominio. */
    const val DOMINIO = "colegiomeson.edu.gt"

    /** Autor de la app. La licencia (LICENSE y NOTICE) exige conservar este crédito en toda copia o versión. */
    const val CREADOR = "31YC0ND3"

    // Las jornadas, la meta de horas y los roles se configuran en el servidor (google-apps-script/1_Configuracion.gs, CONFIG) y los asigna el administrador.
}
