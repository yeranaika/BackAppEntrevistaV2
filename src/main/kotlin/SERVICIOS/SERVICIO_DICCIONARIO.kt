package SERVICIOS

import CONFIGURACION.CORRECCIONES_MAXIMAS_POR_DICCIONARIO
import CONFIGURACION.LARGO_MAXIMO_AREA
import CONFIGURACION.LARGO_MAXIMO_NOMBRE_DICCIONARIO
import CONFIGURACION.LARGO_MAXIMO_TERMINO
import CONFIGURACION.PATRON_AREA_DICCIONARIO
import CONFIGURACION.TERMINOS_MAXIMOS_POR_DICCIONARIO
import ERRORES.ErrorNoEncontrado
import ERRORES.ErrorValidacion
import ESQUEMAS.SolicitudGuardarDiccionario
import MODELOS.ContenidoDiccionario
import MODELOS.CorreccionTerminologia
import MODELOS.DiccionarioTerminologia
import MODELOS.RepositorioDiccionario

/** [creado] es false cuando reemplazó un paquete que ya existía. */
data class DiccionarioGuardado(val diccionario: DiccionarioTerminologia, val creado: Boolean)

/**
 * Diccionarios de terminología por área. La app los baja por versión (manifiesto + paquete) para sesgar y corregir la
 * transcripción; el panel de administración los crea y edita sin publicar una app nueva.
 */
class ServicioDiccionario(private val diccionarios: RepositorioDiccionario) {

    /** Manifiesto para la app (solo activos) o para el panel ([incluirInactivos]). */
    suspend fun listar(incluirInactivos: Boolean): List<DiccionarioTerminologia> = diccionarios.listar(incluirInactivos)

    /** La app nunca recibe un paquete desactivado: para ella no existe. El panel sí lo ve. */
    suspend fun obtener(area: String, incluirInactivos: Boolean): DiccionarioTerminologia {
        val diccionario = diccionarios.buscar(area)
        if (diccionario == null || (!diccionario.activo && !incluirInactivos)) {
            throw ErrorNoEncontrado("diccionario_no_encontrado", "No hay un diccionario para el área $area")
        }
        return diccionario
    }

    /** Crea el paquete o lo reemplaza (sube la versión y lo reactiva). */
    suspend fun guardar(area: String, solicitud: SolicitudGuardarDiccionario): DiccionarioGuardado {
        val areaValida = validarArea(area)
        val contenido = contenidoValido(solicitud)
        val creado = diccionarios.buscar(areaValida) == null
        return DiccionarioGuardado(diccionarios.guardar(areaValida, contenido, solicitud.versionEsperada), creado)
    }

    /** La app deja de recibirlo; el contenido y su historial de versión se conservan. */
    suspend fun desactivar(area: String) {
        if (!diccionarios.desactivar(validarArea(area))) {
            throw ErrorNoEncontrado("diccionario_no_encontrado", "No hay un diccionario para el área $area")
        }
    }

    private fun validarArea(area: String): String {
        val limpia = area.trim()
        if (limpia.length > LARGO_MAXIMO_AREA || !PATRON_AREA_DICCIONARIO.matches(limpia)) {
            throw ErrorValidacion("area_invalida", "El área usa minúsculas, números y guiones (máx. $LARGO_MAXIMO_AREA), ej. programador-front")
        }
        return limpia
    }

    private fun contenidoValido(s: SolicitudGuardarDiccionario): ContenidoDiccionario {
        val nombre = s.nombre.trim()
        if (nombre.isEmpty() || nombre.length > LARGO_MAXIMO_NOMBRE_DICCIONARIO) {
            throw ErrorValidacion("nombre_invalido", "El nombre debe tener entre 1 y $LARGO_MAXIMO_NOMBRE_DICCIONARIO caracteres")
        }
        if (s.terminos.size > TERMINOS_MAXIMOS_POR_DICCIONARIO || s.correcciones.size > CORRECCIONES_MAXIMAS_POR_DICCIONARIO) {
            throw ErrorValidacion("diccionario_demasiado_grande", "Máximo $TERMINOS_MAXIMOS_POR_DICCIONARIO términos y $CORRECCIONES_MAXIMAS_POR_DICCIONARIO correcciones")
        }
        return ContenidoDiccionario(nombre, terminosLimpios(s.terminos), correccionesLimpias(s.correcciones))
    }

    /** Sin vacíos ni repetidos (sin importar mayúsculas); conserva la primera escritura y el orden. */
    private fun terminosLimpios(terminos: List<String>): List<String> {
        val vistos = HashSet<String>()
        return terminos.map { it.trim() }.filter { termino ->
            if (termino.length > LARGO_MAXIMO_TERMINO) {
                throw ErrorValidacion("termino_invalido", "Un término no puede pasar de $LARGO_MAXIMO_TERMINO caracteres")
            }
            termino.isNotEmpty() && vistos.add(termino.lowercase())
        }
    }

    /** "de" distinto de "a" y sin repetir "de": dos reglas para la misma frase serían ambiguas. */
    private fun correccionesLimpias(correcciones: List<CorreccionTerminologia>): List<CorreccionTerminologia> {
        val vistos = HashSet<String>()
        return correcciones.map { CorreccionTerminologia(it.de.trim(), it.a.trim()) }.filter { c ->
            if (c.de.isEmpty() || c.a.isEmpty() || c.de.length > LARGO_MAXIMO_TERMINO || c.a.length > LARGO_MAXIMO_TERMINO) {
                throw ErrorValidacion("correccion_invalida", "Cada corrección necesita \"de\" y \"a\" de hasta $LARGO_MAXIMO_TERMINO caracteres")
            }
            if (c.de.equals(c.a, ignoreCase = true)) {
                throw ErrorValidacion("correccion_invalida", "La corrección \"${c.de}\" no cambia nada")
            }
            vistos.add(c.de.lowercase())
        }
    }
}
