package INTEGRACIONES

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException

const val DOCUMENTO_EULA = "EULA.md"
const val DOCUMENTO_TERMINOS = "TERMINOS_DE_SERVICIO.md"
const val DOCUMENTO_PRIVACIDAD = "POLITICA_DE_PRIVACIDAD.md"

/** Documentos legales en Markdown que se publican tal cual. */
interface FuenteDocumentosLegales {
    /** Contenido del documento, o null si no existe o no se puede leer. */
    suspend fun leer(nombre: String): String?
}

class FuenteDocumentosLegalesArchivo(private val directorio: File = File("docs/legal")) : FuenteDocumentosLegales {
    private val log = LoggerFactory.getLogger(FuenteDocumentosLegalesArchivo::class.java)

    override suspend fun leer(nombre: String): String? = withContext(Dispatchers.IO) {
        val archivo = File(directorio, nombre)
        if (!archivo.isFile) {
            log.warn("No existe el documento legal {}", archivo.path)
            return@withContext null
        }
        try {
            archivo.readText()
        } catch (e: IOException) {
            log.error("No se pudo leer el documento legal {}", archivo.path, e)
            null
        }
    }
}
