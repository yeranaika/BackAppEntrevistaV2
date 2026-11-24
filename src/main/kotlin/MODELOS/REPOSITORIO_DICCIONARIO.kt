package MODELOS

import ERRORES.ErrorConflicto
import UTILIDADES.SQLSTATE_VALOR_DUPLICADO
import UTILIDADES.transaccion
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.Instant

/** Contenido editable de un paquete: lo que manda el panel. La versión la decide el repositorio. */
data class ContenidoDiccionario(
    val nombre: String,
    val terminos: List<String>,
    val correcciones: List<CorreccionTerminologia>
)

interface RepositorioDiccionario {
    /** Por área. Con [incluirInactivos] false, solo los que la app puede usar. */
    suspend fun listar(incluirInactivos: Boolean): List<DiccionarioTerminologia>

    suspend fun buscar(area: String): DiccionarioTerminologia?

    /**
     * Crea el paquete (versión 1) o lo reemplaza subiendo la versión en 1 y reactivándolo. Si [versionEsperada] viene y ya
     * no es la vigente, lanza ErrorConflicto("diccionario_version_desfasada"): otro editor guardó antes que tú.
     */
    suspend fun guardar(area: String, contenido: ContenidoDiccionario, versionEsperada: Int?): DiccionarioTerminologia

    /** La app deja de recibirlo; el contenido se conserva y sube la versión. false si el área no existe. */
    suspend fun desactivar(area: String): Boolean
}

class RepositorioDiccionarioExposed : RepositorioDiccionario {

    override suspend fun listar(incluirInactivos: Boolean): List<DiccionarioTerminologia> = transaccion {
        val consulta = TablaDiccionarioTerminologia.selectAll()
        (if (incluirInactivos) consulta else consulta.where { TablaDiccionarioTerminologia.activo eq true })
            .orderBy(TablaDiccionarioTerminologia.area to SortOrder.ASC)
            .map { it.aDiccionario() }
    }

    override suspend fun buscar(area: String): DiccionarioTerminologia? = transaccion { buscarEnTransaccion(area) }

    override suspend fun guardar(area: String, contenido: ContenidoDiccionario, versionEsperada: Int?): DiccionarioTerminologia = try {
        guardarUnaVez(area, contenido, versionEsperada)
    } catch (e: ExposedSQLException) {
        // Dos altas simultáneas de la misma área: la segunda ya ve la fila de la primera y se trata como edición.
        if (e.sqlState != SQLSTATE_VALOR_DUPLICADO) throw e
        guardarUnaVez(area, contenido, versionEsperada)
    }

    private suspend fun guardarUnaVez(area: String, contenido: ContenidoDiccionario, versionEsperada: Int?): DiccionarioTerminologia =
        transaccion {
            val ahora = Instant.now()
            val actual = buscarEnTransaccion(area)
            if (actual == null) {
                if (versionEsperada != null && versionEsperada != 0) desfasado(versionEsperada, 0)
                TablaDiccionarioTerminologia.insert {
                    it[TablaDiccionarioTerminologia.area] = area
                    it[nombre] = contenido.nombre
                    it[version] = 1
                    it[activo] = true
                    it[terminos] = contenido.terminos
                    it[correcciones] = contenido.correcciones
                    it[actualizadoEn] = ahora
                }
                return@transaccion DiccionarioTerminologia(area, contenido.nombre, 1, true, contenido.terminos, contenido.correcciones, ahora)
            }
            if (versionEsperada != null && versionEsperada != actual.version) desfasado(versionEsperada, actual.version)
            val nueva = actual.version + 1
            // La condición por versión cierra la carrera: si otro guardó entre la lectura y aquí, no actualiza nada.
            val filas = TablaDiccionarioTerminologia.update({
                (TablaDiccionarioTerminologia.area eq area) and (TablaDiccionarioTerminologia.version eq actual.version)
            }) {
                it[nombre] = contenido.nombre
                it[version] = nueva
                it[activo] = true
                it[terminos] = contenido.terminos
                it[correcciones] = contenido.correcciones
                it[actualizadoEn] = ahora
            }
            if (filas == 0) desfasado(versionEsperada ?: actual.version, actual.version + 1)
            DiccionarioTerminologia(area, contenido.nombre, nueva, true, contenido.terminos, contenido.correcciones, ahora)
        }

    override suspend fun desactivar(area: String): Boolean = transaccion {
        val actual = buscarEnTransaccion(area) ?: return@transaccion false
        if (actual.activo) {
            TablaDiccionarioTerminologia.update({ TablaDiccionarioTerminologia.area eq area }) {
                it[activo] = false
                it[version] = actual.version + 1
                it[actualizadoEn] = Instant.now()
            }
        }
        true
    }

    private fun buscarEnTransaccion(area: String): DiccionarioTerminologia? =
        TablaDiccionarioTerminologia.selectAll().where { TablaDiccionarioTerminologia.area eq area }.limit(1).firstOrNull()?.aDiccionario()

    private fun desfasado(esperada: Int, vigente: Int): Nothing = throw ErrorConflicto(
        "diccionario_version_desfasada",
        "El diccionario cambió (versión $vigente, esperabas $esperada). Vuelve a cargarlo antes de guardar"
    )

    private fun ResultRow.aDiccionario() = DiccionarioTerminologia(
        area = this[TablaDiccionarioTerminologia.area],
        nombre = this[TablaDiccionarioTerminologia.nombre],
        version = this[TablaDiccionarioTerminologia.version],
        activo = this[TablaDiccionarioTerminologia.activo],
        terminos = this[TablaDiccionarioTerminologia.terminos],
        correcciones = this[TablaDiccionarioTerminologia.correcciones],
        actualizadoEn = this[TablaDiccionarioTerminologia.actualizadoEn]
    )
}
